package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 사용자 AI 쿼터 리포지토리 — user_ai_quota 테이블 접근 계층.
 *
 * <p>v3.3에서 {@code user_points}에서 분리된 AI 쿼터 테이블에 접근한다.
 * {@link com.monglepick.monglepickbackend.domain.reward.service.QuotaService}에서
 * AI 사용 가능 여부 확인 및 카운터 갱신 시 사용한다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserId(String)} — 일반 조회 (락 없음, 읽기 전용)</li>
 *   <li>{@link #findByUserIdWithLock(String)} — 비관적 쓰기 락 (SELECT FOR UPDATE).
 *       PURCHASED 소스 소비, 자동 지급 이용권 추가 등 카운터 변경 시 사용.</li>
 *   <li>{@link #existsByUserId(String)} — 존재 여부 확인 (회원가입 초기화 멱등성 보장)</li>
 * </ul>
 *
 * <h3>동시성 전략</h3>
 * <p>AI 쿼터 소비({@code consumePurchasedToken}) 및 이용권 추가({@code addPurchasedTokens})는
 * {@link #findByUserIdWithLock}으로 비관적 락을 획득한 뒤 수행해야 한다.
 * 단순 읽기(소스 1/2 판단)는 {@link #findByUserId}를 사용하여 락 경합을 줄인다.</p>
 *
 * @see com.monglepick.monglepickbackend.domain.reward.entity.UserAiQuota
 * @see com.monglepick.monglepickbackend.domain.reward.service.QuotaService
 */
public interface UserAiQuotaRepository extends JpaRepository<UserAiQuota, Long> {

    /**
     * 사용자 ID로 AI 쿼터 레코드를 조회한다.
     *
     * <p>락 없이 조회하므로 읽기 전용 용도(일일 사용량 확인, 잔여 이용권 표시)에 사용한다.
     * PURCHASED 소스 소비나 이용권 추가처럼 쓰기 작업이 필요하면
     * {@link #findByUserIdWithLock(String)}을 사용할 것.</p>
     *
     * @param userId 사용자 ID (VARCHAR(50))
     * @return AI 쿼터 레코드 (없으면 Optional.empty)
     */
    Optional<UserAiQuota> findByUserId(String userId);

    /**
     * 사용자 ID의 AI 쿼터 레코드 존재 여부를 확인한다.
     *
     * <p>회원가입 시 AI 쿼터 초기화({@code initializePoint})의 멱등성 보장에 사용된다.
     * 이미 존재하면 초기화를 건너뛴다.</p>
     *
     * @param userId 사용자 ID (VARCHAR(50))
     * @return 존재하면 true, 없으면 false
     */
    boolean existsByUserId(String userId);

    /**
     * 사용자 ID로 AI 쿼터 레코드를 비관적 쓰기 락과 함께 조회한다.
     *
     * <p>SELECT ... FOR UPDATE를 실행하여 트랜잭션 종료 시까지 해당 행을 잠근다.
     * 구매 이용권 소비({@code consumePurchasedToken}), 이용권 추가({@code addPurchasedTokens}),
     * 자동 지급 이용권 지급 등 카운터 변경 시 반드시 이 메서드를 사용해야 한다.</p>
     *
     * <p>주의: 반드시 {@code @Transactional} 컨텍스트 안에서 호출해야 하며,
     * 트랜잭션 범위를 최소화하여 데드락 위험을 줄여야 한다.</p>
     *
     * @param userId 사용자 ID (VARCHAR(50))
     * @return AI 쿼터 레코드 (없으면 Optional.empty)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM UserAiQuota q WHERE q.userId = :userId")
    Optional<UserAiQuota> findByUserIdWithLock(@Param("userId") String userId);
}
