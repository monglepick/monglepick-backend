package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.DashboardDto;
import com.monglepick.monglepickbackend.admin.repository.AdminChatSessionRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminReportRepository;
import com.monglepick.monglepickbackend.domain.community.entity.PostDeclaration;
import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import com.monglepick.monglepickbackend.domain.payment.repository.UserSubscriptionRepository;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 대시보드 서비스.
 *
 * <p>대시보드 화면에 필요한 세 가지 데이터를 제공한다.</p>
 * <ul>
 *   <li>{@link #getKpi()} — KPI 카드 (핵심 지표 요약)</li>
 *   <li>{@link #getTrends(int)} — 최근 N일 일별 추이 차트</li>
 *   <li>{@link #getRecentActivities(int)} — 최근 활동 피드</li>
 * </ul>
 *
 * <h3>성능 고려사항</h3>
 * <ul>
 *   <li>KPI는 count/sum 쿼리 다수 → 각 리포지토리에 @Query JPQL로 날짜 조건 처리</li>
 *   <li>추이 차트는 날짜별 반복 count → 데이터 규모가 크면 네이티브 GROUP BY 쿼리로 교체 권장</li>
 *   <li>최근 활동 피드는 AdminAuditLog 최신 N건 조회 → 인덱스(created_at) 활용</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 모든 메서드 기본 읽기 전용
public class AdminDashboardService {

    /** 사용자 수 카운트, 날짜 범위 조회 — MyBatis Mapper (JpaRepository 폐기, 설계서 §15) */
    private final UserMapper userMapper;

    /** 결제 금액 합계, 날짜 범위 조회 */
    private final PaymentOrderRepository paymentOrderRepository;

    /** 활성 구독 수 카운트 */
    private final UserSubscriptionRepository userSubscriptionRepository;

    /** 미처리 신고 수 카운트, 최근 신고 조회 */
    private final AdminReportRepository adminReportRepository;

    /** AI 채팅 세션 카운트 — 오늘의 채팅 요청 수 집계 (Phase 4 mock 제거) */
    private final AdminChatSessionRepository adminChatSessionRepository;

    /** 날짜 포맷 (yyyy-MM-dd) — 추이 차트 date 필드에 사용 */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ────────────────────────────────────────────
    // KPI 카드
    // ────────────────────────────────────────────

    /**
     * 대시보드 KPI 카드 데이터를 조회한다.
     *
     * <p>오늘/어제 날짜 경계를 계산하고 각 리포지토리에서 지표를 수집한다.
     * AI 채팅 요청 수는 별도 테이블 미구현이므로 0으로 반환한다.</p>
     *
     * @return KPI 응답 DTO
     */
    public DashboardDto.KpiResponse getKpi() {
        // 오늘 00:00:00 ~ 내일 00:00:00 (exclusive)
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd   = todayStart.plusDays(1);

        // 어제 00:00:00 ~ 오늘 00:00:00 (exclusive)
        LocalDateTime yesterdayStart = todayStart.minusDays(1);

        // 1. 전체 회원 수 (탈퇴 회원 포함 — 관리자는 전수 파악 필요)
        long totalUsers = userMapper.count();

        // 2. 오늘/어제 신규 가입 수 (필드명: todayNewUsers / yesterdayNewUsers — 프론트와 정렬, 2026-04-29)
        long todayNewUsers     = userMapper.countByCreatedAtBetween(todayStart, todayEnd);
        long yesterdayNewUsers = userMapper.countByCreatedAtBetween(yesterdayStart, todayStart);

        // 3. 현재 활성 구독 수 (status = ACTIVE)
        long activeSubscriptions = userSubscriptionRepository.countByStatus(UserSubscription.Status.ACTIVE);

        // 4. 오늘/어제 결제 완료 금액 합계 (status = COMPLETED)
        //    null 안전 처리 — 해당 일자 결제가 없으면 쿼리가 null을 반환할 수 있음
        Long todayAmountRaw = paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, todayStart, todayEnd);
        Long yesterdayAmountRaw = paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                PaymentOrder.OrderStatus.COMPLETED, yesterdayStart, todayStart);
        long todayPaymentAmount     = todayAmountRaw     != null ? todayAmountRaw     : 0L;
        long yesterdayPaymentAmount = yesterdayAmountRaw != null ? yesterdayAmountRaw : 0L;

        // 5. 미처리 신고 수 (status = "pending")
        long pendingReports = adminReportRepository.countByStatus("pending");

        // 6. 오늘 AI 채팅 요청 수 — chat_session_archive 의 created_at 기준 카운트
        //    "오늘 신규 채팅 세션 수"를 의미. 매 턴 Backend `/session/save` fire-and-forget upsert 가 반영.
        //    더 정밀한 턴 기반 측정은 향후 EventLog 통합 시 보강.
        long todayAiChats = adminChatSessionRepository
                .countByCreatedAtBetween(todayStart, todayEnd);

        log.debug("[대시보드 KPI] totalUsers={}, todayNewUsers={}, activeSubscriptions={}, "
                        + "todayPayment={}원, pendingReports={}, todayAiChats={}",
                totalUsers, todayNewUsers, activeSubscriptions,
                todayPaymentAmount, pendingReports, todayAiChats);

        return new DashboardDto.KpiResponse(
                totalUsers,
                todayNewUsers,
                yesterdayNewUsers,
                activeSubscriptions,
                todayPaymentAmount,
                yesterdayPaymentAmount,
                pendingReports,
                todayAiChats
        );
    }

    // ────────────────────────────────────────────
    // 추이 차트
    // ────────────────────────────────────────────

    /**
     * 최근 N일 일별 추이 데이터를 조회한다.
     *
     * <p>오늘 포함 N일치 데이터를 날짜 오름차순으로 반환한다.
     * 각 날짜에 대해 신규 가입 수, 결제 금액을 집계한다.
     * AI 채팅 요청 수는 별도 테이블 미구현이므로 0으로 반환한다.</p>
     *
     * <p>예: days=7이면 6일 전 ~ 오늘(포함) 7일치 데이터를 반환한다.</p>
     *
     * @param days 조회할 일수 (1~90, 기본 7)
     * @return 추이 응답 DTO
     */
    public DashboardDto.TrendsResponse getTrends(int days) {
        // 입력값 방어: 1~90일로 제한
        int safeDays = Math.min(Math.max(days, 1), 90);

        List<DashboardDto.DailyTrend> trends = new ArrayList<>(safeDays);

        // (safeDays-1)일 전 00:00:00부터 오늘 포함하여 safeDays개 날짜를 순회
        LocalDate startDate = LocalDate.now().minusDays(safeDays - 1L);

        for (int i = 0; i < safeDays; i++) {
            LocalDate date      = startDate.plusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end   = start.plusDays(1);

            // 해당 날짜 신규 가입 수
            long newUsers = userMapper.countByCreatedAtBetween(start, end);

            // 해당 날짜 결제 완료 금액 합계 (null → 0)
            Long amountRaw = paymentOrderRepository.sumAmountByStatusAndCreatedAtBetween(
                    PaymentOrder.OrderStatus.COMPLETED, start, end);
            long paymentAmount = amountRaw != null ? amountRaw : 0L;

            // AI 채팅 요청 수 — chat_session_archive 의 created_at 기준 카운트
            // (Phase 4: 기존 하드코딩 0 제거. 일별 신규 채팅 세션 수)
            long chatRequests = adminChatSessionRepository.countByCreatedAtBetween(start, end);

            trends.add(new DashboardDto.DailyTrend(
                    date.format(DATE_FORMATTER),
                    newUsers,
                    paymentAmount,
                    chatRequests
            ));
        }

        log.debug("[대시보드 추이] days={}, 첫날={}", safeDays, startDate.format(DATE_FORMATTER));

        return new DashboardDto.TrendsResponse(safeDays, trends);
    }

    // ────────────────────────────────────────────
    // 최근 활동 피드
    // ────────────────────────────────────────────

    /**
     * 최근 활동 피드를 조회한다.
     *
     * <p>결제 주문, 신고 두 소스에서 최근 N건을 수집하여 통합 정렬(최신순)한다.
     * 각 소스에서 limit건씩 조회 후 병합·정렬·limit 적용한다.</p>
     *
     * <p>향후 게시글, 리뷰 등 소스 추가 시 이 메서드에 수집 로직을 추가한다.</p>
     *
     * @param limit 최근 활동 최대 건수 (1~100, 기본 20)
     * @return 최근 활동 응답 DTO
     */
    public DashboardDto.RecentActivitiesResponse getRecentActivities(int limit) {
        // 입력값 방어: 1~100건으로 제한
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(0, safeLimit);

        List<DashboardDto.ActivityItem> activities = new ArrayList<>();

        // ① 최근 결제 완료 주문
        try {
            List<PaymentOrder> recentOrders = paymentOrderRepository.findTopByOrderByCreatedAtDesc(pageable);
            for (PaymentOrder order : recentOrders) {
                // 결제 유형에 따른 설명 생성
                String desc = buildPaymentDescription(order);
                activities.add(new DashboardDto.ActivityItem(
                        "PAYMENT",
                        desc,
                        order.getPaymentOrderId(),
                        order.getCreatedAt()
                ));
            }
        } catch (Exception e) {
            // 결제 조회 실패 시 해당 소스 건너뜀 (대시보드 전체 중단 방지)
            log.warn("[대시보드 최근활동] 결제 주문 조회 실패: {}", e.getMessage());
        }

        // ② 최근 신고 (상태 무관 — 전체)
        try {
            List<PostDeclaration> recentReports =
                    adminReportRepository.findAllByOrderByCreatedAtDesc(pageable).getContent();
            for (PostDeclaration report : recentReports) {
                String desc = "게시글/댓글 신고 접수 (상태: " + report.getStatus() + ")";
                activities.add(new DashboardDto.ActivityItem(
                        "REPORT",
                        desc,
                        String.valueOf(report.getPostDeclarationId()),
                        report.getCreatedAt()
                ));
            }
        } catch (Exception e) {
            // 신고 조회 실패 시 해당 소스 건너뜀
            log.warn("[대시보드 최근활동] 신고 내역 조회 실패: {}", e.getMessage());
        }

        // 통합 정렬 — createdAt 내림차순 (최신 먼저)
        activities.sort((a, b) -> {
            if (a.createdAt() == null && b.createdAt() == null) return 0;
            if (a.createdAt() == null) return 1;
            if (b.createdAt() == null) return -1;
            return b.createdAt().compareTo(a.createdAt());
        });

        // 최대 safeLimit건만 반환
        List<DashboardDto.ActivityItem> trimmed = activities.size() > safeLimit
                ? activities.subList(0, safeLimit)
                : activities;

        log.debug("[대시보드 최근활동] 수집 {}건 → 반환 {}건", activities.size(), trimmed.size());

        return new DashboardDto.RecentActivitiesResponse(trimmed);
    }

    // ────────────────────────────────────────────
    // 내부 헬퍼
    // ────────────────────────────────────────────

    /**
     * 결제 주문 유형과 상태에 따른 활동 설명 문자열을 생성한다.
     *
     * @param order 결제 주문 엔티티
     * @return 사람이 읽을 수 있는 설명 (예: "POINT_PACK 10,000원 결제 완료")
     */
    private String buildPaymentDescription(PaymentOrder order) {
        String typeLabel = switch (order.getOrderType()) {
            case POINT_PACK   -> "포인트팩";
            case SUBSCRIPTION -> "구독";
        };
        String statusLabel = switch (order.getStatus()) {
            case PENDING             -> "결제 대기";
            case COMPLETED           -> "결제 완료";
            case FAILED              -> "결제 실패";
            case REFUNDED            -> "환불 완료";
            case COMPENSATION_FAILED -> "보상 취소 실패 (수동 조치 필요)";
        };
        // 금액 포맷 — 천 단위 콤마
        String amountFormatted = String.format("%,d", order.getAmount());
        return typeLabel + " " + amountFormatted + "원 " + statusLabel;
    }
}
