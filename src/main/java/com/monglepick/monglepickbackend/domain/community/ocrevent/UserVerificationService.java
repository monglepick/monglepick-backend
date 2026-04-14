package com.monglepick.monglepickbackend.domain.community.ocrevent;

import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent;
import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent.OcrEventStatus;
import com.monglepick.monglepickbackend.domain.community.entity.UserVerification;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 유저 OCR 실관람 인증 제출 서비스 (2026-04-14 신규).
 *
 * <p>영화 상세 페이지 상단 배너의 "인증하러 가기" → 영수증 업로드 → 제출 플로우를 담당한다.</p>
 *
 * <h3>현 단계 구현 범위</h3>
 * <ul>
 *   <li>이벤트 존재·ACTIVE 상태·기간 내 검증</li>
 *   <li>같은 유저·같은 이벤트 중복 제출 차단 (409 DUPLICATE_OCR_VERIFICATION)</li>
 *   <li>{@link UserVerification} INSERT — {@code imageId} 에 업로드된 영수증 URL 저장</li>
 * </ul>
 *
 * <h3>후속 작업 (TODO)</h3>
 * <ul>
 *   <li>Agent OCR 체인 연동 — 영수증에서 영화명/관람일/상영관 자동 추출</li>
 *   <li>관리자 검토 큐 — 도장깨기 {@code CourseVerification} 패턴과 같이
 *       status/reviewedBy/reviewedAt 컬럼 추가 후 승인/반려 플로우 도입</li>
 *   <li>리워드 지급 — 승인 시 {@code RewardService} 호출</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserVerificationService {

    /** 이벤트 검증·엔티티 조회용 */
    private final OcrEventService ocrEventService;

    /** 인증 저장·중복 확인용 */
    private final UserVerificationRepository userVerificationRepository;

    /**
     * 영수증 이미지 URL 기반 OCR 인증 제출.
     *
     * <p>이벤트가 {@code ACTIVE} 상태이고 {@code endDate} 이전일 때만 허용한다.
     * {@code READY}(시작 전) 이벤트는 아직 제출할 수 없어 {@code OCR003} 로 거절한다.</p>
     *
     * @param userId  제출자 사용자 ID (JWT principal)
     * @param eventId 대상 이벤트 PK
     * @param request 이미지 URL + 자유 입력 관람일·영화명
     * @return 저장된 인증 PK + 안내 메시지
     * @throws BusinessException OCR_EVENT_NOT_FOUND / OCR_EVENT_NOT_ACTIVE / DUPLICATE_OCR_VERIFICATION
     */
    @Transactional
    public UserVerificationDto.SubmitResponse submitVerification(
            String userId,
            Long eventId,
            UserVerificationDto.SubmitRequest request
    ) {
        // 1) 이벤트 존재성 확인
        OcrEvent event = ocrEventService.findEntityById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OCR_EVENT_NOT_FOUND));

        // 2) 이벤트 상태·기간 검증
        //    - READY 이면 아직 시작 전이므로 거절
        //    - CLOSED 이거나 endDate 초과면 거절
        LocalDateTime now = LocalDateTime.now();
        boolean active = event.getStatus() == OcrEventStatus.ACTIVE
                && event.getEndDate() != null
                && event.getEndDate().isAfter(now);
        if (!active) {
            log.warn("[OCR 인증 제출] 비활성 이벤트 접근 거절 — eventId={}, status={}, endDate={}",
                    eventId, event.getStatus(), event.getEndDate());
            throw new BusinessException(ErrorCode.OCR_EVENT_NOT_ACTIVE);
        }

        // 3) 중복 제출 방지 (같은 유저 + 같은 이벤트)
        //    엔티티의 event_id 컬럼이 String 이므로 숫자 eventId 를 문자열로 변환해 조회한다.
        String eventIdStr = String.valueOf(eventId);
        if (userVerificationRepository.existsByUserIdAndEventId(userId, eventIdStr)) {
            log.info("[OCR 인증 제출] 중복 제출 차단 — userId={}, eventId={}", userId, eventId);
            throw new BusinessException(ErrorCode.DUPLICATE_OCR_VERIFICATION);
        }

        // 4) INSERT — OCR 자동 추출 칼럼(extractedMovieName/WatchDate/parsedText) 중
        //    유저가 수기로 입력한 값만 그대로 저장. parsedText 는 OCR 연동 전까지 null.
        UserVerification verification = UserVerification.builder()
                .userId(userId)
                .movieId(event.getMovieId())
                .eventId(eventIdStr)
                .imageId(request.imageUrl())
                .extractedMovieName(request.movieName())
                .extractedWatchDate(request.watchDate())
                .parsedText(null)
                .build();
        UserVerification saved = userVerificationRepository.save(verification);

        log.info("[OCR 인증 제출] 성공 — verificationId={}, userId={}, eventId={}, movieId={}",
                saved.getVerificationId(), userId, eventId, event.getMovieId());

        return new UserVerificationDto.SubmitResponse(
                saved.getVerificationId(),
                eventId,
                "영수증이 정상적으로 접수되었습니다. 관리자 검토 후 리워드가 지급됩니다."
        );
    }
}
