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
 *
 * <h3>Jackson 자동 매핑 (2026-04-24)</h3>
 * <p>{@link OcrResponse} 에 {@code @JsonNaming(SnakeCaseStrategy)} 를 부착해
 * Python 서버가 반환하는 snake_case JSON(예: {@code movie_name}, {@code watch_date_ok})
 * 을 camelCase record 필드로 자동 매핑한다. 기존의 Map 수동 파싱 + 타입 캐스팅을
 * 제거해 boilerplate 를 대폭 줄였다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrAnalysisClient {

    private final RestTemplate restTemplate;

    @Value("${ocr.server.url:http://localhost:8001}")
    private String ocrServerUrl;

    /**
     * OCR 분석 응답 DTO.
     *
     * <p>snake_case ↔ camelCase 는 {@code @JsonNaming} 으로 자동 처리된다.
     * primitive(double/boolean) 필드는 JSON null 수신 시 Jackson 기본 동작으로
     * 0.0/false 로 세팅되므로 호출 측의 null 방어 로직이 불필요하다.</p>
     *
     * <p>compact constructor 에서 {@code status} 만 기본값 {@code "FAILED"} 로 보정한다.
     * 이는 기존 Map 파싱 구현의 {@code instanceof String ? s : "FAILED"} 휴리스틱과
     * 호환성을 유지하기 위함.</p>
     */
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
    ) {
        public OcrResponse {
            if (status == null) status = "FAILED";
        }
    }

    /**
     * 영수증 이미지 URL을 OCR 서버에 전송하고 분석 결과를 반환한다.
     *
     * @param imageUrl 분석할 영수증 이미지 URL
     * @param eventId  이벤트 ID (로깅용)
     * @return OCR 분석 결과, 서버 오류 시 null
     */
    public OcrResponse analyze(String imageUrl, String eventId) {
        try {
            String url = ocrServerUrl + "/api/v1/ocr/analyze";
            Map<String, String> body = Map.of(
                    "image_url", imageUrl,
                    "event_id", eventId != null ? eventId : ""
            );
            // Jackson 자동 매핑 — Map<String,Object> 수동 파싱 제거 (2026-04-24)
            return restTemplate.postForObject(url, body, OcrResponse.class);
        } catch (Exception e) {
            log.warn("[OCR] 서버 호출 실패 — imageUrl={}, error={}", imageUrl, e.getMessage());
            return null;
        }
    }
}
