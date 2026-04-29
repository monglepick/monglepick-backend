package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.QuizDetailResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminQuizDto.UpdateStatusRequest;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link AdminQuizService} 단위 테스트 — 상태 전이 PUBLISHED 분기 회귀 안전망 (2026-04-29).
 *
 * <h3>배경</h3>
 * <p>2026-04-29 운영 회귀 — 어드민이 PATCH {@code /admin/quizzes/{id}/status} 로 PUBLISHED 전이만
 * 누른 퀴즈가 사용자 화면 GET {@code /api/v1/quizzes/today} 에 영원히 노출되지 않는 disconnect.
 * 근본 원인은 {@link Quiz#publish()} 가 status 만 바꾸고 quiz_date 를 NULL 로 남기는데,
 * Service 가 그 메서드를 직접 호출하던 것. 도메인 invariant("PUBLISHED 면 quiz_date 가 반드시
 * 있어야 한다") 와 사용자 EP 의 조건절(quiz_date = today AND status = PUBLISHED) 이 깨졌다.</p>
 *
 * <h3>회귀 차단</h3>
 * <p>본 테스트는 {@link AdminQuizService#updateStatus} PUBLISHED 분기가 항상 quiz_date 를 보장하도록
 * 명시한다. quiz_date 가 비어 있으면 오늘로 자동 채워지고, 사전 명시된 quiz_date 는 그대로 존중된다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminQuizServiceTest {

    @Mock
    private AdminQuizRepository adminQuizRepository;

    @InjectMocks
    private AdminQuizService adminQuizService;

    /** APPROVED 상태 + quiz_date 미지정 퀴즈 빌더 — 어드민이 검수 직후 PUBLISHED 로 올리는 직전 상태. */
    private Quiz buildApprovedQuizWithoutDate(Long id) {
        return Quiz.builder()
                .quizId(id)
                .movieId("movie-1")
                .question("이 영화의 감독은?")
                .correctAnswer("정답")
                .options("[\"A\",\"B\",\"C\",\"D\"]")
                .rewardPoint(10)
                .status(Quiz.QuizStatus.APPROVED)
                .quizDate(null) // ← 운영 회귀 시나리오: 어드민이 quiz_date 를 따로 입력하지 않음
                .build();
    }

    /** APPROVED 상태 + quiz_date 사전 지정(미래 출제 예약) 퀴즈 빌더. */
    private Quiz buildApprovedQuizWithFutureDate(Long id, LocalDate futureDate) {
        return Quiz.builder()
                .quizId(id)
                .movieId("movie-1")
                .question("이 영화의 감독은?")
                .correctAnswer("정답")
                .options("[\"A\",\"B\",\"C\",\"D\"]")
                .rewardPoint(10)
                .status(Quiz.QuizStatus.APPROVED)
                .quizDate(futureDate) // ← 어드민이 다음 주로 예약
                .build();
    }

    // ────────────────────────────────────────────────────────────
    // PUBLISHED 전이 — quiz_date 자동/존중 분기
    // ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStatus(target=PUBLISHED) — quiz_date 자동 보강")
    class PublishedTransitionAutoFillDate {

        /**
         * 회귀 시나리오 정확 재현 — quiz_date 가 NULL 이고 status=APPROVED 인 퀴즈를
         * PUBLISHED 로 전이하면, 사용자 화면 /today EP 에 잡히도록 quiz_date 가 오늘로
         * 자동 채워져야 한다.
         */
        @Test
        @DisplayName("quiz_date 가 null 이면 오늘로 자동 세팅된다")
        void quizDateNull_isAutoFilledWithToday() {
            // given
            Quiz quiz = buildApprovedQuizWithoutDate(101L);
            when(adminQuizRepository.findById(101L)).thenReturn(Optional.of(quiz));

            // when
            QuizDetailResponse response = adminQuizService.updateStatus(
                    101L, new UpdateStatusRequest("PUBLISHED")
            );

            // then — entity 와 응답 DTO 양쪽 모두 quiz_date=오늘 + status=PUBLISHED
            assertThat(quiz.getQuizDate()).isEqualTo(LocalDate.now());
            assertThat(quiz.getStatus()).isEqualTo(Quiz.QuizStatus.PUBLISHED);
            assertThat(response.quizDate()).isEqualTo(LocalDate.now());
            assertThat(response.status()).isEqualTo("PUBLISHED");
        }

        /**
         * 어드민이 PUT 수정 모달에서 미리 미래 날짜로 출제 예약을 걸어둔 케이스 —
         * 그 사전 의도를 무시하고 오늘로 덮어쓰면 운영자 신뢰가 깨진다.
         * publishOn() 은 기존 quiz_date 를 그대로 유지해야 한다.
         */
        @Test
        @DisplayName("quiz_date 가 사전 지정돼 있으면 그 값을 그대로 존중한다")
        void quizDatePreset_isPreserved() {
            // given — 다음 주 화요일에 출제 예약
            LocalDate scheduled = LocalDate.now().plusDays(7);
            Quiz quiz = buildApprovedQuizWithFutureDate(202L, scheduled);
            when(adminQuizRepository.findById(202L)).thenReturn(Optional.of(quiz));

            // when
            QuizDetailResponse response = adminQuizService.updateStatus(
                    202L, new UpdateStatusRequest("PUBLISHED")
            );

            // then — quiz_date 는 사전 예약일 유지, status 만 PUBLISHED 로 변경
            assertThat(quiz.getQuizDate()).isEqualTo(scheduled);
            assertThat(quiz.getStatus()).isEqualTo(Quiz.QuizStatus.PUBLISHED);
            assertThat(response.quizDate()).isEqualTo(scheduled);
            assertThat(response.status()).isEqualTo("PUBLISHED");
        }
    }
}
