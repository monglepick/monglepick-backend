package com.monglepick.monglepickbackend.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 AI 운영 API DTO 모음.
 *
 * <p>관리자 페이지 "AI 운영" 탭의 엔드포인트(퀴즈 이력 조회, 챗봇 세션 목록/메시지/통계)를 지원한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.2 AI 운영 참조.</p>
 *
 * <h3>포함 DTO 목록</h3>
 * <ul>
 *   <li>퀴즈: {@link QuizSummary}</li>
 *   <li>챗봇: {@link ChatSessionSummary}, {@link ChatSessionDetail}, {@link ChatStatsResponse}</li>
 *   <li>리뷰 인증: {@link ReviewVerificationSummary}, {@link ReviewVerificationDetail},
 *       {@link ReviewVerificationOverviewResponse}, {@link ReviewDecisionRequest},
 *       {@link ReverifyResponse}</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-08: 리뷰 DTO(ReviewSummary/GenerateReviewRequest/GenerateReviewResponse) 제거 — AI 리뷰 생성 기능 삭제</li>
 *   <li>2026-04-08: 퀴즈 생성 DTO(GenerateQuizRequest/GenerateQuizResponse) 제거 —
 *       POST /admin/ai/quiz/generate 는 monglepick-agent(FastAPI) 로 이관되어 Backend 스텁이 dead code 화</li>
 *   <li>2026-04-14: 리뷰 인증(ReviewVerification*) DTO 5종 추가 — AI 리뷰 검증 에이전트(추후 구현) 모니터링 탭 신설</li>
 * </ul>
 */
public final class AdminAiOpsDto {

    /** 인스턴스 생성 방지 (유틸리티 클래스 패턴) */
    private AdminAiOpsDto() {
    }

    // ======================== 퀴즈 ========================

