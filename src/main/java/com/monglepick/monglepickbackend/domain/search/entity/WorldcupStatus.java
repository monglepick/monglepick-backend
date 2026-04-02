package com.monglepick.monglepickbackend.domain.search.entity;

/**
 * 이상형 월드컵 세션 상태 열거형.
 *
 * <p>월드컵 토너먼트 진행 세션({@link WorldcupSession})의 현재 상태를 나타낸다.</p>
 *
 * <ul>
 *   <li>{@code IN_PROGRESS} — 진행 중 (아직 최종 우승 영화가 결정되지 않은 상태)</li>
 *   <li>{@code COMPLETED}   — 완료 (최종 우승 영화가 결정된 상태, 리워드 지급 대상)</li>
 *   <li>{@code ABANDONED}   — 중단 (사용자가 완주하지 않고 이탈한 세션)</li>
 * </ul>
 */
public enum WorldcupStatus {

    /** 토너먼트 진행 중 — 아직 최종 우승 영화가 결정되지 않은 상태 */
    IN_PROGRESS,

    /** 토너먼트 완료 — 최종 우승 영화 결정 완료, 리워드 지급 대상 */
    COMPLETED,

    /** 토너먼트 중단 — 사용자가 완주하지 않고 이탈한 세션 */
    ABANDONED
}
