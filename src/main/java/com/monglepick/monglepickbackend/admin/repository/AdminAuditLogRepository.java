package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.admin.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 관리자 감사 로그 Repository.
 *
 * <p>관리자 페이지 "시스템 → 감사 로그" 탭의 조회 작업에 사용된다.
 * 도메인 레이어의 admin 패키지에 위치한 {@link AdminAuditLog} 엔티티를 대상으로 한다.</p>
 *
 * <p>감사 로그는 보존 정책상 삭제하지 않으므로, 쓰기 메서드는 별도로 정의하지 않는다.
 * 쓰기(save)는 {@link org.springframework.data.jpa.repository.JpaRepository}의 기본 메서드로
 * {@code AdminAuditService}가 호출한다.</p>
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /**
     * 모든 감사 로그를 최신순(createdAt 내림차순)으로 페이지네이션하여 조회한다.
     *
     * <p>관리자 감사 로그 목록 화면 기본 조회에 사용한다.</p>
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 감사 로그 페이지
     */
    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * actionType에 특정 문자열이 포함된(대소문자 무시) 감사 로그를 최신순으로 조회한다.
     *
     * <p>예: "USER"로 필터링하면 USER_SUSPEND, USER_UNSUSPEND 등이 모두 조회된다.</p>
     *
     * @param actionType 검색할 행위 유형 키워드 (부분 일치)
     * @param pageable   페이지 정보
     * @return 필터링된 감사 로그 페이지
     */
    Page<AdminAuditLog> findByActionTypeContainingIgnoreCaseOrderByCreatedAtDesc(
            String actionType, Pageable pageable);

    /**
     * 다중 조건 복합 필터링 — 2026-04-09 신규 (P1-⑤ 감사 로그 고급 필터링).
     *
     * <p>기존의 {@link #findByActionTypeContainingIgnoreCaseOrderByCreatedAtDesc} 는
     * actionType 한 가지 필터만 지원하여 "특정 관리자가 특정 기간에 수행한 작업"처럼
     * 실제 감사 추적에 필요한 조회가 불가능했다. 본 메서드는 actionType / targetType /
     * targetId / 시간 범위(from~to) 를 모두 optional 파라미터로 받아 복합 조건 조회를
     * 지원한다.</p>
     *
     * <p>2026-04-09 P2-⑮ 확장: {@code targetId} 파라미터 추가. 사용자 360도 뷰에서
     * "이 사용자에게 어떤 관리 조치가 가해졌는가" 를 정확히 조회하기 위함이다.
     * 기존에는 targetType="USER" 로 필터해도 모든 사용자의 로그가 섞여 나와서 특정
     * 사용자 대상 이력을 추적할 수 없었다.</p>
     *
     * <h3>파라미터 규칙</h3>
     * <ul>
     *   <li>모든 파라미터는 {@code null} 허용 — null 이면 해당 조건을 무시한다.</li>
     *   <li>{@code actionType}: 부분 일치 (대소문자 무시) — "USER" → "USER_SUSPEND", "USER_ROLE_UPDATE" 등 포함</li>
     *   <li>{@code targetType}: 정확 일치 (대소문자 구분) — "USER", "PAYMENT", "SUBSCRIPTION" 등</li>
     *   <li>{@code targetId}: 정확 일치 — VARCHAR(100) PK 문자열화 값</li>
     *   <li>{@code fromDate}: 이 시각 이상(inclusive)의 로그만</li>
     *   <li>{@code toDate}: 이 시각 미만(exclusive)의 로그만</li>
     * </ul>
     *
     * <h3>정렬</h3>
     * <p>JPQL 에 {@code ORDER BY a.createdAt DESC} 를 하드코딩하여 항상 최신순이다.
     * Pageable 에 sort 를 주더라도 이 ORDER BY 가 우선한다 (감사 로그는 항상 최신순이 필요).</p>
     *
     * <h3>성능</h3>
     * <p>{@link AdminAuditLog} 엔티티에 이미 다음 인덱스가 걸려 있으므로 각 필터 조건에서
     * 인덱스 스캔을 기대할 수 있다. targetType + targetId 함께 필터링 시 복합 인덱스
     * {@code idx_audit_target} 이 가장 효과적이다.</p>
     * <ul>
     *   <li>{@code idx_audit_action_type (action_type)}</li>
     *   <li>{@code idx_audit_target (target_type, target_id)} — targetType+targetId 결합 시 활용</li>
     *   <li>{@code idx_audit_created_at (created_at)}</li>
     * </ul>
     *
     * @param actionType 행위 유형 부분 일치 키워드 (nullable)
     * @param targetType 대상 유형 정확 일치 (nullable)
     * @param targetId   대상 엔티티 식별자 정확 일치 (nullable) — 2026-04-09 P2-⑮ 추가
     * @param fromDate   시작 시각 inclusive (nullable)
     * @param toDate     종료 시각 exclusive (nullable)
     * @param pageable   페이지 정보
     * @return 필터링된 감사 로그 페이지 (createdAt DESC)
     */
    @Query(
        "SELECT a FROM AdminAuditLog a WHERE " +
        "(:actionType IS NULL OR LOWER(a.actionType) LIKE LOWER(CONCAT('%', :actionType, '%'))) AND " +
        "(:targetType IS NULL OR a.targetType = :targetType) AND " +
        "(:targetId   IS NULL OR a.targetId = :targetId) AND " +
        "(:fromDate IS NULL OR a.createdAt >= :fromDate) AND " +
        "(:toDate   IS NULL OR a.createdAt <  :toDate) " +
        "ORDER BY a.createdAt DESC"
    )
    Page<AdminAuditLog> searchByFilters(
            @Param("actionType") String actionType,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
}
