package com.monglepick.monglepickbackend.domain.community.ocrevent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Python OCR 서버(monglepick-recommend) 호출 클라이언트.
 *
 * <p>POST {OCR_SERVER_URL}/api/v1/ocr/analyze 에 이미지 URL을 전송하고
 * 영화명 / 관람일 / 인원 수 / 원문 텍스트 / 신뢰도를 응답받는다.</p>
 *
 * <p>OCR 서버가 응답하지 않아도 예외를 삼키고 null을 반환하므로
 * 유저 제출 플로우는 OCR 실패와 무관하게 항상 성공한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrAnalysisClient {

    private final RestTemplate restTemplate;

    @Value("${ocr.server.url:http://localhost:8001}")
    private String ocrServerUrl;

    public record OcrResponse(
            boolean success,
            String status,
            String movieName,
            String watchDate,
            Integer headcount,
            String seat,
            String screeningTime,
            String theater,
            String venue,
            String watchedAt,
            String parsedText,
            double confidence,
            String errorMessage,
            boolean movieNameOk,
            boolean watchDateOk,
            boolean headcountOk,
            boolean seatOk,
            boolean screeningTimeOk,
            boolean theaterOk,
            boolean venueOk
    ) {}

    /**
     * 영수증 이미지 URL을 OCR 서버에 전송하고 분석 결과를 반환한다.
     *
     * @param imageUrl 분석할 영수증 이미지 URL
     * @param eventId  이벤트 ID (로깅용)
     * @return OCR 분석 결과, 서버 오류 시 null
     */
    @SuppressWarnings("unchecked")
    public OcrResponse analyze(String imageUrl, String eventId) {
        try {
            String url = ocrServerUrl + "/api/v1/ocr/analyze";
            Map<String, String> body = Map.of(
                    "image_url", imageUrl,
                    "event_id", eventId != null ? eventId : ""
            );

            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            if (response == null) return null;

            return new OcrResponse(
                    Boolean.TRUE.equals(response.get("success")),
                    response.get("status") instanceof String s ? s : "FAILED",
                    (String) response.get("movie_name"),
                    (String) response.get("watch_date"),
                    response.get("headcount") instanceof Number n ? n.intValue() : null,
                    (String) response.get("seat"),
                    (String) response.get("screening_time"),
                    (String) response.get("theater"),
                    (String) response.get("venue"),
                    (String) response.get("watched_at"),
                    (String) response.get("parsed_text"),
                    response.get("confidence") instanceof Number n ? n.doubleValue() : 0.0,
                    (String) response.get("error_message"),
                    Boolean.TRUE.equals(response.get("movie_name_ok")),
                    Boolean.TRUE.equals(response.get("watch_date_ok")),
                    Boolean.TRUE.equals(response.get("headcount_ok")),
                    Boolean.TRUE.equals(response.get("seat_ok")),
                    Boolean.TRUE.equals(response.get("screening_time_ok")),
                    Boolean.TRUE.equals(response.get("theater_ok")),
                    Boolean.TRUE.equals(response.get("venue_ok"))
            );

        } catch (Exception e) {
            log.warn("[OCR] 서버 호출 실패 — imageUrl={}, error={}", imageUrl, e.getMessage());
            return null;
        }
    }
}