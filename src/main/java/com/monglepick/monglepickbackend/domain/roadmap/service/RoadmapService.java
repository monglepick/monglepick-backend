package com.monglepick.monglepickbackend.domain.roadmap.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseProgressResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseDetailResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseListResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseStartResponse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.entity.RoadmapCourse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;
import com.monglepick.monglepickbackend.domain.roadmap.repository.RoadmapCourseRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.UserCourseProgressRepository;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    /** 코스 레포지토리 — roadmap_courses 테이블 접근 */
    private final RoadmapCourseRepository courseRepo;

    /** 리워드 서비스 — 완주 포인트 지급 위임 */
    private final RewardService rewardService;

    /** 업적 서비스 — 코스 완주 업적 달성 연동 */
    private final AchievementService achievementService;

    /**
     * JSON 파싱용 ObjectMapper (스레드 안전, 클래스 로딩 시 1회 초기화).
     * movieIds JSON 컬럼(예: ["12345","67890"])을 List&lt;String&gt;으로 역직렬화할 때 사용한다.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    // 코스 목록/상세 조회
    // ────────────────────────────────────────────────────────────────

    /**
     * 코스 목록을 조회한다.
     *
     * <p>theme 파라미터가 null이면 전체 코스를 반환하고,
     * 값이 있으면 해당 테마의 코스만 필터링하여 반환한다.
     * 각 코스에 대해 현재 사용자의 진행률(progressPercent)을 함께 계산한다.</p>
     *
     * <h4>비로그인 사용자 처리</h4>
     * <p>userId가 null이면 모든 코스의 progressPercent를 0.0으로 반환한다.</p>
     *
     * @param theme  테마 필터 (null이면 전체 조회)
     * @param userId 현재 사용자 ID (비로그인 시 null 허용)
     * @return 코스 목록 DTO 리스트
     */
    public List<CourseListResponse> getCourses(@Nullable String theme, @Nullable String userId) {
        // 테마 유무에 따라 조회 범위 결정
        List<RoadmapCourse> courses = (theme != null && !theme.isBlank())
                ? courseRepo.findByTheme(theme)
                : courseRepo.findAll();

        // 각 코스에 사용자 진행률 매핑.
        // is_active=false (관리자가 비활성화한) 코스는 사용자 측 노출에서 제외한다.
        // 기존 진행 기록은 보존되지만, 신규 시작과 목록 노출만 차단된다.
        return courses.stream()
                .filter(course -> Boolean.TRUE.equals(course.getIsActive()))
                .map(course -> {
                    double progress = resolveProgressPercent(userId, course.getCourseId());
                    return CourseListResponse.from(course, progress);
                })
                .toList();
    }

    /**
     * 코스 상세 정보를 조회한다.
     *
     * <p>courseId(slug)로 RoadmapCourse를 조회하고,
     * movieIds JSON 컬럼을 파싱하여 List&lt;String&gt;으로 변환한다.
     * 현재 사용자의 진행 레코드 존재 여부로 started 플래그를 설정한다.</p>
     *
     * @param courseId 코스 슬러그 (예: "nolan-filmography")
     * @param userId   현재 사용자 ID (비로그인 시 null 허용)
     * @return 코스 상세 DTO
     * @throws BusinessException {@link ErrorCode#NOT_FOUND} 코스가 존재하지 않을 때
     */
    public CourseDetailResponse getCourseDetail(String courseId, @Nullable String userId) {
        // 코스 조회 — slug 기준
        RoadmapCourse course = courseRepo.findByCourseId(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_COURSE_NOT_FOUND,
                        "존재하지 않는 코스입니다: courseId=" + courseId));

        // movieIds JSON 컬럼 파싱 (파싱 실패 시 빈 목록으로 fallback)
        List<String> movieIdList = parseMovieIds(course.getMovieIds());

        // 사용자 진행 레코드 조회
        Optional<UserCourseProgress> progressOpt = (userId != null)
                ? progressRepo.findByUserIdAndCourseId(userId, courseId)
                : Optional.empty();

        boolean started = progressOpt.isPresent();
        double progressPercent = progressOpt
                .map(p -> p.getProgressPercent().doubleValue())
                .orElse(0.0);

        return CourseDetailResponse.from(course, movieIdList, started, progressPercent);
    }

    // ────────────────────────────────────────────────────────────────
    // 코스 시작
    // ────────────────────────────────────────────────────────────────

    /**
     * 코스를 시작한다 (UserCourseProgress 레코드 생성).
     *
     * <p>이미 진행 레코드가 존재하면 신규 생성 없이 동일 응답을 반환한다 (멱등성 보장).
     * 최초 시작 시에만 레코드를 INSERT하며, totalMovies는 RoadmapCourse에서 가져온다.</p>
     *
     * @param courseId 코스 슬러그
     * @param userId   현재 사용자 ID
     * @return 코스 시작 응답 DTO (courseId + status="IN_PROGRESS")
     * @throws BusinessException {@link ErrorCode#NOT_FOUND} 코스가 존재하지 않을 때
     */
    @Transactional
    public CourseStartResponse startCourse(String courseId, String userId) {
        // 코스 존재 여부 확인
        RoadmapCourse course = courseRepo.findByCourseId(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_COURSE_NOT_FOUND,
                        "존재하지 않는 코스입니다: courseId=" + courseId));

        // 이미 진행 레코드가 있으면 멱등 응답 반환 (INSERT 생략)
        boolean alreadyStarted = progressRepo
                .findByUserIdAndCourseId(userId, courseId)
                .isPresent();

        if (alreadyStarted) {
            log.debug("코스 이미 시작됨 (멱등 응답): userId={}, courseId={}", userId, courseId);
            return new CourseStartResponse(courseId, CourseProgressStatus.IN_PROGRESS.name());
        }

        // 진행 레코드 신규 생성 — verifiedMovies=0, status=IN_PROGRESS
        UserCourseProgress progress = UserCourseProgress.builder()
                .userId(userId)
                .courseId(courseId)
                .totalMovies(course.getMovieCount() != null ? course.getMovieCount() : 0)
                .startedAt(LocalDateTime.now())
                .build();
        progressRepo.save(progress);

        log.info("코스 시작: userId={}, courseId={}, totalMovies={}",
                userId, courseId, progress.getTotalMovies());

        return new CourseStartResponse(courseId, CourseProgressStatus.IN_PROGRESS.name());
    }

    // ────────────────────────────────────────────────────────────────
    // 영화 완료 마킹
    // ────────────────────────────────────────────────────────────────

    /**
     * 코스 내 특정 영화를 완료 처리한다.
     *
     * <p>기존 {@link #verifyMovie} 로직에 위임한다.
     * 완주 포인트는 RoadmapCourse에 별도 필드가 없으므로 기본값 100P를 사용한다.</p>
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>코스 존재 확인 — courseId로 RoadmapCourse 조회</li>
     *   <li>verifyMovie() 위임 — verifiedMovies++, 완주 판정, 리워드 지급</li>
     * </ol>
     *
     * @param courseId 코스 슬러그
     * @param movieId  완료 처리할 영화 ID (현재 버전에서는 로그 기록용; 중복 방지는 서비스 레이어 미구현)
     * @param userId   현재 사용자 ID
     * @return 업데이트된 코스 진행 현황 DTO
     * @throws BusinessException {@link ErrorCode#NOT_FOUND} 코스가 존재하지 않을 때
     */
    @Transactional
    public CourseProgressResponse completeMovie(String courseId, String movieId, String userId) {
        // 코스 존재 확인 (totalMovies, defaultRewardPoints 조회 목적)
        RoadmapCourse course = courseRepo.findByCourseId(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_COURSE_NOT_FOUND,
                        "존재하지 않는 코스입니다: courseId=" + courseId));

        int totalMovies = course.getMovieCount() != null ? course.getMovieCount() : 0;
        /* RoadmapCourse 엔티티에 defaultRewardPoints 컬럼이 없으므로 기본값 100P 사용 */
        int rewardPoints = 100;

        log.info("영화 완료 처리: userId={}, courseId={}, movieId={}", userId, courseId, movieId);

        // verifyMovie에 위임 — 진행 레코드 생성/업데이트 + 완주 판정 + 리워드 지급
        return verifyMovie(userId, courseId, totalMovies, rewardPoints);
    }

    // ────────────────────────────────────────────────────────────────
    // private 헬퍼 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 특정 사용자의 특정 코스 진행률(%)을 반환한다.
     *
     * <p>userId가 null이거나 진행 레코드가 없으면 0.0을 반환한다.</p>
     *
     * @param userId   사용자 ID (null 허용)
     * @param courseId 코스 슬러그
     * @return 진행률 % (0.0 ~ 100.0)
     */
    private double resolveProgressPercent(@Nullable String userId, String courseId) {
        if (userId == null) {
            return 0.0;
        }
        return progressRepo.findByUserIdAndCourseId(userId, courseId)
                .map(p -> p.getProgressPercent().doubleValue())
                .orElse(0.0);
    }

    /**
     * RoadmapCourse.movieIds JSON 문자열을 List&lt;String&gt;으로 파싱한다.
     *
     * <p>movieIds 컬럼에는 JSON 배열 형태로 영화 ID가 저장된다.
     * 예: {@code ["12345", "67890", "11111"]}
     * 파싱 실패(null, 빈 문자열, malformed JSON) 시 빈 리스트를 반환하고 WARN 로그를 남긴다.</p>
     *
     * @param movieIdsJson JSON 배열 문자열 (nullable)
     * @return 파싱된 영화 ID 목록 (실패 시 빈 리스트)
     */
    private List<String> parseMovieIds(String movieIdsJson) {
        if (movieIdsJson == null || movieIdsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(movieIdsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("movieIds JSON 파싱 실패 — 빈 목록으로 fallback. json={}, error={}",
                    movieIdsJson, e.getMessage());
            return Collections.emptyList();
        }
    }

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
