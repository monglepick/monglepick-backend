package com.monglepick.monglepickbackend.domain.roadmap.service;

import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.QuizResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.SubmitRequest;
import com.monglepick.monglepickbackend.domain.roadmap.dto.QuizDto.SubmitResponse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import com.monglepick.monglepickbackend.domain.roadmap.entity.QuizParticipation;
import com.monglepick.monglepickbackend.domain.roadmap.repository.QuizParticipationRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.QuizRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
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
            List<QuizResponse> result = quizService.getMovieQuizzes("movie-1");

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
            assertThat(quizService.getMovieQuizzes("movie-X")).isEmpty();
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

            List<QuizResponse> result = quizService.getMovieQuizzes("m");
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

            List<QuizResponse> result = quizService.getTodayQuizzes();
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
            when(participationRepository
                    .existsByQuiz_QuizIdAndUserIdAndIsCorrect(1L, "u1", true))
                    .thenReturn(false);
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
            when(participationRepository
                    .existsByQuiz_QuizIdAndUserIdAndIsCorrect(2L, "u2", true))
                    .thenReturn(false);
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
    @DisplayName("submitAnswer — 이미 정답 기록 있는 재제출")
    class SubmitDuplicateTest {

        @Test
        @DisplayName("alreadyCorrect=true 이면 정답이라도 grantReward 미호출 + rewardPoint=0")
        void duplicateCorrectAnswerSkipsReward() {
            Quiz quiz = buildPublishedQuiz(3L, "정답");
            QuizParticipation existing = QuizParticipation.builder()
                    .quizParticipationId(100L)
                    .quiz(quiz)
                    .userId("u3")
                    .selectedOption("정답")
                    .isCorrect(true)
                    .build();

            when(quizRepository.findById(3L)).thenReturn(Optional.of(quiz));
            when(participationRepository
                    .existsByQuiz_QuizIdAndUserIdAndIsCorrect(3L, "u3", true))
                    .thenReturn(true);  // 이미 정답 기록 존재
            when(participationRepository.findByQuiz_QuizIdAndUserId(3L, "u3"))
                    .thenReturn(Optional.of(existing));

            SubmitResponse res = quizService.submitAnswer("u3", 3L, new SubmitRequest("정답"));

            assertThat(res.correct()).isTrue();          // 채점은 정답
            assertThat(res.rewardPoint()).isEqualTo(0);  // 리워드는 미지급
            verify(rewardService, never()).grantReward(anyString(), anyString(), anyString(), anyInt());
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
    @DisplayName("submitAnswer — 재제출")
    class SubmitResubmitTest {

        @Test
        @DisplayName("기존 참여 기록이 있으면 INSERT 가 아닌 submit() update 경로로 동작")
        void resubmitTriggersSubmitOnExistingRecord() {
            Quiz quiz = buildPublishedQuiz(5L, "정답");
            QuizParticipation existing = QuizParticipation.builder()
                    .quizParticipationId(200L)
                    .quiz(quiz)
                    .userId("u5")
                    .selectedOption("이전답")
                    .isCorrect(false)
                    .build();

            when(quizRepository.findById(5L)).thenReturn(Optional.of(quiz));
            when(participationRepository
                    .existsByQuiz_QuizIdAndUserIdAndIsCorrect(5L, "u5", true))
                    .thenReturn(false);
            when(participationRepository.findByQuiz_QuizIdAndUserId(5L, "u5"))
                    .thenReturn(Optional.of(existing));

            SubmitResponse res = quizService.submitAnswer("u5", 5L, new SubmitRequest("정답"));

            // 기존 record 의 도메인 메서드가 호출되었는지 (필드 업데이트로 검증)
            assertThat(existing.getSelectedOption()).isEqualTo("정답");
            assertThat(existing.getIsCorrect()).isTrue();
            assertThat(existing.getSubmittedAt()).isNotNull();

            // 신규 INSERT 가 호출되지 않아야 함 — JPA dirty checking 으로 update.
            verify(participationRepository, never()).save(any());

            assertThat(res.correct()).isTrue();
            assertThat(res.rewardPoint()).isEqualTo(10);  // 이전 isCorrect=false 였으므로 이번이 최초 정답
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
            when(participationRepository
                    .existsByQuiz_QuizIdAndUserIdAndIsCorrect(anyLong(), anyString(), eq(true)))
                    .thenReturn(false);
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
            when(participationRepository
                    .existsByQuiz_QuizIdAndUserIdAndIsCorrect(7L, "u7", true))
                    .thenReturn(false);
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
}
