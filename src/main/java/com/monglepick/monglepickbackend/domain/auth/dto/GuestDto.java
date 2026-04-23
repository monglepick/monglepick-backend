package com.monglepick.monglepickbackend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 게스트(비로그인) 평생 1회 AI 추천 무료 체험 DTO 모음.
 *
 * <p>관련 EP 3종:</p>
 * <ul>
 *   <li>{@code POST /api/v1/guest/token} — 쿠키 발급, 응답 = {@link GuestTokenResponse}</li>
 *   <li>{@code POST /api/v1/guest/quota/check} — ServiceKey 전용, 요청 = {@link GuestQuotaRequest},
 *       응답 = {@link GuestQuotaCheckResponse}</li>
 *   <li>{@code POST /api/v1/guest/quota/consume} — ServiceKey 전용, 요청 = {@link GuestQuotaRequest},
 *       응답 = {@link GuestQuotaConsumeResponse}</li>
 * </ul>
 *
 * <p>모든 DTO 는 record 로 정의해 JPA Entity 와 혼동을 방지하고 불변성을 보장한다.</p>
 */
public final class GuestDto {

    private GuestDto() {
        /* 인스턴스 생성 방지 */
    }

    /**
     * 쿠키 발급 응답.
     *
     * <p>Client 는 이 응답의 {@code guestId} 를 로컬에 저장할 필요는 없다 —
     * HttpOnly 쿠키 {@code mongle_guest} 자체가 서버-클라이언트 간 단일 진실 원본이기 때문.
     * 다만 디버그/로그 용도로 반환한다.</p>
     *
     * @param guestId 서버가 발급한 UUID 형태의 게스트 식별자
     * @param used    이미 무료 체험을 소비한 게스트인지 여부 (Redis 조회 결과)
     */
    public record GuestTokenResponse(
            String guestId,
            boolean used
    ) {
    }

    /**
     * Agent → Backend 서비스 간 호출 요청 바디 (check/consume 공통).
     *
     * <p>Agent 측에서 쿠키에서 파싱한 guestId 와 실제 클라이언트 IP 를 전달한다.
     * clientIp 는 Nginx 의 {@code X-Forwarded-For} 첫 항목을 사용한다.</p>
     *
     * @param guestId  쿠키에서 추출한 UUID (필수)
     * @param clientIp 실제 클라이언트 IP (필수, 이중 방어선 키로 사용)
     */
    public record GuestQuotaRequest(
            @NotBlank String guestId,
            @NotBlank String clientIp
    ) {
    }

    /**
     * 쿼터 체크 응답.
     *
     * <p>reason 값은 다음 중 하나:
     * <ul>
     *   <li>{@code "OK"} — 아직 소비 기록 없음, 진행 가능</li>
     *   <li>{@code "GUEST_COOKIE_USED"} — 쿠키 키({@code chat:guest_used:{id}}) 에 소비 기록 존재</li>
     *   <li>{@code "GUEST_IP_USED"} — IP 키({@code chat:guest_used_ip:{ip}}) 에 소비 기록 존재</li>
     * </ul>
     *
     * @param allowed true 면 요청 진행 가능, false 면 차단
     * @param reason  상태 설명 코드 (위 3종 중 하나)
     */
    public record GuestQuotaCheckResponse(
            boolean allowed,
            String reason
    ) {
    }

    /**
     * 쿼터 소비 응답.
     *
     * <p>reason 값:
     * <ul>
     *   <li>{@code "OK"} — 쿠키/IP 양쪽 SET 성공 (최초 소비)</li>
     *   <li>{@code "ALREADY_CONSUMED"} — 둘 중 하나라도 이미 존재 (중복 소비 시도)</li>
     * </ul>
     *
     * @param success true 면 신규 소비 성공, false 면 이미 소비된 상태였음
     * @param reason  상태 설명 코드
     */
    public record GuestQuotaConsumeResponse(
            boolean success,
            String reason
    ) {
    }
}
