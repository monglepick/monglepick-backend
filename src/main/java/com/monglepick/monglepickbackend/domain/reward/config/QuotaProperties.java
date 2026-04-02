package com.monglepick.monglepickbackend.domain.reward.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 등급별 쿼터(이용 한도) 설정 프로퍼티.
 *
 * <p>{@code application.yml}의 {@code app.quota} 하위 설정을 바인딩한다.
 * QuotaService에서 이 프로퍼티 클래스를 주입받아 등급별 쿼터를 초기화한다.</p>
 *
 * <h3>application.yml 구조 (설계서 v2.3 §4.5 — 5등급 체계)</h3>
 * <pre>{@code
 * app:
 *   quota:
 *     normal:
 *       daily-limit: 3
 *       monthly-limit: 30
 *       free-daily: 0
 *       max-input-length: 200
 *     bronze:
 *       daily-limit: 5
 *       monthly-limit: 80
 *       free-daily: 1
 *       max-input-length: 300
 *     silver: ...
 *     gold: ...
 *     platinum: ...
 * }</pre>
 *
 * @param normal   NORMAL 등급 설정 (nullable — 미정의 시 QuotaService에서 기본값 적용)
 * @param bronze   BRONZE 등급 설정
 * @param silver   SILVER 등급 설정
 * @param gold     GOLD 등급 설정
 * @param platinum PLATINUM 등급 설정
 */
@ConfigurationProperties(prefix = "app.quota")
public record QuotaProperties(
        GradeConfig normal,
        GradeConfig bronze,
        GradeConfig silver,
        GradeConfig gold,
        GradeConfig platinum
) {
    /**
     * 단일 등급의 쿼터 설정.
     *
     * @param dailyLimit     일일 AI 추천 한도 (-1이면 무제한)
     * @param monthlyLimit   월간 AI 추천 한도 (-1이면 무제한)
     * @param freeDaily      일일 무료 AI 추천 횟수
     * @param maxInputLength 최대 입력 글자 수
     */
    public record GradeConfig(int dailyLimit, int monthlyLimit, int freeDaily, int maxInputLength) {
    }
}
