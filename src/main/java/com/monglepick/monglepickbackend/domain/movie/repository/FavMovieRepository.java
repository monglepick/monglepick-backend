package com.monglepick.monglepickbackend.domain.movie.repository;

import com.monglepick.monglepickbackend.domain.movie.entity.FavMovie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 인생 영화 JPA 리포지토리.
 */
public interface FavMovieRepository extends JpaRepository<FavMovie, Long> {

    long countByUserId(String userId);

    List<FavMovie> findByUserIdOrderByPriorityAscFavMovieIdAsc(String userId);

    void deleteByUserId(String userId);
}
