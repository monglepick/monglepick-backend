package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.QuizDetailResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.UpdateQuizRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.UpdateStatusRequest;
import com.monglepick.monglepickbackend.admin.service.AdminQuizService;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 퀴즈 관리 API 컨트롤러 (수정/상태전이/삭제 전담).
 *
 * <p>기존 {@link AdminAiOpsController}의 {@code /admin/ai/quiz/history}와
 * {@code /admin/ai/quiz/generate}는 그대로 유지하고, 본 컨트롤러는 운영 도구 화면에서
 * 사용할 추가 EP 4개를 제공한다.</p>
 *
 * <h3>담당 엔드포인트</h3>
 * <ul>
 *   <li>GET    /api/v1/admin/quizzes/{id}        — 퀴즈 단건 조회</li>
 *   <li>PUT    /api/v1/admin/quizzes/{id}        — 퀴즈 본문/메타 수정 (상태 제외)</li>
 *   <li>PATCH  /api/v1/admin/quizzes/{id}/status — 퀴즈 상태 전이</li>
 *   <li>DELETE /api/v1/admin/quizzes/{id}        — 퀴즈 삭제 (PENDING/REJECTED만)</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 엔드포인트는 ADMIN 권한이 필요하다.</p>
 */
@Tag(name = "관리자 — 퀴즈 관리", description = "Quiz 수정/상태 전이/삭제 (목록·생성은 AI 운영 컨트롤러 사용)")
@RestController
@RequestMapping("/api/v1/admin/quizzes")
@RequiredArgsConstructor
@Slf4j
public class AdminQuizController {

    /** 관리자 퀴즈 관리 서비스 (수정/상태전이/삭제) */
    private final AdminQuizService adminQuizService;

    /**
     * 퀴즈 단건 조회.
     *
     * @param id 퀴즈 ID
     * @return 200 OK + 퀴즈 응답
     */
    @Operation(summary = "퀴즈 단건 조회", description = "관리자 검수 화면에서 퀴즈 상세 정보를 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> getQuiz(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminQuizService.getQuiz(id)));
    }

    /**
     * 퀴즈 본문/메타 수정.
     *
     * <p>movieId/question/explanation/correctAnswer/options/rewardPoint/quizDate 일괄 수정.
     * 상태 전이는 별도 PATCH /status EP 사용.</p>
     */
    @Operation(
            summary = "퀴즈 수정",
            description = "movieId/question/explanation/correctAnswer/options/rewardPoint/quizDate 일괄 수정. " +
                    "상태(status)는 PATCH /status EP 사용."
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> updateQuiz(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuizRequest request
    ) {
        log.info("[관리자] 퀴즈 수정 요청 — quizId={}", id);
        QuizDetailResponse updated = adminQuizService.updateQuiz(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    /**
     * 퀴즈 상태 전이.
     *
     * <p>허용 전이:
     * <ul>
     *   <li>PENDING   → APPROVED, REJECTED</li>
     *   <li>APPROVED  → PUBLISHED, REJECTED</li>
     *   <li>REJECTED  → PENDING (재검수)</li>
     *   <li>PUBLISHED → REJECTED (긴급 회수)</li>
     * </ul>
     * 잘못된 전이는 400 (INVALID_QUIZ_STATUS_TRANSITION)</p>
     */
    @Operation(
            summary = "퀴즈 상태 전이",
            description = "targetStatus = PENDING/APPROVED/REJECTED/PUBLISHED. 허용되지 않은 전이는 400 응답."
    )
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        log.info("[관리자] 퀴즈 상태 전이 요청 — quizId={}, target={}", id, request.targetStatus());
        QuizDetailResponse updated = adminQuizService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    /**
     * 퀴즈 hard delete (PENDING/REJECTED 상태만 허용).
     *
     * <p>APPROVED/PUBLISHED 퀴즈는 사용자 참여 기록 보호를 위해 삭제 불가.
     * 그 외 상태는 PATCH /status로 REJECTED 전환만 가능.</p>
     */
    @Operation(
            summary = "퀴즈 삭제",
            description = "PENDING/REJECTED 상태만 hard delete 허용. APPROVED/PUBLISHED는 400."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuiz(@PathVariable Long id) {
        log.info("[관리자] 퀴즈 삭제 요청 — quizId={}", id);
        adminQuizService.deleteQuiz(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
