package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.UserActivityProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 유저 활동 진행률 리포지토리 — user_activity_progress 테이블 접근 계층.
 *
 * <p>RewardService에서 리워드 지급 전 한도 검사 + 카운터 갱신에 사용한다.
 * 동시성 안전을 위해 비관적 쓰기 락(SELECT FOR UPDATE) 메서드를 제공한다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserIdAndActionTypeForUpdate} — 비관적 락으로 조회 (리워드 지급 시 필수)</li>
 *   <li>{@link #findByUserIdAndActionType} — 일반 조회 (읽기 전용)</li>
 *   <li>{@link #findAllByUserId} — 사용자의 전체 활동 진행률 조회</li>
 * </ul>
 *
 * @see UserActivityProgress
 */
public interface UserActivityProgressRepository extends JpaRepository<UserActivityProgress, Long> {

    /**
     * 비관적 쓰기 락으로 유저의 특정 활동 진행률을 조회한다 (SELECT FOR UPDATE).
     *
     * <p>RewardService.grantReward()에서 반드시 이 메서드를 사용해야 한다.
     * 트랜잭션 종료 시까지 해당 행을 잠가 동시 리워드 요청의 경쟁 조건을 방지한다.</p>
     *
     * <p>조회 후 {@code lazyResetIfNeeded(today)}를 호출하여 일일 카운터를 리셋하고,
     * 한도 검사 → 포인트 지급 → 카운터 갱신을 잠금 상태에서 원자적으로 수행한다.</p>
     *
     * @param userId     사용자 ID
     * @param actionType 활동 유형 코드 (reward_policy.action_type)
     * @return 진행률 레코드 (없으면 Optional.empty → 신규 생성 필요)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UserActivityProgress p WHERE p.userId = :userId AND p.actionType = :actionType")
    Optional<UserActivityProgress> findByUserIdAndActionTypeForUpdate(
            @Param("userId") String userId,
            @Param("actionType") String actionType
    );

    /**
     * 유저의 특정 활동 진행률을 일반 조회한다 (락 없음, 읽기 전용).
     *
     * <p>클라이언트에서 활동 현황을 표시하거나, 관리자 통계 등 읽기 전용 용도로 사용.</p>
     *
     * @param userId     사용자 ID
     * @param actionType 활동 유형 코드
     * @return 진행률 레코드 (없으면 Optional.empty)
     */
    Optional<UserActivityProgress> findByUserIdAndActionType(String userId, String actionType);

    /**
     * 사용자의 전체 활동 진행률 목록을 조회한다.
     *
     * <p>클라이언트의 "내 활동 현황" 화면이나 관리자 사용자 상세 페이지에서 사용.</p>
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 모든 활동 진행률 (없으면 빈 리스트)
     */
    List<UserActivityProgress> findAllByUserId(String userId);

    /**
     * 특정 활동 유형의 전체 사용자 진행률을 조회한다.
     *
     * <p>관리자 리워드 대시보드에서 활동별 통계(수혜 유저 수, 한도 도달률 등) 산출에 사용.</p>
     *
     * @param actionType 활동 유형 코드
     * @return 해당 활동의 모든 사용자 진행률
     */
    List<UserActivityProgress> findAllByActionType(String actionType);

    /**
     * 활동 진행 레코드를 가진 고유 사용자 수를 집계한다.
     *
     * <p>관리자 통계 "활동 사용자" KPI는 사용자 수 지표이므로
     * user_activity_progress 행 수가 아니라 DISTINCT userId 기준으로 계산한다.</p>
     *
     * @return 1개 이상 활동 진행 레코드가 있는 고유 사용자 수
     */
    @Query("SELECT COUNT(DISTINCT uap.userId) FROM UserActivityProgress uap")
    long countDistinctUsers();

    // ══════════════════════════════════════════════
    // 관리자 통계용 집계 쿼리 (AdminStatsService 섹션 12 — 사용자 참여도)
    // ══════════════════════════════════════════════

    /**
     * actionType별 참여 사용자 수와 총 활동 횟수를 집계한다.
     *
     * <p>관리자 통계 "활동별 참여 현황" 차트에 사용된다.
     * 반환: [actionType(String), userCount(Long), totalActions(Long)] 형태의 Object[] 리스트.
     * totalActions 내림차순으로 정렬하여 가장 활발한 활동 유형이 먼저 반환된다.</p>
     *
     * @return [actionType, userCount, totalActions] Object[] 리스트
     */
    @Query("""
            SELECT uap.actionType, COUNT(uap), SUM(uap.totalCount)
            FROM UserActivityProgress uap
            GROUP BY uap.actionType
            ORDER BY SUM(uap.totalCount) DESC
            """)
    List<Object[]> countGroupByActionType();
}
