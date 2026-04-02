package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.RewardPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 리워드 정책 리포지토리 — reward_policy 테이블 접근 계층.
 *
 * <p>RewardService에서 활동 발생 시 정책을 조회하거나, 관리자 페이지에서
 * 정책 목록을 카테고리별로 관리할 때 사용한다.</p>
 *
 * <h3>주요 조회 패턴</h3>
 * <ul>
 *   <li>{@link #findByActionTypeAndIsActiveTrue} — 리워드 지급 시 활성 정책 조회 (가장 빈번)</li>
 *   <li>{@link #findByParentActionTypeAndIsActiveTrue} — 활동 후 자식 달성 정책(마일스톤) 연쇄 확인</li>
 *   <li>{@link #findByActionCategoryAndIsActiveTrue} — 관리자 페이지 카테고리별 필터링</li>
 *   <li>{@link #existsByActionType} — 시드 데이터 멱등성 보장 (재시작 시 중복 INSERT 방지)</li>
 * </ul>
 *
 * <h3>트랜잭션 주의사항</h3>
 * <p>이 리포지토리는 별도의 락(@Lock)을 제공하지 않는다. 정책 자체는 읽기 위주이며,
 * 포인트 동시성 제어는 {@link UserActivityProgressRepository#findByUserIdAndActionTypeForUpdate}
 * (비관적 락)에서 담당한다.</p>
 *
 * @see RewardPolicy
 * @see com.monglepick.monglepickbackend.domain.reward.service.RewardService
 */
public interface RewardPolicyRepository extends JpaRepository<RewardPolicy, Long> {

    /**
     * 활동 코드로 활성 정책 단건을 조회한다.
     *
     * <p>RewardService.grantReward()에서 활동 발생 시 가장 먼저 호출하는 메서드.
     * isActive=false인 정책은 반환하지 않으므로, 관리자 페이지에서 정책을
     * 비활성화하면 즉시(서버 재시작 없이) 포인트 지급이 중단된다.</p>
     *
     * @param actionType 활동 유형 코드 (예: "REVIEW_CREATE", "SIGNUP_BONUS")
     * @return 활성 상태의 리워드 정책 (비활성이거나 없으면 Optional.empty)
     */
    Optional<RewardPolicy> findByActionTypeAndIsActiveTrue(String actionType);

    /**
     * 활동 코드로 정책 단건을 조회한다 (활성/비활성 무관).
     *
     * <p>관리자 페이지에서 비활성 정책을 포함하여 특정 정책을 수정하거나
     * 상세 조회할 때 사용한다.</p>
     *
     * @param actionType 활동 유형 코드
     * @return 정책 (없으면 Optional.empty)
     */
    Optional<RewardPolicy> findByActionType(String actionType);

    /**
     * 부모 활동 코드로 자식 달성 정책 목록을 조회한다 (활성만).
     *
     * <p>RewardService.checkThresholdRewards()에서 기준 활동 후 연쇄적으로
     * 달성 가능한 마일스톤/스트릭/일일 보너스를 확인할 때 사용한다.</p>
     *
     * <p>예: REVIEW_CREATE 활동 후 이 메서드로 REVIEW_MILESTONE_5,
     * REVIEW_MILESTONE_20, DAILY_REVIEW_3 등의 자식 정책을 조회하여
     * 달성 여부를 순차 확인한다.</p>
     *
     * <p>달성 정책 자체는 parentActionType=NULL이므로 이 메서드가 빈 리스트를
     * 반환 → 무한 재귀 불가.</p>
     *
     * @param parentActionType 부모 활동 유형 코드 (예: "REVIEW_CREATE", "AI_CHAT_USE")
     * @return 해당 활동을 부모로 가진 활성 달성 정책 목록 (없으면 빈 리스트)
     */
    List<RewardPolicy> findByParentActionTypeAndIsActiveTrue(String parentActionType);

    /**
     * 카테고리별 활성 정책 목록을 조회한다.
     *
     * <p>관리자 페이지에서 CONTENT/ENGAGEMENT/MILESTONE/ATTENDANCE 탭별로
     * 정책을 분류하여 보여줄 때 사용한다.</p>
     *
     * @param actionCategory 활동 카테고리 (CONTENT / ENGAGEMENT / MILESTONE / ATTENDANCE)
     * @return 해당 카테고리의 활성 정책 목록 (없으면 빈 리스트)
     */
    List<RewardPolicy> findByActionCategoryAndIsActiveTrue(String actionCategory);

    /**
     * 전체 활성 정책 목록을 조회한다.
     *
     * <p>관리자 대시보드 전체 정책 목록 화면이나 서비스 시작 시
     * 정책 캐싱(향후 개선) 등에서 사용한다.</p>
     *
     * @return 모든 활성 리워드 정책 목록 (없으면 빈 리스트)
     */
    List<RewardPolicy> findByIsActiveTrue();

    /**
     * 활동 코드 존재 여부를 확인한다.
     *
     * <p>RewardPolicyInitializer에서 시드 데이터를 INSERT하기 전 멱등성 확인에 사용한다.
     * 이미 존재하면 INSERT를 건너뛰어 재시작 시 중복 삽입을 방지한다.</p>
     *
     * @param actionType 활동 유형 코드
     * @return true이면 이미 존재 (INSERT 불필요), false이면 존재하지 않음 (INSERT 필요)
     */
    boolean existsByActionType(String actionType);
}
