package com.monglepick.monglepickbackend.domain.movie.repository;

import com.monglepick.monglepickbackend.domain.movie.entity.FavGenre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 선호 장르 JPA 리포지토리.
 */
public interface FavGenreRepository extends JpaRepository<FavGenre, Long> {

    long countByUserId(String userId);

    List<FavGenre> findByUserIdOrderByPriorityAscFavGenreIdAsc(String userId);

    void deleteByUserId(String userId);
}
