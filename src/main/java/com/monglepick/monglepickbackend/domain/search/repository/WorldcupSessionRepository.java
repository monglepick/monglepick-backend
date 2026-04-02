package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupSession;
import com.monglepick.monglepickbackend.domain.search.entity.WorldcupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 이상형 월드컵 세션 레포지토리 — worldcup_session 테이블 접근.
 *
 * <p>사용자의 월드컵 진행 세션을 조회하고 관리한다.
 * 진행 중인 세션 조회, 사용자별 세션 목록 조회 등을 제공한다.</p>
 */
public interface WorldcupSessionRepository extends JpaRepository<WorldcupSession, Long> {

    /**
     * 특정 사용자의 특정 상태 세션 목록을 조회한다.
     *
     * <p>IN_PROGRESS 상태로 조회하여 사용자가 재개할 수 있는 세션을 찾거나,
     * COMPLETED 상태로 조회하여 완료된 세션 이력을 확인할 때 사용한다.</p>
     *
     * @param userId 사용자 ID
     * @param status 세션 상태 ({@link WorldcupStatus})
     * @return 해당 상태의 세션 목록 (최신순 정렬은 서비스 레이어에서 처리)
     */
    List<WorldcupSession> findByUserIdAndStatus(String userId, WorldcupStatus status);

    /**
     * 특정 사용자의 특정 세션 ID를 특정 상태로 단건 조회한다.
     *
     * <p>매치 선택(pick) API에서 세션 소유자 확인과 진행 상태 검증을 동시에 수행한다.
     * userId + sessionId 조합으로 타인의 세션에 접근하는 것을 방지한다.</p>
     *
     * @param userId    세션 소유자 사용자 ID
     * @param sessionId 조회할 세션 ID
     * @param status    기대하는 세션 상태 (보통 IN_PROGRESS)
     * @return 세션 Optional (소유자 불일치 또는 상태 불일치 시 empty)
     */
    Optional<WorldcupSession> findByUserIdAndSessionIdAndStatus(
            String userId,
            Long sessionId,
            WorldcupStatus status
    );
}
