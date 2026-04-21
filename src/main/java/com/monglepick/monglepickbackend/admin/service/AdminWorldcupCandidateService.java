package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.BulkOperationResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.CandidateResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.CreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.DeactivateBelowRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.MovieSearchResult;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.UpdateActiveRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminWorldcupCandidateDto.UpdateRequest;
import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupCandidate;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupCategory;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupCandidateRepository;
import com.monglepick.monglepickbackend.domain.search.repository.WorldcupCategoryRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 월드컵 후보 영화(WorldcupCandidate) 관리 서비스.
 *
 * <p>월드컵 후보 풀의 등록·수정·활성화 토글·일괄 비활성화·삭제 비즈니스 로직.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>후보 목록 조회 (페이징 + 카테고리 필터)</li>
 *   <li>후보 단건 조회</li>
 *   <li>신규 후보 등록 (movieId+category UNIQUE)</li>
 *   <li>후보 메타 수정</li>
 *   <li>활성화 토글</li>
 *   <li>인기도 임계값 미만 일괄 비활성화</li>
 *   <li>후보 hard delete</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminWorldcupCandidateService {

    private final WorldcupCandidateRepository repository;
    private final WorldcupCategoryRepository worldcupCategoryRepository;
    private final MovieRepository movieRepository;

    @Value("${admin.health.recommend-url:http://localhost:8001}")
    private String recommendUrl;

    /** recommend 검색 API 호출용 클라이언트. 실패 시 MySQL fallback으로 복구한다. */
    private final RestClient restClient = RestClient.create();

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    /**
     * 월드컵 후보 등록용 영화 제목 검색.
     *
     * <p>recommend 검색 API를 우선 호출하여 ES 검색을 사용하고,
     * recommend 호출 실패 또는 빈 결과 시 MySQL title LIKE 검색으로 폴백한다.</p>
     */
    public Page<MovieSearchResult> searchMovies(
            String keyword,
            Double popularityMin,
            Double popularityMax,
            int page,
            int size
    ) {
        int requestPage = Math.max(page, 0);
        int limit = Math.max(1, Math.min(size, 50));
        String normalizedKeyword = keyword != null ? keyword.trim() : "";
        Pageable pageable = PageRequest.of(
                requestPage,
                limit,
                Sort.by(
                        Sort.Order.desc("popularityScore"),
                        Sort.Order.desc("rating"),
                        Sort.Order.desc("releaseYear")
                )
        );

        boolean hasKeyword = !normalizedKeyword.isBlank();
        boolean hasPopularityFilter = popularityMin != null || popularityMax != null;
        if (!hasKeyword && !hasPopularityFilter) {
            return Page.empty(pageable);
        }

        if (hasKeyword) {
            Page<MovieSearchResult> recommendPage = searchMoviesViaRecommend(
                    normalizedKeyword,
                    popularityMin,
                    popularityMax,
                    requestPage,
                    limit
            );
            if (recommendPage != null && recommendPage.hasContent()) {
                return recommendPage;
            }
        }

        log.debug("[관리자] 월드컵 후보 영화 검색 MySQL fallback — keyword={}, size={}",
                normalizedKeyword, limit);
        return movieRepository.searchForWorldcupCandidateSelection(
                hasKeyword ? normalizedKeyword : null,
                popularityMin,
                popularityMax,
                pageable
        ).map(MovieSearchResult::from);
    }

    public Page<CandidateResponse> getCandidates(String category, Pageable pageable) {
        Page<WorldcupCandidate> pageResult = (category != null && !category.isBlank())
                ? repository.findByCategoryCategoryCodeOrderByCreatedAtDesc(category, pageable)
                : repository.findAllByOrderByCreatedAtDesc(pageable);

        Map<String, Movie> movieById = loadMoviesById(pageResult.getContent().stream()
                .map(WorldcupCandidate::getMovieId)
                .toList());
        return pageResult.map(entity -> toResponse(entity, movieById.get(entity.getMovieId())));
    }

    public CandidateResponse getCandidate(Long id) {
        WorldcupCandidate entity = findOrThrow(id);
        Movie movie = movieRepository.findById(entity.getMovieId()).orElse(null);
        return toResponse(entity, movie);
    }

    // ─────────────────────────────────────────────
    // 쓰기
    // ─────────────────────────────────────────────

    @Transactional
    public CandidateResponse createCandidate(CreateRequest request) {
        String categoryCode = (request.category() != null && !request.category().isBlank())
                ? request.category() : "DEFAULT";
        Movie movie = findMovieOrThrow(request.movieId());
        WorldcupCategory category = findCategoryByCodeOrThrow(categoryCode);

        if (repository.existsByMovieIdAndCategoryCategoryId(request.movieId(), category.getCategoryId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_WORLDCUP_CANDIDATE);
        }

        WorldcupCandidate entity = WorldcupCandidate.builder()
                .movieId(request.movieId())
                .category(category)
                .popularity(resolveMoviePopularity(movie))
                .isActive(true)
                .addedBy(resolveCurrentAdminId())
                .build();

        WorldcupCandidate saved = repository.save(entity);
        log.info("[관리자] 월드컵 후보 등록 — id={}, movieId={}, category={}",
                saved.getId(), saved.getMovieId(), saved.getCategory().getCategoryCode());

        return toResponse(saved, movie);
    }

    @Transactional
    public CandidateResponse updateCandidate(Long id, UpdateRequest request) {
        WorldcupCandidate entity = findOrThrow(id);
        Movie movie = findMovieOrThrow(entity.getMovieId());
        entity.updateInfo(resolveMoviePopularity(movie), request.isActive());

        log.info("[관리자] 월드컵 후보 수정 — id={}, movieId={}", id, entity.getMovieId());
        return toResponse(entity, movie);
    }

    @Transactional
    public CandidateResponse updateActiveStatus(Long id, UpdateActiveRequest request) {
        WorldcupCandidate entity = findOrThrow(id);
        boolean newActive = Boolean.TRUE.equals(request.isActive());
        entity.updateActiveStatus(newActive);

        log.info("[관리자] 월드컵 후보 활성 토글 — id={}, isActive={}", id, newActive);
        Movie movie = movieRepository.findById(entity.getMovieId()).orElse(null);
        return toResponse(entity, movie);
    }

    /**
     * 인기도 임계값 미만 일괄 비활성화.
     *
     * @param request 임계값 (popularity &lt; threshold 인 후보 → isActive=false)
     * @return 영향받은 행 수
     */
    @Transactional
    public BulkOperationResponse deactivateBelowPopularity(DeactivateBelowRequest request) {
        double threshold = request.threshold() != null ? request.threshold() : 0.0;
        int affected = repository.deactivateBelowPopularity(threshold);
        log.info("[관리자] 월드컵 후보 일괄 비활성화 — threshold={}, affected={}", threshold, affected);
        return new BulkOperationResponse(
                affected,
                String.format("movies.popularity_score < %.2f 인 %d개 후보를 비활성화했습니다.", threshold, affected)
        );
    }

    @Transactional
    public void deleteCandidate(Long id) {
        WorldcupCandidate entity = findOrThrow(id);
        repository.delete(entity);
        log.warn("[관리자] 월드컵 후보 삭제 — id={}, movieId={}, category={}",
                id, entity.getMovieId(), entity.getCategory().getCategoryCode());
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    /**
     * recommend 영화 검색 API 호출.
     *
     * <p>recommend 내부에서 ES 우선 + MySQL fallback을 수행하므로,
     * backend는 관리자 화면에 필요한 최소 메타데이터만 정규화한다.</p>
     */
    private Page<MovieSearchResult> searchMoviesViaRecommend(
            String keyword,
            Double popularityMin,
            Double popularityMax,
            int page,
            int limit
    ) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(recommendUrl)
                    .path("/api/v1/search/movies")
                    .queryParam("q", keyword)
                    .queryParam("search_type", "title")
                    .queryParam("page", page + 1)
                    .queryParam("size", limit)
                    .queryParam("sort_by", "relevance")
                    .queryParam("sort_order", "desc")
                    .queryParamIfPresent("popularity_min", java.util.Optional.ofNullable(popularityMin))
                    .queryParamIfPresent("popularity_max", java.util.Optional.ofNullable(popularityMax))
                    .encode()
                    .build()
                    .toUri();

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> recommendMovies = extractRecommendMovies(payload);
            long total = extractRecommendTotal(payload);

            log.debug("[관리자] 월드컵 후보 영화 검색 recommend 성공 — searchSource={}, keyword={}, size={}",
                    payload != null ? payload.get("search_source") : null, keyword, limit);
            List<MovieSearchResult> content = mergeRecommendMovieResults(recommendMovies);
            return new PageImpl<>(content, PageRequest.of(page, limit), total);
        } catch (Exception e) {
            log.warn("[관리자] 월드컵 후보 영화 검색 recommend 실패 — keyword={}, size={}, error={}",
                    keyword, limit, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRecommendMovies(Map<String, Object> payload) {
        if (payload == null) {
            return List.of();
        }
        Object movies = payload.get("movies");
        if (!(movies instanceof List<?> movieList) || movieList.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Object item : movieList) {
            if (item instanceof Map<?, ?>) {
                results.add((Map<String, Object>) item);
            }
        }
        return results;
    }

    /**
     * recommend 결과를 MySQL 영화 메타데이터로 보강하여 관리자 UI용 DTO로 만든다.
     *
     * <p>recommend MovieBrief에는 director가 없으므로 movie_id 기준으로 movies 테이블을 일괄 조회해
     * director/title/releaseYear/posterPath를 우선 채운다.</p>
     */
    private List<MovieSearchResult> mergeRecommendMovieResults(List<Map<String, Object>> recommendMovies) {
        List<String> movieIds = recommendMovies.stream()
                .map(item -> asString(item.get("movie_id")))
                .filter(movieId -> movieId != null && !movieId.isBlank())
                .toList();

        Map<String, Movie> movieById = new LinkedHashMap<>();
        if (!movieIds.isEmpty()) {
            movieRepository.findAllByMovieIdIn(movieIds)
                    .forEach(movie -> movieById.put(movie.getMovieId(), movie));
        }

        List<MovieSearchResult> results = new ArrayList<>();
        for (Map<String, Object> item : recommendMovies) {
            String movieId = asString(item.get("movie_id"));
            if (movieId == null || movieId.isBlank()) {
                continue;
            }

            Movie movie = movieById.get(movieId);
            if (movie != null) {
                results.add(MovieSearchResult.from(movie));
                continue;
            }

            results.add(new MovieSearchResult(
                    movieId,
                    asString(item.get("title")),
                    asString(item.get("title_en")),
                    asInteger(item.get("release_year")),
                    null,
                    asString(item.get("poster_url")),
                    null
            ));
        }
        return results;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private long extractRecommendTotal(Map<String, Object> payload) {
        if (payload == null) {
            return 0L;
        }
        Object pagination = payload.get("pagination");
        if (pagination instanceof Map<?, ?> paginationMap) {
            Object total = paginationMap.get("total");
            if (total instanceof Number number) {
                return number.longValue();
            }
            if (total instanceof String text && !text.isBlank()) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private Movie findMovieOrThrow(String movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
    }

    private double resolveMoviePopularity(Movie movie) {
        return movie != null && movie.getPopularityScore() != null
                ? movie.getPopularityScore()
                : 0.0;
    }

    private Map<String, Movie> loadMoviesById(List<String> movieIds) {
        Map<String, Movie> movieById = new LinkedHashMap<>();
        if (movieIds == null || movieIds.isEmpty()) {
            return movieById;
        }
        movieRepository.findAllByMovieIdIn(movieIds)
                .forEach(movie -> movieById.put(movie.getMovieId(), movie));
        return movieById;
    }

    private WorldcupCandidate findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.WORLDCUP_CANDIDATE_NOT_FOUND,
                        "월드컵 후보 ID " + id + "를 찾을 수 없습니다"));
    }

    private WorldcupCategory findCategoryByCodeOrThrow(String categoryCode) {
        return worldcupCategoryRepository.findByCategoryCode(categoryCode)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.WORLDCUP_CATEGORY_NOT_FOUND,
                        "월드컵 카테고리 코드 " + categoryCode + "를 찾을 수 없습니다"
                ));
    }

    private String resolveCurrentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    private CandidateResponse toResponse(WorldcupCandidate entity, Movie movie) {
        WorldcupCategory category = entity.getCategory();
        return new CandidateResponse(
                entity.getId(),
                entity.getMovieId(),
                movie != null ? movie.getTitle() : null,
                movie != null ? movie.getTitleEn() : null,
                category != null ? category.getCategoryId() : null,
                category != null ? category.getCategoryCode() : null,
                category != null ? category.getCategoryName() : null,
                resolveMoviePopularity(movie),
                entity.getIsActive(),
                entity.getAddedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
