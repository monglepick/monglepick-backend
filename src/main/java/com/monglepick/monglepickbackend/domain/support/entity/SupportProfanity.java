package com.monglepick.monglepickbackend.domain.support.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비속어 사전 엔티티 — support_profanity 테이블 매핑.
 *
 * <p>관리자 페이지에서 등록/삭제하는 비속어 단어 목록을 저장한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.3 고객센터(23 API) 중 비속어 사전 4개 API 전용 엔티티.</p>
 *
 * <h3>사용 방식</h3>
 * <p>게시글/댓글/리뷰 작성 시 서버가 이 테이블의 단어 목록을 로드하여 정규식 매칭으로 필터링한다.
 * 관리자 페이지에서 CSV 임포트/익스포트를 지원한다 (컨트롤러 레이어에서 처리).</p>
 *
 * <h3>제약 조건</h3>
 * <ul>
 *   <li>{@code word} — UNIQUE (대소문자 구분 없이 동일한 단어는 중복 등록 불가)</li>
 * </ul>
 */
@Entity
@Table(
        name = "support_profanity",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_support_profanity_word", columnNames = "word")
        },
        indexes = {
                @Index(name = "idx_support_profanity_severity", columnList = "severity")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SupportProfanity extends BaseAuditEntity {

    /** 비속어 사전 고유 ID (BIGINT AUTO_INCREMENT PK). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profanity_id")
    private Long profanityId;

    /**
     * 금지어/비속어 단어 (VARCHAR(100), 필수, UNIQUE).
     *
     * <p>원본 형태 그대로 저장한다. 서버 검열 시 정규식 이스케이프 후 매칭에 사용된다.</p>
     */
    @Column(name = "word", length = 100, nullable = false)
    private String word;

    /**
     * 유해 단계 (VARCHAR(20), 기본값: MEDIUM).
     *
     * <ul>
     *   <li>{@code LOW} — 약한 비속어 (경고만)</li>
     *   <li>{@code MEDIUM} — 일반 비속어 (블라인드 처리)</li>
     *   <li>{@code HIGH} — 심각한 혐오/차별 표현 (즉시 차단)</li>
     * </ul>
     */
    @Column(name = "severity", length = 20, nullable = false)
    @Builder.Default
    private String severity = "MEDIUM";

    /** 관리자 비고 (nullable, VARCHAR(300)). */
    @Column(name = "note", length = 300)
    private String note;
}
