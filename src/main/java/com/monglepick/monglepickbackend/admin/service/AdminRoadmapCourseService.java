package com.monglepick.monglepickbackend.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monglepick.monglepickbackend.admin.dto.AdminRoadmapCourseDto.CourseResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminRoadmapCourseDto.CreateCourseRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminRoadmapCourseDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminRoadmapCourseDto.UpdateCourseRequest;
import com.monglepick.monglepickbackend.domain.roadmap.entity.RoadmapCourse;
import com.monglepick.monglepickbackend.domain.roadmap.repository.RoadmapCourseRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 관리자 도장깨기(RoadmapCourse) 템플릿 관리 서비스.
 *
 * <p>코스 마스터 데이터의 등록·수정·활성화 토글 비즈니스 로직을 담당한다.
 * 사용자 측 {@link com.monglepick.monglepickbackend.domain.roadmap.service.RoadmapService}와는
 * 책임이 분리되어 있으며, 본 서비스는 관리자 화면 전용이다.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>코스 목록 조회 (활성/비활성 모두 포함, 페이징)</li>
 *   <li>코스 단건 조회</li>
 *   <li>코스 신규 등록 (course_id UNIQUE 사전 검증)</li>
 *   <li>코스 수정 (course_id 변경 불가)</li>
 *   <li>활성/비활성 토글 (사용자 진행 기록은 보존)</li>
 * </ol>
 *
 * <h3>movieIds JSON 처리</h3>
 * <p>RoadmapCourse 엔티티는 {@code movie_ids}를 JSON 문자열 컬럼으로 저장하지만,
 * DTO는 {@code List&lt;String&gt;}로 받는다. 이 서비스 레이어에서 Jackson을 통해
 * 양방향 직렬화/역직렬화한다. 직렬화 실패 시 {@link ErrorCode#INVALID_COURSE_MOVIE_IDS} 발생.</p>
 *
 * <h3>비활성화 정책</h3>
 * <p>물리 삭제는 지원하지 않는다. {@code user_course_progress} 등 사용자 진행 기록이
 * course_id slug로 코스를 참조하므로, 더 이상 사용하지 않는 코스는 토글로
 * 비활성화만 한다. is_active=false 코스는 사용자 측 노출에서 제외된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRoadmapCourseService {

    /** 도장깨기 코스 레포지토리 (JPA, 윤형주 admin 도메인) */
    private final RoadmapCourseRepository roadmapCourseRepository;

    /**
     * Jackson ObjectMapper 인스턴스.
     *
     * <p>movieIds JSON 직렬화/역직렬화에 사용한다. Spring Boot가 자동 등록하는 빈을 주입.</p>
     */
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 코스 목록 조회 (페이징, 활성/비활성 모두).
     *
     * @param pageable 페이지 정보 (page/size/sort)
     * @return 페이징된 코스 응답
     */
    public Page<CourseResponse> getCourses(Pageable pageable) {
        return roadmapCourseRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * 코스 단건 조회.
     *
     * @param id roadmap_course_id (BIGINT PK)
     * @return 코스 응답 DTO
     * @throws BusinessException 존재하지 않으면 ROADMAP_COURSE_NOT_FOUND
     */
    public CourseResponse getCourse(Long id) {
        return toResponse(findCourseByIdOrThrow(id));
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    /**
     * 신규 코스 등록.
     *
     * <p>course_id UNIQUE 제약을 사전 검증하여 친화적 409를 반환한다.
     * 신규 등록 시 isActive 기본값은 true.</p>
     *
     * @param request 신규 등록 요청
     * @return 생성된 코스 응답 DTO
     * @throws BusinessException 코드 중복 시 DUPLICATE_COURSE_ID
     */
    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request) {
        // 1) course_id 중복 사전 검증
        if (roadmapCourseRepository.existsByCourseId(request.courseId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_COURSE_ID);
        }

        // 2) movieIds 직렬화 (List → JSON 문자열)
        String movieIdsJson = serializeMovieIds(request.movieIds());

        // 3) 난이도 enum 변환 (null/빈 문자열 → beginner)
        RoadmapCourse.Difficulty difficulty = parseDifficulty(request.difficulty());

        // 4) 엔티티 빌더
        RoadmapCourse entity = RoadmapCourse.builder()
                .courseId(request.courseId())
                .title(request.title())
                .description(request.description())
                .theme(request.theme())
                .movieIds(movieIdsJson)
                .movieCount(request.movieIds().size())
                .difficulty(difficulty)
                .quizEnabled(Boolean.TRUE.equals(request.quizEnabled()))
                .isActive(true)
                .build();

        RoadmapCourse saved = roadmapCourseRepository.save(entity);
        log.info("[관리자] 도장깨기 코스 등록 — id={}, courseId={}, title={}, movieCount={}",
                saved.getRoadmapCourseId(), saved.getCourseId(), saved.getTitle(), saved.getMovieCount());

        return toResponse(saved);
    }

    /**
     * 기존 코스 수정 (course_id 제외).
     *
     * @param id      수정 대상 PK
     * @param request 수정 요청
     * @return 수정된 코스 응답
     */
    @Transactional
    public CourseResponse updateCourse(Long id, UpdateCourseRequest request) {
        RoadmapCourse entity = findCourseByIdOrThrow(id);

        String movieIdsJson = serializeMovieIds(request.movieIds());
        RoadmapCourse.Difficulty difficulty = parseDifficulty(request.difficulty());

        // 도메인 메서드 호출 — JPA dirty checking 자동 UPDATE
        entity.updateInfo(
                request.title(),
                request.description(),
                request.theme(),
                movieIdsJson,
                request.movieIds().size(),
                difficulty,
                Boolean.TRUE.equals(request.quizEnabled())
        );

        log.info("[관리자] 도장깨기 코스 수정 — id={}, title={}, movieCount={}",
                id, entity.getTitle(), entity.getMovieCount());

        return toResponse(entity);
    }

    /**
     * 활성/비활성 토글.
     *
     * @param id      대상 PK
     * @param request 토글 요청
     * @return 갱신된 코스 응답
     */
    @Transactional
    public CourseResponse updateActiveStatus(Long id, UpdateActiveRequest request) {
        RoadmapCourse entity = findCourseByIdOrThrow(id);
        boolean newActive = Boolean.TRUE.equals(request.isActive());
        entity.updateActiveStatus(newActive);

        log.info("[관리자] 도장깨기 코스 활성 상태 변경 — id={}, courseId={}, isActive={}",
                id, entity.getCourseId(), newActive);
        return toResponse(entity);
    }

    // ─────────────────────────────────────────────
    // 헬퍼 — JSON 직렬화/역직렬화 + 조회
    // ─────────────────────────────────────────────

    /**
     * ID로 코스를 조회하거나 404 예외 발생.
     */
    private RoadmapCourse findCourseByIdOrThrow(Long id) {
        return roadmapCourseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ROADMAP_COURSE_NOT_FOUND,
                        "도장깨기 코스 ID " + id + "를 찾을 수 없습니다"));
    }

    /**
     * movieIds 리스트를 JSON 문자열로 직렬화.
     *
     * <p>실패 시 INVALID_COURSE_MOVIE_IDS 예외 발생.</p>
     */
    private String serializeMovieIds(List<String> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            // record @NotEmpty가 검증하지만 방어 코드 추가
            throw new BusinessException(ErrorCode.INVALID_COURSE_MOVIE_IDS);
        }
        try {
            return objectMapper.writeValueAsString(movieIds);
        } catch (JsonProcessingException e) {
            log.error("movieIds JSON 직렬화 실패: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.INVALID_COURSE_MOVIE_IDS);
        }
    }

    /**
     * JSON 문자열을 movieIds 리스트로 역직렬화.
     *
     * <p>저장된 데이터가 손상된 경우에도 예외를 던지지 않고 빈 리스트를 반환한다.</p>
     */
    private List<String> parseMovieIds(String movieIdsJson) {
        if (movieIdsJson == null || movieIdsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(movieIdsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("movieIds JSON 파싱 실패 — 빈 리스트 반환: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 난이도 문자열을 enum으로 변환 (null/빈/잘못된 값은 beginner).
     */
    private RoadmapCourse.Difficulty parseDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return RoadmapCourse.Difficulty.beginner;
        }
        try {
            return RoadmapCourse.Difficulty.valueOf(difficulty.toLowerCase());
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 난이도 값 '{}' — beginner로 대체", difficulty);
            return RoadmapCourse.Difficulty.beginner;
        }
    }

    /**
     * 엔티티 → 응답 DTO 변환.
     */
    private CourseResponse toResponse(RoadmapCourse entity) {
        return new CourseResponse(
                entity.getRoadmapCourseId(),
                entity.getCourseId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getTheme(),
                parseMovieIds(entity.getMovieIds()),
                entity.getMovieCount(),
                entity.getDifficulty() != null ? entity.getDifficulty().name() : "beginner",
                Boolean.TRUE.equals(entity.getQuizEnabled()),
                Boolean.TRUE.equals(entity.getIsActive()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
