package com.monglepick.monglepickbackend.domain.watchhistory.service;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import com.monglepick.monglepickbackend.domain.watchhistory.dto.WatchHistoryRequest;
import com.monglepick.monglepickbackend.domain.watchhistory.dto.WatchHistoryResponse;
import com.monglepick.monglepickbackend.domain.watchhistory.entity.WatchHistory;
import com.monglepick.monglepickbackend.domain.watchhistory.repository.WatchHistoryRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 시청이력 도메인 전용 서비스.
 *
 * <p>기존 {@code UserService}에서 제공하던 시청이력 조회(getWatchHistory)는
 * 마이페이지 맥락에서 그대로 유지되고, 이 서비스는
 * 시청이력의 <b>추가·삭제</b>와 독립 경로(/api/v1/watch-history) 조회를 전담합니다.</p>
 *
 * <h3>대용량 테이블 주의사항</h3>
 * <p>watch_history 테이블은 26M+ 행의 대용량 테이블입니다.
 * 모든 조회에는 반드시 Pageable을 사용해야 합니다.</p>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 조회 성능 최적화</li>
 *   <li>쓰기 메서드: {@code @Transactional} 으로 오버라이드</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchHistoryService {

    private final WatchHistoryRepository watchHistoryRepository;
    private final UserRepository userRepository;

    // ════════════════════════════════════════════════════════════════
    // 조회 (readOnly = true 클래스 레벨 적용)
    // ════════════════════════════════════════════════════════════════

    /**
     * 로그인한 사용자의 시청이력을 페이징 조회합니다.
     *
     * <p>대용량 테이블이므로 Pageable은 호출자(컨트롤러)에서 반드시 크기 제한을 설정해야 합니다.
     * 기본 정렬은 컨트롤러의 {@code @PageableDefault}에서 watchedAt DESC로 지정합니다.</p>
     *
     * @param userId   JWT에서 추출한 사용자 ID
     * @param pageable 페이징·정렬 정보
     * @return 페이지 단위의 시청이력 응답 DTO
     */
    public Page<WatchHistoryResponse> getWatchHistory(String userId, Pageable pageable) {
        log.debug("시청이력 조회 - userId: {}, page: {}, size: {}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        return watchHistoryRepository.findByUser_UserId(userId, pageable)
                .map(WatchHistoryResponse::from);
    }

    // ════════════════════════════════════════════════════════════════
    // 쓰기 (readOnly 오버라이드)
    // ════════════════════════════════════════════════════════════════

    /**
     * 시청 기록을 추가합니다.
     *
     * <p>동일한 영화를 여러 번 시청하는 경우를 허용합니다(중복 허용).
     * AI 협업 필터링(CF)의 정확도를 위해 실제 시청 횟수를 그대로 기록합니다.</p>
     *
     * <p>{@code watchedAt}이 null이면 서버 수신 시각(LocalDateTime.now())으로 대체합니다.
     * 이는 클라이언트가 시청 직후 기록하는 일반적인 케이스를 지원합니다.</p>
     *
     * @param userId  JWT에서 추출한 사용자 ID
     * @param request 시청 기록 요청 DTO (movieId 필수, watchedAt·rating 선택)
     * @return 저장된 시청이력의 응답 DTO
     * @throws BusinessException {@code USER_NOT_FOUND} — userId에 해당하는 사용자 없음
     */
    @Transactional
    public WatchHistoryResponse addWatchHistory(String userId, WatchHistoryRequest request) {
        // 사용자 조회 — 존재하지 않으면 예외
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // watchedAt null 처리: 클라이언트 미입력 시 현재 시각
        LocalDateTime watchedAt = request.watchedAt() != null
                ? request.watchedAt()
                : LocalDateTime.now();

        // 시청이력 엔티티 생성 및 저장 (Phase 2: watchSource, watchDurationSeconds, completionStatus 추가)
        WatchHistory watchHistory = WatchHistory.builder()
                .user(user)
                .movieId(request.movieId())
                .watchedAt(watchedAt)
                .rating(request.rating())
                .watchSource(request.watchSource())
                .watchDurationSeconds(request.watchDurationSeconds())
                .completionStatus(request.completionStatus())
                .build();

        WatchHistory saved = watchHistoryRepository.save(watchHistory);
        log.info("시청이력 추가 - userId: {}, movieId: {}, watchHistoryId: {}",
                userId, request.movieId(), saved.getWatchHistoryId());

        return WatchHistoryResponse.from(saved);
    }

    /**
     * 특정 시청 기록을 삭제합니다.
     *
     * <p>삭제 대상이 요청한 사용자의 기록인지 소유권을 검증합니다.
     * 타인의 시청 기록을 삭제하려는 시도는 {@code G002 INVALID_INPUT}으로 거부합니다.</p>
     *
     * @param userId         JWT에서 추출한 사용자 ID
     * @param watchHistoryId 삭제할 시청이력 PK
     * @throws BusinessException {@code G002 INVALID_INPUT} — 시청 기록 없음 또는 소유권 불일치
     */
    @Transactional
    public void deleteWatchHistory(String userId, Long watchHistoryId) {
        // 시청이력 조회
        WatchHistory watchHistory = watchHistoryRepository.findById(watchHistoryId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT, "해당 시청 기록을 찾을 수 없습니다"));

        // 소유권 검증 — 본인 기록만 삭제 가능
        if (!watchHistory.getUser().getUserId().equals(userId)) {
            log.warn("시청이력 삭제 권한 없음 - 요청 userId: {}, 기록 소유 userId: {}, watchHistoryId: {}",
                    userId, watchHistory.getUser().getUserId(), watchHistoryId);
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "본인의 시청 기록만 삭제할 수 있습니다");
        }

        watchHistoryRepository.delete(watchHistory);
        log.info("시청이력 삭제 - userId: {}, watchHistoryId: {}", userId, watchHistoryId);
    }
}
