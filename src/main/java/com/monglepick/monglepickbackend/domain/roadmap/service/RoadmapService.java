package com.monglepick.monglepickbackend.domain.roadmap.service;

// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CompleteMovieSaveResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseCompleteResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseProgressResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseDetailResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseListResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.CourseStartResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseResponse.MovieInfo;
import com.monglepick.monglepickbackend.domain.roadmap.dto.CourseReviewResponse;
import com.monglepick.monglepickbackend.domain.roadmap.dto.VerifyResultRequest;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseProgressStatus;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseReview;
import com.monglepick.monglepickbackend.domain.roadmap.entity.CourseVerification;
import com.monglepick.monglepickbackend.domain.roadmap.entity.RoadmapCourse;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserCourseProgress;
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

    /** AI 리뷰 검증 에이전트 HTTP 클라이언트 */
    private final ReviewVerificationAgentClient agentClient;

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

        // ② 이미 완주한 코스이면 추가 인증 불가
        if (progress.getStatus() == CourseProgressStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미 완주한 코스입니다: courseId=" + courseId);
        }

        // ③ 영화 인증 처리 — verifiedMovies++, progressPercent 재계산
        progress.verify();
        log.debug("영화 인증: userId={}, courseId={}, verifiedMovies={}/{}",
                userId, courseId, progress.getVerifiedMovies(), progress.getTotalMovies());

        // ④ 완주 판정 — totalMovies > 0 보장 후 비교 (0이면 완주 조건 미충족으로 처리)
        if (progress.getTotalMovies() > 0 && progress.getVerifiedMovies() >= progress.getTotalMovies()) {
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
     * 도장깨기 영화 시청 인증 리뷰를 저장하고 AI 검증 준비 정보를 반환한다.
     *
     * <p>기존 Spring Boot → FastAPI 동기 호출 방식에서 변경된 새 아키텍처:
     * <ol>
     *   <li>Spring Boot: 리뷰 저장 + CourseVerification(PENDING) 생성 → 200 즉시 반환</li>
     *   <li>프론트엔드: FastAPI에 직접 AI 검증 요청</li>
     *   <li>프론트엔드: AI 결과를 {@link #applyVerificationResult}로 Spring Boot에 전달</li>
     * </ol>
     * </p>
     *
     * @param courseId   코스 슬러그
     * @param movieId    영화 ID
     * @param userId     사용자 ID
     * @param reviewText 리뷰 본문 (nullable)
     * @return AI 검증에 필요한 verificationId, moviePlot 포함 응답
     */
    @Transactional
    public CompleteMovieSaveResponse completeMovie(String courseId, String movieId,
                                                   String userId, String reviewText) {
        // 코스 존재 확인
        RoadmapCourse course = courseRepo.findByCourseId(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_COURSE_NOT_FOUND,
                        "존재하지 않는 코스입니다: courseId=" + courseId));

        int totalMovies = course.getMovieCount() != null ? course.getMovieCount() : 0;

        log.info("영화 시청 인증 리뷰 저장: userId={}, courseId={}, movieId={}", userId, courseId, movieId);

        // 기존 인증 레코드 확인
        Optional<CourseVerification> existingVerification = courseVerificationRepository
                .findByUserIdAndCourseIdAndMovieId(userId, courseId, movieId);
        boolean isRejected = existingVerification
                .map(v -> "ADMIN_REJECTED".equals(v.getReviewStatus()) || "AUTO_REJECTED".equals(v.getReviewStatus()))
                .orElse(false);

        Optional<CourseReview> existingReview = courseReviewRepository
                .findByCourseIdAndMovieIdAndUserId(courseId, movieId, userId);

        // 이미 인증된 영화(정상 상태) — FastAPI 재호출 없이 기존 상태 반환
        if (existingReview.isPresent() && !isRejected) {
            log.debug("이미 인증된 영화 — 재처리 생략: courseId={}, movieId={}, userId={}",
                    courseId, movieId, userId);
            String existingStatus = existingVerification
                    .map(CourseVerification::getReviewStatus).orElse("PENDING");
            Long existingVerificationId = existingVerification
                    .map(CourseVerification::getVerificationId).orElse(null);
            // moviePlot=null → 프론트엔드가 FastAPI를 호출하지 않도록 신호
            return new CompleteMovieSaveResponse(existingVerificationId, courseId, movieId, null, existingStatus);
        }

        // 신규 리뷰 저장 또는 재인증 처리
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

        // 영화 줄거리 조회 (FastAPI 검증 시 필요)
        String moviePlot = movieRepository.findById(movieId)
                .map(m -> m.getOverview() != null ? m.getOverview() : "")
                .orElse("");

        // 진행 레코드 없으면 PENDING 상태로 미리 생성 (applyVerificationResult에서 갱신됨)
        progressRepo.findByUserIdAndCourseId(userId, courseId)
                .orElseGet(() -> progressRepo.save(UserCourseProgress.builder()
                        .userId(userId).courseId(courseId).totalMovies(totalMovies)
                        .startedAt(LocalDateTime.now()).build()));

        return new CompleteMovieSaveResponse(
                verification.getVerificationId(), courseId, movieId, moviePlot, "PENDING");
    }

    /**
     * 프론트엔드로부터 FastAPI AI 검증 결과를 받아 CourseVerification에 적용한다.
     *
     * <p>새 아키텍처 3단계 중 마지막 단계:
     * 프론트엔드가 FastAPI 검증 결과를 이 엔드포인트로 전달하면
     * DB 업데이트와 진행률 반영(AUTO_VERIFIED 시)을 수행한다.</p>
     *
     * @param courseId 코스 슬러그
     * @param movieId  영화 ID
     * @param userId   사용자 ID
     * @param req      FastAPI 검증 결과 DTO
     * @return 업데이트된 코스 진행 현황
     */
    @Transactional
    public CourseCompleteResponse applyVerificationResult(String courseId, String movieId,
                                                          String userId, VerifyResultRequest req) {
        CourseVerification verification = courseVerificationRepository
                .findById(req.verificationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_VERIFICATION_NOT_FOUND,
                        "인증 레코드를 찾을 수 없습니다: verificationId=" + req.verificationId()));

        // matched_keywords List → JSON 직렬화
        String matchedKeywordsJson = null;
        if (req.matchedKeywords() != null && !req.matchedKeywords().isEmpty()) {
            try {
                matchedKeywordsJson = OBJECT_MAPPER.writeValueAsString(req.matchedKeywords());
            } catch (Exception ex) {
                log.warn("matched_keywords 직렬화 실패: {}", ex.getMessage());
            }
        }

        verification.applyAiDecision(
                req.similarityScore(),
                matchedKeywordsJson,
                req.confidence(),
                req.reviewStatus(),
                req.rationale()
        );
        courseVerificationRepository.save(verification);

        log.info("AI 검증 결과 적용 — verificationId={}, status={}, userId={}",
                req.verificationId(), req.reviewStatus(), userId);

        RoadmapCourse course = courseRepo.findByCourseId(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROADMAP_COURSE_NOT_FOUND,
                        "존재하지 않는 코스입니다: courseId=" + courseId));
        int totalMovies = course.getMovieCount() != null ? course.getMovieCount() : 0;
        int rewardPoints = 100;

        // AUTO_VERIFIED일 때만 진행률 반영
        if ("AUTO_VERIFIED".equals(req.reviewStatus())) {
            verifyMovie(userId, courseId, totalMovies, rewardPoints);
        }

        UserCourseProgress progress = progressRepo
                .findByUserIdAndCourseId(userId, courseId)
                .orElseGet(() -> progressRepo.save(UserCourseProgress.builder()
                        .userId(userId).courseId(courseId).totalMovies(totalMovies)
                        .startedAt(LocalDateTime.now()).build()));

        return CourseCompleteResponse.from(progress, req.reviewStatus(), req.rationale(), req.similarityScore(), true);
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
