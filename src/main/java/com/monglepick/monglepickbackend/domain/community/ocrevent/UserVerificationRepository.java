package com.monglepick.monglepickbackend.domain.community.ocrevent;

import com.monglepick.monglepickbackend.domain.community.entity.UserVerification;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 유저 OCR 실관람 인증 리포지토리 (2026-04-14 신규).
 *
 * <p>영수증 업로드 기반 인증 제출({@code POST /api/v1/ocr-events/{eventId}/verify})의
 * 중복 방지를 담당한다. OCR 추출 결과 칼럼은 현재 Agent 체인이 없으므로
 * 저장 시 null 로 비워두고, 관리자 검토 흐름이 추가되면 별도 마이그레이션으로
 * status/reviewedBy 등의 컬럼을 추가할 예정이다.</p>
 */
public interface UserVerificationRepository extends JpaRepository<UserVerification, Long> {

    /**
     * 특정 사용자의 OCR 실관람 인증 제출 수를 집계한다.
     *
     * <p>CSV/관리자 등록 업적 {@code ocr_*} 진행률 계산에 사용한다.</p>
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 OCR 인증 제출 수
     */
    long countByUserId(String userId);

    /**
     * 같은 사용자가 같은 이벤트에 이미 인증을 제출했는지 확인.
     *
     * <p>{@code event_id} 컬럼은 엔티티에서 String 으로 매핑되어 있으므로
     * 호출자는 {@code Long eventId} 를 문자열로 변환해 전달한다.</p>
     *
     * @param userId  사용자 ID (users.user_id)
     * @param eventId 이벤트 ID (ocr_event.event_id 를 문자열로 캐스팅)
     * @return 이미 제출된 인증이 있으면 true
     */
    boolean existsByUserIdAndEventId(String userId, String eventId);
}
