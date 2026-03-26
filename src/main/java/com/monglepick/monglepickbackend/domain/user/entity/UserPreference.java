package com.monglepick.monglepickbackend.domain.user.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 선호도 엔티티
 *
 * <p>MySQL user_preferences 테이블과 매핑됩니다.
 * 사용자의 영화 선호 장르, 분위기, 시청 맥락 등을 JSON 형태로 저장합니다.</p>
 *
 * <p>AI 추천 에이전트가 이 데이터를 참조하여 개인화된 추천을 생성합니다.
 * 대화 중 추출된 선호도가 이 테이블에 축적됩니다.</p>
 */
@Entity
@Table(name = "user_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * BaseAuditEntity 상속: created_at, updated_at, created_by, updated_by 자동 관리
 * — 수동 createdAt/updatedAt 필드 및 @PrePersist/@PreUpdate 메서드 제거됨
 * — PK 필드명: id → preferenceId로 변경 (DDL 컬럼명 preference_id 매핑)
 */
public class UserPreference extends BaseAuditEntity {

    /** 선호도 고유 식별자 (PK, BIGINT AUTO_INCREMENT, 컬럼명: preference_id) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preference_id")
    private Long preferenceId;

    /** 선호도 소유 사용자 (지연 로딩) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 선호 장르 목록 (JSON 배열 형태로 저장)
     * <p>예: ["액션", "SF", "스릴러"]</p>
     */
    @Column(name = "preferred_genres", columnDefinition = "JSON")
    private String preferredGenres;

    /**
     * 선호 분위기/무드 목록 (JSON 배열 형태로 저장)
     * <p>예: ["긴장감있는", "감동적인", "유쾌한"]</p>
     */
    @Column(name = "preferred_moods", columnDefinition = "JSON")
    private String preferredMoods;

    /**
     * 선호 OTT 플랫폼 목록 (JSON 배열 형태로 저장)
     * <p>예: ["넷플릭스", "왓챠", "디즈니+"]</p>
     */
    @Column(name = "preferred_platforms", columnDefinition = "JSON")
    private String preferredPlatforms;

    /**
     * 비선호/제외 키워드 목록 (JSON 배열 형태로 저장)
     * <p>예: ["공포", "잔인한", "슬픈결말"]</p>
     */
    @Column(name = "excluded_keywords", columnDefinition = "JSON")
    private String excludedKeywords;

    /**
     * 비선호 장르 목록 (REQ_067: 온보딩 시 비선호 장르 선택, JSON 배열)
     * <p>예: ["호러", "다큐멘터리"]</p>
     * <p>추천 엔진에서 이 장르들을 필터링하여 제외한다.</p>
     */
    @Column(name = "disliked_genres", columnDefinition = "JSON")
    private String dislikedGenres;

    /**
     * 최애 영화 목록 (REQ_069: 영화 월드컵 결과 저장, JSON 배열, 최대 6개)
     * <p>예: ["movie_12345", "movie_67890", ...]</p>
     * <p>온보딩 월드컵 결과에서 선정된 영화 ID를 저장하며, CBF 기반 추천의 시드 데이터로 활용된다.</p>
     */
    @Column(name = "favorite_movies", columnDefinition = "JSON")
    private String favoriteMovies;

    /* created_at, updated_at은 BaseAuditEntity(→BaseTimeEntity)에서 자동 관리 — 수동 필드 제거됨 */

    @Builder
    public UserPreference(User user, String preferredGenres, String preferredMoods,
                          String preferredPlatforms, String excludedKeywords,
                          String dislikedGenres, String favoriteMovies) {
        this.user = user;
        this.preferredGenres = preferredGenres;
        this.preferredMoods = preferredMoods;
        this.preferredPlatforms = preferredPlatforms;
        this.excludedKeywords = excludedKeywords;
        this.dislikedGenres = dislikedGenres;
        this.favoriteMovies = favoriteMovies;
    }

    /* @PrePersist/@PreUpdate 제거됨 — BaseTimeEntity의 @CreationTimestamp/@UpdateTimestamp로 자동 관리 */

    /** 선호 장르 업데이트 */
    public void updatePreferredGenres(String preferredGenres) {
        this.preferredGenres = preferredGenres;
    }

    /** 선호 분위기 업데이트 */
    public void updatePreferredMoods(String preferredMoods) {
        this.preferredMoods = preferredMoods;
    }

    /** 선호 플랫폼 업데이트 */
    public void updatePreferredPlatforms(String preferredPlatforms) {
        this.preferredPlatforms = preferredPlatforms;
    }

    /** 제외 키워드 업데이트 */
    public void updateExcludedKeywords(String excludedKeywords) {
        this.excludedKeywords = excludedKeywords;
    }

    /** 비선호 장르 업데이트 (REQ_067) */
    public void updateDislikedGenres(String dislikedGenres) {
        this.dislikedGenres = dislikedGenres;
    }

    /** 최애 영화 업데이트 (REQ_069: 월드컵 결과 저장) */
    public void updateFavoriteMovies(String favoriteMovies) {
        this.favoriteMovies = favoriteMovies;
    }
}
