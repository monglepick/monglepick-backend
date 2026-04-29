package com.monglepick.monglepickbackend.admin.dto;

import com.monglepick.monglepickbackend.domain.support.entity.SupportChatLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 페이지 — 고객센터 챗봇 로그 조회 DTO 모음.
 *
 * <p>Agent 가 INSERT 한 {@link SupportChatLog} 를 관리자 페이지에서 분석할 수 있도록
 * 가공한 응답 객체들. 모든 record 는 read-only.</p>
 */
public class AdminSupportChatLogDto {

    private AdminSupportChatLogDto() {}

    /**
     * 단일 로그 항목 — 페이징 검색 결과 row.
     */
    @Schema(description = "고객센터 챗봇 로그 단건")
    public record LogItem(
            Long id,
            String sessionId,
            String userId,
            boolean guest,
            String userMessage,
            String responseText,
            String intentKind,
            BigDecimal intentConfidence,
            String intentReason,
            boolean needsHuman,
            int hopCount,
            String toolCallsJson,
            LocalDateTime createdAt
    ) {
        public static LogItem from(SupportChatLog log) {
            return new LogItem(
                    log.getId(),
                    log.getSessionId(),
                    log.getUserId(),
                    log.isGuest(),
                    log.getUserMessage(),
                    log.getResponseText(),
                    log.getIntentKind(),
                    log.getIntentConfidence(),
                    log.getIntentReason(),
                    log.isNeedsHuman(),
                    log.getHopCount(),
                    log.getToolCallsJson(),
                    log.getCreatedAt()
            );
        }
    }

    /**
     * 의도별 분포 단일 row.
     */
    @Schema(description = "의도별 건수")
    public record IntentCount(String intentKind, long count) {}

    /**
     * 일자별 시계열 row.
     */
    @Schema(description = "일자별 로그 건수")
    public record DailyCount(String date, long total, long needsHuman) {}

    /**
     * 자주 들어온 발화 row.
     */
    @Schema(description = "자주 묻는 질문 row")
    public record TopMessage(String userMessage, long count) {}

    /**
     * 통계 요약 — 대시보드 위젯용.
     */
    @Schema(description = "고객센터 챗봇 통계 요약")
    public record Summary(
            long totalCount,
            long needsHumanCount,
            double needsHumanRatio,
            List<IntentCount> intentDistribution,
            List<DailyCount> dailyTrend,
            List<TopMessage> topMessages
    ) {}
}
