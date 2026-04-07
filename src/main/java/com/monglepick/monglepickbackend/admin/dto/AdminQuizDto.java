package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 퀴즈(Quiz) 관리 DTO 모음.
 *
 * <p>퀴즈 상태 전이·수정·삭제 화면에서 사용하는 요청/응답 record를 모두 정의한다.
 * 기존 AdminAiOpsController의 quiz/history(조회)와 quiz/generate(PENDING INSERT)는
 * 그대로 유지하고, 본 DTO는 신규 AdminQuizController에서만 사용한다.</p>
 *
 * <h3>포함 DTO 목록</h3>
 * <ul>
 *   <li>{@link UpdateQuizRequest}     — 퀴즈 본문/메타 수정 (상태 제외)</li>
 *   <li>{@link UpdateStatusRequest}   — 퀴즈 상태 전이 (PENDING→APPROVED/REJECTED/PUBLISHED)</li>
 *   <li>{@link QuizDetailResponse}    — 퀴즈 단건 응답</li>
 * </ul>
 *
 * <h3>상태 전이 정책</h3>
 * <pre>
 * PENDING   → APPROVED, REJECTED   (검수)
 * APPROVED  → PUBLISHED, REJECTED  (출제 또는 회수)
 * REJECTED  → PENDING              (재검수 회부)
 * PUBLISHED → REJECTED             (긴급 회수)
 * </pre>
 *
 * <p>DTO 자체는 단순 값 객체이며, 전이 가능 여부는 Service 레이어에서 검증한다.</p>
 */
public class AdminQuizDto {

    // ─────────────────────────────────────────────
    // 요청 DTO
    // ─────────────────────────────────────────────

    /**
     * 퀴즈 본문/메타 수정 요청 DTO (상태 제외).
     *
     * <p>movieId/question/explanation/correctAnswer/options/rewardPoint/quizDate를
     * 일괄 수정한다. 상태 전이는 별도 EP({@link UpdateStatusRequest})로 처리한다.</p>
     */
    public record UpdateQuizRequest(
            @Size(max = 50, message = "영화 ID는 최대 50자입니다.")
            String movieId,

            @NotBlank(message = "퀴즈 문제는 필수입니다.")
            String question,

            String explanation,

            @NotBlank(message = "정답은 필수입니다.")
            @Size(max = 500, message = "정답은 최대 500자입니다.")
            String correctAnswer,

            /**
             * 선택지 JSON 문자열 (객관식). 주관식은 null/공백 허용.
             * 예: {@code ["A","B","C","D"]}
             */
            String options,

            @Min(value = 0, message = "보상 포인트는 0 이상이어야 합니다.")
            Integer rewardPoint,

            /**
             * 출제 예정일 (YYYY-MM-DD).
             * null이면 즉시 출제 가능.
             */
            LocalDate quizDate
    ) {}

    /**
     * 퀴즈 상태 전이 요청 DTO.
     *
     * <p>{@code targetStatus}는 Quiz.QuizStatus enum 이름 문자열
     * (PENDING/APPROVED/REJECTED/PUBLISHED).</p>
     */
    public record UpdateStatusRequest(
            @NotBlank(message = "변경할 상태(targetStatus)는 필수입니다.")
            String targetStatus
    ) {}

    // ─────────────────────────────────────────────
    // 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * 퀴즈 단건 응답 DTO (관리자 상세 화면 + CUD 결과 반환용).
     */
    public record QuizDetailResponse(
            Long quizId,
            String movieId,
            String question,
            String explanation,
            String correctAnswer,
            String options,
            Integer rewardPoint,
            String status,
            LocalDate quizDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
