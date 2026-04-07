package com.monglepick.monglepickbackend.domain.roadmap.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 퀴즈 리워드 엔티티 — quiz_rewards 테이블 매핑.
 *
 * <p>사용자가 퀴즈를 정답으로 풀었을 때 실제 지급된 포인트 내역을 저장한다.
 * 퀴즈 파이프라인의 마지막 단계로, {@link QuizParticipation}에서 is_correct = true인
 * 경우에만 레코드가 생성된다.</p>
 *
 * <h3>파이프라인 역할</h3>
 * <ol>
 *   <li>QuizParticipation.isCorrect = true 확인</li>
 *   <li>Quiz.rewardPoint 기준으로 포인트 계산 (등급별 보너스 등 서비스 레이어에서 처리)</li>
 *   <li>이 테이블에 리워드 지급 레코드 INSERT</li>
 *   <li>reward 도메인의 PointsHistory에도 내역 기록 (서비스 레이어 책임)</li>
 * </ol>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code quiz} — 리워드 대상 퀴즈 (FK → quizzes.quiz_id, LAZY)</li>
 *   <li>{@code userId} — 리워드 수령 사용자 ID (String FK → users.user_id, JPA/MyBatis 하이브리드 §15.4)</li>
 *   <li>{@code rewardPoints} — 실제 지급된 포인트 수 (INT)</li>
 *   <li>{@code rewardedAt} — 포인트 지급 시각 (DATETIME)</li>
 * </ul>
 *
 * <h3>중복 지급 방지</h3>
 * <p>서비스 레이어에서 동일 (quiz_id, user_id) 조합의 QuizReward 존재 여부를
 * 확인한 후 INSERT하여 중복 지급을 방지해야 한다.
 * DB 레벨 UNIQUE 제약은 의도적으로 생략했으며(재지급 시나리오 대비),
 * 비즈니스 로직으로 중복을 제어한다.</p>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-03-31: 신규 생성 — 퀴즈 파이프라인 5테이블 중 리워드 지급 이력 테이블</li>
 * </ul>
 */
@Entity
@Table(name = "quiz_rewards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
/* BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리 */
public class QuizReward extends BaseAuditEntity {

    /**
     * 퀴즈 리워드 레코드 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_reward_id")
    private Long quizRewardId;

    /**
     * 리워드 대상 퀴즈 (FK → quizzes.quiz_id, LAZY, 필수).
     * 어떤 퀴즈에 대한 보상인지 추적하기 위해 유지한다.
     * LAZY 로딩으로 리워드 목록 조회 시 N+1 문제를 방지한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    /**
     * 리워드 수령 사용자 ID — users.user_id를 String으로 직접 참조한다.
     *
     * <p>포인트를 지급받은 사용자를 식별한다.
     * users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 JPA @ManyToOne 매핑을 두지 않고
     * String FK로만 보관한다 (설계서 §15.4). Quiz 참조는 같은 roadmap 도메인이므로
     * @ManyToOne 유지한다.</p>
     */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /**
     * 실제 지급된 포인트 수 (INT, 필수).
     * Quiz.rewardPoint를 기준으로 하되, 등급 보너스 등이 적용된
     * 최종 지급 포인트가 저장된다.
     * 서비스 레이어에서 계산하여 주입한다.
     */
    @Column(name = "reward_points", nullable = false)
    private Integer rewardPoints;

    /**
     * 포인트 지급 시각 (DATETIME, nullable).
     * 실제 포인트가 사용자 계정에 반영된 시각이다.
     * BaseAuditEntity의 created_at(레코드 생성 시각)과 별도로 관리된다.
     */
    @Column(name = "rewarded_at")
    private LocalDateTime rewardedAt;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    /**
     * 포인트 지급 완료를 기록한다.
     * 실제 포인트 지급(reward 도메인 처리) 후 호출하여
     * 지급 시각을 현재 시각으로 설정한다.
     */
    public void markAsRewarded() {
        this.rewardedAt = LocalDateTime.now();
    }
}
