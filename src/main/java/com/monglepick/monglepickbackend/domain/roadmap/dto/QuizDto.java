package com.monglepick.monglepickbackend.domain.roadmap.dto;

import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 퀴즈 관련 DTO 모음 클래스.
 *
 * <p>퀴즈 목록 응답, 정답 제출 요청/응답에 사용하는 record 타입 DTO를 중앙 관리한다.</p>
 *
 * <h3>포함 DTO</h3>
 * <ul>
 *   <li>{@link QuizResponse}   — 퀴즈 목록용 응답 (정답 미포함, 보안)</li>
 *   <li>{@link SubmitRequest}  — 정답 제출 요청</li>
 *   <li>{@link SubmitResponse} — 채점 결과 응답</li>
 * </ul>
 *
 * <h3>보안 주의</h3>
 * <p>{@link QuizResponse}에는 {@code correctAnswer} 필드가 의도적으로 포함되지 않는다.
 * 정답은 {@code POST /quizzes/{quizId}/submit} 응답의 {@link SubmitResponse}에서
 * 채점 결과({@code correct})와 해설({@code explanation})로만 간접 확인 가능하다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-07: 신규 생성 — QuizService/QuizController 구현에 필요한 DTO 정의</li>
 * </ul>
 */
public class QuizDto {

    /**
     * 퀴즈 목록 조회 응답 DTO.
     *
     * <p>GET /api/v1/quizzes/movie/{movieId} 또는 GET /api/v1/quizzes/today 응답에 사용한다.
     * 클라이언트가 퀴즈 UI를 렌더링하는 데 필요한 정보를 포함한다.</p>
     *
     * <h4>정답 미포함 정책</h4>
     * <p>{@code correctAnswer}는 응답에 포함되지 않는다.
     * 클라이언트 개발자 도구에서 정답이 노출되면 퀴즈의 의미가 없어지므로
     * 서버에서 채점 후 채점 결과만 반환한다.</p>
     *
     * @param quizId      퀴즈 고유 ID
     * @param movieId     퀴즈 대상 영화 ID (null이면 일반/데일리 퀴즈)
     * @param question    퀴즈 문제 텍스트
     * @param options     선택지 목록 (주관식이면 빈 리스트)
     * @param rewardPoint 정답 시 지급할 보상 포인트
     */
    public record QuizResponse(
            Long quizId,
            String movieId,
            String question,
            List<String> options,
            int rewardPoint,
            Boolean solved
    ) {
        /** 비로그인 상태용 — solved=null */
        public static QuizResponse from(Quiz quiz, List<String> options) {
            return new QuizResponse(
                    quiz.getQuizId(),
                    quiz.getMovieId(),
                    quiz.getQuestion(),
                    options,
                    quiz.getRewardPoint(),
                    null
            );
        }

        /** 로그인 상태용 — solved=true/false */
        public static QuizResponse from(Quiz quiz, List<String> options, boolean solved) {
            return new QuizResponse(
                    quiz.getQuizId(),
                    quiz.getMovieId(),
                    quiz.getQuestion(),
                    options,
                    quiz.getRewardPoint(),
                    solved
            );
        }
    }

    /**
     * 정답 제출 요청 DTO.
     *
     * <p>POST /api/v1/quizzes/{quizId}/submit 요청 body에 사용한다.
     * 클라이언트는 사용자가 선택/입력한 답변 문자열을 {@code answer} 필드에 담아 전송한다.</p>
     *
     * <h4>유효성 검사</h4>
     * <ul>
     *   <li>{@code answer}: 빈 값 불가, 최대 500자 (Quiz.correctAnswer 컬럼 길이와 동일)</li>
     * </ul>
     *
     * @param answer 사용자가 제출하는 답변 문자열 (객관식이면 선택지 텍스트, 주관식이면 입력 텍스트)
     */
    public record SubmitRequest(
            @NotBlank(message = "답변을 입력해주세요")
            @Size(max = 500, message = "답변은 500자 이하여야 합니다")
            String answer
    ) {}

