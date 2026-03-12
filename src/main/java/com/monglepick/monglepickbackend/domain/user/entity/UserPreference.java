package com.monglepick.monglepickbackend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
public class UserPreference {

    /** 선호도 고유 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    /** 선호도 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 선호도 수정 시각 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public UserPreference(User user, String preferredGenres, String preferredMoods,
                          String preferredPlatforms, String excludedKeywords) {
        this.user = user;
        this.preferredGenres = preferredGenres;
        this.preferredMoods = preferredMoods;
        this.preferredPlatforms = preferredPlatforms;
        this.excludedKeywords = excludedKeywords;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

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
}
