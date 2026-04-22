package com.monglepick.monglepickbackend.domain.roadmap.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 몽글픽 AI 에이전트(FastAPI) 리뷰 검증 엔드포인트 호출 클라이언트.
 *
 * <p>POST {agent-base-url}/api/v1/admin/ai/review-verification/verify 를 호출하여
 * 영화 줄거리 ↔ 리뷰 유사도를 계산하고 AUTO_VERIFIED / NEEDS_REVIEW / AUTO_REJECTED 를 반환받는다.
 * 에이전트 호출 실패 시 예외를 전파하지 않고 {@link AgentUnavailableException}을 던져
 * 서비스 레이어에서 PENDING 처리로 fallback 할 수 있게 한다.</p>
 */
@Slf4j
@Service
public class ReviewVerificationAgentClient {

    private final RestClient restClient;
    // Transfer-Encoding: chunked → Content-Length 변환을 위해 byte[] 직접 직렬화용 매퍼
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ReviewVerificationAgentClient(
            @Value("${AGENT_BASE_URL:http://localhost:8000}") String agentBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(agentBaseUrl)
                .build();
    }

    // ─────────────────────────────────────────────
    // 요청/응답 DTO (에이전트 API 스펙과 1:1 매핑)
    // ─────────────────────────────────────────────

    public record VerifyRequest(
            long verification_id,
            String user_id,
            String course_id,
            String movie_id,
            Long review_id,
            String review_text,
            String movie_plot
    ) {}

    public record VerifyResponse(
            long verification_id,
            float similarity_score,
            List<String> matched_keywords,
            float confidence,
            String review_status,
            String rationale
    ) {}

    public static class AgentUnavailableException extends RuntimeException {
        public AgentUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ─────────────────────────────────────────────
    // 호출
    // ─────────────────────────────────────────────

    /**
     * AI 에이전트에 리뷰 검증을 요청한다.
     *
     * @param verificationId course_verification PK
     * @param userId         리뷰 작성자 user_id
     * @param courseId       코스 ID
     * @param movieId        영화 ID
     * @param reviewId       course_review PK (nullable)
     * @param reviewText     사용자가 작성한 리뷰 본문
     * @param moviePlot      비교 기준 영화 줄거리
     * @return 에이전트 판정 결과
     * @throws AgentUnavailableException 에이전트 호출 실패 시
     */
    public VerifyResponse verify(long verificationId, String userId, String courseId,
                                 String movieId, Long reviewId,
                                 String reviewText, String moviePlot) {
        try {
            log.info("[ReviewVerificationAgent] 호출 시작 — verificationId={}, movieId={}, reviewLen={}",
                    verificationId, movieId, reviewText != null ? reviewText.length() : 0);

            Map<String, Object> requestMap = new LinkedHashMap<>();
            requestMap.put("verification_id", verificationId);
            requestMap.put("user_id", userId != null ? userId : "");
            requestMap.put("course_id", courseId != null ? courseId : "");
            requestMap.put("movie_id", movieId != null ? movieId : "");
            requestMap.put("review_id", reviewId);
            requestMap.put("review_text", reviewText != null ? reviewText : "");
            requestMap.put("movie_plot", moviePlot != null ? moviePlot : "");

            log.info("[ReviewVerificationAgent] 요청 데이터 — {}", requestMap);

            // Map → byte[]로 직렬화: ByteArrayHttpMessageConverter가 Content-Length를 명시하여
            // Transfer-Encoding: chunked 대신 Content-Length 헤더로 전송된다.
            // (chunked 전송 시 Starlette BaseHTTPMiddleware + asyncio.wait_for 조합에서 body가 유실됨)
            byte[] bodyBytes = MAPPER.writeValueAsBytes(requestMap);

            VerifyResponse response = restClient.post()
                    .uri("/api/v1/admin/ai/review-verification/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(bodyBytes)
                    .retrieve()
                    .body(VerifyResponse.class);

            log.info("[ReviewVerificationAgent] 판정 완료 — verificationId={}, status={}, confidence={}",
                    verificationId,
                    response != null ? response.review_status() : "null",
                    response != null ? response.confidence() : 0);

            return response;

        } catch (Exception e) {
            log.warn("[ReviewVerificationAgent] 호출 실패 — verificationId={}, error={}",
                    verificationId, e.getMessage());
            throw new AgentUnavailableException(
                    "AI 리뷰 검증 에이전트 호출 실패: " + e.getMessage(), e);
        }
    }
}
