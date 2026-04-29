package com.monglepick.monglepickbackend.domain.community.ocrevent;

import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent;
import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent.OcrEventStatus;
import com.monglepick.monglepickbackend.domain.community.entity.UserVerification;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserVerificationService {

    private static final double CONFIDENCE_MIN  = 0.50; // 이 미만이면 제출 차단
    private static final double CONFIDENCE_AUTO = 1.00; // 이 이상이면 자동 승인

    private final OcrEventService ocrEventService;
    private final UserVerificationRepository userVerificationRepository;
    private final RewardService rewardService;

    /**
     * 영수증 인증 제출 — 프론트가 /analyze 에서 추출한 OCR 결과를 그대로 저장한다.
     * OCR 재호출 없이 즉시 커밋하므로 응답 지연이 없다.
     */
    @Transactional
    public UserVerificationDto.SubmitResponse submitVerification(
            String userId,
            Long eventId,
            UserVerificationDto.SubmitRequest request
    ) {
        OcrEvent event = ocrEventService.findEntityById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OCR_EVENT_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        boolean active = event.getStatus() == OcrEventStatus.ACTIVE
                && event.getEndDate() != null
                && event.getEndDate().isAfter(now);
        if (!active) {
            log.warn("[OCR 인증 제출] 비활성 이벤트 — eventId={}, status={}", eventId, event.getStatus());
            throw new BusinessException(ErrorCode.OCR_EVENT_NOT_ACTIVE);
        }

        // 신뢰도 50% 미만 → 제출 차단 (서버 측 방어선)
        Double confidence = request.ocrConfidence();
        if (confidence != null && confidence < CONFIDENCE_MIN) {
            log.warn("[OCR 인증 제출] 신뢰도 미달 차단 — userId={}, eventId={}, confidence={}",
                    userId, eventId, confidence);
            throw new BusinessException(ErrorCode.OCR_CONFIDENCE_TOO_LOW);
        }

        String eventIdStr = String.valueOf(eventId);
        if (userVerificationRepository.existsByUserIdAndEventId(userId, eventIdStr)) {
            log.info("[OCR 인증 제출] 중복 제출 차단 — userId={}, eventId={}", userId, eventId);
            throw new BusinessException(ErrorCode.DUPLICATE_OCR_VERIFICATION);
        }

        UserVerification verification = UserVerification.builder()
                .userId(userId)
                .movieId(event.getMovieId())
                .eventId(eventIdStr)
                .imageId(request.imageUrl())
                .extractedMovieName(request.extractedMovieName())
                .extractedWatchDate(request.extractedWatchDate())
                .parsedText(request.parsedText())
                .extractedHeadcount(request.extractedHeadcount())
                .ocrConfidence(request.ocrConfidence())
                .extractedSeat(request.extractedSeat())
                .extractedTheater(request.extractedTheater())
                .extractedVenue(request.extractedVenue())
                .extractedScreeningTime(request.extractedScreeningTime())
                .extractedWatchedAt(request.extractedWatchedAt())
                .build();
        UserVerification saved = userVerificationRepository.save(verification);

        log.info("[OCR 인증 제출] 완료 — verificationId={}, userId={}, eventId={}, confidence={}, movie={}, date={}, headcount={}, seat={}, theater={}, venue={}",
                saved.getVerificationId(), userId, eventId, confidence,
                request.extractedMovieName(), request.extractedWatchDate(), request.extractedHeadcount(),
                request.extractedSeat(), request.extractedTheater(), request.extractedVenue());

        // 신뢰도 100% → 자동 승인 + 리워드 즉시 지급
        boolean autoApproved = confidence != null && confidence >= CONFIDENCE_AUTO;
        if (autoApproved) {
            saved.approve("AUTO");
            try {
                rewardService.grantReward(userId, "OCR_VERIFY", "verification_" + saved.getVerificationId(), 0);
                log.info("[OCR 인증] 자동 승인 + 리워드 지급 — verificationId={}, userId={}", saved.getVerificationId(), userId);
            } catch (Exception e) {
                log.warn("[OCR 인증] 자동 승인 리워드 지급 실패 (승인은 유지) — verificationId={}, error={}",
                        saved.getVerificationId(), e.getMessage());
            }
        }

        String message = autoApproved
                ? "실관람이 인증되었습니다! 리워드가 자동으로 지급됩니다."
                : "영수증이 정상적으로 접수되었습니다. 관리자 검토 후 리워드가 지급됩니다.";

        return new UserVerificationDto.SubmitResponse(saved.getVerificationId(), eventId, message);
    }
}
