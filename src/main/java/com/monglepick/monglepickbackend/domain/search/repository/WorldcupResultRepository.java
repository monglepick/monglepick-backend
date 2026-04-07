package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 이상형 월드컵 결과 JPA 리포지토리 — worldcup_results 테이블 접근.
 *
 * <p>월드컵 완료 시 생성되는 {@link WorldcupResult} 엔티티의 저장/조회를 담당한다.</p>
 *
 * <h3>주요 사용처</h3>
 * <ul>
 *   <li>{@code WorldcupService.handleGameComplete()} — 게임 완료 시 결과 INSERT</li>
 *   <li>{@code WorldcupService.getResult()} — 결과 조회 API (GET /api/v1/worldcup/result/{sessionId})</li>
 * </ul>
 */
@Repository
public interface WorldcupResultRepository extends JpaRepository<WorldcupResult, Long> {

    /**
     * 세션 ID로 월드컵 결과를 조회한다.
     *
     * <p>결과 조회 API에서 sessionId(=gameId)로 결과를 찾을 때 사용한다.
     * 한 세션당 결과는 1건이므로 Optional로 반환한다.</p>
     *
     * @param sessionId 세션 ID (worldcup_session.session_id)
     * @return 해당 세션의 월드컵 결과 (없으면 empty)
     */
    Optional<WorldcupResult> findBySessionId(Long sessionId);
}
