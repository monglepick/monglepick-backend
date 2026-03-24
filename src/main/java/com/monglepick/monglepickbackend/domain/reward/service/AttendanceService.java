package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.AttendanceResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.AttendanceStatusResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.EarnResponse;
import com.monglepick.monglepickbackend.domain.reward.entity.UserAttendance;
import com.monglepick.monglepickbackend.domain.reward.repository.UserAttendanceRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 출석 체크 서비스 — 일일 출석, 연속 출석(streak), 보너스 포인트 지급 비즈니스 로직.
 *
 * <p>사용자가 매일 출석 체크를 수행하면 기본 포인트를 지급하고,
 * 연속 출석일(streak)에 따라 추가 보너스를 지급한다.</p>
 *
 * <h3>보너스 포인트 정책</h3>
 * <table>
 *   <tr><th>연속 출석일</th><th>지급 포인트</th><th>설명</th></tr>
 *   <tr><td>1~6일</td><td>10P</td><td>기본 출석 보상</td></tr>
 *   <tr><td>7~29일</td><td>30P</td><td>기본 10P + 7일 연속 보너스 20P</td></tr>
 *   <tr><td>30일 이상</td><td>60P</td><td>기본 10P + 30일 연속 보너스 50P</td></tr>
 * </table>
 *
 * <h3>연속 출석(streak) 계산 규칙</h3>
 * <ul>
 *   <li>어제 출석한 경우: 이전 streak + 1</li>
 *   <li>어제 출석하지 않은 경우: streak 1로 리셋</li>
 *   <li>첫 출석: streak 1</li>
 * </ul>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly = true)} — 기본 읽기 전용</li>
 *   <li>{@link #checkIn}: 개별 {@code @Transactional} — 쓰기 트랜잭션 (출석 저장 + 포인트 지급)</li>
 * </ul>
 *
 * @see UserAttendanceRepository
 * @see PointService#earnPoint(String, int, String, String, String)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    /** 출석 체크 리포지토리 (출석 기록 조회/저장) */
    private final UserAttendanceRepository attendanceRepository;

    /** 포인트 서비스 (출석 보상 포인트 지급) */
    private final PointService pointService;

    // ──────────────────────────────────────────────
    // 보너스 포인트 상수 정의
    // ──────────────────────────────────────────────

    /** 기본 출석 보상 포인트 (1~6일 연속) */
    private static final int BASE_POINT = 10;

    /** 7일 연속 출석 보상 포인트 (기본 10P + 보너스 20P = 30P) */
    private static final int STREAK_7_POINT = 30;

    /** 30일 연속 출석 보상 포인트 (기본 10P + 보너스 50P = 60P) */
    private static final int STREAK_30_POINT = 60;

    /** 7일 연속 보너스 적용 기준 일수 */
    private static final int STREAK_7_THRESHOLD = 7;

    /** 30일 연속 보너스 적용 기준 일수 */
    private static final int STREAK_30_THRESHOLD = 30;

    // ──────────────────────────────────────────────
    // 출석 체크 (쓰기 트랜잭션)
    // ──────────────────────────────────────────────

    /**
     * 오늘의 출석 체크를 수행하고 보너스 포인트를 지급한다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>오늘 이미 출석했는지 확인 (중복 방지)</li>
     *   <li>가장 최근 출석 기록으로 연속 출석일(streak) 계산</li>
     *   <li>출석 기록 저장 (user_attendance INSERT)</li>
     *   <li>streak에 따른 보너스 포인트 계산 및 지급 (PointService.earnPoint 호출)</li>
     * </ol>
     *
     * <h4>예외 상황</h4>
     * <ul>
     *   <li>오늘 이미 출석 → {@link BusinessException}(ALREADY_ATTENDED, 409 Conflict)</li>
     *   <li>포인트 레코드 없음 → {@link BusinessException}(POINT_NOT_FOUND, 404 Not Found)
     *       — PointService에서 발생</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @return 출석 결과 (출석일, 연속일수, 획득 포인트, 잔액)
     * @throws BusinessException 이미 출석한 경우 또는 포인트 레코드 없음
     */
    @Transactional
    public AttendanceResponse checkIn(String userId) {
        LocalDate today = LocalDate.now();
        log.info("출석 체크 시작: userId={}, date={}", userId, today);

        // 1. 오늘 이미 출석했는지 확인 (user_id + check_date UNIQUE 제약)
        if (attendanceRepository.findByUserIdAndCheckDate(userId, today).isPresent()) {
            log.warn("중복 출석 시도: userId={}, date={}", userId, today);
            throw new BusinessException(ErrorCode.ALREADY_ATTENDED);
        }

        // 2. 가장 최근 출석 기록으로 연속 출석일(streak) 계산
        Optional<UserAttendance> lastAttendance =
                attendanceRepository.findTopByUserIdOrderByCheckDateDesc(userId);
        int streak = 1;  // 기본값: 첫 출석 또는 연속 끊김

        if (lastAttendance.isPresent()
                && lastAttendance.get().getCheckDate().equals(today.minusDays(1))) {
            // 어제 출석했으면 streak를 이어감
            streak = lastAttendance.get().getStreakCount() + 1;
            log.debug("연속 출석 유지: userId={}, previousStreak={}, newStreak={}",
                    userId, lastAttendance.get().getStreakCount(), streak);
        } else {
            log.debug("연속 출석 리셋: userId={}, streak=1", userId);
        }

        // 3. 출석 기록 저장 (user_attendance INSERT)
        UserAttendance attendance = UserAttendance.builder()
                .userId(userId)
                .checkDate(today)
                .streakCount(streak)
                .build();
        attendanceRepository.save(attendance);
        log.debug("출석 기록 저장 완료: userId={}, date={}, streak={}", userId, today, streak);

        // 4. 보너스 포인트 계산 및 지급
        int bonus = calculateBonus(streak);
        EarnResponse earnResult = pointService.earnPoint(
                userId,
                bonus,
                "earn",
                "출석 체크 보상 (연속 " + streak + "일)",
                "attendance-" + today
        );

        log.info("출석 체크 완료: userId={}, streak={}, bonus={}P, balance={}",
                userId, streak, bonus, earnResult.balanceAfter());

        // 5. 출석 응답 반환
        return new AttendanceResponse(today, streak, bonus, earnResult.balanceAfter());
    }

    // ──────────────────────────────────────────────
    // 출석 현황 조회 (읽기 전용)
    // ──────────────────────────────────────────────

    /**
     * 사용자의 출석 현황을 조회한다.
     *
     * <p>클라이언트의 출석 체크 화면에서 다음 정보를 표시하기 위해 사용된다:</p>
     * <ul>
     *   <li>현재 연속 출석일 (currentStreak)</li>
     *   <li>누적 총 출석 일수 (totalDays)</li>
     *   <li>오늘 출석 여부 (checkedToday)</li>
     *   <li>이번 달 출석한 날짜 목록 (monthlyDates — 캘린더 표시용)</li>
     * </ul>
     *
     * <h4>연속 출석일 계산 로직</h4>
     * <ul>
     *   <li>마지막 출석일 == 오늘 → streak = 해당 레코드의 streakCount</li>
     *   <li>마지막 출석일 == 어제 → streak = 해당 레코드의 streakCount (내일 출석 시 이어짐)</li>
     *   <li>그 외 → streak = 0 (연속 끊김)</li>
     * </ul>
     *
     * @param userId 사용자 ID
     * @return 출석 현황 (연속일수, 총일수, 오늘출석여부, 월간날짜목록)
     */
    public AttendanceStatusResponse getStatus(String userId) {
        LocalDate today = LocalDate.now();
        log.debug("출석 현황 조회: userId={}", userId);

        // 현재 연속 출석일(streak) 및 오늘 출석 여부 계산
        Optional<UserAttendance> latest =
                attendanceRepository.findTopByUserIdOrderByCheckDateDesc(userId);
        int currentStreak = 0;
        boolean checkedToday = false;

        if (latest.isPresent()) {
            LocalDate lastDate = latest.get().getCheckDate();

            if (lastDate.equals(today)) {
                // 오늘 출석함 → streak 유효, 오늘 출석 완료
                currentStreak = latest.get().getStreakCount();
                checkedToday = true;
            } else if (lastDate.equals(today.minusDays(1))) {
                // 어제 출석함 → streak 유효 (아직 오늘 출석 안 함)
                currentStreak = latest.get().getStreakCount();
            }
            // 그 외: streak = 0 (연속 끊김)
        }

        // 총 출석 일수 집계
        long totalDays = attendanceRepository.countByUserId(userId);

        // 이번 달 출석 날짜 목록 (캘린더 표시용)
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        List<LocalDate> monthlyDates = attendanceRepository
                .findByUserIdAndCheckDateBetween(userId, monthStart, monthEnd)
                .stream()
                .map(UserAttendance::getCheckDate)
                .toList();

        log.debug("출석 현황: userId={}, streak={}, totalDays={}, checkedToday={}, monthlyCount={}",
                userId, currentStreak, totalDays, checkedToday, monthlyDates.size());

        return new AttendanceStatusResponse(currentStreak, (int) totalDays, checkedToday, monthlyDates);
    }

    // ──────────────────────────────────────────────
    // private 헬퍼 메서드
    // ──────────────────────────────────────────────

    /**
     * 연속 출석일(streak)에 따른 보너스 포인트를 계산한다.
     *
     * <p>보너스 정책:</p>
     * <ul>
     *   <li>30일 이상 연속: 60P (기본 10P + 30일 보너스 50P)</li>
     *   <li>7일 이상 연속: 30P (기본 10P + 7일 보너스 20P)</li>
     *   <li>그 외: 10P (기본 출석 보상)</li>
     * </ul>
     *
     * @param streak 연속 출석 일수 (1 이상)
     * @return 지급할 보너스 포인트
     */
    private int calculateBonus(int streak) {
        if (streak >= STREAK_30_THRESHOLD) {
            return STREAK_30_POINT;  // 기본 10P + 30일 보너스 50P = 60P
        }
        if (streak >= STREAK_7_THRESHOLD) {
            return STREAK_7_POINT;   // 기본 10P + 7일 보너스 20P = 30P
        }
        return BASE_POINT;           // 기본 10P
    }
}
