package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import com.monglepick.monglepickbackend.domain.roadmap.entity.QuizParticipation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuizParticipationRepository.aggregateMyStats JPQL 가 H2(MySQL 모드) + Hibernate 7 에서
 * 정상 동작하는지 검증.
 *
 * <p>2026-04-29: 운영 GET /api/v1/quizzes/me/stats 가 500 으로 회귀하여 회귀 안전망 추가.
 * Spring Boot 4 / Hibernate 7 환경에서 멀티 SELECT JPQL 결과는 반드시
 * {@code List<Object[]>} 로 받아야 한다 (메서드 반환 타입을 {@code Object[]} 로
 * 선언하면 Hibernate 가 한 번 더 배열로 감싸 캐스팅이 깨진다).</p>
 */
@SpringBootTest
@Transactional
@DisplayName("QuizParticipationRepository — aggregateMyStats 통합 테스트 (2026-04-29)")
class QuizParticipationRepositoryTest {

    @Autowired
    private QuizParticipationRepository repository;

    @Autowired
    private QuizRepository quizRepository;

    @Test
    @DisplayName("응시 0건 사용자 — aggregation 은 항상 1행, [0L, null, null, null] 로 매핑")
    void aggregateMyStats_emptyUser() {
        // when — 응시 기록이 없는 사용자에 대해서도 aggregation 은 항상 1행 반환
        List<Object[]> rows = repository.aggregateMyStats("non-existent-user-stats-test");

        // then — List 가 정확히 1개 row 를 담고 row 는 4개 컬럼
        assertThat(rows).isNotNull();
        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        assertThat(row).hasSize(4);
        assertThat(((Number) row[0]).longValue()).isEqualTo(0L);
        assertThat(row[1]).isNull();
        assertThat(row[2]).isNull();
        assertThat(row[3]).isNull();
    }

    @Test
    @DisplayName("정답 1건 — COUNT=1, 정답수=1, 누적포인트=10, MAX(submittedAt) 채워짐")
    void aggregateMyStats_mixed() {
        // given — Quiz + 정답 1건 응시
        Quiz quiz = quizRepository.save(Quiz.builder()
                .movieId("movie-stats-test-1")
                .question("Q?")
                .correctAnswer("A")
                .options("[\"A\",\"B\"]")
                .rewardPoint(10)
                .status(Quiz.QuizStatus.PUBLISHED)
                .quizDate(LocalDate.now())
                .build());

        repository.save(QuizParticipation.builder()
                .quiz(quiz).userId("u-stats-test-1").selectedOption("A").isCorrect(true)
                .submittedAt(LocalDateTime.of(2026, 4, 29, 9, 0)).build());

        // when
        List<Object[]> rows = repository.aggregateMyStats("u-stats-test-1");

        // then
        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        assertThat(((Number) row[0]).longValue()).isEqualTo(1L);
        assertThat(((Number) row[1]).longValue()).isEqualTo(1L);
        assertThat(((Number) row[2]).longValue()).isEqualTo(10L);
        assertThat(row[3]).isNotNull();
    }
}
