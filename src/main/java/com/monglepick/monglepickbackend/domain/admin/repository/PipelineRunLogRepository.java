package com.monglepick.monglepickbackend.domain.admin.repository;

import com.monglepick.monglepickbackend.domain.admin.entity.PipelineRunLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 파이프라인 실행 로그 레포지토리 — pipeline_run_log 테이블 CRUD.
 *
 * <p>데이터 파이프라인(TMDB 동기화, ES 재인덱싱, Qdrant 재적재 등)의
 * 실행 이력을 조회하는 데 사용한다.</p>
 */
@Repository
public interface PipelineRunLogRepository extends JpaRepository<PipelineRunLog, Long> {

    /**
     * 파이프라인 유형별 실행 이력을 최신순으로 조회한다.
     *
     * @param pipelineType 파이프라인 유형 (예: TMDB_SYNC, ES_REINDEX)
     * @return 해당 유형의 실행 로그 목록 (최신 순)
     */
    List<PipelineRunLog> findByPipelineTypeOrderByCreatedAtDesc(String pipelineType);

    /**
     * 특정 관리자가 실행한 파이프라인 이력을 최신순으로 조회한다.
     *
     * @param adminId 실행자 관리자 ID
     * @return 해당 관리자의 실행 로그 목록
     */
    List<PipelineRunLog> findByAdminIdOrderByCreatedAtDesc(String adminId);

    /**
     * 상태별 파이프라인 이력을 최신순으로 조회한다.
     *
     * @param status 실행 상태 (RUNNING, COMPLETED, FAILED)
     * @return 해당 상태의 실행 로그 목록
     */
    List<PipelineRunLog> findByStatusOrderByCreatedAtDesc(String status);
}
