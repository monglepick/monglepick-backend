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

import java.time.LocalDateTime;

/**
 * 데이터 파이프라인 실행 로그 엔티티 — pipeline_run_log 테이블 매핑.
 *
 * <p>TMDB 동기화, ES 재인덱싱, Qdrant 재적재 등 관리자가 실행하는 데이터 파이프라인의
 * 실행 이력과 결과를 기록한다. 관리자 페이지의 "데이터 관리" 탭에서 조회되며,
 * 파이프라인 성공/실패 여부와 처리 건수를 추적하는 데 활용된다.</p>
 *
 * <p>Excel DB 설계서 Table 56 기준으로 생성되었다.</p>
 *
 * <h3>파이프라인 유형 (pipelineType)</h3>
 * <ul>
 *   <li>{@code TMDB_SYNC} — TMDB API 영화 데이터 동기화</li>
 *   <li>{@code ES_REINDEX} — Elasticsearch 인덱스 재생성</li>
 *   <li>{@code QDRANT_RELOAD} — Qdrant 벡터 DB 임베딩 재적재</li>
 *   <li>{@code KOBIS_SYNC} — 영화진흥위원회 데이터 동기화</li>
 *   <li>{@code KMDB_SYNC} — 한국영화데이터베이스 동기화</li>
 * </ul>
 *
 * <h3>상태 (status)</h3>
 * <ul>
 *   <li>{@code RUNNING} — 현재 실행 중</li>
 *   <li>{@code COMPLETED} — 정상 완료</li>
 *   <li>{@code FAILED} — 오류로 실패</li>
 * </ul>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code pipelineType} — 파이프라인 종류 코드 (최대 50자)</li>
 *   <li>{@code status} — 실행 상태 (RUNNING / COMPLETED / FAILED)</li>
 *   <li>{@code startedAt} — 파이프라인 시작 시각</li>
 *   <li>{@code endedAt} — 파이프라인 종료 시각 (실행 중이면 NULL)</li>
 *   <li>{@code totalCount} — 전체 처리 대상 건수</li>
 *   <li>{@code successCount} — 성공 처리 건수</li>
 *   <li>{@code failCount} — 실패 처리 건수</li>
 *   <li>{@code errorMessage} — 실패 시 오류 메시지 (TEXT)</li>
 *   <li>{@code adminId} — 실행을 트리거한 관리자 ID (nullable, 스케줄러 자동 실행 시 NULL)</li>
 * </ul>
 *
 * <h3>인덱스 설계</h3>
 * <ul>
 *   <li>{@code idx_pipeline_type} — 파이프라인 유형별 최근 실행 이력 조회</li>
 *   <li>{@code idx_pipeline_started_at} — 날짜 범위 필터 조회</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-05: Excel Table 56 기준으로 최초 생성</li>
 * </ul>
 */
@Entity
@Table(
        name = "pipeline_run_log",
        indexes = {
                /* 파이프라인 유형별 최근 실행 이력 조회 */
                @Index(name = "idx_pipeline_type", columnList = "pipeline_type"),
                /* 날짜 범위 필터 (관리자 페이지 날짜 필터) */
                @Index(name = "idx_pipeline_started_at", columnList = "started_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PipelineRunLog extends BaseAuditEntity {

    /**
     * 파이프라인 실행 로그 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pipeline_run_id")
    private Long pipelineRunId;

    /**
     * 파이프라인 유형 코드 (VARCHAR(50), NOT NULL).
     * 허용 값: TMDB_SYNC, ES_REINDEX, QDRANT_RELOAD, KOBIS_SYNC, KMDB_SYNC
     */
    @Column(name = "pipeline_type", length = 50, nullable = false)
    private String pipelineType;

    /**
     * 실행 상태 (VARCHAR(20), NOT NULL).
     * 허용 값: RUNNING(실행중), COMPLETED(완료), FAILED(실패)
     * 파이프라인 시작 시 RUNNING으로 생성되고, 종료 시 complete() 또는 fail()로 갱신된다.
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "RUNNING";

    /**
     * 파이프라인 시작 시각 (NOT NULL).
     * 파이프라인 실행 요청 시 기록되는 도메인 타임스탬프로,
     * BaseAuditEntity의 created_at과는 별도로 관리된다.
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * 파이프라인 종료 시각 (nullable).
     * 실행 중(RUNNING)이면 NULL, 완료 또는 실패 시 종료 시각이 기록된다.
     */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * 전체 처리 대상 건수 (기본값: 0).
     * 파이프라인이 처리를 시도한 총 레코드 수.
     */
    @Column(name = "total_count")
    @Builder.Default
    private Integer totalCount = 0;

    /**
     * 성공 처리 건수 (기본값: 0).
     * 정상적으로 처리 완료된 레코드 수.
     */
    @Column(name = "success_count")
    @Builder.Default
    private Integer successCount = 0;

    /**
     * 실패 처리 건수 (기본값: 0).
     * 오류로 처리에 실패한 레코드 수.
     */
    @Column(name = "fail_count")
    @Builder.Default
    private Integer failCount = 0;

    /**
     * 오류 메시지 (TEXT, nullable).
     * 파이프라인 실패(FAILED) 시 발생한 예외 메시지나 스택 트레이스 요약을 저장한다.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 실행을 트리거한 관리자 ID (VARCHAR(50), nullable).
     * users.user_id를 논리적으로 참조한다.
     * 관리자가 수동 실행한 경우 해당 관리자 ID, 스케줄러 자동 실행의 경우 NULL.
     */
    @Column(name = "admin_id", length = 50)
    private String adminId;

    /* created_at, updated_at → BaseAuditEntity(BaseTimeEntity)에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 메서드명 사용)
    // ─────────────────────────────────────────────

    /**
     * 파이프라인 정상 완료 처리.
     *
     * <p>파이프라인이 오류 없이 완료되었을 때 호출한다.
     * 상태를 COMPLETED로 변경하고, 종료 시각 및 처리 결과를 기록한다.</p>
     *
     * @param totalCount   전체 처리 대상 건수
     * @param successCount 성공 처리 건수
     * @param failCount    실패 처리 건수
     */
    public void complete(int totalCount, int successCount, int failCount) {
        this.status = "COMPLETED";
        this.endedAt = LocalDateTime.now();
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failCount = failCount;
    }

    /**
     * 파이프라인 실패 처리.
     *
     * <p>파이프라인이 예외로 중단되었을 때 호출한다.
     * 상태를 FAILED로 변경하고, 종료 시각 및 오류 메시지를 기록한다.</p>
     *
     * @param errorMessage 발생한 예외 메시지 또는 스택 트레이스 요약 (TEXT)
     */
    public void fail(String errorMessage) {
        this.status = "FAILED";
        this.endedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }
}
