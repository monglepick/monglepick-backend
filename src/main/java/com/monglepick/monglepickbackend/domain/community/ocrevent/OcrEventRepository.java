package com.monglepick.monglepickbackend.domain.community.ocrevent;

import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent;
import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent.OcrEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 유저 전용 OCR 인증 이벤트 공개 리포지토리.
 *
 * <p>관리자용 {@code AdminOcrEventRepository}와 분리된 조회 전용 리포지토리이다.
 * 유저 커뮤니티 "실관람인증" 탭에서 아래 조건으로 이벤트를 노출한다:</p>
 *
 * <ul>
 *   <li>상태가 {@code ACTIVE}(진행 중) 또는 {@code READY}(시작 대기) 인 이벤트</li>
 *   <li>종료일이 현재 시각 이후인 이벤트 (이미 끝난 이벤트 숨김)</li>
 *   <li>정렬: 상태 우선(ACTIVE → READY) + 종료일 임박 순</li>
 * </ul>
 *
 * <p>CLOSED 이벤트는 유저 페이지에서 자동 숨김. 관리자가 오래된 이벤트를
 * CLOSED 로 내리지 않아도 {@code endDate} 기준으로 자연스럽게 사라진다.</p>
 */
public interface OcrEventRepository extends JpaRepository<OcrEvent, Long> {

    /**
     * 유저 커뮤니티 노출용 "현재 진행 중이거나 곧 시작하는" 이벤트 목록.
     *
     * <p>JPQL 쿼리 조건:</p>
     * <ul>
     *   <li>{@code status} IN (ACTIVE, READY) — CLOSED 제외</li>
     *   <li>{@code endDate} &gt; {@code now} — 이미 종료된 이벤트 숨김</li>
     * </ul>
     *
     * <p>정렬: ACTIVE 상태가 먼저 보이도록 상태 문자열 역순(ACTIVE > READY)
     * + 종료일 임박 순(마감 임박 이벤트 상단 노출).</p>
     *
     * @param statuses 허용 상태 (ACTIVE, READY)
     * @param now      현재 시각 (endDate 비교용)
     * @return 유저에게 노출 가능한 이벤트 목록
     */
    @Query("""
            SELECT e
              FROM OcrEvent e
             WHERE e.status IN :statuses
               AND e.endDate > :now
             ORDER BY
                CASE e.status WHEN com.monglepick.monglepickbackend.domain.community.entity.OcrEvent.OcrEventStatus.ACTIVE THEN 0 ELSE 1 END,
                e.endDate ASC
            """)
    List<OcrEvent> findPublicEvents(@Param("statuses") List<OcrEventStatus> statuses,
                                    @Param("now") LocalDateTime now);
}
