package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.AttendanceResponse;
import com.monglepick.monglepickbackend.domain.reward.dto.PointDto.AttendanceStatusResponse;
import com.monglepick.monglepickbackend.domain.reward.entity.UserActivityProgress;
import com.monglepick.monglepickbackend.domain.reward.entity.UserAttendance;
import com.monglepick.monglepickbackend.domain.reward.repository.UserActivityProgressRepository;
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
 * 출석 체크 서비스 — 일일 출석, 연속 출석(streak), 리워드 포인트 지급 비즈니스 로직.
 *
 * <p>사용자가 매일 출석 체크를 수행하면 {@link RewardService#grantReward}를 통해
 * {@code reward_policy} 정책 기반으로 포인트를 지급한다.
 * 포인트 금액·한도·등급 배율·연속 마일스톤(streak) 보상은 모두 정책 테이블에서 관리된다.</p>
 *
 * <h3>리워드 연동 흐름</h3>
 * <ol>
 *   <li>{@code user_attendance} INSERT + streak 계산</li>
 *   <li>{@code UserActivityProgress.updateStreak(today)} — streak 필드 갱신
 *       (grantReward 내부의 threshold 판정이 갱신된 값을 읽어야 하므로 먼저 호출)</li>
 *   <li>{@code rewardService.grantReward(userId, "ATTENDANCE_BASE", "date_" + today, 0)}
 *       — 기본 출석 포인트 + 7/15/30일 연속 마일스톤 보너스 + 등급 배율 자동 처리</li>
 * </ol>
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
 *   <li>{@link #checkIn}: 개별 {@code @Transactional} — 쓰기 트랜잭션 (출석 저장 + streak 갱신)</li>
 *   <li>{@code RewardService.grantReward()} 는 {@code REQUIRES_NEW}로 별도 트랜잭션 수행</li>
 * </ul>
 *
 * @see UserAttendanceRepository
 * @see RewardService#grantReward(String, String, String, int)
 * @see UserActivityProgressRepository
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    /** 출석 체크 리포지토리 (출석 기록 조회/저장) */
    private final UserAttendanceRepository attendanceRepository;

    /**
     * 리워드 서비스 — ATTENDANCE_BASE 정책 기반 포인트 지급 + streak 마일스톤 보너스 위임.
     *
     * <p>기존 하드코딩 방식(10P/30P/60P 직접 지급)을 대체한다.
     * 포인트 금액·한도·등급 배율·threshold 보너스는 reward_policy 테이블에서 관리된다.</p>
     */
    private final RewardService rewardService;

    /**
     * 유저 활동 진행률 리포지토리 — ATTENDANCE_BASE progress의 streak 갱신에 사용.
     *
     * <p>{@code grantReward()} 내부의 threshold 판정이 갱신된 streak을 읽어야 하므로
     * {@code grantReward()} 호출 전에 {@code updateStreak(today)}를 먼저 수행한다.</p>
     */
    private final UserActivityProgressRepository progressRepo;

    // ──────────────────────────────────────────────
    // 출석 체크 (쓰기 트랜잭션)
    // ──────────────────────────────────────────────

    /**
     * 오늘의 출석 체크를 수행하고 리워드 포인트를 지급한다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>오늘 이미 출석했는지 확인 (중복 방지)</li>
     *   <li>가장 최근 출석 기록으로 연속 출석일(streak) 계산</li>
     *   <li>출석 기록 저장 ({@code user_attendance} INSERT)</li>
     *   <li>{@code UserActivityProgress.updateStreak(today)} 호출
     *       — grantReward 내부 threshold 판정이 갱신 값을 읽기 위해 먼저 수행</li>
     *   <li>{@code rewardService.grantReward(userId, "ATTENDANCE_BASE", "date_" + today, 0)} 호출
     *       — 기본 출석 포인트 + streak 마일스톤 보너스 + 등급 배율 자동 처리</li>
     * </ol>
     *
     * <h4>예외 상황</h4>
     * <ul>
     *   <li>오늘 이미 출석 → {@link BusinessException}(ALREADY_ATTENDED, 409 Conflict)</li>
     * </ul>
     *
     * <h4>포인트 지급 방식 변경</h4>
     * <p>기존 하드코딩(1~6일=10P, 7~29일=30P, 30일+=60P)은 {@code reward_policy} 정책으로 대체.
     * 이중 지급 방지를 위해 {@code pointService.earnPoint()} 직접 호출 코드는 제거됨.</p>
     *
     * @param userId 사용자 ID
     * @return 출석 결과 (출석일, 연속일수, 획득 포인트 0·잔액 0 — 실제 금액은 RewardService가 처리)
     * @throws BusinessException 이미 출석한 경우
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

        // 4. UserActivityProgress의 ATTENDANCE_BASE streak 갱신
        //    grantReward() 내부에서 threshold(7일/15일/30일) 판정 시 갱신된 streak을 읽어야 하므로
        //    반드시 grantReward() 호출 전에 수행한다.
        //    첫 출석인 경우 progress 레코드가 없을 수 있으며, 이때는 grantReward가 신규 생성하므로 무시한다.
        Optional<UserActivityProgress> progressOpt =
                progressRepo.findByUserIdAndActionType(userId, "ATTENDANCE_BASE");
        if (progressOpt.isPresent()) {
            progressOpt.get().updateStreak(today);
            log.debug("ATTENDANCE_BASE streak 갱신 완료: userId={}, streak={}", userId, streak);
        } else {
            log.debug("ATTENDANCE_BASE progress 레코드 미존재, grantReward에서 신규 생성 예정: userId={}", userId);
        }

        // 5. 리워드 서비스 연동 — ATTENDANCE_BASE 정책 기반 포인트 지급
        //    - 기본 출석 포인트 (reward_policy.points_amount)
        //    - streak 마일스톤 보너스 (7일/15일/30일 threshold 달성 시 연쇄 지급)
        //    - 등급 배율 자동 적용 (BRONZE=1.0x ~ PLATINUM=2.0x)
        //    - referenceId: "date_YYYY-MM-DD" 형식으로 날짜별 중복 방지
        //    ※ 기존 pointService.earnPoint() 직접 호출(하드코딩 10P/30P/60P)은
        //      이중 지급 방지를 위해 제거하고 이 한 줄로 대체한다.
        rewardService.grantReward(userId, "ATTENDANCE_BASE", "date_" + today, 0);

        log.info("출석 체크 완료 (리워드 위임): userId={}, streak={}", userId, streak);

        // 6. 출석 응답 반환
        //    실제 지급 포인트·잔액은 RewardService(REQUIRES_NEW 트랜잭션)가 처리하므로
        //    응답에는 streak 정보만 포함하고 포인트/잔액은 0으로 반환한다.
        return new AttendanceResponse(today, streak, 0, 0);
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
}
