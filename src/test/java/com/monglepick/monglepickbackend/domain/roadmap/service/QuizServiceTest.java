package com.monglepick.monglepickbackend.domain.roadmap.service;

import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.MyHistoryItem;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.MyStatsResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.QuizResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.SubmitRequest;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.SubmitResponse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import com.monglepick.monglepickbackend.domain.roadmap.entity.QuizParticipation;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.QuizParticipationRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.QuizRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.domain.roadmap.service.AchievementService;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QuizService} 단위 테스트 (2026-04-29 신규).
 *
 * <p>Mockito 기반 — Repository / RewardService 모킹.
 * 사용자 EP 흐름(목록 조회 + 정답 제출 + 리워드 지급)의 회귀 안전망 확보.</p>
 *
 * <h3>테스트 그룹</h3>
 * <ul>
 *   <li>{@link MovieQuizzesTest}     — 영화별 PUBLISHED 목록 (정답 미노출 검증)</li>
 *   <li>{@link TodayQuizzesTest}     — 오늘 날짜 PUBLISHED 목록 + 옵션 JSON 파싱</li>
 *   <li>{@link AllPublishedTest}     — 전체 PUBLISHED 목록</li>
 *   <li>{@link SubmitCorrectTest}    — 정답 + 최초 정답 → 리워드 지급 호출</li>
 *   <li>{@link SubmitWrongTest}      — 오답 → 리워드 미호출</li>
 *   <li>{@link SubmitDuplicateTest}  — 이미 정답 기록 있음 → 리워드 미호출 (중복 지급 방지)</li>
 *   <li>{@link SubmitNotPublishedTest} — PUBLISHED 가 아닌 퀴즈 → BusinessException(QUIZ_NOT_FOUND)</li>
 *   <li>{@link SubmitResubmitTest}   — 재제출 — 기존 참여 기록 update 경로</li>
 *   <li>{@link SubmitTrimIgnoreCaseTest} — 채점은 trim + equalsIgnoreCase</li>
 *   <li>{@link RewardFailureTest}    — RewardService 예외도 퀴즈 제출 자체는 성공 (warn 로그만)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuizParticipationRepository participationRepository;
    @Mock private RewardService rewardService;
    @Mock private AchievementService achievementService;
    @Mock private MovieRepository movieRepository;

    @InjectMocks private QuizService quizService;

    /** 테스트용 PUBLISHED Quiz 빌더 */
    private Quiz buildPublishedQuiz(Long id, String correctAnswer) {
        return Quiz.builder()
                .quizId(id)
                .movieId("movie-1")
                .question("이 영화의 감독은?")
                .correctAnswer(correctAnswer)
                .options("[\"A\",\"B\",\"C\",\"D\"]")
                .rewardPoint(10)
                .status(Quiz.QuizStatus.PUBLISHED)
                .quizDate(LocalDate.now())
                .explanation("해설입니다.")
                .build();
    }

    private Quiz buildPendingQuiz(Long id) {
        return Quiz.builder()
                .quizId(id)
                .movieId("movie-1")
                .question("질문")
                .correctAnswer("정답")
                .options("[\"A\",\"B\",\"C\",\"D\"]")
                .rewardPoint(10)
                .status(Quiz.QuizStatus.PENDING)
                .build();
    }

    // ============================================================
    // 1) 영화별 퀴즈 목록 — 정답 미노출
    // ============================================================

    @Nested
    @DisplayName("getMovieQuizzes — 영화별 PUBLISHED")
    class MovieQuizzesTest {

        @Test
        @DisplayName("PUBLISHED 퀴즈만 조회되고 응답 DTO 에 correctAnswer 가 노출되지 않는다")
        void movieQuizzesDoNotExposeCorrectAnswer() {
            // given — 한 영화에 PUBLISHED 퀴즈 2건
            Quiz q1 = buildPublishedQuiz(1L, "정답A");
            Quiz q2 = buildPublishedQuiz(2L, "정답B");
            when(quizRepository.findByMovieIdAndStatus("movie-1", Quiz.QuizStatus.PUBLISHED))
                    .thenReturn(List.of(q1, q2));

            // when
            List<QuizResponse> result = quizService.getMovieQuizzes("movie-1", null);

            // then — 응답에 정답이 노출되지 않는 record 인지 컴파일 시점에 보장됨.
            //         options 파싱이 List<String> 4개로 동작하는지 검증.
            assertThat(result).hasSize(2);
            assertThat(result.get(0).quizId()).isEqualTo(1L);
            assertThat(result.get(0).options()).containsExactly("A", "B", "C", "D");
            assertThat(result.get(0).rewardPoint()).isEqualTo(10);
            // QuizResponse record 에는 correctAnswer 필드 자체가 없으므로 추가 검증 불필요.
        }

        @Test
        @DisplayName("후보 0건이면 빈 리스트")
        void emptyResultWhenNoQuiz() {
            when(quizRepository.findByMovieIdAndStatus(anyString(), eq(Quiz.QuizStatus.PUBLISHED)))
                    .thenReturn(List.of());
            assertThat(quizService.getMovieQuizzes("movie-X", null)).isEmpty();
        }

        @Test
        @DisplayName("options JSON 파싱 실패 시 빈 리스트로 fallback (warn 로그만)")
        void optionsJsonParseFailureFallsBackToEmpty() {
            Quiz broken = Quiz.builder()
                    .quizId(99L).movieId("m").question("q").correctAnswer("a")
                    .options("not-a-json")  // 깨진 JSON
                    .rewardPoint(10).status(Quiz.QuizStatus.PUBLISHED)
                    .build();
            when(quizRepository.findByMovieIdAndStatus(anyString(), eq(Quiz.QuizStatus.PUBLISHED)))
                    .thenReturn(List.of(broken));

            List<QuizResponse> result = quizService.getMovieQuizzes("m", null);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).options()).isEmpty();
        }
    }

    // ============================================================
    // 2) 오늘의 퀴즈 — quizDate=today 필터
    // ============================================================

    @Nested
    @DisplayName("getTodayQuizzes — 오늘 날짜 PUBLISHED")
    class TodayQuizzesTest {

        @Test
        @DisplayName("today + PUBLISHED 인 퀴즈만 반환한다")
        void filtersByTodayAndPublished() {
            Quiz today = buildPublishedQuiz(10L, "X");
            when(quizRepository.findByQuizDateAndStatus(eq(LocalDate.now()), eq(Quiz.QuizStatus.PUBLISHED)))
                    .thenReturn(List.of(today));

            List<QuizResponse> result = quizService.getTodayQuizzes(null);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).quizId()).isEqualTo(10L);
        }
    }

    // ============================================================
    // 3) 전체 PUBLISHED — fallback 메서드
    // ============================================================

    @Nested
    @DisplayName("getAllPublishedQuizzes — 전체 PUBLISHED")
    class AllPublishedTest {

        @Test
        @DisplayName("status=PUBLISHED 전체를 반환한다")
        void returnsAllPublished() {
            when(quizRepository.findByStatus(Quiz.QuizStatus.PUBLISHED))
                    .thenReturn(List.of(buildPublishedQuiz(1L, "a"), buildPublishedQuiz(2L, "b")));

            assertThat(quizService.getAllPublishedQuizzes()).hasSize(2);
        }
    }

    // ============================================================
    // 4) 정답 제출 — 정상 / 정답 + 최초 → 리워드 지급
    // ============================================================

    @Nested
    @DisplayName("submitAnswer — 정답 + 최초 정답")
    class SubmitCorrectTest {

        @Test
        @DisplayName("정답이고 alreadyCorrect=false 이면 grantReward(QUIZ_CORRECT) 호출 + rewardPoint 반환")
        void correctFirstTimeGrantsReward() {
            Quiz quiz = buildPublishedQuiz(1L, "정답");
            when(quizRepository.findById(1L)).thenReturn(Optional.of(quiz));
            when(participationRepository.findByQuiz_QuizIdAndUserId(1L, "u1"))
                    .thenReturn(Optional.empty());

            SubmitResponse res = quizService.submitAnswer("u1", 1L, new SubmitRequest("정답"));

            assertThat(res.correct()).isTrue();
            assertThat(res.rewardPoint()).isEqualTo(10);
            assertThat(res.explanation()).isEqualTo("해설입니다.");

            // grantReward 호출 검증 — actionType=QUIZ_CORRECT, ref=quiz_1
            ArgumentCaptor<String> userIdCap = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> actionTypeCap = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> refIdCap = ArgumentCaptor.forClass(String.class);
            verify(rewardService).grantReward(userIdCap.capture(), actionTypeCap.capture(),
                    refIdCap.capture(), anyInt());
            assertThat(userIdCap.getValue()).isEqualTo("u1");
            assertThat(actionTypeCap.getValue()).isEqualTo("QUIZ_CORRECT");
            assertThat(refIdCap.getValue()).isEqualTo("quiz_1");

            // 신규 참여 기록 INSERT 검증
            verify(participationRepository).save(any(QuizParticipation.class));
        }
    }

    // ============================================================
    // 5) 정답 제출 — 오답 → 리워드 미호출
    // ============================================================

    @Nested
    @DisplayName("submitAnswer — 오답")
    class SubmitWrongTest {

        @Test
        @DisplayName("오답이면 grantReward 미호출 + rewardPoint=0")
        void wrongAnswerSkipsReward() {
            Quiz quiz = buildPublishedQuiz(2L, "정답");
            when(quizRepository.findById(2L)).thenReturn(Optional.of(quiz));
            when(participationRepository.findByQuiz_QuizIdAndUserId(2L, "u2"))
                    .thenReturn(Optional.empty());

            SubmitResponse res = quizService.submitAnswer("u2", 2L, new SubmitRequest("틀린답"));

            assertThat(res.correct()).isFalse();
            assertThat(res.rewardPoint()).isEqualTo(0);
            verify(rewardService, never()).grantReward(anyString(), anyString(), anyString(), anyInt());
        }
    }

    // ============================================================
    // 6) 정답 제출 — 이미 정답 기록 있음 → 리워드 중복 지급 방지
    // ============================================================

    @Nested
    @DisplayName("submitAnswer — 이미 제출한 퀴즈 재제출 차단")
    class SubmitDuplicateTest {

        @Test
        @DisplayName("이미 제출 기록이 있으면 QUIZ_ALREADY_SUBMITTED 예외가 발생한다")
        void duplicateSubmitThrowsAlreadySubmitted() {
            Quiz quiz = buildPublishedQuiz(3L, "정답");
            QuizParticipation existing = QuizParticipation.builder()
                    .quizParticipationId(100L)
                    .quiz(quiz)
                    .userId("u3")
                    .selectedOption("정답")
                    .isCorrect(true)
                    .build();

            when(quizRepository.findById(3L)).thenReturn(Optional.of(quiz));
            when(participationRepository.findByQuiz_QuizIdAndUserId(3L, "u3"))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() ->
                    quizService.submitAnswer("u3", 3L, new SubmitRequest("정답"))
            ).isInstanceOf(BusinessException.class)
             .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUIZ_ALREADY_SUBMITTED);

            verify(rewardService, never()).grantReward(anyString(), anyString(), anyString(), anyInt());
            verify(participationRepository, never()).save(any());
        }
    }

    // ============================================================
    // 7) 정답 제출 — PUBLISHED 가 아닌 퀴즈
    // ============================================================

    @Nested
    @DisplayName("submitAnswer — PUBLISHED 가 아닌 퀴즈")
    class SubmitNotPublishedTest {

        @Test
        @DisplayName("PENDING 퀴즈에 제출하면 BusinessException(QUIZ_NOT_FOUND)")
        void pendingQuizThrowsNotFound() {
            Quiz pending = buildPendingQuiz(4L);
            when(quizRepository.findById(4L)).thenReturn(Optional.of(pending));

            assertThatThrownBy(() ->
                    quizService.submitAnswer("u4", 4L, new SubmitRequest("정답"))
            ).isInstanceOf(BusinessException.class)
             .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUIZ_NOT_FOUND);

            verify(participationRepository, never()).save(any());
            verify(rewardService, never()).grantReward(anyString(), anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("존재하지 않는 quizId 이면 BusinessException(QUIZ_NOT_FOUND)")
        void missingQuizIdThrowsNotFound() {
            when(quizRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    quizService.submitAnswer("u9", 999L, new SubmitRequest("아무거나"))
            ).isInstanceOf(BusinessException.class)
             .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUIZ_NOT_FOUND);
        }
    }

    // ============================================================
    // 8) 정답 제출 — 재제출 (기존 기록 update)
    // ============================================================

    @Nested
    @DisplayName("submitAnswer — 재제출 차단")
    class SubmitResubmitTest {

        @Test
        @DisplayName("오답 후 재제출 시도해도 QUIZ_ALREADY_SUBMITTED 예외가 발생한다")
        void resubmitAfterWrongAnswerThrowsAlreadySubmitted() {
            Quiz quiz = buildPublishedQuiz(5L, "정답");
            QuizParticipation existing = QuizParticipation.builder()
                    .quizParticipationId(200L)
                    .quiz(quiz)
                    .userId("u5")
                    .selectedOption("이전답")
                    .isCorrect(false)
                    .build();

            when(quizRepository.findById(5L)).thenReturn(Optional.of(quiz));
            when(participationRepository.findByQuiz_QuizIdAndUserId(5L, "u5"))
                    .thenReturn(Optional.of(existing));

            assertThatThrownBy(() ->
                    quizService.submitAnswer("u5", 5L, new SubmitRequest("정답"))
            ).isInstanceOf(BusinessException.class)
             .hasFieldOrPropertyWithValue("errorCode", ErrorCode.QUIZ_ALREADY_SUBMITTED);

            verify(participationRepository, never()).save(any());
            verify(rewardService, never()).grantReward(anyString(), anyString(), anyString(), anyInt());
        }
    }

    // ============================================================
    // 9) 정답 제출 — 채점은 trim + equalsIgnoreCase
    // ============================================================

    @Nested
    @DisplayName("submitAnswer — trim + 대소문자 무시 채점")
    class SubmitTrimIgnoreCaseTest {

        @Test
        @DisplayName("앞뒤 공백 + 대소문자 차이가 있어도 정답 처리")
        void trimAndIgnoreCase() {
            Quiz quiz = buildPublishedQuiz(6L, "Parasite");
            when(quizRepository.findById(6L)).thenReturn(Optional.of(quiz));
            when(participationRepository.findByQuiz_QuizIdAndUserId(anyLong(), anyString()))
                    .thenReturn(Optional.empty());

            SubmitResponse res = quizService.submitAnswer("u6", 6L,
                    new SubmitRequest("  PARASITE  "));
            assertThat(res.correct()).isTrue();
        }
    }

    // ============================================================
    // 10) RewardService 예외 흡수 — 퀴즈 제출 자체는 성공
    // ============================================================

    @Nested
    @DisplayName("submitAnswer — RewardService 예외")
    class RewardFailureTest {

        @Test
        @DisplayName("grantReward 가 예외를 던져도 퀴즈 제출은 정상 응답한다 (warn 로그만)")
        void rewardFailureDoesNotPropagate() {
            Quiz quiz = buildPublishedQuiz(7L, "정답");
            when(quizRepository.findById(7L)).thenReturn(Optional.of(quiz));
            when(participationRepository.findByQuiz_QuizIdAndUserId(7L, "u7"))
                    .thenReturn(Optional.empty());
            // grantReward 가 예외를 던지는 시나리오
            when(rewardService.grantReward(anyString(), anyString(), anyString(), anyInt()))
                    .thenThrow(new RuntimeException("reward DB 일시 장애"));

            // when / then — 예외가 외부로 전파되지 않아야 함
            SubmitResponse res = quizService.submitAnswer("u7", 7L, new SubmitRequest("정답"));

            // 퀴즈 채점 자체는 정상이고 rewardPoint 는 awarded "의도" 에 따라 10P 로 노출
            //    (응답 DTO 는 정답 + alreadyCorrect=false 만으로 결정 — 실제 지급 실패는 warn 로그)
            assertThat(res.correct()).isTrue();
            assertThat(res.rewardPoint()).isEqualTo(10);
        }
    }

    // ============================================================
    // 11) getMyStats — 사용자별 응시 통계 (2026-04-29 신규)
    // ============================================================

    @Nested
    @DisplayName("getMyStats — 사용자별 응시 통계")
    class MyStatsTest {

        @Test
        @DisplayName("정상 응시 — 정답률 / 누적 포인트 / 마지막 응시 시각이 정확히 매핑된다")
        void aggregatesNormalCase() {
            // given — Repository 가 List<Object[]> 1행으로 [총응시 10, 정답 7, 누적포인트 70, 마지막시각] 반환
            // (Spring Boot 4 / Hibernate 7 에서 멀티 SELECT JPQL 결과는 반드시 List<Object[]>)
            // List.of(Object[]) 는 Object... 가변인자로 해석되어 List<Object> 가 되므로
            // singletonList 로 명시적 List<Object[]> 를 만든다.
            java.time.LocalDateTime lastAt = java.time.LocalDateTime.of(2026, 4, 29, 12, 30);
            when(participationRepository.aggregateMyStats("u-stat-1"))
                    .thenReturn(java.util.Collections.singletonList(
                            new Object[]{10L, 7L, 70L, lastAt}));

            // when
            MyStatsResponse res = quizService.getMyStats("u-stat-1");

            // then
            assertThat(res.totalAttempts()).isEqualTo(10L);
            assertThat(res.correctCount()).isEqualTo(7L);
            assertThat(res.totalEarnedPoints()).isEqualTo(70L);
            assertThat(res.accuracyRate()).isEqualTo(0.7);
            assertThat(res.lastAttemptedAt()).isEqualTo(lastAt);
        }

        @Test
        @DisplayName("응시 0건 — 정답률 0.0 (NaN 방지) + lastAttemptedAt=null")
        void emptyAttemptsReturnsZero() {
            // given — SUM 이 null 인 환경 (응시 0건). aggregation 은 항상 1행이 나오므로
            // List 는 정확히 1개 row 를 담는다.
            when(participationRepository.aggregateMyStats("u-stat-2"))
                    .thenReturn(java.util.Collections.singletonList(
                            new Object[]{0L, null, null, null}));

            // when
            MyStatsResponse res = quizService.getMyStats("u-stat-2");

            // then — 모두 0 + 정답률 0.0 (NaN 아님) + lastAt null
            assertThat(res.totalAttempts()).isEqualTo(0L);
            assertThat(res.correctCount()).isEqualTo(0L);
            assertThat(res.totalEarnedPoints()).isEqualTo(0L);
            assertThat(res.accuracyRate()).isEqualTo(0.0);
            assertThat(Double.isNaN(res.accuracyRate())).isFalse();
            assertThat(res.lastAttemptedAt()).isNull();
        }

        @Test
        @DisplayName("Number 다형성 — Integer/BigInteger 도 안전하게 long 으로 변환")
        void handlesNumberPolymorphism() {
            // given — Hibernate 가 환경에 따라 Integer 로 반환할 수도 있음
            when(participationRepository.aggregateMyStats("u-stat-3"))
                    .thenReturn(java.util.Collections.singletonList(
                            new Object[]{Integer.valueOf(5), Integer.valueOf(3),
                                    Integer.valueOf(30), null}));

            MyStatsResponse res = quizService.getMyStats("u-stat-3");

            assertThat(res.totalAttempts()).isEqualTo(5L);
            assertThat(res.correctCount()).isEqualTo(3L);
            assertThat(res.totalEarnedPoints()).isEqualTo(30L);
            assertThat(res.accuracyRate()).isEqualTo(0.6);
        }
    }

    // ============================================================
    // 12) getMyHistory — 사용자별 응시 이력 페이징 (2026-04-29 신규)
    // ============================================================

    @Nested
    @DisplayName("getMyHistory — 사용자별 응시 이력 페이징")
    class MyHistoryTest {

        @Test
        @DisplayName("정상 페이지 — Quiz JOIN FETCH 결과를 MyHistoryItem 으로 변환")
        void returnsHistoryPage() {
            // given — Quiz + Participation 2건이 페이지로 반환됨
            Quiz q1 = buildPublishedQuiz(101L, "정답A");
            Quiz q2 = buildPublishedQuiz(102L, "정답B");

            QuizParticipation p1 = QuizParticipation.builder()
                    .quizParticipationId(1L)
                    .quiz(q1)
                    .userId("u-hist-1")
                    .selectedOption("정답A")
                    .isCorrect(true)
                    .submittedAt(java.time.LocalDateTime.of(2026, 4, 29, 12, 0))
                    .build();

            QuizParticipation p2 = QuizParticipation.builder()
                    .quizParticipationId(2L)
                    .quiz(q2)
                    .userId("u-hist-1")
                    .selectedOption("틀린답")
                    .isCorrect(false)
                    .submittedAt(java.time.LocalDateTime.of(2026, 4, 29, 11, 0))
                    .build();

            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 10);
            org.springframework.data.domain.Page<QuizParticipation> page =
                    new org.springframework.data.domain.PageImpl<>(
                            java.util.List.of(p1, p2), pageable, 2);

            when(participationRepository.findMyHistory("u-hist-1", pageable))
                    .thenReturn(page);

            // when
            org.springframework.data.domain.Page<MyHistoryItem> result =
                    quizService.getMyHistory("u-hist-1", pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).hasSize(2);
            MyHistoryItem first = result.getContent().get(0);
            assertThat(first.quizId()).isEqualTo(101L);
            assertThat(first.selectedOption()).isEqualTo("정답A");
            assertThat(first.correctAnswer()).isEqualTo("정답A");
            assertThat(first.isCorrect()).isTrue();
            assertThat(first.options()).containsExactly("A", "B", "C", "D");
            assertThat(first.explanation()).isEqualTo("해설입니다.");
            assertThat(first.rewardPoint()).isEqualTo(10);

            MyHistoryItem second = result.getContent().get(1);
            assertThat(second.isCorrect()).isFalse();
            assertThat(second.selectedOption()).isEqualTo("틀린답");
        }

        @Test
        @DisplayName("빈 결과 — 응시 0건 사용자에게는 빈 페이지 반환")
        void returnsEmptyPage() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 10);
            org.springframework.data.domain.Page<QuizParticipation> empty =
                    new org.springframework.data.domain.PageImpl<>(
                            java.util.List.of(), pageable, 0);

            when(participationRepository.findMyHistory("u-empty", pageable)).thenReturn(empty);

            org.springframework.data.domain.Page<MyHistoryItem> result =
                    quizService.getMyHistory("u-empty", pageable);

            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("options JSON 깨짐 — 빈 리스트 fallback (warn 로그만)")
        void brokenOptionsJsonFallsBackToEmpty() {
            // given — options 가 깨진 JSON 인 Quiz
            Quiz broken = Quiz.builder()
                    .quizId(999L)
                    .movieId("m")
                    .question("q")
                    .correctAnswer("a")
                    .options("not-a-json")  // 깨진 JSON
                    .rewardPoint(10)
                    .status(Quiz.QuizStatus.PUBLISHED)
                    .build();
            QuizParticipation p = QuizParticipation.builder()
                    .quizParticipationId(99L)
                    .quiz(broken)
                    .userId("u-broken")
                    .selectedOption("x")
                    .isCorrect(false)
                    .submittedAt(java.time.LocalDateTime.now())
                    .build();

            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 10);
            when(participationRepository.findMyHistory("u-broken", pageable))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(
                            java.util.List.of(p), pageable, 1));

            org.springframework.data.domain.Page<MyHistoryItem> result =
                    quizService.getMyHistory("u-broken", pageable);

            // 변환은 성공하고 options 만 빈 리스트로 fallback
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).options()).isEmpty();
        }
    }
}
