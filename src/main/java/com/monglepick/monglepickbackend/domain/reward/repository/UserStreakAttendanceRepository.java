package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.UserStreakAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 연속 출석 현황 리포지토리 — user_streak_attendance 테이블 접근.
 *
 * <p>사용자의 연속 출석(streak) 달성 이력 조회 및 보상 지급 근거 확인에 사용된다.
 * RewardService에서 연속 출석 보상 처리 시 중복 지급 방지 및 이력 저장에 활용된다.</p>
 *
 * <h3>주요 쿼리 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserId} — 사용자의 전체 연속 출석 달성 이력 조회</li>
 *   <li>{@link #findTopByUserIdOrderByCreatedAtDesc} — 가장 최근 연속 출석 달성 기록 조회</li>
 *   <li>{@link #findByUserIdAndStreakCheckDate} — 특정 연속 출석 일수 달성 이력 확인 (중복 보상 방지)</li>
 * </ul>
 *
 * @see UserStreakAttendance
 */
public interface UserStreakAttendanceRepository extends JpaRepository<UserStreakAttendance, Long> {

    /**
     * 특정 사용자의 전체 연속 출석 달성 이력을 조회한다.
     *
     * <p>마이페이지의 출석 보상 이력 화면에서 연속 출석 달성 목록을 표시할 때 사용한다.</p>
     *
     * @param userId 사용자 ID (users.user_id)
     * @return 해당 사용자의 연속 출석 이력 목록 (없으면 빈 리스트)
     */
    List<UserStreakAttendance> findByUserId(String userId);

    /**
     * 특정 사용자의 가장 최근 연속 출석 달성 기록을 조회한다.
     *
     * <p>현재 진행 중인 streak 상태 확인 및 최근 보상 지급 시점 파악에 사용된다.</p>
     *
     * @param userId 사용자 ID (users.user_id)
     * @return 가장 최근 연속 출석 달성 기록 (없으면 Optional.empty())
     */
    Optional<UserStreakAttendance> findTopByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * 특정 사용자의 특정 연속 출석 일수 달성 이력을 조회한다.
     *
     * <p>동일 연속 출석 일수에 대한 중복 보상 지급을 방지하기 위해 사용된다.
     * 예: 7일 연속 출석 보상을 이미 받은 경우 재지급 차단.</p>
     *
     * @param userId          사용자 ID (users.user_id)
     * @param streakCheckDate 달성한 연속 출석 일수
     * @return 해당 연속 출석 일수 달성 이력 목록 (없으면 빈 리스트)
     */
    List<UserStreakAttendance> findByUserIdAndStreakCheckDate(String userId, Integer streakCheckDate);
}
