package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.UserPoint;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 사용자 포인트 리포지토리 — user_points 테이블 접근 계층.
 *
 * <p>사용자별 포인트 잔액 조회 및 동시성 안전한 갱신을 제공한다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserId(String)} — 일반 조회 (락 없음, 읽기 전용)</li>
 *   <li>{@link #findByUserIdForUpdate(String)} — 비관적 쓰기 락 (SELECT FOR UPDATE)
 *       으로 포인트 차감/획득 시 동시성 충돌 방지</li>
 *   <li>{@link #existsByUserId(String)} — 존재 여부 확인 (멱등 초기화용)</li>
 * </ul>
 *
 * <h3>동시성 전략</h3>
 * <p>포인트 차감/획득은 반드시 {@code findByUserIdForUpdate()}를 사용하여
 * 비관적 락(PESSIMISTIC_WRITE)으로 행 잠금 후 수행해야 한다.
 * 이는 동일 사용자의 동시 요청(예: AI 추천 + 출석 보상)에서
 * lost update를 방지한다.</p>
 */
public interface UserPointRepository extends JpaRepository<UserPoint, Long> {

    /**
     * 사용자 ID로 포인트 레코드를 조회한다.
     *
     * <p>락 없이 조회하므로 읽기 전용 용도(잔액 확인, 등급 표시)에만 사용한다.
     * 포인트 변경이 필요하면 {@link #findByUserIdForUpdate(String)}을 사용할 것.</p>
     *
     * @param userId 사용자 ID (VARCHAR(50))
     * @return 포인트 레코드 (없으면 Optional.empty)
     */
    Optional<UserPoint> findByUserId(String userId);

    /**
     * 사용자 ID로 포인트 레코드를 비관적 쓰기 락과 함께 조회한다.
     *
     * <p>SELECT ... FOR UPDATE를 실행하여 트랜잭션 종료 시까지 해당 행을 잠근다.
     * 포인트 차감({@code deductPoints}), 획득({@code addPoints}) 등
     * 잔액 변경 시 반드시 이 메서드를 사용해야 한다.</p>
     *
     * <p>주의: 반드시 {@code @Transactional} 컨텍스트 안에서 호출해야 하며,
     * 트랜잭션 범위를 최소화하여 데드락 위험을 줄여야 한다.</p>
     *
     * @param userId 사용자 ID (VARCHAR(50))
     * @return 포인트 레코드 (없으면 Optional.empty)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UserPoint p WHERE p.userId = :userId")
    Optional<UserPoint> findByUserIdForUpdate(@Param("userId") String userId);

    /**
     * 사용자 ID의 포인트 레코드 존재 여부를 확인한다.
     *
     * <p>회원가입 시 포인트 초기화({@code initializePoint})의 멱등성 보장에 사용된다.
     * 이미 존재하면 초기화를 건너뛴다.</p>
     *
     * @param userId 사용자 ID (VARCHAR(50))
     * @return 존재하면 true, 없으면 false
     */
    boolean existsByUserId(String userId);
}
