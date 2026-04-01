package com.monglepick.monglepickbackend.domain.admin.entity;

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
 * 관리자 감사 로그 엔티티 — admin_audit_logs 테이블 매핑.
 *
 * <p>관리자가 수행한 모든 중요 행위(사용자 정지, 포인트 수동 지급, FAQ 생성 등)를
 * 기록한다. 변경 전/후 데이터를 JSON으로 보존하여 추적성과 책임 소재를 명확히 한다.</p>
 *
 * <p>관리자 페이지의 "설정 → 감사 로그" 탭에서 조회되며,
 * 운영 사고 발생 시 원인 분석의 근거 자료로 활용된다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code adminId} — 행위를 수행한 관리자 레코드 ID (admin 테이블 FK, nullable)</li>
 *   <li>{@code actionType} — 행위 유형 코드 (필수)</li>
 *   <li>{@code targetType} — 대상 엔티티 유형</li>
 *   <li>{@code targetId} — 대상 엔티티 식별자 (문자열로 저장하여 VARCHAR/UUID 모두 수용)</li>
 *   <li>{@code description} — 사람이 읽을 수 있는 행위 설명</li>
 *   <li>{@code ipAddress} — 관리자 클라이언트 IP (IPv6 포함 최대 45자)</li>
 *   <li>{@code beforeData} — 변경 전 데이터 스냅샷 (JSON)</li>
 *   <li>{@code afterData} — 변경 후 데이터 스냅샷 (JSON)</li>
 * </ul>
 *
 * <h3>actionType 예시</h3>
 * <ul>
 *   <li>{@code USER_SUSPEND} — 사용자 계정 정지</li>
 *   <li>{@code USER_UNSUSPEND} — 사용자 계정 정지 해제</li>
 *   <li>{@code POINT_MANUAL} — 포인트 수동 지급/차감</li>
 *   <li>{@code FAQ_CREATE} — FAQ 항목 생성</li>
 *   <li>{@code FAQ_UPDATE} — FAQ 항목 수정</li>
 *   <li>{@code FAQ_DELETE} — FAQ 항목 삭제</li>
 *   <li>{@code BANNER_CREATE} — 배너 생성</li>
 *   <li>{@code BANNER_UPDATE} — 배너 수정</li>
 *   <li>{@code POST_DELETE} — 게시글 삭제 (콘텐츠 관리)</li>
 *   <li>{@code PAYMENT_REFUND} — 결제 환불 처리</li>
 *   <li>{@code SUBSCRIPTION_CANCEL} — 구독 강제 취소</li>
 * </ul>
 *
 * <h3>targetType 예시</h3>
 * <ul>
 *   <li>{@code USER} — 사용자 엔티티</li>
 *   <li>{@code POST} — 게시글 엔티티</li>
 *   <li>{@code PAYMENT} — 결제 주문 엔티티</li>
 *   <li>{@code FAQ} — FAQ 엔티티</li>
 *   <li>{@code BANNER} — 배너 엔티티</li>
 *   <li>{@code SUBSCRIPTION} — 구독 엔티티</li>
 * </ul>
 *
 * <h3>인덱스 설계 근거</h3>
 * <ul>
 *   <li>{@code idx_audit_admin_id} — 특정 관리자의 행위 이력 조회 (관리자 감사)</li>
 *   <li>{@code idx_audit_action_type} — 행위 유형별 필터링 (예: 포인트 지급 전체 조회)</li>
 *   <li>{@code idx_audit_target} — 특정 대상에 대한 행위 추적 (예: 특정 사용자에게 가해진 조치)</li>
 *   <li>{@code idx_audit_created_at} — 시간 범위 조회 (관리자 페이지 날짜 필터)</li>
 * </ul>
 */
@Entity
@Table(
        name = "admin_audit_logs",
        indexes = {
                /* 특정 관리자의 행위 이력 조회 */
                @Index(name = "idx_audit_admin_id", columnList = "admin_id"),
                /* 행위 유형별 필터링 (USER_SUSPEND, POINT_MANUAL 등) */
                @Index(name = "idx_audit_action_type", columnList = "action_type"),
                /* 대상 유형 + 대상 ID 복합 조회 (특정 리소스에 가해진 조치 추적) */
                @Index(name = "idx_audit_target", columnList = "target_type, target_id"),
                /* 시간 범위 조회 — 관리자 페이지 날짜 필터 */
                @Index(name = "idx_audit_created_at", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminAuditLog extends BaseAuditEntity {

    /**
     * 감사 로그 고유 ID (BIGINT AUTO_INCREMENT PK).
     * 삭제 불가 — 감사 로그는 보존 정책상 삭제하지 않는다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_log_id")
    private Long auditLogId;

    /**
     * 행위를 수행한 관리자 레코드 ID.
     * admin 테이블의 admin_id를 참조하는 논리적 FK이며,
     * 관리자 계정이 삭제되더라도 로그는 보존되어야 하므로
     * JPA FK 제약 없이 Long 타입으로 저장한다.
     * 시스템 자동 처리(예: 스케줄러)의 경우 NULL일 수 있다.
     */
    @Column(name = "admin_id")
    private Long adminId;

    /**
     * 행위 유형 코드 (최대 50자, 필수).
     * 열거형이 아닌 VARCHAR로 저장하여 신규 유형 추가 시 DDL 변경 없이 확장 가능하다.
     * 예: "USER_SUSPEND", "POINT_MANUAL", "FAQ_CREATE", "PAYMENT_REFUND"
     */
    @Column(name = "action_type", length = 50, nullable = false)
    private String actionType;

    /**
     * 대상 엔티티 유형 (최대 50자).
     * 행위가 적용된 도메인 객체의 종류를 나타낸다.
     * 예: "USER", "POST", "PAYMENT", "FAQ", "BANNER", "SUBSCRIPTION"
     */
    @Column(name = "target_type", length = 50)
    private String targetType;

    /**
     * 대상 엔티티 식별자 (최대 100자).
     * VARCHAR(50) user_id, BIGINT post_id, UUID payment_order_id 등
     * 다양한 PK 타입을 문자열로 통일하여 저장한다.
     */
    @Column(name = "target_id", length = 100)
    private String targetId;

    /**
     * 행위에 대한 사람이 읽을 수 있는 설명 (TEXT).
     * 예: "사용자 user_abc의 계정을 7일 정지 처리 (사유: 반복 비속어 사용)"
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 관리자 클라이언트 IP 주소 (최대 45자).
     * IPv4(최대 15자)와 IPv6(최대 45자)를 모두 수용한다.
     * 예: "192.168.1.1", "2001:0db8:85a3:0000:0000:8a2e:0370:7334"
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * 변경 전 데이터 스냅샷 (JSON).
     * UPDATE/DELETE 행위 시 변경 전 원본 데이터를 JSON 형식으로 직렬화하여 저장한다.
     * INSERT 행위의 경우 NULL이다.
     * 예: {"status": "ACTIVE", "point_balance": 5000}
     */
    @Column(name = "before_data", columnDefinition = "JSON")
    private String beforeData;

    /**
     * 변경 후 데이터 스냅샷 (JSON).
     * INSERT/UPDATE 행위 시 변경 후 최종 데이터를 JSON 형식으로 직렬화하여 저장한다.
     * DELETE 행위의 경우 NULL이다.
     * 예: {"status": "SUSPENDED", "point_balance": 5000}
     */
    @Column(name = "after_data", columnDefinition = "JSON")
    private String afterData;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */
}
