package com.monglepick.monglepickbackend.domain.payment.repository;

import com.monglepick.monglepickbackend.domain.payment.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 구독 리포지토리 — user_subscriptions 테이블 데이터 접근.
 *
 * <p>사용자의 구독 현황 조회, 활성 구독 검증, 만료 대상 조회 등을 제공한다.
 * 결제 서비스에서 구독 생성/갱신/취소 시, 스케줄러에서 만료 배치 처리 시 사용된다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserIdAndStatus(String, UserSubscription.Status)} — 특정 상태의 구독 조회 (주로 ACTIVE)</li>
 *   <li>{@link #findByStatusAndExpiresAtBeforeAndAutoRenewTrue(UserSubscription.Status, LocalDateTime)} — 자동 갱신 대상 조회 (스케줄러)</li>
 *   <li>{@link #findByUserIdOrderByCreatedAtDesc(String)} — 사용자 구독 이력 전체 (최신순)</li>
 * </ul>
 *
 * @see UserSubscription 사용자 구독 엔티티
 */
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /**
     * 특정 사용자의 특정 상태 구독을 조회한다.
     *
     * <p>주로 {@code Status.ACTIVE}와 함께 사용하여 현재 활성 구독이 있는지 확인한다.
     * 한 사용자는 동시에 1개의 ACTIVE 구독만 가질 수 있으므로 (서비스 레이어 검증),
     * 최대 1건이 반환된다.</p>
     *
     * <p>활용 예시:</p>
     * <ul>
     *   <li>신규 구독 시: ACTIVE 구독 존재 여부 확인 (중복 구독 방지)</li>
     *   <li>구독 취소 시: ACTIVE 구독 조회 후 {@code cancel()} 호출</li>
     *   <li>구독 혜택 확인: ACTIVE 구독의 plan 정보로 포인트 지급량 결정</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @param status 구독 상태 (ACTIVE / CANCELLED / EXPIRED)
     * @return 해당 상태의 구독 (존재하지 않으면 empty)
     */
    Optional<UserSubscription> findByUserIdAndStatus(String userId, UserSubscription.Status status);

    /**
     * 자동 갱신 대상 구독 목록을 조회한다 (만료 배치/스케줄러용).
     *
     * <p>지정 시각({@code now}) 이전에 만료 예정이면서,
     * 자동 갱신이 활성화된({@code autoRenew=true}) ACTIVE 구독을 반환한다.
     * 스케줄러가 주기적으로 호출하여 자동 결제를 시도한다.</p>
     *
     * <p>SQL 동치:</p>
     * <pre>
     * SELECT * FROM user_subscriptions
     * WHERE status = 'ACTIVE'
     *   AND expires_at &lt; :now
     *   AND auto_renew = TRUE
     * </pre>
     *
     * @param status 구독 상태 (보통 {@code Status.ACTIVE})
     * @param now    현재 시각 (이 시각 이전에 만료된 구독 조회)
     * @return 자동 갱신 대상 구독 목록
     */
    List<UserSubscription> findByStatusAndExpiresAtBeforeAndAutoRenewTrue(
            UserSubscription.Status status, LocalDateTime now);

    /**
     * 사용자의 전체 구독 이력을 최신순으로 조회한다.
     *
     * <p>마이페이지의 구독 이력 화면에서 사용한다.
     * 활성/취소/만료 모든 상태의 구독이 포함되며,
     * 가장 최근에 생성된 구독이 먼저 반환된다.</p>
     *
     * @param userId 사용자 ID
     * @return 구독 이력 목록 (최신순 정렬)
     */
    List<UserSubscription> findByUserIdOrderByCreatedAtDesc(String userId);
}