    /**
     * 정답 제출 결과 응답 DTO.
     *
     * <p>POST /api/v1/quizzes/{quizId}/submit 응답에 사용한다.
     * 클라이언트는 {@code correct} 값으로 정답 여부를 표시하고,
     * {@code explanation}으로 해설을 보여준다.
     * {@code rewardPoint}가 0이면 이번 제출에서 리워드가 지급되지 않은 것이다
     * (오답이거나 이미 이전에 정답 처리된 경우).</p>
     *
     * @param correct      채점 결과 (true: 정답, false: 오답)
     * @param explanation  정답 해설 (관리자가 입력한 해설 텍스트, null이면 해설 없음)
     * @param rewardPoint  이번 제출로 지급된 포인트 (최초 정답 시 quiz.rewardPoint, 그 외 0)
     */
    public record SubmitResponse(
            boolean correct,
            String explanation,
            int rewardPoint
    ) {}

    /**
     * 사용자별 퀴즈 응시 통계 응답 DTO — 2026-04-29 신규.
     *
     * <p>{@code GET /api/v1/quizzes/me/stats} 응답.
     * QuizPage 상단 "내 응시 현황" 카드가 4 KPI 박스로 렌더한다.</p>
     *
     * <h4>필드</h4>
     * <ul>
     *   <li>{@code totalAttempts}      — 총 응시 횟수 (재제출은 1회로 카운트, UNIQUE 제약 덕)</li>
     *   <li>{@code correctCount}       — 정답 처리된 응시 수</li>
     *   <li>{@code accuracyRate}       — 정답률 [0.0~1.0], 모수 0 시 0.0 (NaN 방지)</li>
     *   <li>{@code totalEarnedPoints}  — 정답으로 획득한 누적 포인트 (quiz.rewardPoint 합)</li>
     *   <li>{@code lastAttemptedAt}    — 마지막 응시 시각 (없으면 null)</li>
     * </ul>
     *
     * @param totalAttempts     총 응시 횟수
     * @param correctCount      정답 수
     * @param accuracyRate      정답률 (0.0~1.0)
     * @param totalEarnedPoints 누적 획득 포인트
     * @param lastAttemptedAt   마지막 응시 시각 (nullable)
     */
    public record MyStatsResponse(
            long totalAttempts,
            long correctCount,
            double accuracyRate,
            long totalEarnedPoints,
            LocalDateTime lastAttemptedAt
    ) {}

    /**
     * 응시 이력 페이지 1건 응답 DTO — 2026-04-29 신규.
     *
     * <p>{@code GET /api/v1/quizzes/me/history} 응답의 각 row.
     * {@code MyQuizHistoryList} 컴포넌트가 정답/오답 색상 + 해설로 렌더한다.</p>
     *
     * <h4>보안 결정</h4>
     * <p>응시 이력은 본인 row 만 노출하므로 {@code correctAnswer} 와 {@code explanation} 을
     * 응답에 포함해도 안전하다 (이미 본인은 답을 알고 있는 상태). 이를 통해 클라이언트는
     * 별도 EP 호출 없이 "내가 무엇을 골랐고 정답은 무엇이었는지" 즉시 표시할 수 있다.</p>
     *
     * @param quizId         퀴즈 고유 ID
     * @param movieId        대상 영화 ID (nullable)
     * @param question       퀴즈 문제 본문
     * @param options        선택지 배열
     * @param selectedOption 사용자가 선택한 답
     * @param correctAnswer  정답
     * @param isCorrect      정답 여부
     * @param explanation    해설 (nullable)
     * @param rewardPoint    퀴즈 보상 포인트 (응시 시점 quiz.rewardPoint)
     * @param submittedAt    제출 시각
     */
    public record MyHistoryItem(
            Long quizId,
            String movieId,
            String question,
            List<String> options,
            String selectedOption,
            String correctAnswer,
            Boolean isCorrect,
            String explanation,
            Integer rewardPoint,
            LocalDateTime submittedAt
    ) {}
}
