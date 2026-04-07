package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminMovieDto.CreateMovieRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminMovieDto.MovieResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminMovieDto.UpdateMovieRequest;
import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 영화(Movie) 마스터 관리 서비스.
 *
 * <p>영화 데이터의 등록·수정·삭제 비즈니스 로직을 담당한다.
 * 외부 데이터 소스(TMDB/Kaggle/KMDb/KOBIS) 동기화 파이프라인과는 별도로,
 * 관리자가 수동으로 영화를 추가/수정/삭제할 수 있는 경로를 제공한다.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>영화 목록 조회 (페이징 + 키워드 검색)</li>
 *   <li>영화 단건 조회</li>
 *   <li>영화 신규 등록 (movieId/tmdbId UNIQUE 사전 검증, source='admin' 고정)</li>
 *   <li>영화 수정 (movieId/tmdbId/source 제외 핵심 필드만)</li>
 *   <li>영화 hard delete</li>
 * </ol>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>관리자 등록 영화는 source='admin'으로 표시하여 TMDB/Kaggle 동기화에서 보호</li>
 *   <li>tmdbId는 신규 등록 시 입력 가능. 이후 수정 시에는 동기화 충돌 방지를 위해 변경 불가</li>
 *   <li>JSON 컬럼(genres/castMembers/keywords/ottPlatforms/moodTags)은 클라이언트가
 *       JSON 문자열을 직접 구성. 서비스 레이어는 그대로 저장.</li>
 *   <li>hard delete 지원 — Movie 엔티티에 is_deleted 컬럼이 없고, FK 관계가 있는
 *       reviews/recommendation_impact/playlist_items 등은 movie_id를 String으로 보관하여
 *       삭제 시 orphan ID가 남을 수 있음. 운영상 신중하게 사용해야 함.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMovieService {

    /** 영화 JPA 리포지토리 (JpaRepository&lt;Movie, String&gt;) */
    private final MovieRepository movieRepository;

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 영화 목록 조회 (페이징 + 키워드 검색).
     *
     * <p>{@code keyword}가 있으면 title/titleEn LIKE 검색,
     * 없으면 전체 영화를 페이징하여 반환한다.</p>
     *
     * @param keyword  제목 검색 키워드 (null/공백이면 전체 조회)
     * @param pageable 페이지 정보 (page/size/sort)
     * @return 페이징된 영화 목록
     */
    public Page<MovieResponse> getMovies(String keyword, Pageable pageable) {
        Page<Movie> page = (keyword != null && !keyword.isBlank())
                ? movieRepository.searchByTitle(keyword.trim(), pageable)
                : movieRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    /**
     * 영화 단건 조회.
     *
     * @param movieId 영화 ID (VARCHAR(50) PK)
     * @return 영화 응답 DTO
     * @throws BusinessException 존재하지 않으면 MOVIE_NOT_FOUND
     */
    public MovieResponse getMovie(String movieId) {
        return toResponse(findMovieByIdOrThrow(movieId));
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    /**
     * 신규 영화 등록.
     *
     * <p>movieId UNIQUE — 중복 시 409.
     * tmdbId가 있으면 별도 UNIQUE 검증.
     * source는 'admin'으로 고정.</p>
     *
     * @param request 신규 등록 요청
     * @return 생성된 영화 응답
     * @throws BusinessException movieId 중복 시 DUPLICATE_MOVIE_ID
     * @throws BusinessException tmdbId 중복 시 DUPLICATE_TMDB_ID
     */
    @Transactional
    public MovieResponse createMovie(CreateMovieRequest request) {
        // 1) movieId 중복 사전 검증
        if (movieRepository.existsById(request.movieId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_MOVIE_ID);
        }

        // 2) tmdbId 중복 사전 검증 (있을 때만)
        if (request.tmdbId() != null
                && movieRepository.findByTmdbId(request.tmdbId()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_TMDB_ID);
        }

        // 3) Movie 엔티티 생성 — source='admin' 고정
        Movie entity = Movie.builder()
                .movieId(request.movieId())
                .tmdbId(request.tmdbId())
                .title(request.title())
                .titleEn(request.titleEn())
                .overview(request.overview())
                .genres(request.genres())
                .releaseYear(request.releaseYear())
                .releaseDate(request.releaseDate())
                .runtime(request.runtime())
                .rating(request.rating())
                .posterPath(request.posterPath())
                .castMembers(request.castMembers())
                .director(request.director())
                .keywords(request.keywords())
                .ottPlatforms(request.ottPlatforms())
                .moodTags(request.moodTags())
                .source("admin")
                .certification(request.certification())
                .trailerUrl(request.trailerUrl())
                .tagline(request.tagline())
                .originalLanguage(request.originalLanguage())
                .backdropPath(request.backdropPath())
                .adult(request.adult())
                .awards(request.awards())
                .filmingLocation(request.filmingLocation())
                .build();

        Movie saved = movieRepository.save(entity);
        log.info("[관리자] 영화 등록 — movieId={}, title={}, source=admin",
                saved.getMovieId(), saved.getTitle());

        return toResponse(saved);
    }

    /**
     * 기존 영화 수정 (movieId/tmdbId/source 등 식별자 제외).
     *
     * @param movieId 수정 대상 영화 ID
     * @param request 수정 요청
     * @return 수정된 영화 응답
     */
    @Transactional
    public MovieResponse updateMovie(String movieId, UpdateMovieRequest request) {
        Movie entity = findMovieByIdOrThrow(movieId);

        entity.updateInfo(
                request.title(),
                request.titleEn(),
                request.overview(),
                request.genres(),
                request.releaseYear(),
                request.releaseDate(),
                request.runtime(),
                request.rating(),
                request.posterPath(),
                request.castMembers(),
                request.director(),
                request.keywords(),
                request.ottPlatforms(),
                request.moodTags(),
                request.certification(),
                request.trailerUrl(),
                request.tagline(),
                request.originalLanguage(),
                request.backdropPath(),
                request.adult(),
                request.awards(),
                request.filmingLocation()
        );

        log.info("[관리자] 영화 수정 — movieId={}, title={}", movieId, entity.getTitle());
        return toResponse(entity);
    }

    /**
     * 영화 hard delete.
     *
     * <p>주의: Movie 엔티티에 is_deleted 컬럼이 없어 hard delete만 지원한다.
     * reviews/playlist_items 등이 movie_id를 String FK로 보관하므로
     * 삭제 시 해당 테이블에는 orphan ID가 남을 수 있다. 운영상 신중하게 사용.</p>
     *
     * @param movieId 삭제 대상 영화 ID
     */
    @Transactional
    public void deleteMovie(String movieId) {
        Movie entity = findMovieByIdOrThrow(movieId);
        movieRepository.delete(entity);
        log.warn("[관리자] 영화 hard delete — movieId={}, title={}, source={}",
                movieId, entity.getTitle(), entity.getSource());
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    /** ID로 영화 조회 또는 404 */
    private Movie findMovieByIdOrThrow(String movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MOVIE_NOT_FOUND,
                        "영화 ID " + movieId + "를 찾을 수 없습니다"));
    }

    /** 엔티티 → 응답 DTO */
    private MovieResponse toResponse(Movie entity) {
        return new MovieResponse(
                entity.getMovieId(),
                entity.getTmdbId(),
                entity.getTitle(),
                entity.getTitleEn(),
                entity.getOverview(),
                entity.getGenres(),
                entity.getReleaseYear(),
                entity.getReleaseDate(),
                entity.getRuntime(),
                entity.getRating(),
                entity.getPosterPath(),
                entity.getCastMembers(),
                entity.getDirector(),
                entity.getKeywords(),
                entity.getOttPlatforms(),
                entity.getMoodTags(),
                entity.getSource(),
                entity.getCertification(),
                entity.getTrailerUrl(),
                entity.getTagline(),
                entity.getOriginalLanguage(),
                entity.getBackdropPath(),
                entity.getAdult(),
                entity.getPopularityScore(),
                entity.getVoteCount(),
                entity.getAwards(),
                entity.getFilmingLocation(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
