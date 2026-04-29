package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminSupportChatLogDto;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportChatLogDto.DailyCount;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportChatLogDto.IntentCount;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportChatLogDto.LogItem;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportChatLogDto.Summary;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportChatLogDto.TopMessage;
import com.monglepick.monglepickbackend.domain.support.entity.SupportChatLog;
import com.monglepick.monglepickbackend.domain.support.repository.SupportChatLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 페이지 — 고객센터 챗봇 로그 조회·통계 서비스.
 *
 * <p>Agent 가 INSERT 한 {@code support_chat_log} 데이터를 다양한 시각으로 집계해
 * 관리자가 사용자들의 챗봇 사용 패턴을 분석할 수 있도록 한다.</p>
 *
 * <p>모든 메서드는 read-only 트랜잭션. INSERT 는 Agent 직접 SQL.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSupportChatLogService {

    private final SupportChatLogRepository repository;

    /**
     * 페이징 검색 — 모든 필터는 NULL 허용.
     */
    public Page<LogItem> searchLogs(
            String intentKind,
            Boolean needsHuman,
            String userId,
            String keyword,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    ) {
        // 빈 문자열을 NULL 로 정규화 — Repository 쿼리의 :param IS NULL 조건과 호환
        String intentParam = (intentKind == null || intentKind.isBlank()) ? null : intentKind;
        String userIdParam = (userId == null || userId.isBlank()) ? null : userId;
        String keywordParam = (keyword == null || keyword.isBlank()) ? null : keyword;

        Page<SupportChatLog> page = repository.searchLogs(
                intentParam, needsHuman, userIdParam, keywordParam, from, to, pageable
        );
        return page.map(LogItem::from);
    }

    /**
     * 단일 세션 트레이스 — 한 사용자의 대화 흐름 시간순.
     */
    public List<LogItem> getSessionTrace(String sessionId) {
        return repository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(LogItem::from)
                .toList();
    }

    /**
     * 통계 요약 — 대시보드용 종합 응답.
     *
     * <p>집계 항목:</p>
     * <ul>
     *   <li>총 건수 / 1:1 유도 건수 / 비율</li>
     *   <li>의도별 분포 (intent_kind GROUP BY)</li>
     *   <li>일자별 시계열 (DATE 기준)</li>
     *   <li>자주 묻는 질문 TOP 20</li>
     * </ul>
     */
    public Summary getSummary(LocalDateTime from, LocalDateTime to, int topN) {
        // ── 의도별 분포 ──
        List<Object[]> intentRows = repository.countByIntent(from, to);
        List<IntentCount> intentDistribution = intentRows.stream()
                .map(row -> new IntentCount(
                        (String) row[0],
                        ((Number) row[1]).longValue()
                ))
                .toList();

        // ── 일자별 시계열 ──
        List<Object[]> dailyRows = repository.dailyCounts(from, to);
        List<DailyCount> dailyTrend = dailyRows.stream()
                .map(row -> {
                    // Native 쿼리 DATE() 결과는 java.sql.Date — toLocalDate() 로 변환 후 ISO 포맷
                    String dateStr;
                    Object d = row[0];
                    if (d instanceof Date sqlDate) {
                        dateStr = sqlDate.toLocalDate().toString();
                    } else {
                        dateStr = String.valueOf(d);
                    }
                    long total = ((Number) row[1]).longValue();
                    long needsHuman = row[2] == null ? 0L : ((Number) row[2]).longValue();
                    return new DailyCount(dateStr, total, needsHuman);
                })
                .toList();

        // ── TOP 메시지 ──
        List<Object[]> topRows = repository.topUserMessages(from, to, topN);
        List<TopMessage> topMessages = topRows.stream()
                .map(row -> new TopMessage(
                        (String) row[0],
                        ((Number) row[1]).longValue()
                ))
                .toList();

        // ── needs_human 비율 ──
        Object[] ratioRow = repository.needsHumanRatio(from, to);
        // SUM 결과가 NULL 일 수 있으므로 안전 변환
        long needsHumanCount;
        long totalCount;
        if (ratioRow == null || ratioRow.length < 2) {
            needsHumanCount = 0L;
            totalCount = 0L;
        } else if (ratioRow[0] instanceof Object[] inner) {
            // JPQL 다중 컬럼 select 가 어떤 환경에서는 Object[][] 1xN 으로 래핑되는 케이스 방어
            needsHumanCount = inner[0] == null ? 0L : ((Number) inner[0]).longValue();
            totalCount = inner[1] == null ? 0L : ((Number) inner[1]).longValue();
        } else {
            needsHumanCount = ratioRow[0] == null ? 0L : ((Number) ratioRow[0]).longValue();
            totalCount = ratioRow[1] == null ? 0L : ((Number) ratioRow[1]).longValue();
        }
        double ratio = totalCount > 0 ? (double) needsHumanCount / totalCount : 0.0;

        return new Summary(
                totalCount,
                needsHumanCount,
                ratio,
                intentDistribution,
                dailyTrend,
                topMessages
        );
    }
}
