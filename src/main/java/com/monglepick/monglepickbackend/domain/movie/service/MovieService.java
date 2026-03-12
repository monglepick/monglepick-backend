package com.monglepick.monglepickbackend.domain.movie.service;

import com.monglepick.monglepickbackend.domain.movie.dto.MovieResponse;
import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public MovieResponse getMovie(Long movieId) {
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

    // ※ searchMovies 메서드는 삭제되었습니다.
    // 영화 검색 기능은 monglepick-recommend(FastAPI) 프로젝트에서 담당합니다.
    // - 키워드 검색: Elasticsearch Nori 한국어 분석기 기반
    // - 하이브리드 검색: Qdrant(벡터) + ES(BM25) + Neo4j(그래프) → RRF 합산
}
