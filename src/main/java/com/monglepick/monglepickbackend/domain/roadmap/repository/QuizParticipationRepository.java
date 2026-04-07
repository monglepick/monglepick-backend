package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.QuizParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 퀴즈 참여 리포지토리 — quiz_participations 테이블 접근.
 *
 * <p>일반/데일리 퀴즈에 사용자가 답변을 제출한 참여 이력을 관리한다.
 * (quiz_id, user_id) UNIQUE 제약으로 동일 퀴즈 중복 참여를 DB 레벨에서도 차단한다.</p>
 *
 * <h3>주요 역할</h3>
 * <ul>
 *   <li>정답 제출 전 중복 참여 여부 확인 ({@link #findByQuiz_QuizIdAndUserId})</li>
 *   <li>이미 정답을 맞춘 경우 리워드 중복 지급 방지 ({@link #existsByQuiz_QuizIdAndUserIdAndIsCorrect})</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-07: 신규 생성 — QuizService.submitAnswer() 구현에서 사용</li>
 * </ul>
 */
public interface QuizParticipationRepository extends JpaRepository<QuizParticipation, Long> {

    /**
     * 특정 사용자의 특정 퀴즈 참여 기록을 조회한다.
     *
     * <p>정답 제출 요청 시 이미 참여한 기록이 있는지 확인하여
     * 중복 제출 처리 방식(재제출 허용 or 차단)을 결정할 때 사용한다.</p>
     *
     * @param quizId 퀴즈 ID
     * @param userId 사용자 ID (VARCHAR(50))
     * @return 참여 기록 (없으면 빈 Optional)
     */
    Optional<QuizParticipation> findByQuiz_QuizIdAndUserId(Long quizId, String userId);

    /**
     * 특정 사용자가 특정 퀴즈에서 정답을 맞춘 참여 기록이 있는지 확인한다.
     *
     * <p>리워드는 최초 정답 1회만 지급한다.
     * 이미 isCorrect=true 참여 기록이 있으면 grantReward 호출을 건너뛴다.</p>
     *
     * @param quizId    퀴즈 ID
     * @param userId    사용자 ID (VARCHAR(50))
     * @param isCorrect 정답 여부 (true 고정으로 사용)
     * @return 해당 조건의 참여 기록이 있으면 true
     */
    boolean existsByQuiz_QuizIdAndUserIdAndIsCorrect(Long quizId, String userId, Boolean isCorrect);
}
