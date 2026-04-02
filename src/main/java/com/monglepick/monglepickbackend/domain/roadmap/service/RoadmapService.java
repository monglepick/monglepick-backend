package com.monglepick.monglepickbackend.domain.roadmap.service;

import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseProgressResponse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserCourseProgressRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 도장깨기(로드맵) 코스 진행 관리 서비스.
 *
 * <p>영화 인증, 코스 완주 판정, 리워드 지급을 담당한다.</p>
 *
 * <h3>진행 흐름</h3>
 * <pre>
 * verifyMovie() 호출
 *   → UserCourseProgress 조회 or 신규 생성
 *   → verify() — verifiedMovies++, progressPercent 재계산
 *   → isCompleted() 판정
 *   → 완주 시: complete(), COURSE_COMPLETE 리워드, COURSE_FIRST 리워드, 업적 연동
 * </pre>
 *
 * <h3>리워드 지급 정책</h3>
 * <ul>
 *   <li>{@code COURSE_COMPLETE} — 코스별 1회 가변 포인트 지급 (PER_REF)</li>
 *   <li>{@code COURSE_FIRST}    — 최초 코스 완주 시 1회만 지급 (max_count=1)</li>
 * </ul>
 *
 * @see UserCourseProgress  코스 진행 현황 엔티티
 * @see RewardService       포인트 지급 위임
 * @see AchievementService  업적 달성 연동
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoadmapService {

    /** 코스 진행 현황 레포지토리 — (user_id, course_id) 기준 조회/생성 */
    private final UserCourseProgressRepository progressRepo;

    /** 리워드 서비스 — 완주 포인트 지급 위임 */
    private final RewardService rewardService;

    /** 업적 서비스 — 코스 완주 업적 달성 연동 */
    private final AchievementService achievementService;

    // ────────────────────────────────────────────────────────────────
    // public 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 코스 내 영화 인증을 처리한다.
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>기존 진행 레코드 조회. 없으면 신규 생성.</li>
     *   <li>이미 완주한 코스이면 {@link BusinessException} 발생.</li>
     *   <li>{@link UserCourseProgress#verify()} 호출 — verifiedMovies++, progressPercent 재계산.</li>
     *   <li>완주 판정 ({@code verifiedMovies >= totalMovies}):</li>
     *   <ul>
     *     <li>{@link UserCourseProgress#complete(LocalDateTime)} 호출.</li>
     *     <li>{@code COURSE_COMPLETE} 리워드 지급 ({@code grantRewardWithAmount}, 코스별 동적 포인트).</li>
     *     <li>{@code COURSE_FIRST} 리워드 지급 (최초 완주 1회, max_count=1으로 중복 자동 차단).</li>
     *     <li>{@code course_complete} 업적 달성 확인.</li>
     *   </ul>
     * </ol>
     *
     * @param userId       사용자 ID
     * @param courseId     코스 ID (roadmap_courses.course_id slug 형태)
     * @param totalMovies  코스 내 총 영화 수 (신규 시작 시 필요, 기존 진행 레코드 있으면 무시됨)
     * @param rewardPoints 완주 시 지급할 포인트 (코스별 설정값, 0이면 포인트 미지급)
     * @return 업데이트된 코스 진행 현황 DTO
     * @throws BusinessException {@link ErrorCode#INVALID_INPUT} 이미 완주한 코스 재인증 시도 시
     */
    @Transactional
    public CourseProgressResponse verifyMovie(String userId, String courseId,
                                              int totalMovies, int rewardPoints) {
        // ① 기존 진행 레코드 조회 or 신규 생성
        UserCourseProgress progress = progressRepo
                .findByUserIdAndCourseId(userId, courseId)
                .orElseGet(() -> {
                    // 첫 번째 인증 — 진행 레코드 신규 생성
                    UserCourseProgress newProgress = UserCourseProgress.builder()
                            .userId(userId)
                            .courseId(courseId)
                            .totalMovies(totalMovies)
                            .startedAt(LocalDateTime.now())
                            .build();
                    log.info("코스 진행 시작: userId={}, courseId={}, totalMovies={}",
                            userId, courseId, totalMovies);
                    return progressRepo.save(newProgress);
                });

        // ② 이미 완주한 코스이면 추가 인증 불가
        if (progress.getStatus() == CourseProgressStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미 완주한 코스입니다: courseId=" + courseId);
        }

        // ③ 영화 인증 처리 — verifiedMovies++, progressPercent 재계산
        progress.verify();
        log.debug("영화 인증: userId={}, courseId={}, verifiedMovies={}/{}",
                userId, courseId, progress.getVerifiedMovies(), progress.getTotalMovies());

        // ④ 완주 판정
        if (progress.getVerifiedMovies() >= progress.getTotalMovies()) {
            handleCourseComplete(userId, courseId, rewardPoints, progress);
        }

        return CourseProgressResponse.from(progress);
    }

    /**
     * 특정 사용자의 전체 코스 진행 현황 목록을 조회한다.
     *
     * <p>마이페이지 도장깨기 탭에서 진행 중/완료 코스 목록을 표시할 때 사용한다.</p>
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 전체 코스 진행 현황 엔티티 목록
     */
    public List<UserCourseProgress> getCourseProgress(String userId) {
        return progressRepo.findByUserId(userId);
    }

    // ────────────────────────────────────────────────────────────────
    // private 헬퍼 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 코스 완주 후처리를 담당한다.
     *
     * <p>complete() 호출 → COURSE_COMPLETE 리워드 → COURSE_FIRST 리워드 → 업적 연동 순으로 처리한다.
     * 리워드/업적 서비스 호출은 REQUIRES_NEW 트랜잭션으로 분리되어,
     * 지급 실패가 완주 처리 롤백을 유발하지 않는다.</p>
     *
     * @param userId       사용자 ID
     * @param courseId     완주한 코스 ID
     * @param rewardPoints 코스별 완주 포인트
     * @param progress     완주 처리할 진행 엔티티
     */
    private void handleCourseComplete(String userId, String courseId,
                                       int rewardPoints, UserCourseProgress progress) {
        // 완주 상태 전환
        progress.complete(LocalDateTime.now());
        progress.markRewardGranted();
        log.info("코스 완주: userId={}, courseId={}, rewardPoints={}", userId, courseId, rewardPoints);

        // COURSE_COMPLETE — 코스별 동적 포인트 지급 (PER_REF 정책, 코스당 1회)
        if (rewardPoints > 0) {
            rewardService.grantRewardWithAmount(
                    userId,
                    "COURSE_COMPLETE",
                    "course_" + courseId,
                    rewardPoints
            );
        }

        // COURSE_FIRST — 최초 완주 1회 보너스 (max_count=1로 중복 자동 차단)
        rewardService.grantReward(userId, "COURSE_FIRST", "course_first", 0);

        // 업적 달성 확인 — course_complete 코드로 코스별 1회 업적 처리
        achievementService.checkAndGrant(userId, "course_complete", courseId);
    }
}
