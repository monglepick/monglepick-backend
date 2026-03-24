package com.monglepick.monglepickbackend.domain.reward.entity;

import com.monglepick.monglepickbackend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 사용자 포인트 엔티티 — user_points 테이블 매핑.
 *
 * <p>사용자의 포인트 잔액 및 등급 정보를 관리한다.
 * 각 사용자당 하나의 포인트 레코드만 존재한다 (user_id UNIQUE).</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 사용자 ID (FK → users.user_id, UNIQUE)</li>
 *   <li>{@code pointHave} — 현재 보유 포인트</li>
 *   <li>{@code totalEarned} — 누적 획득 포인트</li>
 *   <li>{@code dailyEarned} — 오늘 획득 포인트 (일일 한도 관리용)</li>
 *   <li>{@code dailyReset} — 일일 리셋 기준일 (DATE)</li>
 *   <li>{@code userGrade} — 사용자 등급 (BRONZE, SILVER, GOLD, PLATINUM)</li>
 * </ul>
 */
@Entity
@Table(
        name = "user_points",
        uniqueConstraints = @UniqueConstraint(columnNames = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserPoint extends BaseTimeEntity {

    /** 포인트 레코드 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_id")
    private Long pointId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL, UNIQUE).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 현재 보유 포인트.
     * 기본값: 0.
     */
    @Column(name = "point_have")
    @Builder.Default
    private Integer pointHave = 0;

    /**
     * 누적 획득 포인트.
     * 기본값: 0.
     * 가입 이후 전체 획득 포인트 합산.
     */
    @Column(name = "total_earned")
    @Builder.Default
    private Integer totalEarned = 0;

    /**
     * 오늘 획득 포인트.
     * 기본값: 0.
     * 일일 포인트 획득 한도 관리에 사용된다.
     */
    @Column(name = "daily_earned")
    @Builder.Default
    private Integer dailyEarned = 0;

    /**
     * 일일 리셋 기준일.
     * dailyEarned가 마지막으로 0으로 초기화된 날짜.
     * 날짜가 바뀌면 dailyEarned를 0으로 리셋한다.
     */
    @Column(name = "daily_reset")
    private LocalDate dailyReset;

    /**
     * 사용자 등급 (최대 20자).
     * 기본값: "BRONZE".
     * 누적 포인트에 따라 BRONZE → SILVER → GOLD → PLATINUM 으로 승급.
     */
    @Column(name = "user_grade", length = 20)
    @Builder.Default
    private String userGrade = "BRONZE";

    // ──────────────────────────────────────────────
    // 도메인 메서드 (Lombok @Getter only, setter 대신 사용)
    // ──────────────────────────────────────────────

    /**
     * 포인트 차감.
     *
     * <p>잔액(pointHave)에서 요청 금액을 차감한다.
     * 서비스 레이어에서 사전 검증 후 호출하므로 여기서는 방어적 검증만 수행한다.
     * 잔액이 부족하면 음수 방지를 위해 예외를 발생시킨다.</p>
     *
     * @param amount 차감할 포인트 (양수)
     * @throws IllegalArgumentException 잔액 부족 시 (서비스 레이어에서 이미 검증하므로 정상 흐름에서는 발생하지 않음)
     */
    public void deductPoints(int amount) {
        if (this.pointHave < amount) {
            throw new IllegalArgumentException(
                    "잔액 부족: 보유=" + this.pointHave + ", 필요=" + amount
                            + " (서비스 레이어 사전 검증 누락 가능성 — InsufficientPointException 확인 필요)"
            );
        }
        this.pointHave -= amount;
    }

    /**
     * 포인트 획득.
     *
     * <p>보유 포인트(pointHave), 누적 획득(totalEarned), 일일 획득(dailyEarned)을 갱신한다.
     * 날짜가 바뀌었으면 일일 획득량을 먼저 리셋한 뒤 적용한다.</p>
     *
     * @param amount 획득 포인트 (양수)
     * @param today  오늘 날짜 (일일 리셋 판단용)
     */
    public void addPoints(int amount, LocalDate today) {
        resetDailyIfNeeded(today);
        this.pointHave += amount;
        this.totalEarned += amount;
        this.dailyEarned += amount;
    }

    /**
     * 일일 획득량 리셋.
     *
     * <p>dailyReset 날짜가 오늘이 아니면 dailyEarned를 0으로 초기화하고
     * dailyReset을 오늘로 갱신한다. 이미 오늘이면 아무 작업도 하지 않는다.</p>
     *
     * @param today 오늘 날짜
     */
    public void resetDailyIfNeeded(LocalDate today) {
        if (this.dailyReset == null || !this.dailyReset.equals(today)) {
            this.dailyEarned = 0;
            this.dailyReset = today;
        }
    }

    /**
     * 등급 갱신.
     *
     * <p>사용자 등급을 새로운 값으로 변경한다.
     * 등급 계산 로직은 서비스 레이어에서 수행하며, 이 메서드는 단순히 값을 설정한다.</p>
     *
     * @param newGrade 새 등급 (BRONZE, SILVER, GOLD, PLATINUM)
     */
    public void updateGrade(String newGrade) {
        this.userGrade = newGrade;
    }
}
