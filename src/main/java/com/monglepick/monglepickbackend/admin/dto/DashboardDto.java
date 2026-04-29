package com.monglepick.monglepickbackend.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 대시보드 API DTO 모음.
 *
 * <p>대시보드 화면에서 사용하는 세 가지 응답 그룹을 포함한다.</p>
 * <ul>
 *   <li>{@link KpiResponse} — KPI 카드 (핵심 지표 요약)</li>
 *   <li>{@link TrendsResponse} — 추이 차트 (최근 N일 일별 데이터)</li>
 *   <li>{@link RecentActivitiesResponse} — 최근 활동 피드</li>
 * </ul>
 */
public class DashboardDto {

    // ────────────────────────────────────────────
    // KPI 카드
    // ────────────────────────────────────────────

    /**
     * 대시보드 KPI 카드 응답.
     *
     * <p>각 지표는 오늘/어제 값을 함께 제공하여 전일 대비 변화율을 프론트에서 계산할 수 있도록 한다.</p>
     *
     * <p><b>필드명 컨벤션</b> — 시점(today/yesterday) 접두사를 일관되게 적용하여
     * 프론트엔드 KpiCards 와 동일한 키로 직접 매핑된다. (2026-04-29 정렬)</p>
     *
     * @param totalUsers              전체 회원 수 (users 테이블 COUNT)
     * @param todayNewUsers           오늘 신규 가입 수 (created_at >= 오늘 00:00:00)
     * @param yesterdayNewUsers       어제 신규 가입 수 (전일 비교용)
     * @param activeSubscriptions     현재 활성 구독 수 (status = 'ACTIVE')
     * @param todayPaymentAmount      오늘 결제 완료 금액 합계 (status = 'COMPLETED', 단위: 원)
     * @param yesterdayPaymentAmount  어제 결제 완료 금액 합계 (전일 비교용, 단위: 원)
     * @param pendingReports          미처리 신고 수 (status = 'pending')
     * @param todayAiChats            오늘 AI 채팅 요청 수 — chat_session_archive 의 오늘 created_at 카운트
     */
    public record KpiResponse(
            long totalUsers,
            long todayNewUsers,
            long yesterdayNewUsers,
            long activeSubscriptions,
            long todayPaymentAmount,
            long yesterdayPaymentAmount,
            long pendingReports,
            long todayAiChats
    ) {}

    // ────────────────────────────────────────────
    // 추이 차트
    // ────────────────────────────────────────────

    /**
     * 추이 차트 응답 — 최근 N일 일별 데이터 목록을 포함한다.
     *
     * @param days   조회 기간 (일수, 예: 7)
     * @param trends 일별 추이 데이터 목록 (날짜 오름차순 정렬)
     */
    public record TrendsResponse(
            int days,
            List<DailyTrend> trends
    ) {}

    /**
     * 하루치 추이 데이터.
     *
     * @param date           날짜 문자열 (yyyy-MM-dd, 예: "2026-04-01")
     * @param newUsers        해당 날 신규 가입 수
     * @param paymentAmount   해당 날 결제 완료 금액 합계 (단위: 원)
     * @param chatRequests    해당 날 AI 채팅 요청 수 (미구현 시 0)
     */
    public record DailyTrend(
            String date,
            long newUsers,
            long paymentAmount,
            long chatRequests
    ) {}

    // ────────────────────────────────────────────
    // 최근 활동 피드
    // ────────────────────────────────────────────

    /**
     * 최근 활동 피드 응답 — 여러 도메인의 최근 이벤트를 통합하여 반환한다.
     *
     * @param activities 최근 활동 항목 목록 (최신순 정렬)
     */
    public record RecentActivitiesResponse(
            List<ActivityItem> activities
    ) {}

    /**
     * 단일 최근 활동 항목.
     *
     * <p>결제, 신고, 회원 가입, 리뷰, 게시글 등 다양한 도메인 이벤트를 통합하여 표현한다.</p>
     *
     * @param type        활동 유형 (PAYMENT, REPORT, USER_JOIN, REVIEW, POST)
     * @param description 사람이 읽을 수 있는 활동 설명 (예: "홍길동님이 10,000원 결제 완료")
     * @param targetId    관련 리소스 ID (결제 주문 UUID, 게시글 ID 등; 없으면 null)
     * @param createdAt   활동 발생 시각
     */
    public record ActivityItem(
            String type,
            String description,
            String targetId,
            LocalDateTime createdAt
    ) {}
}
