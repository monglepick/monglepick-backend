package com.monglepick.monglepickbackend.domain.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 이벤트 로그 저장 요청 DTO.
 *
 * <p>클라이언트(monglepick-client) 또는 AI Agent(monglepick-agent)가
 * 사용자 행동 이벤트를 기록할 때 전송하는 요청 바디다.</p>
 *
 * <h3>이벤트 유형 예시 (eventType)</h3>
 * <ul>
 *   <li>{@code click}        — 영화 카드 클릭</li>
 *   <li>{@code view}         — 영화 상세 페이지 조회</li>
 *   <li>{@code hover}        — 영화 카드 마우스 호버</li>
 *   <li>{@code search}       — 검색어 입력</li>
 *   <li>{@code trailer_play} — 예고편 재생</li>
 *   <li>{@code skip}         — 추천 영화 건너뛰기</li>
 *   <li>{@code rate}         — 영화 평가(별점)</li>
 *   <li>{@code recommend}    — 추천 결과 수신</li>
 * </ul>
 *
 * <h3>metadata 형식 예시</h3>
 * <pre>{@code
 * {"source": "chat", "session_id": "abc-123", "position": 1}
 * {"query": "액션 영화", "result_count": 5}
 * }</pre>
 *
 * @param eventType      이벤트 유형 (필수, 최대 50자)
 * @param movieId        관련 영화 ID (nullable, 영화와 무관한 이벤트인 경우 생략)
 * @param recommendScore 추천 당시 점수 (nullable, 추천 이벤트에서만 전달)
 * @param metadata       추가 메타데이터 — JSON 문자열 형식 (nullable)
 */
public record EventLogRequest(

        /**
         * 이벤트 유형 (필수, 최대 50자).
         * click · view · hover · search · trailer_play · skip · rate · recommend 등
         */
        @NotBlank(message = "이벤트 유형은 필수입니다")
        @Size(max = 50, message = "이벤트 유형은 50자 이하여야 합니다")
        String eventType,

        /**
         * 관련 영화 ID (nullable, VARCHAR(50)).
         * 영화와 무관한 이벤트(예: 검색)인 경우 null 또는 생략 가능.
         */
        @Size(max = 50, message = "영화 ID는 50자 이하여야 합니다")
        String movieId,

        /**
         * 추천 당시 점수 (nullable).
         * 추천 이벤트인 경우 해당 영화의 추천 점수를 함께 전달한다.
         */
        Float recommendScore,

        /**
         * 추가 메타데이터 — JSON 문자열 형식 (nullable).
         * 이벤트에 대한 부가 정보를 자유롭게 담는다.
         * 예: {@code {"source":"chat","session_id":"uuid","position":1}}
         */
        String metadata

) {}
