package com.monglepick.monglepickbackend.domain.roadmap.scheduler;

import com.monglepick.monglepickbackend.admin.repository.AdminQuizRepository;
import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QuizPublishScheduler} 단위 테스트.
 *
 * <p>Mockito 기반 — Repository 만 Mock 처리하고 스케줄러의 분기·멱등·FIFO 로직을 검증한다.</p>
 *
 * <h3>테스트 그룹</h3>
 * <ul>
 *   <li>{@link MultiCandidateTest} — APPROVED 후보 1건이 정상 발행되는지</li>
 *   <li>{@link IdempotencyTest}    — 같은 날짜 PUBLISHED 가 이미 있을 때 스킵</li>
 *   <li>{@link NoCandidateTest}    — APPROVED 후보가 0건이면 발행하지 않음</li>
 *   <li>{@link RunDailyTest}       — 스케줄 진입점이 예외를 흡수하는지</li>
 *   <li>{@link ManualTriggerTest}  — 수동 트리거가 동일 정책으로 동작하는지</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class QuizPublishSchedulerTest {

    /** 한국 표준시 — 스케줄러와 동일 zone */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private AdminQuizRepository adminQuizRepository;

    @InjectMocks
    private QuizPublishScheduler scheduler;

    /** 테스트용 Quiz 인스턴스 빌더 — Lombok @Builder 활용 */
    private Quiz buildApprovedQuiz(Long id, String movieId) {
        return Quiz.builder()
                .quizId(id)
                .movieId(movieId)
                .question("테스트 퀴즈")
                .correctAnswer("정답")
                .options("[\"정답\",\"오답1\",\"오답2\",\"오답3\"]")
                .rewardPoint(10)
                .status(Quiz.QuizStatus.APPROVED)
                .build();
    }

    // ============================================================
    // 1) 정상 발행 — APPROVED 후보 1건이 PUBLISHED + 오늘 quiz_date 로 전환
    // ============================================================

    @Nested
    @DisplayName("APPROVED 후보가 있는 정상 케이스")
    class MultiCandidateTest {

        @Test
        @DisplayName("후보 1건을 PUBLISHED + 오늘 날짜로 발행한다")
        void publishesCandidateWithTodayDate() {
            // given — 멱등 가드 통과 + 후보 1건
            Quiz candidate = buildApprovedQuiz(1L, "movie-1");
            LocalDate today = LocalDate.now(KST);

            when(adminQuizRepository.existsByStatusAndQuizDate(Quiz.QuizStatus.PUBLISHED, today))
                    .thenReturn(false);
            when(adminQuizRepository
                    .findFirstByStatusAndQuizDateIsNullOrderByCreatedAtAsc(Quiz.QuizStatus.APPROVED))
                    .thenReturn(Optional.of(candidate));

            // when
            int result = scheduler.manualPublish();

            // then — 1건 발행 + 엔티티 상태 전이 + save 호출
            assertThat(result).isEqualTo(1);
            assertThat(candidate.getStatus()).isEqualTo(Quiz.QuizStatus.PUBLISHED);
            assertThat(candidate.getQuizDate()).isEqualTo(today);
            verify(adminQuizRepository).save(candidate);
        }
    }

    // ============================================================
    // 2) 멱등성 — 같은 날짜의 PUBLISHED 가 이미 있으면 발행 X
    // ============================================================

    @Nested
    @DisplayName("멱등 가드 — 오늘 PUBLISHED 가 이미 존재")
    class IdempotencyTest {

        @Test
        @DisplayName("same-day PUBLISHED 가 있으면 후보 조회/발행을 모두 스킵한다")
        void skipsWhenTodayAlreadyPublished() {
            // given — 멱등 가드 hit
            LocalDate today = LocalDate.now(KST);
            when(adminQuizRepository.existsByStatusAndQuizDate(Quiz.QuizStatus.PUBLISHED, today))
                    .thenReturn(true);

            // when
            int result = scheduler.manualPublish();

            // then — 0 반환 + 후보 조회 / save 모두 호출되지 않음
            assertThat(result).isEqualTo(0);
            verify(adminQuizRepository, never())
                    .findFirstByStatusAndQuizDateIsNullOrderByCreatedAtAsc(any());
            verify(adminQuizRepository, never()).save(any());
        }
    }

    // ============================================================
    // 3) APPROVED 후보가 0건인 경우 — warn 로그 + 발행 X
    // ============================================================

    @Nested
    @DisplayName("APPROVED 대기열 빈 상태")
    class NoCandidateTest {

        @Test
        @DisplayName("APPROVED 0건이면 save 호출 없이 0 을 반환한다")
        void returnsZeroWhenNoCandidate() {
            // given — 멱등 통과 + 후보 비어있음
            LocalDate today = LocalDate.now(KST);
            when(adminQuizRepository.existsByStatusAndQuizDate(Quiz.QuizStatus.PUBLISHED, today))
                    .thenReturn(false);
            when(adminQuizRepository
                    .findFirstByStatusAndQuizDateIsNullOrderByCreatedAtAsc(Quiz.QuizStatus.APPROVED))
                    .thenReturn(Optional.empty());

            // when
            int result = scheduler.manualPublish();

            // then
            assertThat(result).isEqualTo(0);
            verify(adminQuizRepository, never()).save(any());
        }
    }

    // ============================================================
    // 4) runDailyPublish — Repository 예외를 흡수해 cron 잡이 멈추지 않게
    // ============================================================

    @Nested
    @DisplayName("스케줄 진입점 (runDailyPublish)")
    class RunDailyTest {

        @Test
        @DisplayName("Repository 예외가 던져져도 스케줄러는 예외를 전파하지 않는다")
        void swallowsRepositoryException() {
            // given — exists 호출이 RuntimeException 을 던지는 시나리오
            when(adminQuizRepository.existsByStatusAndQuizDate(any(), any()))
                    .thenThrow(new RuntimeException("DB 일시 장애"));

            // when / then — 예외가 외부로 전파되지 않아야 한다 (cron 잡이 영구 정지하지 않도록)
            scheduler.runDailyPublish();
            // 통과하는 것 자체가 검증 — assertion 없음
        }

        @Test
        @DisplayName("정상 흐름에서도 진입점이 예외 없이 1건 발행을 위임한다")
        void delegatesToPublishOnHappyPath() {
            // given
            Quiz candidate = buildApprovedQuiz(2L, "movie-2");
            LocalDate today = LocalDate.now(KST);
            when(adminQuizRepository.existsByStatusAndQuizDate(Quiz.QuizStatus.PUBLISHED, today))
                    .thenReturn(false);
            when(adminQuizRepository
                    .findFirstByStatusAndQuizDateIsNullOrderByCreatedAtAsc(Quiz.QuizStatus.APPROVED))
                    .thenReturn(Optional.of(candidate));

            // when
            scheduler.runDailyPublish();

            // then — save 가 호출되었고, 엔티티는 PUBLISHED + 오늘 날짜
            verify(adminQuizRepository).save(candidate);
            assertThat(candidate.getStatus()).isEqualTo(Quiz.QuizStatus.PUBLISHED);
            assertThat(candidate.getQuizDate()).isEqualTo(today);
        }
    }

    // ============================================================
    // 5) manualPublish — runDailyPublish 와 동일 정책 (멱등 + FIFO)
    // ============================================================

    @Nested
    @DisplayName("운영자 수동 트리거")
    class ManualTriggerTest {

        @Test
        @DisplayName("수동 트리거도 same-day 멱등 가드를 통과해야 한다")
        void manualPublishRespectsIdempotency() {
            // given
            LocalDate today = LocalDate.now(KST);
            when(adminQuizRepository.existsByStatusAndQuizDate(Quiz.QuizStatus.PUBLISHED, today))
                    .thenReturn(true);

            // when
            int result = scheduler.manualPublish();

            // then — runDailyPublish 와 동일하게 0 반환 + save 미호출
            assertThat(result).isEqualTo(0);
            verify(adminQuizRepository, never()).save(any());
        }
    }
}
