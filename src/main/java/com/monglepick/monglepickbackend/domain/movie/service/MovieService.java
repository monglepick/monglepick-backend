package com.monglepick.monglepickbackend.domain.movie.service;

import com.monglepick.monglepickbackend.domain.movie.dto.MovieResponse;
import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 영화 서비스
 *
 * <p>영화 기본 정보 조회 비즈니스 로직을 처리합니다.
 * MySQL movies 테이블(157,194건)에서 영화 상세 정보를 조회합니다.</p>
 *
 * <p>※ 검색 기능(키워드/하이브리드)은 monglepick-recommend(FastAPI) 프로젝트로 이관되었습니다.
 * 기존 SearchService 및 searchMovies 메서드는 삭제되었습니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

    /**
     * 영화 ID로 영화를 조회합니다.
     *
     * @param movieId 영화 ID (내부 DB)
     * @return 영화 정보 응답 DTO
     * @throws BusinessException 영화를 찾을 수 없는 경우
     */
    public MovieResponse getMovie(String movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> {
                    log.warn("영화 조회 실패 - 존재하지 않는 ID: {}", movieId);
                    return new BusinessException(ErrorCode.MOVIE_NOT_FOUND);
                });

        return MovieResponse.from(movie);
    }

    /**
     * TMDB ID로 영화를 조회합니다.
     *
     * <p>외부 API(TMDB)의 영화 ID로 내부 DB의 영화를 찾을 때 사용됩니다.</p>
     *
     * @param tmdbId TMDB 영화 ID
     * @return 영화 정보 응답 DTO
     * @throws BusinessException 영화를 찾을 수 없는 경우
     */
    public MovieResponse getMovieByTmdbId(Long tmdbId) {
        Movie movie = movieRepository.findByTmdbId(tmdbId)
                .orElseThrow(() -> {
                    log.warn("영화 조회 실패 - 존재하지 않는 TMDB ID: {}", tmdbId);
                    return new BusinessException(ErrorCode.MOVIE_NOT_FOUND);
                });

        return MovieResponse.from(movie);
    }

    /**
     * 키워드로 영화를 검색합니다 (한국어/영어 제목 LIKE 검색).
     *
     * <p>플레이리스트 영화 추가 등 간단한 제목 검색에 사용됩니다.
     * 고급 검색(유사도/전문검색)은 FastAPI Recommend 서버를 사용하세요.</p>
     *
     * @param keyword 검색 키워드
     * @param size    반환 건수 (최대 30)
     * @return 영화 검색 결과 목록
     */
    public List<MovieResponse> searchByKeyword(String keyword, int size) {
        int limit = Math.min(size, 30);
        Pageable pageable = PageRequest.of(0, limit);
        return movieRepository.searchByTitle(keyword, pageable)
                .map(MovieResponse::from)
                .getContent();
    }

    /**
     * 인기 영화 목록을 조회합니다 (평점 내림차순).
     *
     * <p>홈 페이지 "인기 영화" 섹션에서 사용됩니다.
     * 평점이 NULL인 영화는 제외하고, 평점 높은 순으로 반환합니다.</p>
     *
     * @param pageable 페이징 정보 (기본 size=8)
     * @return 평점순 영화 페이지
     */
    public Page<MovieResponse> getPopularMovies(Pageable pageable) {
        log.debug("인기 영화 조회 - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return movieRepository.findByRatingIsNotNullOrderByRatingDesc(pageable)
                .map(MovieResponse::from);
    }

    /**
     * 최신 영화 목록을 조회합니다 (개봉일 내림차순).
     *
     * <p>홈 페이지 "최신 영화" 섹션과 클라이언트 {@code getLatestMovies()}
     * ({@code GET /api/v1/movies/latest})에서 호출됩니다.
     * 개봉일이 NULL이거나 포스터 경로가 없는 영화는 결과에서 제외합니다
     * (UI 카드 가독성 및 무결성 확보 목적).</p>
     *
     * @param pageable 페이징 정보 (기본 size=8)
     * @return 최신 개봉 영화 페이지
     */
    public Page<MovieResponse> getLatestMovies(Pageable pageable) {
        log.debug("최신 영화 조회 - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        return movieRepository
                .findByReleaseDateIsNotNullAndPosterPathIsNotNullOrderByReleaseDateDesc(pageable)
                .map(MovieResponse::from);
    }
}
