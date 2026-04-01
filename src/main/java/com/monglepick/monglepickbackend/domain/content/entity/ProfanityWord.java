package com.monglepick.monglepickbackend.domain.content.entity;

/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
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
 * 비속어 사전 엔티티 — profanity_dictionary 테이블 매핑.
 *
 * <p>AI Agent 및 커뮤니티 콘텐츠 모더레이션에 사용되는 비속어/금칙어 목록을 관리한다.
 * {@link ToxicityLog}가 실시간 감지 이력을 기록하는 반면,
 * 이 엔티티는 감지 기준이 되는 금칙어 마스터 데이터를 저장한다.</p>
 *
 * <p>관리자 페이지 "콘텐츠 관리 → 혐오표현 관리" 탭에서 CRUD 작업이 이루어진다.
 * {@code isActive} = false로 설정하면 삭제 없이 비활성화하여 이력을 보존할 수 있다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code word} — 금칙어 원문 (UNIQUE, 필수)</li>
 *   <li>{@code category} — 비속어 분류 카테고리 (기본값: "GENERAL")</li>
 *   <li>{@code severity} — 심각도 등급 (기본값: "MEDIUM")</li>
 *   <li>{@code isActive} — 활성 여부 (false면 감지 대상에서 제외)</li>
 * </ul>
 *
 * <h3>category 코드</h3>
 * <ul>
 *   <li>{@code GENERAL} — 일반 비속어 (욕설, 은어 등)</li>
 *   <li>{@code SEXUAL} — 성적 표현</li>
 *   <li>{@code DISCRIMINATION} — 혐오·차별 표현 (특정 집단, 인종, 성별 등)</li>
 *   <li>{@code VIOLENCE} — 폭력·협박 표현</li>
 * </ul>
 *
 * <h3>severity 코드</h3>
 * <ul>
 *   <li>{@code LOW} — 낮음: 경고 메시지만 표시, 게시는 허용</li>
 *   <li>{@code MEDIUM} — 중간: 게시 전 검토 대기 또는 필터링 처리</li>
 *   <li>{@code HIGH} — 높음: 즉시 차단, 반복 시 계정 정지 검토</li>
 * </ul>
 *
 * <h3>인덱스 설계 근거</h3>
 * <ul>
 *   <li>{@code uk_profanity_word} — word UNIQUE 제약 (중복 등록 방지)</li>
 *   <li>{@code idx_profanity_category} — 카테고리별 조회 (관리자 필터링)</li>
 *   <li>{@code idx_profanity_severity_active} — 심각도 + 활성 여부 복합 조회
 *       (예: HIGH 등급 활성 단어만 실시간 감지에 활용)</li>
 * </ul>
 */
@Entity
@Table(
        name = "profanity_dictionary",
        uniqueConstraints = {
                /* word 컬럼 UNIQUE — 동일 금칙어 중복 등록 방지 */
                @UniqueConstraint(name = "uk_profanity_word", columnNames = "word")
        },
        indexes = {
                /* 카테고리별 조회 — 관리자 페이지 카테고리 필터 */
                @Index(name = "idx_profanity_category", columnList = "category"),
                /* 심각도 + 활성 여부 복합 조회 — 실시간 모더레이션 필터 */
                @Index(name = "idx_profanity_severity_active", columnList = "severity, is_active")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProfanityWord extends BaseAuditEntity {

    /**
     * 비속어 사전 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profanity_id")
    private Long profanityId;

    /**
     * 금칙어 원문 (최대 100자, NOT NULL, UNIQUE).
     * 대소문자 구분 여부는 DB 콜레이션 설정을 따른다.
     * 서비스 레이어에서 trim() 처리 후 저장하여 공백 포함 중복을 방지한다.
     */
    @Column(name = "word", length = 100, nullable = false)
    private String word;

    /**
     * 비속어 분류 카테고리 (최대 50자, 기본값: "GENERAL").
     * 예: "GENERAL"(일반 비속어), "SEXUAL"(성적 표현),
     *     "DISCRIMINATION"(혐오·차별), "VIOLENCE"(폭력·협박)
     */
    @Column(name = "category", length = 50)
    @Builder.Default
    private String category = "GENERAL";

    /**
     * 심각도 등급 (최대 20자, 기본값: "MEDIUM").
     * 감지 시 취해지는 조치의 강도를 결정한다.
     * 예: "LOW"(경고), "MEDIUM"(필터링), "HIGH"(즉시 차단)
     */
    @Column(name = "severity", length = 20)
    @Builder.Default
    private String severity = "MEDIUM";

    /**
     * 활성화 여부 (기본값: true).
     * false로 설정하면 모더레이션 감지 대상에서 제외된다.
     * 레코드를 삭제하지 않고 비활성화함으로써 등록 이력을 보존한다.
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ======================== 도메인 메서드 ========================

    /**
     * 비속어 정보 수정.
     * 관리자 API 서비스에서 setter 대신 이 메서드를 통해 변경한다.
     *
     * @param category 새 카테고리 코드
     * @param severity 새 심각도 코드
     */
    public void update(String category, String severity) {
        this.category = category;
        this.severity = severity;
    }

    /**
     * 비속어 활성화 여부 변경.
     * 삭제 대신 비활성화를 사용하여 이력을 보존한다.
     *
     * @param active true면 활성(감지 대상), false면 비활성(감지 제외)
     */
    public void setActive(boolean active) {
        this.isActive = active;
    }
}
