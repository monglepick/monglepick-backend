package com.monglepick.monglepickbackend.domain.search.repository;

import com.monglepick.monglepickbackend.domain.search.entity.WorldcupMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 이상형 월드컵 매치 레포지토리 — worldcup_match 테이블 접근.
 *
 * <p>세션 내 개별 매치 조회, 특정 라운드 매치 목록 조회 등을 제공한다.
 * 라운드 완료 여부 판정(모든 매치의 winnerMovieId 설정 여부)에도 사용된다.</p>
 */
public interface WorldcupMatchRepository extends JpaRepository<WorldcupMatch, Long> {

    /**
     * 특정 세션의 특정 라운드에 속한 매치 목록을 전체 조회한다.
     *
     * <p>라운드 완료 여부 판정 시 사용한다.
     * 반환된 목록 중 winnerMovieId가 null인 항목이 없으면 라운드 완료로 판정한다.</p>
     *
     * @param sessionId   세션 ID
     * @param roundNumber 라운드 번호 (16강=16, 8강=8 등)
     * @return 해당 라운드의 매치 목록 (matchOrder 오름차순 정렬은 서비스 레이어에서 처리)
     */
    List<WorldcupMatch> findBySessionSessionIdAndRoundNumber(Long sessionId, int roundNumber);

    /**
     * 특정 세션의 모든 매치를 조회한다.
     *
     * <p>세션 완료 후 전체 선택 이력을 저장하거나 통계를 산출할 때 사용한다.</p>
     *
     * @param sessionId 세션 ID
     * @return 해당 세션의 전체 매치 목록
     */
    List<WorldcupMatch> findBySessionSessionId(Long sessionId);

    /**
     * 세션 ID + 라운드 번호 + 매치 순서로 특정 매치를 단건 조회한다.
     *
     * <p>pick API에서 사용자가 선택한 매치를 정확히 특정할 때 사용한다.
     * (session_id, round_number, match_order) UNIQUE 제약에 대응하는 조회 메서드이다.</p>
     *
     * @param sessionId   세션 ID
     * @param roundNumber 라운드 번호
     * @param matchOrder  라운드 내 매치 순서 (0-based)
     * @return 매치 Optional (존재하지 않으면 empty)
     */
    Optional<WorldcupMatch> findBySessionSessionIdAndRoundNumberAndMatchOrder(
            Long sessionId,
            int roundNumber,
            int matchOrder
    );
}