    /**
     * 퀴즈 목록 요약 응답 DTO.
     *
     * <p>관리자 AI 퀴즈 이력 탭에서 한 건의 퀴즈를 표현한다.
     * PENDING/APPROVED/REJECTED/PUBLISHED 전 상태가 노출된다.</p>
     *
     * @param quizId         퀴즈 고유 ID
     * @param movieId        대상 영화 ID (nullable)
     * @param question       퀴즈 문제 텍스트
     * @param correctAnswer  정답 문자열 (관리자 화면에서는 노출 — 검수 목적)
     * @param options        선택지 JSON 문자열 (관리자는 원본 그대로 확인)
     * @param rewardPoint    보상 포인트
     * @param status         퀴즈 상태 (PENDING/APPROVED/REJECTED/PUBLISHED)
     * @param quizDate       출제 예정일 (nullable)
     * @param createdAt      생성 시각
     * @param updatedAt      수정 시각
     */
    public record QuizSummary(
            Long quizId,
            String movieId,
            String question,
            String correctAnswer,
            String options,
            Integer rewardPoint,
            String status,
            String quizDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    // 2026-04-08: 리뷰 섹션(ReviewSummary/GenerateReviewRequest/GenerateReviewResponse) 제거 — AI 리뷰 생성 기능 삭제
    // 2026-04-08: GenerateQuizRequest/GenerateQuizResponse 제거 — 퀴즈 생성이 Agent(FastAPI)로 이관됨
    //   (기존 Backend 스텁은 LLM 호출 없이 입력값을 INSERT 만 하던 dead code)

    // ======================== 챗봇 세션 ========================

    /**
     * 챗봇 세션 목록 요약 응답 DTO.
     *
     * <p>관리자 AI 운영 탭에서 한 건의 채팅 세션을 표현한다.</p>
     *
     * @param chatSessionArchiveId 아카이브 PK
     * @param sessionId            세션 UUID
     * @param userId               대화 참여 사용자 ID
     * @param title                세션 제목 (nullable)
     * @param turnCount            대화 턴 수
     * @param recommendedMovieCount 추천 영화 수
     * @param startedAt            세션 시작 시각
     * @param lastMessageAt        마지막 메시지 시각
     * @param isActive             활성 여부
     */
    public record ChatSessionSummary(
            Long chatSessionArchiveId,
            String sessionId,
            String userId,
            String title,
            Integer turnCount,
            Integer recommendedMovieCount,
            LocalDateTime startedAt,
            LocalDateTime lastMessageAt,
            Boolean isActive
    ) {}

    /**
     * 챗봇 세션 상세 응답 DTO.
     *
     * <p>세션 한 건의 전체 메시지 내용과 부가 메타데이터(intentSummary 등)를 포함한다.
     * messages/sessionState/intentSummary 는 JSON 문자열 원본 그대로 전달한다 — 프론트엔드에서 파싱.</p>
     *
     * @param sessionId      세션 UUID
     * @param userId         사용자 ID
     * @param title          세션 제목 (nullable)
     * @param messages       전체 메시지 JSON 문자열 (원본)
     * @param sessionState   Agent 세션 상태 JSON (nullable)
     * @param intentSummary  의도 요약 JSON (nullable)
     * @param turnCount      대화 턴 수
     * @param startedAt      시작 시각
     * @param lastMessageAt  마지막 메시지 시각
     * @param isActive       활성 여부
     */
    public record ChatSessionDetail(
            String sessionId,
            String userId,
            String title,
            String messages,
            String sessionState,
            String intentSummary,
            Integer turnCount,
            LocalDateTime startedAt,
            LocalDateTime lastMessageAt,
            Boolean isActive
    ) {}

    /**
     * 챗봇 세션 통계(선택) 응답 DTO — 향후 확장용.
     *
     * @param totalSessions   총 세션 수
     * @param activeSessions  활성 세션 수
     * @param totalTurns      총 대화 턴 수
     * @param topIntents      의도별 빈도 상위 (예: ["recommend", "search", "general"])
     */
    public record ChatStatsResponse(
            long totalSessions,
            long activeSessions,
            long totalTurns,
            List<String> topIntents
    ) {}

    // ======================== 리뷰 인증 (2026-04-14 추가) ========================

    /**
     * 도장깨기 리뷰 인증 목록 요약 응답 DTO.
     *
     * <p>관리자 페이지 "AI 운영 → 리뷰 인증" 탭 테이블의 한 행을 표현한다.
     * 리뷰 본문 원문은 길이가 길어 목록에서는 {@link ReviewVerificationDetail} 상세 조회 시에만
     * 노출하고, 여기서는 길이 기반 미리보기({@code reviewPreview}, 최대 100자)와 판정 메타데이터만 포함한다.</p>
     *
     * @param verificationId   course_verification 테이블 PK
     * @param userId           인증 요청 사용자 ID
     * @param courseId         코스 ID
     * @param movieId          대상 영화 ID
     * @param reviewPreview    리뷰 본문 미리보기 (최대 100자, nullable)
     * @param similarityScore  줄거리 ↔ 리뷰 유사도 (nullable, 0.0~1.0)
     * @param aiConfidence     AI 종합 신뢰도 (nullable, 0.0~1.0)
     * @param reviewStatus     리뷰 인증 상태 (PENDING/AUTO_VERIFIED/NEEDS_REVIEW/AUTO_REJECTED/ADMIN_APPROVED/ADMIN_REJECTED)
     * @param isVerified       최종 인증 성공 여부 (코스 진행률 반영 여부)
     * @param reviewedBy       관리자 수동 판정자 user_id (nullable — AI 자동이면 null)
     * @param reviewedAt       판정 시각 (nullable)
     * @param createdAt        인증 요청 생성 시각
     */
    public record ReviewVerificationSummary(
            Long verificationId,
            String userId,
            String courseId,
            String movieId,
            String reviewPreview,
            Float similarityScore,
            Float aiConfidence,
            String reviewStatus,
            Boolean isVerified,
            String reviewedBy,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt
    ) {}

    /**
     * 도장깨기 리뷰 인증 상세 응답 DTO.
     *
     * <p>목록에서 행을 클릭해 여는 상세 모달/패널에서 사용한다. 리뷰 본문 전체 + 영화 줄거리 +
     * 매칭된 키워드 JSON + 판정 사유를 함께 내려 관리자가 "왜 이 판정이 나왔는지"를 즉시
     * 검증할 수 있게 한다.</p>
     *
     * @param verificationId   인증 PK
     * @param userId           사용자 ID
     * @param courseId         코스 ID
     * @param movieId          영화 ID
     * @param movieTitle       영화 제목 (nullable — movies 테이블 JOIN 결과, 미존재 시 null)
     * @param moviePlot        영화 줄거리/시놉시스 (nullable)
     * @param reviewText       리뷰 본문 전체 (nullable)
     * @param similarityScore  유사도 점수 (nullable)
     * @param aiConfidence     AI 종합 신뢰도 (nullable)
     * @param matchedKeywords  매칭 키워드 JSON 배열 원문 문자열 (nullable)
     * @param reviewStatus     리뷰 인증 상태
     * @param isVerified       최종 인증 여부
     * @param decisionReason   판정 사유 (AI 또는 관리자, nullable)
     * @param reviewedBy       관리자 수동 판정자 (nullable)
     * @param reviewedAt       판정 시각 (nullable)
     * @param verifiedAt       인증 확정 시각 (nullable — 반려된 경우에도 과거 승인 이력이 있다면 남아있음)
     * @param createdAt        인증 요청 생성 시각
     * @param updatedAt        마지막 수정 시각
     */
    public record ReviewVerificationDetail(
            Long verificationId,
            String userId,
            String courseId,
            String movieId,
            String movieTitle,
            String moviePlot,
            String reviewText,
            Float similarityScore,
            Float aiConfidence,
            String matchedKeywords,
            String reviewStatus,
            Boolean isVerified,
            String decisionReason,
            String reviewedBy,
            LocalDateTime reviewedAt,
            LocalDateTime verifiedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /**
     * 리뷰 인증 탭 상단 KPI 집계 응답 DTO.
     *
     * <p>관리자 화면 상단에 "대기 / 자동 승인 / 검토 필요 / 자동 반려 / 관리자 처리(승인+반려)" 건수를
     * 카드 형태로 노출하기 위한 집계.</p>
     *
     * @param pending        PENDING 건수 (AI 에이전트 판정 대기)
     * @param autoVerified   AUTO_VERIFIED 건수
     * @param needsReview    NEEDS_REVIEW 건수 (관리자 검수 필요)
     * @param autoRejected   AUTO_REJECTED 건수
     * @param adminApproved  관리자 수동 승인 건수
     * @param adminRejected  관리자 수동 반려 건수
     * @param threshold      현재 적용 중인 AI 자동 승인 임계값 (application.yml {@code app.ai.review-verification.threshold})
     */
    public record ReviewVerificationOverviewResponse(
            long pending,
            long autoVerified,
            long needsReview,
            long autoRejected,
            long adminApproved,
            long adminRejected,
            float threshold
    ) {}

    /**
     * 리뷰 인증 수동 판정 요청 DTO (승인/반려 공통).
     *
     * <p>관리자가 승인 또는 반려 버튼을 누를 때 함께 전달하는 사유. 감사 로그에 기록되므로 필수는
     * 아니지만 의미 있는 사유를 남기도록 UI 에서 유도한다. 공백이면 "사유 미기재"로 대체한다.</p>
     *
     * @param reason 판정 사유 (nullable/blank 허용, 최대 500자)
     */
    public record ReviewDecisionRequest(
            String reason
    ) {}

    /**
     * 재검증 요청 응답 DTO.
     *
     * <p>관리자가 "AI 재검증" 버튼을 눌렀을 때 반환되는 최소 응답. 에이전트 구현 전에는
     * {@code agentAvailable=false} 로 응답하여 UI 가 "에이전트 준비 중" 배너를 띄운다.</p>
     *
     * @param verificationId 대상 인증 PK
     * @param reviewStatus   재요청 후 상태 (보통 "PENDING")
     * @param agentAvailable AI 에이전트가 현재 호출 가능한지 (false 면 PENDING 만 남기고 실제 호출은 안 됨)
     * @param message        UI 표시용 한 줄 메시지
     */
    public record ReverifyResponse(
            Long verificationId,
            String reviewStatus,
            boolean agentAvailable,
            String message
    ) {}
}
