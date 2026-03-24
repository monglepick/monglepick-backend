package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.UserAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 출석 체크 리포지토리 — user_attendance 테이블 접근.
 *
 * <p>출석 체크 기록의 조회, 저장, 연속 출석일(streak) 계산,
 * 월간 출석 캘린더 표시 등에 사용되는 쿼리 메서드를 제공한다.</p>
 *
 * <h3>주요 쿼리 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserIdAndCheckDate} — 특정 날짜 출석 여부 확인 (중복 출석 방지)</li>
 *   <li>{@link #findTopByUserIdOrderByCheckDateDesc} — 가장 최근 출석 기록 조회 (streak 계산용)</li>
 *   <li>{@link #countByUserId} — 총 출석 일수 집계</li>
 *   <li>{@link #findByUserIdAndCheckDateBetween} — 기간별 출석 목록 (월간 캘린더용)</li>
 * </ul>
 *
 * <h3>UNIQUE 제약</h3>
 * <p>user_attendance 테이블은 {@code (user_id, check_date)} 복합 유니크 제약이 있으므로
 * 동일 사용자가 같은 날 두 번 출석할 수 없다.</p>
 *
 * @see UserAttendance
 * @see com.monglepick.monglepickbackend.domain.reward.service.AttendanceService
 */
public interface UserAttendanceRepository extends JpaRepository<UserAttendance, Long> {

    /**
     * 특정 사용자의 특정 날짜 출석 기록을 조회한다.
     *
     * <p>오늘 이미 출석했는지 확인하는 용도로 사용된다.
     * 결과가 존재하면 이미 출석한 상태이므로 중복 출석을 방지한다.</p>
     *
     * @param userId    사용자 ID
     * @param checkDate 출석 날짜 (보통 LocalDate.now())
     * @return 해당 날짜의 출석 기록 (없으면 Optional.empty())
     */
    Optional<UserAttendance> findByUserIdAndCheckDate(String userId, LocalDate checkDate);

    /**
     * 특정 사용자의 가장 최근 출석 기록을 조회한다.
     *
     * <p>연속 출석일(streak) 계산에 사용된다.
     * 가장 최근 출석일이 어제(today - 1)이면 streak를 +1 하고,
     * 그 외의 경우 streak를 1로 리셋한다.</p>
     *
     * @param userId 사용자 ID
     * @return 가장 최근 출석 기록 (출석 이력이 없으면 Optional.empty())
     */
    Optional<UserAttendance> findTopByUserIdOrderByCheckDateDesc(String userId);

    /**
     * 특정 사용자의 총 출석 일수를 집계한다.
     *
     * <p>출석 현황(AttendanceStatusResponse)의 totalDays 필드에 사용된다.</p>
     *
     * @param userId 사용자 ID
     * @return 총 출석 일수 (출석 기록 없으면 0)
     */
    long countByUserId(String userId);

    /**
     * 특정 사용자의 기간별 출석 기록을 조회한다.
     *
     * <p>클라이언트의 월간 출석 캘린더에 출석한 날짜를 표시하기 위해 사용된다.
     * start와 end를 해당 월의 1일과 말일로 설정하면 월간 출석 목록을 얻을 수 있다.</p>
     *
     * <h4>사용 예시</h4>
     * <pre>{@code
     * LocalDate monthStart = today.withDayOfMonth(1);
     * LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
     * List<UserAttendance> monthly = repository.findByUserIdAndCheckDateBetween(userId, monthStart, monthEnd);
     * }</pre>
     *
     * @param userId 사용자 ID
     * @param start  조회 시작 날짜 (포함)
     * @param end    조회 종료 날짜 (포함)
     * @return 해당 기간의 출석 기록 목록 (없으면 빈 리스트)
     */
    List<UserAttendance> findByUserIdAndCheckDateBetween(String userId, LocalDate start, LocalDate end);
}
