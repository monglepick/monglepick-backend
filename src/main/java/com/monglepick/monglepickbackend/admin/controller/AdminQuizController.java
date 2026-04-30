package com.monglepick.monglepickbackend.admin.controller;

import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.ParticipationResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.PublishNowResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.QuizDetailResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.UpdateQuizRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.UpdateStatusRequest;
import com.monglepick.monglepickbackend.admin.service.AdminQuizService;
import com.monglepick.monglepickbackend.domain.roadmap.scheduler.QuizPublishScheduler;
import com.monglepick.monglepickbackend.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 *   <li>POST   /api/v1/admin/quizzes/publish-now — 오늘 퀴즈 강제 발행 (스케줄러 수동 트리거, 2026-04-29)</li>
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
     * 퀴즈 자동 출제 스케줄러 — 운영자 수동 발행 hook.
     * 매일 00:00 KST 자동 실행 외에, "강제 발행" 버튼이 호출하여 즉시 1건 발행한다.
     */
    private final QuizPublishScheduler quizPublishScheduler;

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

    /**
     * 오늘 퀴즈 강제 발행 — 2026-04-29 신규.
     *
     * <p>{@link QuizPublishScheduler#manualPublish()} 를 호출하여 즉시 1건을 발행한다.
     * 매일 00:00 KST 의 자동 발행 외에, 운영자가 검수를 마친 직후 즉시 노출하고 싶을 때 사용.</p>
     *
     * <h4>동작</h4>
     * <ul>
     *   <li>같은 날짜 PUBLISHED 가 이미 있으면 published=0 + 안내 메시지 (HTTP 200, 멱등)</li>
     *   <li>APPROVED 후보가 0건이면 published=0 + 안내 메시지 (검수 적체 신호)</li>
     *   <li>정상 발행 시 published=1 + 성공 메시지</li>
     * </ul>
     *
     * <h4>응답 형태</h4>
     * <pre>
     * { "published": 1, "message": "오늘 퀴즈 1건이 발행되었습니다." }
     * </pre>
     *
     * <p>모든 케이스를 200 으로 반환하여 UI 가 토스트로 안내할 수 있게 한다 (404/409 미사용).
     * 권한: ADMIN.</p>
     *
     * @return 200 OK + {@link PublishNowResponse} (published 0 또는 1)
     */
    /**
     * 퀴즈 참여자 목록 조회 (페이징).
     *
     * @param id   퀴즈 ID
     * @param page 페이지 번호 (0-based, 기본값 0)
     * @param size 페이지 크기 (기본값 20)
     * @return 참여 기록 Page
     */
    @Operation(summary = "퀴즈 참여자 목록 조회", description = "특정 퀴즈의 참여 기록을 제출 시각 역순으로 페이징 조회")
    @GetMapping("/{id}/participations")
    public ResponseEntity<ApiResponse<Page<ParticipationResponse>>> getParticipations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(adminQuizService.getParticipations(id, pageable)));
    }

    @Operation(
            summary = "오늘 퀴즈 강제 발행",
            description = "QuizPublishScheduler.manualPublish() 를 호출하여 APPROVED 1건을 즉시 발행한다. " +
                    "이미 발행됐거나 후보 0건인 경우에도 200 + published=0 + 안내 메시지로 반환."
    )
    @PostMapping("/publish-now")
    public ResponseEntity<ApiResponse<PublishNowResponse>> publishNow() {
        log.info("[관리자] 오늘 퀴즈 강제 발행 요청");
        // 스케줄러의 manualPublish 는 같은 멱등 + FIFO 정책을 사용한다.
        int published = quizPublishScheduler.manualPublish();

        // UI 토스트에 노출할 한국어 안내 메시지 — published 결과에 따라 분기.
        String message;
        if (published == 1) {
            message = "오늘 퀴즈 1건이 발행되었습니다.";
        } else {
            // 0 — 이미 발행됐거나 APPROVED 후보 없음. 운영자가 검수 적체를 확인하도록 안내.
            message = "오늘 발행할 퀴즈가 없거나, 이미 오늘 출제가 완료되었습니다.";
        }

        return ResponseEntity.ok(ApiResponse.ok(new PublishNowResponse(published, message)));
    }
}
