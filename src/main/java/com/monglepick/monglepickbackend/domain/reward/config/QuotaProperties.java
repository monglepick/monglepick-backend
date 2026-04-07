package com.monglepick.monglepickbackend.domain.reward.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 등급별 쿼터(이용 한도) 설정 프로퍼티.
 *
 * <p>{@code application.yml}의 {@code app.quota} 하위 설정을 바인딩한다.
 * QuotaService에서 이 프로퍼티 클래스를 주입받아 등급별 쿼터를 초기화한다.</p>
 *
 * <h3>application.yml 구조 (설계서 v3.2 §4.5 — 6등급 팝콘 테마)</h3>
 * <pre>{@code
 * app:
 *   quota:
 *     normal:
 *       daily-limit: 3
 *       max-input-length: 200
 *     bronze:
 *       daily-limit: 5
 *       max-input-length: 400
 *     silver:
 *       daily-limit: 7
 *       max-input-length: 500
 *     gold:
 *       daily-limit: 10
 *       max-input-length: 800
 *     platinum:
 *       daily-limit: 15
 *       max-input-length: 3000
 *     diamond:
 *       daily-limit: -1
 *       max-input-length: -1
 * }</pre>
 *
 * @param normal   알갱이(NORMAL) 등급 설정 (nullable — 미정의 시 QuotaService에서 기본값 적용)
 * @param bronze   강냉이(BRONZE) 등급 설정
 * @param silver   팝콘(SILVER) 등급 설정
 * @param gold     카라멜팝콘(GOLD) 등급 설정
 * @param platinum 몽글팝콘(PLATINUM) 등급 설정
 * @param diamond  몽아일체(DIAMOND) 등급 설정 (nullable — 미정의 시 무제한 기본값 적용)
 */
@ConfigurationProperties(prefix = "app.quota")
public record QuotaProperties(
        GradeConfig normal,
        GradeConfig bronze,
        GradeConfig silver,
        GradeConfig gold,
        GradeConfig platinum,
        GradeConfig diamond
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
