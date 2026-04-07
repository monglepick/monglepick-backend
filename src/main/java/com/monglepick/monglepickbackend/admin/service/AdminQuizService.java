package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.QuizDetailResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.UpdateQuizRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.UpdateStatusRequest;
import com.monglepick.monglepickbackend.admin.repository.AdminQuizRepository;
import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz.QuizStatus;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 퀴즈 관리 서비스 (수정/상태전이/삭제 전담).
 *
 * <p>기존 {@link AdminAiOpsService}의 quiz 관련 메서드(history/generate)는 그대로 유지하고,
 * 본 서비스는 추가로 필요한 수정/상태전이/삭제 EP를 제공한다.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>퀴즈 단건 조회 (관리자 검수용)</li>
 *   <li>퀴즈 본문/메타 수정 (movieId/question/explanation/correctAnswer/options/rewardPoint/quizDate)</li>
 *   <li>퀴즈 상태 전이 (PENDING/APPROVED/REJECTED/PUBLISHED)</li>
 *   <li>퀴즈 삭제 (hard delete) — PENDING/REJECTED 상태만 허용</li>
 * </ol>
 *
 * <h3>상태 전이 정책</h3>
 * <p>{@link #STATUS_TRANSITIONS} 맵에 정의된 전이만 허용한다.
 * 잘못된 전이 요청 시 {@link ErrorCode#INVALID_QUIZ_STATUS_TRANSITION} 발생.</p>
 *
 * <pre>
 * PENDING   → APPROVED, REJECTED
 * APPROVED  → PUBLISHED, REJECTED
 * REJECTED  → PENDING                (재검수)
 * PUBLISHED → REJECTED               (긴급 회수)
 * </pre>
 *
 * <h3>삭제 정책</h3>
 * <p>PUBLISHED/APPROVED 퀴즈는 사용자 참여 기록(quiz_participation)과 연결되어
 * 있을 가능성이 있으므로 hard delete를 금지한다. PENDING/REJECTED 상태만 삭제 가능.
 * 그 외에는 REJECTED 상태로 전이만 허용한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminQuizService {

    /** 관리자 전용 퀴즈 리포지토리 (페이징 + 상태 필터) — AdminAiOpsService와 공유 */
    private final AdminQuizRepository adminQuizRepository;

    /**
     * 허용된 상태 전이 맵.
     *
     * <p>키 = 현재 상태, 값 = 전이 가능한 상태 집합.</p>
     */
    private static final Map<QuizStatus, Set<QuizStatus>> STATUS_TRANSITIONS = Map.of(
            QuizStatus.PENDING,   EnumSet.of(QuizStatus.APPROVED, QuizStatus.REJECTED),
            QuizStatus.APPROVED,  EnumSet.of(QuizStatus.PUBLISHED, QuizStatus.REJECTED),
            QuizStatus.REJECTED,  EnumSet.of(QuizStatus.PENDING),
            QuizStatus.PUBLISHED, EnumSet.of(QuizStatus.REJECTED)
    );

    /** 삭제 가능한 상태 집합 (사용자 참여 기록 보호) */
    private static final Set<QuizStatus> DELETABLE_STATUSES =
            EnumSet.of(QuizStatus.PENDING, QuizStatus.REJECTED);

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 퀴즈 단건 조회.
     *
     * @param id 퀴즈 ID
     * @return 퀴즈 응답 DTO
     * @throws BusinessException 존재하지 않으면 QUIZ_NOT_FOUND
     */
    public QuizDetailResponse getQuiz(Long id) {
        return toResponse(findQuizByIdOrThrow(id));
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    /**
     * 퀴즈 본문/메타 수정 (상태 제외).
     *
     * <p>상태(status)는 본 메서드에서 변경하지 않는다.
     * 상태 전이는 {@link #updateStatus(Long, UpdateStatusRequest)}로 별도 처리.</p>
     *
     * @param id      대상 퀴즈 ID
     * @param request 수정 요청
     * @return 수정된 퀴즈 응답
     */
    @Transactional
    public QuizDetailResponse updateQuiz(Long id, UpdateQuizRequest request) {
        Quiz quiz = findQuizByIdOrThrow(id);

        // 도메인 메서드 호출 — JPA dirty checking 자동 UPDATE
        quiz.updateInfo(
                request.movieId(),
                request.question(),
                request.explanation(),
                request.correctAnswer(),
                request.options(),
                request.rewardPoint(),
                request.quizDate()
        );

        log.info("[관리자] 퀴즈 수정 — quizId={}, status={}", id, quiz.getStatus());
        return toResponse(quiz);
    }

    /**
     * 퀴즈 상태 전이.
     *
     * <p>{@link #STATUS_TRANSITIONS}에 정의된 전이만 허용한다.
     * 잘못된 전이 요청 시 INVALID_QUIZ_STATUS_TRANSITION 발생.</p>
     *
     * @param id      대상 퀴즈 ID
     * @param request 상태 전이 요청 (targetStatus = enum 이름 문자열)
     * @return 갱신된 퀴즈 응답
     */
    @Transactional
    public QuizDetailResponse updateStatus(Long id, UpdateStatusRequest request) {
        Quiz quiz = findQuizByIdOrThrow(id);

        // targetStatus 파싱
        QuizStatus target;
        try {
            target = QuizStatus.valueOf(request.targetStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "허용되지 않은 상태 값: " + request.targetStatus());
        }

        // 동일 상태로의 전이는 멱등 처리 (no-op + 정상 응답)
        if (quiz.getStatus() == target) {
            log.info("[관리자] 퀴즈 상태 전이 멱등 처리 — quizId={}, status={}", id, target);
            return toResponse(quiz);
        }

        // 허용된 전이인지 검증
        Set<QuizStatus> allowed = STATUS_TRANSITIONS.getOrDefault(quiz.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            log.warn("[관리자] 잘못된 퀴즈 상태 전이 — quizId={}, from={}, to={}",
                    id, quiz.getStatus(), target);
            throw new BusinessException(ErrorCode.INVALID_QUIZ_STATUS_TRANSITION,
                    String.format("%s → %s 전이는 허용되지 않습니다", quiz.getStatus(), target));
        }

        // 도메인 메서드를 통한 안전한 상태 전이
        switch (target) {
            case APPROVED -> quiz.approve();
            case REJECTED -> quiz.reject();
            case PUBLISHED -> quiz.publish();
            case PENDING -> {
                // PENDING 도메인 메서드가 없어 reflection 없이 직접 전이 — 임시로 reject 후 별도 메서드 추가가 안전.
                // 우선 신규 도메인 메서드 추가 없이 처리하기 위해 builder 재생성 패턴은 부적합하므로,
                // STATUS_TRANSITIONS 에서 PENDING 복귀 분기를 별도 처리한다.
                resetToPending(quiz);
            }
        }

        log.info("[관리자] 퀴즈 상태 전이 완료 — quizId={}, newStatus={}", id, target);
        return toResponse(quiz);
    }

    /**
     * REJECTED → PENDING 재검수 회부.
     *
     * <p>Quiz 엔티티에 PENDING 복귀 도메인 메서드가 없어 reflection 없이 처리하기 위해
     * 별도 헬퍼 사용. JPA dirty checking이 동작하도록 entity의 status 필드를 직접 변경할 수
     * 없으므로, 향후 Quiz 엔티티에 {@code resetPending()} 도메인 메서드를 추가하는 것이 권장된다.</p>
     *
     * <p>현재 임시 구현: 동일 트랜잭션 내에서 새 인스턴스를 만들어 save() — JPA가 ID로 merge 처리.</p>
     */
    private void resetToPending(Quiz quiz) {
        // PENDING 으로 변경 — 새 인스턴스 빌더로 PK 유지 + status PENDING 으로 재구성
        Quiz updated = Quiz.builder()
                .quizId(quiz.getQuizId())
                .movieId(quiz.getMovieId())
                .question(quiz.getQuestion())
                .explanation(quiz.getExplanation())
                .correctAnswer(quiz.getCorrectAnswer())
                .options(quiz.getOptions())
                .rewardPoint(quiz.getRewardPoint())
                .status(QuizStatus.PENDING)
                .quizDate(quiz.getQuizDate())
                .build();
        adminQuizRepository.save(updated);
    }

    /**
     * 퀴즈 hard delete (PENDING/REJECTED 상태만 허용).
     *
     * @param id 삭제 대상 ID
     * @throws BusinessException 삭제 불가 상태일 때 INVALID_QUIZ_STATUS_TRANSITION
     */
    @Transactional
    public void deleteQuiz(Long id) {
        Quiz quiz = findQuizByIdOrThrow(id);

        if (!DELETABLE_STATUSES.contains(quiz.getStatus())) {
            log.warn("[관리자] 삭제 불가 상태의 퀴즈 hard delete 시도 — quizId={}, status={}",
                    id, quiz.getStatus());
            throw new BusinessException(ErrorCode.INVALID_QUIZ_STATUS_TRANSITION,
                    quiz.getStatus() + " 상태 퀴즈는 삭제할 수 없습니다. REJECTED 로 전환하세요.");
        }

        adminQuizRepository.delete(quiz);
        log.info("[관리자] 퀴즈 삭제 완료 — quizId={}", id);
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    /** ID로 퀴즈 조회 또는 404 */
    private Quiz findQuizByIdOrThrow(Long id) {
        return adminQuizRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.QUIZ_NOT_FOUND,
                        "퀴즈 ID " + id + "를 찾을 수 없습니다"));
    }

    /** 엔티티 → 응답 DTO */
    private QuizDetailResponse toResponse(Quiz quiz) {
        return new QuizDetailResponse(
                quiz.getQuizId(),
                quiz.getMovieId(),
                quiz.getQuestion(),
                quiz.getExplanation(),
                quiz.getCorrectAnswer(),
                quiz.getOptions(),
                quiz.getRewardPoint(),
                quiz.getStatus().name(),
                quiz.getQuizDate(),
                quiz.getCreatedAt(),
                quiz.getUpdatedAt()
        );
    }
}
