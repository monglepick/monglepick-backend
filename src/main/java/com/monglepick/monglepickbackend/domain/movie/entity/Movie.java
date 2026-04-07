package com.monglepick.monglepickbackend.domain.movie.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
@Table(name = "movies", indexes = {
        @Index(name = "idx_movies_tmdb", columnList = "tmdb_id"),
        @Index(name = "idx_movies_release_year", columnList = "release_year"),
        @Index(name = "idx_movies_rating", columnList = "rating"),
        @Index(name = "idx_movies_popularity", columnList = "popularity_score"),
        @Index(name = "idx_movies_source", columnList = "source")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리
 */
public class Movie extends BaseAuditEntity {

    /** 영화 고유 식별자 (VARCHAR(50), DDL에서 PK로 관리) */
    @Id
    @Column(name = "movie_id", length = 50)
    private String movieId;

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

    /** 개봉 연도 */
    @Column(name = "release_year")
    private Integer releaseYear;

    /** 평균 평점 (0.0 ~ 10.0) */
    @Column
    private Double rating;

    /** TMDB 포스터 이미지 경로 (예: /abcdef.jpg) */
    @Column(name = "poster_path", length = 500)
    private String posterPath;

    /** 출연진 정보 (JSON 배열) */
    @Column(name = "cast_members", columnDefinition = "JSON")
    private String castMembers;

    /** 감독 정보 */
    @Column(length = 500)
    private String director;

    /** 영화 키워드 (JSON 배열) */
    @Column(columnDefinition = "JSON")
    private String keywords;

    /** OTT 플랫폼 정보 (JSON 배열) */
    @Column(name = "ott_platforms", columnDefinition = "JSON")
    private String ottPlatforms;

    /** 무드 태그 (JSON 배열) */
    @Column(name = "mood_tags", columnDefinition = "JSON")
    private String moodTags;

    /** 데이터 출처 (예: tmdb, kaggle, kobis, kmdb) */
    @Column(length = 50)
    private String source;

    // ========== v5 설계서 기준 추가 컬럼 (17개) ==========

    /** 개봉일 (DATE) — 운영 서버 다운 원인이었던 필수 컬럼 */
    @Column(name = "release_date")
    private LocalDate releaseDate;

    /** 상영 시간 (분 단위) */
    @Column(name = "runtime")
    private Integer runtime;

    /** 투표/평점 참여 수 (TMDB 기준) */
    @Column(name = "vote_count")
    private Long voteCount;

    /** 인기도 점수 (TMDB 기준, 높을수록 인기) */
    @Column(name = "popularity_score")
    private Double popularityScore;

    /** 관람등급 (예: 15세이상관람가, R, PG-13) */
    @Column(name = "certification", length = 50)
    private String certification;

    /** 예고편 URL (YouTube 등) */
    @Column(name = "trailer_url", length = 500)
    private String trailerUrl;

    /** 영화 태그라인 (짧은 홍보 문구) */
    @Column(name = "tagline", length = 500)
    private String tagline;

    /** IMDb 영화 ID (외부 연동용, 예: tt1375666) */
    @Column(name = "imdb_id", length = 50)
    private String imdbId;

    /** 원어 (예: en, ko, ja) */
    @Column(name = "original_language", length = 10)
    private String originalLanguage;

    /** 소속 컬렉션/시리즈명 (예: 마블 시네마틱 유니버스) */
    @Column(name = "collection_name", length = 200)
    private String collectionName;

    /** KOBIS 영화 코드 (영화진흥위원회 고유 코드) */
    @Column(name = "kobis_movie_cd", length = 50)
    private String kobisMovieCd;

    /** 누적 매출액 (원 단위, KOBIS 기준) */
    @Column(name = "sales_acc")
    private Long salesAcc;

    /** 누적 관객수 (KOBIS 기준) */
    @Column(name = "audience_count")
    private Long audienceCount;

    /** 스크린 수 (KOBIS 기준) */
    @Column(name = "screen_count")
    private Integer screenCount;

    /** KOBIS 관람등급 (예: 전체관람가, 15세이상관람가) */
    @Column(name = "kobis_watch_grade", length = 100)
    private String kobisWatchGrade;

    /** KOBIS 개봉일 */
    @Column(name = "kobis_open_dt")
    private LocalDate kobisOpenDt;

    /** KMDb 영화 ID (한국영화데이터베이스 고유 코드) */
    @Column(name = "kmdb_id", length = 50)
    private String kmdbId;

    /** 배경 이미지 경로 (TMDB backdrop) */
    @Column(name = "backdrop_path", length = 500)
    private String backdropPath;

    /** 성인 영화 여부 */
    @Column(name = "adult")
    private Boolean adult;

    // ========== Excel Table 1 기준 추가 컬럼 (2개) ==========

    /**
     * 수상 이력 (KMDb 기준, JSON 배열).
     * 예: [{"award": "청룡영화상", "year": 2020, "category": "최우수작품상"}]
     */
    @Column(name = "awards", columnDefinition = "TEXT")
    private String awards;

    /**
     * 촬영 장소 정보 (filming_location).
     * 실제 영화 촬영이 이루어진 주요 장소를 기록한다. (최대 500자)
     * 예: "서울 종로구, 부산 해운대구"
     */
    @Column(name = "filming_location", length = 500)
    private String filmingLocation;

    @Builder
    public Movie(String movieId, Long tmdbId, String title, String titleEn, String overview,
                 String genres, Integer releaseYear, Double rating, String posterPath,
                 String castMembers, String director, String keywords,
                 String ottPlatforms, String moodTags, String source,
                 LocalDate releaseDate, Integer runtime, Long voteCount,
                 Double popularityScore, String certification, String trailerUrl,
                 String tagline, String imdbId, String originalLanguage,
                 String collectionName, String kobisMovieCd, Long salesAcc,
                 Long audienceCount, Integer screenCount, String kobisWatchGrade,
                 LocalDate kobisOpenDt, String kmdbId, String backdropPath, Boolean adult,
                 String awards, String filmingLocation) {
        this.movieId = movieId;
        this.tmdbId = tmdbId;
        this.title = title;
        this.titleEn = titleEn;
        this.overview = overview;
        this.genres = genres;
        this.releaseYear = releaseYear;
        this.rating = rating;
        this.posterPath = posterPath;
        this.castMembers = castMembers;
        this.director = director;
        this.keywords = keywords;
        this.ottPlatforms = ottPlatforms;
        this.moodTags = moodTags;
        this.source = source;
        this.releaseDate = releaseDate;
        this.runtime = runtime;
        this.voteCount = voteCount;
        this.popularityScore = popularityScore;
        this.certification = certification;
        this.trailerUrl = trailerUrl;
        this.tagline = tagline;
        this.imdbId = imdbId;
        this.originalLanguage = originalLanguage;
        this.collectionName = collectionName;
        this.kobisMovieCd = kobisMovieCd;
        this.salesAcc = salesAcc;
        this.audienceCount = audienceCount;
        this.screenCount = screenCount;
        this.kobisWatchGrade = kobisWatchGrade;
        this.kobisOpenDt = kobisOpenDt;
        this.kmdbId = kmdbId;
        this.backdropPath = backdropPath;
        this.adult = adult != null ? adult : false;
        this.awards = awards;
        this.filmingLocation = filmingLocation;
    }
}
