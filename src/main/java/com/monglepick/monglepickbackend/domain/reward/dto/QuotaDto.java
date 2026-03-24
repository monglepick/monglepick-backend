package com.monglepick.monglepickbackend.domain.reward.dto;

/**
 * 등급별 쿼터(이용 한도) 관련 DTO 모음.
 *
 * <p>사용자 등급(BRONZE, SILVER, GOLD, PLATINUM)에 따라 일일/월간 AI 추천 사용 횟수,
 * 무료 사용 횟수, 입력 글자 수 제한 등을 관리하기 위한 DTO를 정의한다.</p>
 *
 * <h3>사용 흐름</h3>
 * <ol>
 *   <li>{@link QuotaService}가 {@code application.yml}의 {@code app.quota.*} 설정을
 *       {@link GradeQuota}로 로드한다.</li>
 *   <li>AI 추천 요청 시 {@link QuotaService#checkQuota}가 사용 이력을 조회하고
 *       {@link QuotaCheckResult}를 반환한다.</li>
 *   <li>{@link QuotaCheckResult}는 {@link PointDto.CheckResponse}에 통합되어
 *       최종 응답으로 반환된다.</li>
 * </ol>
 *
 * @see com.monglepick.monglepickbackend.domain.reward.service.QuotaService
 * @see PointDto.CheckResponse
 */
public final class QuotaDto {

    /** 인스턴스 생성 방지 (유틸리티 클래스 패턴) */
    private QuotaDto() {
    }

    // ──────────────────────────────────────────────
    // 쿼터 확인 결과
    // ──────────────────────────────────────────────

    /**
     * 등급별 쿼터 확인 결과.
     *
     * <p>{@link com.monglepick.monglepickbackend.domain.reward.service.QuotaService#checkQuota}의
     * 반환값으로, 일일/월간 사용 횟수와 한도를 비교하여 사용 가능 여부를 판정한 결과이다.</p>
     *
     * <p>이 결과는 {@link com.monglepick.monglepickbackend.domain.reward.service.PointService#checkPoint}에서
     * {@link PointDto.CheckResponse}에 통합되어 최종 API 응답으로 반환된다.</p>
     *
     * <h4>무료 사용 로직</h4>
     * <p>등급별 일일 무료 횟수(freeDaily)가 설정되어 있으면, 오늘 사용 횟수가 무료 한도 미만인 경우
     * {@code effectiveCost}가 0이 되어 포인트 차감 없이 AI 추천을 이용할 수 있다.</p>
     *
     * <h4>무제한 처리</h4>
     * <p>{@code dailyLimit} 또는 {@code monthlyLimit}이 -1이면 해당 한도는 무제한으로 간주한다.
     * PLATINUM 등급이 이에 해당한다.</p>
     *
     * @param allowed        쿼터 내 사용 가능 여부 (일일/월간 한도 모두 통과 시 true)
     * @param dailyUsed      오늘 AI 추천 사용 횟수 (points_history에서 카운트)
     * @param dailyLimit     일일 한도 (-1이면 무제한)
     * @param monthlyUsed    이번 달 AI 추천 사용 횟수
     * @param monthlyLimit   월간 한도 (-1이면 무제한)
     * @param freeRemaining  오늘 남은 무료 사용 횟수 (0이면 포인트 차감)
     * @param effectiveCost  실제 차감할 포인트 (무료 잔여가 있으면 0, 없으면 baseCost)
     * @param maxInputLength 등급별 최대 입력 글자 수 (BRONZE:200, SILVER:500, GOLD:1000, PLATINUM:2000)
     * @param message        한도 초과 시 사용자에게 표시할 안내 메시지 (정상이면 빈 문자열)
     */
    public record QuotaCheckResult(
            boolean allowed,
            int dailyUsed,
            int dailyLimit,
            int monthlyUsed,
            int monthlyLimit,
            int freeRemaining,
            int effectiveCost,
            int maxInputLength,
            String message
    ) {
    }

    // ──────────────────────────────────────────────
    // 등급별 쿼터 설정값
    // ──────────────────────────────────────────────

    /**
     * 등급별 쿼터 설정값 (application.yml에서 로드).
     *
     * <p>{@code application.yml}의 {@code app.quota.<등급>.*} 값을 1:1 매핑한 불변 레코드이다.
     * {@link com.monglepick.monglepickbackend.domain.reward.service.QuotaService}의 생성자에서
     * {@code @Value} 어노테이션으로 주입받아 {@code Map<String, GradeQuota>}로 관리한다.</p>
     *
     * <h4>등급별 기본 설정 (application.yml)</h4>
     * <table border="1">
     *   <tr><th>등급</th><th>dailyLimit</th><th>monthlyLimit</th><th>freeDaily</th><th>maxInputLength</th></tr>
     *   <tr><td>BRONZE</td><td>3</td><td>30</td><td>0</td><td>200</td></tr>
     *   <tr><td>SILVER</td><td>10</td><td>200</td><td>2</td><td>500</td></tr>
     *   <tr><td>GOLD</td><td>30</td><td>600</td><td>5</td><td>1,000</td></tr>
     *   <tr><td>PLATINUM</td><td>-1(무제한)</td><td>-1(무제한)</td><td>10</td><td>2,000</td></tr>
     * </table>
     *
     * @param dailyLimit     일일 AI 추천 사용 한도 (-1이면 무제한)
     * @param monthlyLimit   월간 AI 추천 사용 한도 (-1이면 무제한)
     * @param freeDaily      일일 무료 AI 추천 횟수 (이 횟수까지는 포인트 차감 없음)
     * @param maxInputLength 등급별 최대 입력 글자 수
     */
    public record GradeQuota(
            int dailyLimit,
            int monthlyLimit,
            int freeDaily,
            int maxInputLength
    ) {
    }
}
