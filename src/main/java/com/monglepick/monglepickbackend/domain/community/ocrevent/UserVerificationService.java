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

@Slf4j
@Service
@RequiredArgsConstructor
public class UserVerificationService {

    private final OcrEventService ocrEventService;
    private final UserVerificationRepository userVerificationRepository;

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

        log.info("[OCR 인증 제출] 완료 — verificationId={}, userId={}, eventId={}, movie={}, date={}, headcount={}, seat={}, theater={}, venue={}",
                saved.getVerificationId(), userId, eventId,
                request.extractedMovieName(), request.extractedWatchDate(), request.extractedHeadcount(),
                request.extractedSeat(), request.extractedTheater(), request.extractedVenue());

        return new UserVerificationDto.SubmitResponse(
                saved.getVerificationId(),
                eventId,
                "영수증이 정상적으로 접수되었습니다. 관리자 검토 후 리워드가 지급됩니다."
        );
    }
}
