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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약관/정책 엔티티 — terms 테이블 매핑.
 *
 * <p>서비스 이용약관, 개인정보처리방침, 마케팅 수신 동의 등
 * 사용자가 가입 시 또는 서비스 이용 중 동의해야 하는 약관 문서를 관리한다.</p>
 *
 * <p>관리자 페이지 "설정 → 약관/정책 관리" 탭에서 CRUD 작업이 이루어지며,
 * 버전 관리를 통해 약관 개정 이력을 보존한다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code title} — 약관 제목 (관리자 식별 명칭, 필수)</li>
 *   <li>{@code content} — 약관 전문 (TEXT, 필수)</li>
 *   <li>{@code type} — 약관 유형 코드 (필수)</li>
 *   <li>{@code version} — 버전 문자열 ("1.0", "2.0" 등)</li>
 *   <li>{@code isRequired} — 필수 동의 여부 (false면 선택 동의)</li>
 *   <li>{@code isActive} — 현재 활성 약관 여부</li>
 * </ul>
 *
 * <h3>type 코드</h3>
 * <ul>
 *   <li>{@code TERMS_OF_SERVICE} — 서비스 이용약관</li>
 *   <li>{@code PRIVACY_POLICY} — 개인정보처리방침</li>
 *   <li>{@code MARKETING} — 마케팅 수신 동의</li>
 *   <li>{@code AGE_VERIFICATION} — 만 14세 이상 확인</li>
 * </ul>
 *
 * <h3>인덱스 설계 근거</h3>
 * <ul>
 *   <li>{@code idx_terms_type_active} — 유형별 활성 약관 조회 (프론트엔드 회원가입 화면)</li>
 *   <li>{@code idx_terms_type_version} — 특정 유형의 버전 이력 조회</li>
 * </ul>
 *
 * <h3>도메인 메서드</h3>
 * <p>관리자 API 서비스에서 직접 setter 대신 도메인 메서드를 통해 상태를 변경한다.</p>
 */
@Entity
@Table(
        name = "terms",
        indexes = {
                /* 유형 + 활성 여부 복합 조회 — 프론트엔드 회원가입 화면에서 활성 약관 목록 조회 */
                @Index(name = "idx_terms_type_active", columnList = "type, is_active"),
                /* 유형 + 버전 복합 조회 — 특정 유형의 약관 버전 이력 추적 */
                @Index(name = "idx_terms_type_version", columnList = "type, version")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Terms extends BaseAuditEntity {

    /**
     * 약관 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_id")
    private Long termsId;

    /**
     * 약관 제목 (최대 200자, 필수).
     * 관리자 페이지에서 약관을 식별하기 위한 명칭이다.
     * 예: "서비스 이용약관 v2.0", "개인정보처리방침 2026년 개정판"
     */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /**
     * 약관 전문 (TEXT, 필수).
     * 사용자에게 노출되는 약관 본문 텍스트를 저장한다.
     * 마크다운 형식 또는 HTML 형식을 허용한다.
     */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 약관 유형 코드 (최대 50자).
     * 열거형이 아닌 VARCHAR로 저장하여 신규 유형 추가 시 DDL 변경 없이 확장 가능하다.
     * 예: "TERMS_OF_SERVICE"(이용약관), "PRIVACY_POLICY"(개인정보처리방침),
     *     "MARKETING"(마케팅 동의), "AGE_VERIFICATION"(만 14세 이상 확인)
     */
    @Column(name = "type", length = 50)
    private String type;

    /**
     * 약관 버전 문자열 (최대 20자).
     * 약관 개정 시 버전을 올려 이력을 관리한다.
     * 예: "1.0", "1.1", "2.0"
     */
    @Column(name = "version", length = 20)
    private String version;

    /**
     * 필수 동의 여부 (기본값: true).
     * true면 회원가입 시 반드시 동의해야 하는 필수 약관이다.
     * false면 선택 동의 약관으로, 동의하지 않아도 가입 가능하다.
     */
    @Column(name = "is_required")
    @Builder.Default
    private Boolean isRequired = true;

    /**
     * 약관 활성화 여부 (기본값: true).
     * false로 설정하면 프론트엔드에 노출되지 않는다.
     * 약관 개정 시 구 버전은 비활성화하고 신 버전을 활성화한다.
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ======================== 도메인 메서드 ========================

    /**
     * 약관 내용 전체 수정.
     * 관리자 API 서비스에서 setter 대신 이 메서드를 통해 변경한다.
     *
     * @param title      새 약관 제목
     * @param content    새 약관 전문
     * @param type       새 약관 유형 코드
     * @param version    새 버전 문자열
     * @param isRequired 새 필수 동의 여부
     */
    public void update(String title, String content, String type,
                       String version, Boolean isRequired) {
        this.title = title;
        this.content = content;
        this.type = type;
        this.version = version;
        this.isRequired = isRequired;
    }

    /**
     * 약관 활성화 여부 변경.
     * 구 버전 약관을 비활성화하거나, 신규 약관을 즉시 활성화할 때 사용한다.
     *
     * @param active true면 활성화, false면 비활성화
     */
    public void setActive(boolean active) {
        this.isActive = active;
    }
}
