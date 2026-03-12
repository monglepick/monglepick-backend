package com.monglepick.monglepickbackend.domain.movie.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 영화 엔티티
 *
 * <p>MySQL movies 테이블과 매핑됩니다.
 * 영화의 기본 메타데이터를 저장하며, 157,194건의 영화 데이터가 적재되어 있습니다.</p>
 *
 * <p>데이터 출처:</p>
 * <ul>
 *   <li>TMDB API: 3,617건 (포스터, 줄거리, 장르 등)</li>
 *   <li>Kaggle MovieLens: 42,591건 (평점 데이터)</li>
 *   <li>KMDb 한국영화DB: 36,233건 (한국 영화 정보)</li>
 *   <li>KOBIS 영화진흥위원회: 77,223건 (흥행 데이터)</li>
 * </ul>
 *
 * <p>상세 영화 정보는 Qdrant(벡터)/ES(전문검색)/Neo4j(그래프)에도
 * 저장되어 있으며, 이 테이블은 RDB 기반의 기본 조회에 사용됩니다.</p>
 */
@Entity
@Table(name = "movies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    /** 영화 고유 식별자 (AUTO_INCREMENT) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TMDB 영화 ID (외부 API 연동용) */
    @Column(name = "tmdb_id", unique = true)
    private Long tmdbId;

    /** 영화 한국어 제목 */
    @Column(nullable = false, length = 500)
    private String title;

    /** 영화 영어 원제 */
    @Column(name = "title_en", length = 500)
    private String titleEn;

    /** 영화 줄거리/개요 (TEXT 타입) */
    @Column(columnDefinition = "TEXT")
    private String overview;

    /** 장르 목록 (JSON 배열 형태로 저장, 예: ["액션", "SF"]) */
    @Column(columnDefinition = "JSON")
    private String genres;

    /** 개봉일 */
    @Column(name = "release_date")
    private LocalDate releaseDate;

    /** 평균 평점 (0.0 ~ 10.0) */
    @Column
    private Double rating;

    /** TMDB 포스터 이미지 경로 (예: /abcdef.jpg) */
    @Column(name = "poster_path", length = 500)
    private String posterPath;

    @Builder
    public Movie(Long tmdbId, String title, String titleEn, String overview,
                 String genres, LocalDate releaseDate, Double rating, String posterPath) {
        this.tmdbId = tmdbId;
        this.title = title;
        this.titleEn = titleEn;
        this.overview = overview;
        this.genres = genres;
        this.releaseDate = releaseDate;
        this.rating = rating;
        this.posterPath = posterPath;
    }
}
