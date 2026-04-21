package com.monglepick.monglepickbackend.domain.search.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 월드컵용 영화 후보 집합 조회 리포지토리.
 *
 * <p>사용자가 고른 장르를 모두 만족하고, 최소 vote_count 조건을 충족하는 영화 후보를 계산한다.</p>
 */
@Repository
public class WorldcupMovieQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public long countEligibleMovieIdsByGenresAllMatched(List<String> genres, long minVoteCount) {
        String sql = buildGenreAllMatchSql(false, genres);
        Query query = entityManager.createNativeQuery(sql);
        bindGenreAllMatchParams(query, genres, minVoteCount);
        Object result = query.getSingleResult();
        return result instanceof Number number ? number.longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    public List<String> findRandomEligibleMovieIdsByGenresAllMatched(List<String> genres, long minVoteCount, int limit) {
        String sql = buildGenreAllMatchSql(true, genres);
        Query query = entityManager.createNativeQuery(sql);
        bindGenreAllMatchParams(query, genres, minVoteCount);
        query.setMaxResults(limit);
        return (List<String>) query.getResultList();
    }

    private String buildGenreAllMatchSql(boolean randomOrder, List<String> genres) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append(randomOrder ? "m.movie_id " : "COUNT(m.movie_id) ");
        sql.append("FROM movies m ");
        sql.append("WHERE m.poster_path IS NOT NULL ");
        sql.append("AND COALESCE(m.vote_count, 0) >= :minVoteCount ");

        Map<String, String> uniqueGenres = normalizeGenres(genres);
        for (String parameterName : uniqueGenres.keySet()) {
            sql.append("AND JSON_CONTAINS(m.genres, JSON_QUOTE(:").append(parameterName).append(")) ");
        }

        if (randomOrder) {
            sql.append("ORDER BY RAND()");
        }
        return sql.toString();
    }

    private void bindGenreAllMatchParams(Query query, List<String> genres, long minVoteCount) {
        query.setParameter("minVoteCount", minVoteCount);
        normalizeGenres(genres).forEach(query::setParameter);
    }

    private Map<String, String> normalizeGenres(List<String> genres) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (genres == null) {
            return normalized;
        }

        List<String> deduped = new ArrayList<>();
        for (String genre : genres) {
            if (genre == null) {
                continue;
            }
            String trimmed = genre.trim();
            if (trimmed.isBlank() || deduped.contains(trimmed)) {
                continue;
            }
            deduped.add(trimmed);
        }

        for (int i = 0; i < deduped.size(); i++) {
            normalized.put("genre" + i, deduped.get(i));
        }
        return normalized;
    }
}
