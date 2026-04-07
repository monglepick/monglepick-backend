package com.monglepick.monglepickbackend.domain.user.entity;

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
 * 사용자 활동(로그인) 이력 엔티티 — user_activity 테이블 매핑.
 *
 * <p>사용자의 로그인 이력을 저장한다. 접속 IP, 접속 기기 유형(WEB/MOBILE/APP)을 기록하여
 * 보안 감사, 이상 로그인 탐지, 사용자 행동 분석에 활용된다.</p>
 *
 * <p>Excel DB 설계서 Table 46 기준으로 생성되었다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 로그인한 사용자 ID (users.user_id 참조)</li>
 *   <li>{@code loginIp} — 접속 IP 주소 (IPv4/IPv6 최대 50자)</li>
 *   <li>{@code loginDevice} — 접속 기기 유형 (WEB / MOBILE / APP)</li>
 * </ul>
 *
 * <h3>인덱스 설계</h3>
 * <ul>
 *   <li>{@code idx_user_activity_user_id} — 사용자별 로그인 이력 조회</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-05: Excel Table 46 기준으로 최초 생성</li>
 * </ul>
 */
@Entity
@Table(
        name = "user_activity",
        indexes = {
                /* 사용자별 활동 이력 조회 (최신순 정렬) */
                @Index(name = "idx_user_activity_user_id", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserActivity extends BaseAuditEntity {

    /**
     * 활동 이력 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_activity_id")
    private Long userActivityId;

    /**
     * 로그인한 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 논리적으로 참조한다.
     * 계정이 삭제된 후에도 이력 보존을 위해 JPA FK 제약 없이 저장한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 접속 IP 주소 (VARCHAR(50), nullable).
     * IPv4(최대 15자)와 IPv6(최대 45자)를 모두 수용한다.
     * 예: "192.168.1.1", "2001:0db8::1"
     */
    @Column(name = "login_ip", length = 50)
    private String loginIp;

    /**
     * 접속 기기 유형 (VARCHAR(50), nullable).
     * 허용 값: WEB, MOBILE, APP
     * 예: "WEB" (PC 브라우저), "MOBILE" (모바일 웹), "APP" (네이티브 앱)
     */
    @Column(name = "login_device", length = 50)
    private String loginDevice;

    /* created_at → BaseAuditEntity(BaseTimeEntity)에서 상속 */
}
