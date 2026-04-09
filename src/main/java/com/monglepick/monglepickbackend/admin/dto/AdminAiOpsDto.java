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
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-08: 리뷰 DTO(ReviewSummary/GenerateReviewRequest/GenerateReviewResponse) 제거 — AI 리뷰 생성 기능 삭제</li>
 *   <li>2026-04-08: 퀴즈 생성 DTO(GenerateQuizRequest/GenerateQuizResponse) 제거 —
 *       POST /admin/ai/quiz/generate 는 monglepick-agent(FastAPI) 로 이관되어 Backend 스텁이 dead code 화</li>
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
}
