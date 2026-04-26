package com.monglepick.monglepickbackend.domain.roadmap.service;

// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseCompleteResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseProgressResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseDetailResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseListResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseStartResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.MovieInfo;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseReviewResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.FinalReviewResponse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseFinalMovie;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseReview;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseVerification;
import com.monglepick.monglepickbackend.domain.roadmap.entity.RoadmapCourse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;
import com.monglepick.monglepickbackend.domain.roadmap.repository.CourseFinalMovieRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.CourseReviewRepository;
import com.monglepick.monglepickbackend.domain.roadmap.repository.CourseVerificationRepository;
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

    /** 영화 레포지토리 — 코스 상세 조회 시 영화 제목/포스터 조회에 사용 */
    private final MovieRepository movieRepository;

    /** 도장깨기 리뷰 레포지토리 — course_review 테이블 저장/조회 */
    private final CourseReviewRepository courseReviewRepository;

    /** 도장깨기 인증 레포지토리 — course_verification 테이블 저장 */
    private final CourseVerificationRepository courseVerificationRepository;

    /** 리워드 서비스 — 완주 포인트 지급 위임 */
    private final RewardService rewardService;

    /** 업적 서비스 — 코스 완주 업적 달성 연동 */
    private final AchievementService achievementService;

    /** 도장깨기 최종 감상평 레포지토리 — course_final_movie 테이블 */
    private final CourseFinalMovieRepository finalMovieRepository;

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
        //    신규 생성 시 totalMovies는 반드시 RoadmapCourse.movieCount에서 가져온다.
        //    파라미터로 넘어온 totalMovies는 레거시 /verify 엔드포인트 호환용이며,
        //    0이거나 신뢰할 수 없으므로 코스 DB 조회 값을 우선 사용한다.
        UserCourseProgress progress = progressRepo
                .findByUserIdAndCourseId(userId, courseId)
                .orElseGet(() -> {
                    // 코스 조회 — movieCount(정확한 totalMovies)와 deadlineDays 확보
                    RoadmapCourse course = courseRepo.findByCourseId(courseId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_COURSE_NOT_FOUND,
                                    "존재하지 않는 코스입니다: courseId=" + courseId));
                    // DB의 movieCount를 사용; 파라미터 totalMovies는 0이거나 틀릴 수 있음
                    int actualTotalMovies = (course.getMovieCount() != null && course.getMovieCount() > 0)
                            ? course.getMovieCount()
                            : totalMovies;
                    LocalDateTime startedAt = LocalDateTime.now();
                    Integer deadlineDays = course.getDeadlineDays();
                    LocalDateTime deadlineAt = (deadlineDays != null && deadlineDays > 0)
                            ? startedAt.plusDays(deadlineDays)
                            : null;
                    UserCourseProgress newProgress = UserCourseProgress.builder()
                            .userId(userId)
                            .courseId(courseId)
                            .totalMovies(actualTotalMovies)
                            .startedAt(startedAt)
                            .deadlineAt(deadlineAt)
                            .build();
                    log.info("코스 진행 시작: userId={}, courseId={}, totalMovies={}, deadlineAt={}",
                            userId, courseId, actualTotalMovies, deadlineAt);
                    return progressRepo.save(newProgress);
                });

        // ② 이미 완주했거나 최종 감상평 대기 중인 코스이면 추가 인증 불가
        if (progress.getStatus() == CourseProgressStatus.COMPLETED
                || progress.getStatus() == CourseProgressStatus.FINAL_REVIEW_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미 모든 영화를 완료한 코스입니다: courseId=" + courseId);
        }

        // ③ 영화 인증 처리 — verifiedMovies++, progressPercent 재계산
        progress.verify();
        log.debug("영화 인증: userId={}, courseId={}, verifiedMovies={}/{}",
                userId, courseId, progress.getVerifiedMovies(), progress.getTotalMovies());

        // ④ 완주 판정 — 모든 영화 완료 시 최종 감상평 대기 상태로 전환
        // 리워드 및 COMPLETED 처리는 최종 감상평 제출(submitFinalReview) 시점에 수행한다.
        if (progress.getTotalMovies() > 0 && progress.getVerifiedMovies() >= progress.getTotalMovies()) {
            progress.enterFinalReviewPending();
            log.info("모든 영화 인증 완료 → 최종 감상평 대기 상태: userId={}, courseId={}", userId, courseId);
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
                    int movieCount = course.getMovieCount() != null ? course.getMovieCount() : 0;
                    double progress = resolveProgressPercent(userId, course.getCourseId(), movieCount);
                    boolean started = userId != null && progressRepo
                            .findByUserIdAndCourseId(userId, course.getCourseId())
                            .isPresent();
                    return CourseListResponse.from(course, progress, started);
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

        // 영화 상세 조회 — movieId 목록으로 Movie 엔티티를 일괄 조회하여 순서 보존
        List<Movie> movieEntities = movieIdList.isEmpty()
                ? Collections.emptyList()
                : movieRepository.findAllByMovieIdIn(movieIdList);

        // movieId 기준 Map으로 변환 후 원본 순서대로 MovieInfo 목록 구성
        java.util.Map<String, Movie> movieMap = new java.util.LinkedHashMap<>();
        movieEntities.forEach(m -> movieMap.put(m.getMovieId(), m));
        List<MovieInfo> movies = movieIdList.stream()
                .filter(movieMap::containsKey)
                .map(id -> MovieInfo.from(movieMap.get(id)))
                .toList();

        // 사용자 진행 레코드 조회
        Optional<UserCourseProgress> progressOpt = (userId != null)
                ? progressRepo.findByUserIdAndCourseId(userId, courseId)
                : Optional.empty();

        boolean started = progressOpt.isPresent();
        double progressPercent = progressOpt
                .map(p -> p.getProgressPercent().doubleValue())
                .orElse(0.0);
        LocalDateTime deadlineAt = progressOpt
                .map(UserCourseProgress::getDeadlineAt)
                .orElse(null);

        // ADMIN_REJECTED 영화 목록 — rejectedMovies 응답 구성 + completedMovieIds 필터링용
        List<com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.RejectedMovieInfo> rejectedMovies;
        java.util.Set<String> rejectedSet;
        java.util.Set<String> pendingSet;
        if (userId != null) {
            List<Object[]> rawRejected = courseVerificationRepository
                    .findRejectedMoviesByUserIdAndCourseId(userId, courseId);
            rejectedMovies = rawRejected.stream()
                    .map(row -> new com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.RejectedMovieInfo(
                            (String) row[0],
                            (String) row[1]))
                    .toList();
            rejectedSet = rejectedMovies.stream()
                    .map(com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.RejectedMovieInfo::movieId)
                    .collect(java.util.stream.Collectors.toSet());

            // PENDING/NEEDS_REVIEW 영화 ID 세트 — "AI 검증 중" 버튼 표시용
            pendingSet = new java.util.HashSet<>(
                    courseVerificationRepository.findPendingMovieIdsByUserIdAndCourseId(userId, courseId));
        } else {
            rejectedMovies = Collections.emptyList();
            rejectedSet = java.util.Collections.emptySet();
            pendingSet = java.util.Collections.emptySet();
        }

        // 완료된 영화 ID — ADMIN_REJECTED · PENDING · NEEDS_REVIEW 제외, AI 검증 완료(AUTO_VERIFIED/ADMIN_APPROVED)만 포함
        List<String> completedMovieIds = (userId != null)
                ? courseReviewRepository.findAllByCourseIdAndUserId(courseId, userId).stream()
                .map(com.monglepick.monglepickbackend.domain.roadmap.entity.CourseReview::getMovieId)
                .filter(id -> !rejectedSet.contains(id) && !pendingSet.contains(id))
                .toList()
                : Collections.emptyList();

        List<String> pendingMovieIds = new java.util.ArrayList<>(pendingSet);

        return CourseDetailResponse.from(course, movies, started, progressPercent, completedMovieIds, pendingMovieIds, rejectedMovies, deadlineAt);
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
        Optional<UserCourseProgress> existing = progressRepo.findByUserIdAndCourseId(userId, courseId);
        if (existing.isPresent()) {
            log.debug("코스 이미 시작됨 (멱등 응답): userId={}, courseId={}", userId, courseId);
            return new CourseStartResponse(courseId, CourseProgressStatus.IN_PROGRESS.name(),
                    existing.get().getDeadlineAt());
        }

        // 진행 레코드 신규 생성 — deadlineAt = startedAt + deadlineDays
        LocalDateTime startedAt = LocalDateTime.now();
        Integer deadlineDays = course.getDeadlineDays();
        LocalDateTime deadlineAt = (deadlineDays != null && deadlineDays > 0)
                ? startedAt.plusDays(deadlineDays)
                : null;

        UserCourseProgress progress = UserCourseProgress.builder()
                .userId(userId)
                .courseId(courseId)
                .totalMovies(course.getMovieCount() != null ? course.getMovieCount() : 0)
                .startedAt(startedAt)
                .deadlineAt(deadlineAt)
                .build();
        progressRepo.save(progress);

        log.info("코스 시작: userId={}, courseId={}, totalMovies={}, deadlineAt={}",
                userId, courseId, progress.getTotalMovies(), deadlineAt);

        return new CourseStartResponse(courseId, CourseProgressStatus.IN_PROGRESS.name(), deadlineAt);
    }

    // ────────────────────────────────────────────────────────────────
    // 시청 리뷰 조회
    // ────────────────────────────────────────────────────────────────

    /**
     * 특정 코스+영화에 대해 사용자가 작성한 시청 리뷰를 조회한다.
     *
     * @param courseId 코스 슬러그
     * @param movieId  영화 ID
     * @param userId   사용자 ID
     * @return 리뷰 응답 DTO (인증 기록 없으면 verified=false)
     */
    public CourseReviewResponse getMovieReview(String courseId, String movieId, String userId) {
        return courseReviewRepository
                .findByCourseIdAndMovieIdAndUserId(courseId, movieId, userId)
                .map(review -> {
                    // CourseVerification에서 상태와 반려 사유 조회
                    String reviewStatus = null;
                    String decisionReason = null;
                    Optional<CourseVerification> verification = courseVerificationRepository
                            .findByUserIdAndCourseIdAndMovieId(userId, courseId, movieId);
                    if (verification.isPresent()) {
                        reviewStatus = verification.get().getReviewStatus();
                        decisionReason = verification.get().getDecisionReason();
                    }
                    return CourseReviewResponse.from(review, reviewStatus, decisionReason);
                })
                .orElseGet(() -> CourseReviewResponse.notVerified(courseId, movieId));
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
    /**
     * 도장깨기 영화 시청 인증 리뷰를 저장하고 verificationId + moviePlot을 반환한다.
     *
     * <p>2026-04-24 구조 변경: 프론트엔드 직접 에이전트 호출 방식으로 전환.
     * Backend는 리뷰/인증 레코드를 PENDING으로 저장하고 verificationId와 moviePlot만 반환한다.
     * 프론트엔드가 이 값으로 에이전트를 직접 호출하고, 판정 결과를
     * applyAiVerificationResult()로 Backend에 업데이트한다.</p>
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>코스 존재 확인 + 이미 인증된 영화는 기존 상태 그대로 반환</li>
     *   <li>CourseReview 저장 + CourseVerification(PENDING) 생성 (신규/재인증)</li>
     *   <li>영화 줄거리(moviePlot) 조회</li>
     *   <li>verificationId + moviePlot을 포함한 PENDING 상태 응답 반환</li>
     * </ol>
     *
     * @param courseId   코스 슬러그
     * @param movieId    영화 ID
     * @param userId     사용자 ID
     * @param reviewText 리뷰 본문 (nullable)
     * @return PENDING 상태 + verificationId + moviePlot 포함 응답
     */
    @Transactional
    public CourseCompleteResponse completeMovie(String courseId, String movieId,
                                                String userId, String reviewText) {
        // 1. 코스 존재 확인
        RoadmapCourse course = courseRepo.findByCourseId(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_COURSE_NOT_FOUND,
                        "존재하지 않는 코스입니다: courseId=" + courseId));

        int totalMovies = course.getMovieCount() != null ? course.getMovieCount() : 0;
        int rewardPoints = 100;

        log.info("영화 시청 인증 리뷰 제출: userId={}, courseId={}, movieId={}", userId, courseId, movieId);

        // 2. 기존 인증 레코드 확인
        Optional<CourseVerification> existingVerification = courseVerificationRepository
                .findByUserIdAndCourseIdAndMovieId(userId, courseId, movieId);
        boolean isRejected = existingVerification
                .map(v -> "ADMIN_REJECTED".equals(v.getReviewStatus()) || "AUTO_REJECTED".equals(v.getReviewStatus()))
                .orElse(false);

        Optional<CourseReview> existingReview = courseReviewRepository
                .findByCourseIdAndMovieIdAndUserId(courseId, movieId, userId);

        // 3. 이미 인증된 영화(정상 상태) — 재검증 없이 기존 상태 반환
        if (existingReview.isPresent() && !isRejected) {
            log.debug("이미 인증된 영화 — 재처리 생략: courseId={}, movieId={}, userId={}",
                    courseId, movieId, userId);
            String existingStatus = existingVerification
                    .map(CourseVerification::getReviewStatus).orElse("PENDING");
            String existingRationale = existingVerification
                    .map(CourseVerification::getDecisionReason).orElse(null);
            UserCourseProgress progress = progressRepo.findByUserIdAndCourseId(userId, courseId)
                    .orElseGet(() -> progressRepo.save(UserCourseProgress.builder()
                            .userId(userId).courseId(courseId).totalMovies(totalMovies)
                            .startedAt(LocalDateTime.now()).build()));
            return CourseCompleteResponse.from(progress, existingStatus, existingRationale, null, true);
        }

        // 4. 신규 리뷰 저장 또는 재인증 처리
        CourseVerification verification;
        if (isRejected) {
            existingReview.ifPresent(review -> {
                review.updateReviewText((reviewText != null && !reviewText.isBlank()) ? reviewText : null);
                courseReviewRepository.save(review);
            });
            existingVerification.get().resetForResubmit();
            verification = courseVerificationRepository.save(existingVerification.get());
            log.info("도장깨기 재인증 처리 — courseId={}, movieId={}, userId={}", courseId, movieId, userId);
        } else {
            CourseReview newReview = CourseReview.builder()
                    .courseId(courseId).movieId(movieId).userId(userId)
                    .reviewText((reviewText != null && !reviewText.isBlank()) ? reviewText : null)
                    .build();
            courseReviewRepository.save(newReview);

            CourseVerification newVerification = CourseVerification.builder()
                    .userId(userId).courseId(courseId).movieId(movieId)
                    .verificationType("REVIEW").build();
            verification = courseVerificationRepository.save(newVerification);
            log.info("도장깨기 리뷰 저장 완료 — courseId={}, movieId={}, userId={}", courseId, movieId, userId);
        }

        // 5. 영화 줄거리 조회 — 프론트엔드가 에이전트 직접 호출 시 movie_plot으로 전달할 값
        String moviePlot = movieRepository.findById(movieId)
                .map(m -> m.getOverview() != null ? m.getOverview() : "")
                .orElse("");

        // 6. 진행 레코드 없으면 PENDING 상태로 미리 생성
        progressRepo.findByUserIdAndCourseId(userId, courseId)
                .orElseGet(() -> progressRepo.save(UserCourseProgress.builder()
                        .userId(userId).courseId(courseId).totalMovies(totalMovies)
                        .startedAt(LocalDateTime.now()).build()));

        log.info("리뷰 저장 완료 — 프론트엔드가 에이전트를 직접 호출할 예정. verificationId={}, movieId={}",
                verification.getVerificationId(), movieId);

        // 7. 최종 응답 구성
        // 2026-04-24 구조 변경: Backend 에이전트 직접 호출 → 프론트엔드 직접 호출 방식으로 전환.
        // Backend 는 리뷰/인증 레코드를 PENDING 상태로 저장하고 verificationId + moviePlot 을 반환한다.
        // 프론트엔드는 이 값으로 /api/v1/admin/ai/review-verification/verify 를 직접 호출하여
        // AI 판정 결과를 받은 뒤, 별도 패치 API 로 Backend 에 결과를 업데이트한다.
        UserCourseProgress finalProgress = progressRepo
                .findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new IllegalStateException(
                        "진행 레코드가 존재해야 합니다 — userId=" + userId + ", courseId=" + courseId));

        return CourseCompleteResponse.from(
                finalProgress,
                "PENDING",
                null,
                null,
                true,
                verification.getVerificationId(),
                moviePlot
        );
    }

    // ────────────────────────────────────────────────────────────────
    // private 헬퍼 메서드
    // ────────────────────────────────────────────────────────────────

    /**
     * 특정 사용자의 특정 코스 진행률(%)을 반환한다.
     *
     * <p>course_review 테이블의 실제 인증 완료 영화 수를 기준으로 계산하여
     * 코스 상세 페이지의 진행률과 동일한 값을 반환한다.</p>
     *
     * @param userId     사용자 ID (null 허용)
     * @param courseId   코스 슬러그
     * @param movieCount 코스 내 총 영화 수
     * @return 진행률 % (0.0 ~ 100.0)
     */
    private double resolveProgressPercent(@Nullable String userId, String courseId, int movieCount) {
        if (userId == null || movieCount == 0) {
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
     * 프론트엔드에서 에이전트 AI 판정 결과를 전달받아 CourseVerification 에 적용한다.
     *
     * <p>2026-04-24 클라이언트 직접 호출 구조:
     * Frontend → complete API → PENDING 반환(verificationId 포함) → Agent 직접 호출 → 이 API 호출</p>
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>verificationId + userId 로 소유권 검증 (타인의 인증 레코드 업데이트 방지)</li>
     *   <li>AI 판정 결과를 CourseVerification 에 반영</li>
     *   <li>AUTO_VERIFIED 시 verifyMovie() 위임 — 진행률 ++ / 완주 판정 / 리워드 지급</li>
     * </ol>
     *
     * @param verificationId course_verification PK
     * @param userId         소유권 검증용 사용자 ID
     * @param reviewStatus   AI 판정 결과 (AUTO_VERIFIED / NEEDS_REVIEW / AUTO_REJECTED)
     * @param similarityScore 줄거리 ↔ 리뷰 유사도 (0.0~1.0)
     * @param matchedKeywords 매칭된 핵심 키워드 목록
     * @param confidence     종합 신뢰도 점수 (0.0~1.0)
     * @param rationale      AI 판정 근거 요약
     * @return 업데이트된 코스 진행 현황 DTO
     */
    @Transactional
    public CourseCompleteResponse applyAiVerificationResult(
            Long verificationId,
            String userId,
            String reviewStatus,
            Float similarityScore,
            List<String> matchedKeywords,
            Float confidence,
            String rationale
    ) {
        // 1. 인증 레코드 조회 + 소유권 검증
        CourseVerification verification = courseVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "인증 레코드를 찾을 수 없습니다: verificationId=" + verificationId));

        if (!userId.equals(verification.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "해당 인증 레코드에 접근할 권한이 없습니다.");
        }

        // PENDING/AUTO_REJECTED 상태에서만 AI 결과 업데이트 가능
        String currentStatus = verification.getReviewStatus();
        if (!"PENDING".equals(currentStatus) && !"AUTO_REJECTED".equals(currentStatus)) {
            log.info("AI 결과 업데이트 생략 — 이미 판정된 상태: verificationId={}, status={}",
                    verificationId, currentStatus);
            // 이미 처리된 상태면 현재 상태를 그대로 반환
            UserCourseProgress existingProgress = progressRepo
                    .findByUserIdAndCourseId(userId, verification.getCourseId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_COURSE_NOT_FOUND));
            return CourseCompleteResponse.from(
                    existingProgress, currentStatus, verification.getDecisionReason(),
                    verification.getSimilarityScore(), true, verificationId, null);
        }

        String courseId = verification.getCourseId();
        String movieId = verification.getMovieId();

        // 2. matched_keywords JSON 직렬화
        String matchedKeywordsJson = null;
        if (matchedKeywords != null && !matchedKeywords.isEmpty()) {
            try {
                matchedKeywordsJson = OBJECT_MAPPER.writeValueAsString(matchedKeywords);
            } catch (Exception ex) {
                log.warn("matched_keywords 직렬화 실패: {}", ex.getMessage());
            }
        }

        // 3. AI 판정 결과 적용
        verification.applyAiDecision(similarityScore, matchedKeywordsJson, confidence, reviewStatus, rationale);
        courseVerificationRepository.save(verification);

        log.info("AI 검증 결과 적용 완료 — verificationId={}, status={}", verificationId, reviewStatus);

        // 4. AUTO_VERIFIED 시 진행률 반영
        if ("AUTO_VERIFIED".equals(reviewStatus)) {
            RoadmapCourse course = courseRepo.findByCourseId(courseId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_COURSE_NOT_FOUND,
                            "존재하지 않는 코스입니다: courseId=" + courseId));
            int totalMovies = course.getMovieCount() != null ? course.getMovieCount() : 0;
            verifyMovie(userId, courseId, totalMovies, 100);
        }

        // 5. 최종 응답 구성
        UserCourseProgress finalProgress = progressRepo
                .findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new IllegalStateException(
                        "진행 레코드가 존재해야 합니다 — userId=" + userId + ", courseId=" + courseId));

        return CourseCompleteResponse.from(finalProgress, reviewStatus, rationale, similarityScore, true, verificationId, null);
    }

    /**
     * 도장깨기 최종 감상평을 제출하고 코스 완주를 확정한다.
     *
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>코스 존재 확인</li>
     *   <li>진행 상태가 FINAL_REVIEW_PENDING 인지 검증</li>
     *   <li>중복 감상평 방지 (이미 제출했으면 409)</li>
     *   <li>CourseFinalMovie 저장 (isCompleted=true)</li>
     *   <li>handleCourseComplete() — COMPLETED 전환 + 리워드 지급 + 업적</li>
     * </ol>
     *
     * @param userId     사용자 ID
     * @param courseId   코스 슬러그
     * @param reviewText 최종 감상평 본문
     * @return 감상평 + 완주 상태 응답
     */
    @Transactional
    public FinalReviewResponse submitFinalReview(String userId, String courseId, String reviewText) {
        // 1. 코스 존재 확인 및 리워드 포인트 조회
        RoadmapCourse course = courseRepo.findByCourseId(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_COURSE_NOT_FOUND,
                        "존재하지 않는 코스입니다: courseId=" + courseId));
        int rewardPoints = 100; // 기본값; 코스 엔티티에 reward_points 필드 추가 시 교체

        // 2. 진행 상태 검증 — FINAL_REVIEW_PENDING 이어야만 감상평 제출 가능
        UserCourseProgress progress = progressRepo.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "코스를 시작하지 않았습니다: courseId=" + courseId));

        if (progress.getStatus() == CourseProgressStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미 완주 처리된 코스입니다.");
        }
        if (progress.getStatus() != CourseProgressStatus.FINAL_REVIEW_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "아직 모든 영화를 완료하지 않았습니다. 모든 영화 시청 인증 후 감상평을 작성할 수 있습니다.");
        }

        // 3. 중복 감상평 방지
        if (finalMovieRepository.existsByCourseIdAndUserId(courseId, userId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미 최종 감상평을 제출했습니다.");
        }

        // 4. 최종 감상평 저장
        CourseFinalMovie finalReview = CourseFinalMovie.builder()
                .courseId(courseId)
                .userId(userId)
                .finalReviewText(reviewText != null ? reviewText.strip() : "")
                .build();
        finalReview.complete();
        CourseFinalMovie saved = finalMovieRepository.save(finalReview);
        log.info("최종 감상평 저장 완료: userId={}, courseId={}", userId, courseId);

        // 5. 코스 완주 처리 + 리워드 지급 + 업적
        handleCourseComplete(userId, courseId, rewardPoints, progress);
        progressRepo.save(progress);

        return FinalReviewResponse.from(saved, progress);
    }

    /**
     * 도장깨기 최종 감상평을 조회한다.
     *
     * <p>감상평이 아직 제출되지 않았으면 미제출 상태 응답을 반환한다.
     * 프론트엔드에서 감상평 화면 재진입 시 기존 감상평 여부를 확인하는 데 사용한다.</p>
     *
     * @param userId   사용자 ID
     * @param courseId 코스 슬러그
     * @return 감상평 조회 응답 (미제출이면 isCompleted=false)
     */
    public FinalReviewResponse getFinalReview(String userId, String courseId) {
        UserCourseProgress progress = progressRepo.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "코스를 시작하지 않았습니다: courseId=" + courseId));

        return finalMovieRepository.findByCourseIdAndUserId(courseId, userId)
                .map(finalReview -> FinalReviewResponse.from(finalReview, progress))
                .orElse(FinalReviewResponse.notSubmitted(courseId, userId, progress));
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