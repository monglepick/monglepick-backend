package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.entity.PointsHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 포인트 변동 이력 리포지토리 — points_history 테이블 접근 계층.
 *
 * <p>포인트의 모든 변동(획득, 사용, 만료, 보너스) 이력을 조회한다.
 * 이력 레코드는 한 번 생성되면 수정/삭제하지 않는다 (append-only).</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #findByUserIdOrderByCreatedAtDesc(String, Pageable)} — 사용자별 이력 페이징 조회 (최신순)</li>
 *   <li>{@link #countByUserIdAndPointTypeAndDescriptionContaining(String, String, String, LocalDateTime, LocalDateTime)}
 *       — 특정 기간 내 특정 유형+키워드 이력 건수 (일일 쿼터, 이벤트 중복 방지용)</li>
 * </ul>
 */
public interface PointsHistoryRepository extends JpaRepository<PointsHistory, Long> {

    /**
     * 사용자의 포인트 변동 이력을 최신순으로 페이징 조회한다.
     *
     * <p>클라이언트의 "포인트 내역" 화면에서 사용된다.
     * created_at DESC 정렬이므로 가장 최근 변동이 먼저 표시된다.</p>
     *
     * @param userId   사용자 ID (VARCHAR(50))
     * @param pageable 페이징 정보 (page, size)
     * @return 포인트 변동 이력 페이지
     */
    Page<PointsHistory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 특정 기간 내 특정 유형과 설명 키워드를 포함하는 이력 건수를 조회한다.
     *
     * <p>일일 쿼터 계산이나 이벤트 중복 참여 방지에 사용된다.
     * 예: 오늘 "AI 추천 사용" 이력이 몇 건인지 확인하여 일일 한도를 적용할 수 있다.</p>
     *
     * <h4>사용 예시</h4>
     * <pre>{@code
     * // 오늘 AI 추천 사용 횟수 조회
     * long todayAiUsage = repository.countByUserIdAndPointTypeAndDescriptionContaining(
     *     "user123", "spend", "AI 추천",
     *     today.atStartOfDay(), today.plusDays(1).atStartOfDay()
     * );
     * }</pre>
     *
     * @param userId    사용자 ID
     * @param pointType 포인트 변동 유형 (earn, spend, expire, bonus)
     * @param keyword   설명(description)에 포함되어야 하는 키워드
     * @param start     조회 시작 시각 (inclusive)
     * @param end       조회 종료 시각 (exclusive — start 이상, end 미만)
     * @return 조건에 맞는 이력 건수
     */
    @Query("SELECT COUNT(h) FROM PointsHistory h " +
            "WHERE h.userId = :userId " +
            "AND h.pointType = :pointType " +
            "AND h.description LIKE %:keyword% " +
            "AND h.createdAt >= :start AND h.createdAt < :end")
    long countByUserIdAndPointTypeAndDescriptionContaining(
            @Param("userId") String userId,
            @Param("pointType") String pointType,
            @Param("keyword") String keyword,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
