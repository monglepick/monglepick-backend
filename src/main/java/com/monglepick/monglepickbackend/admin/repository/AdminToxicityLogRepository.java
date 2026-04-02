package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.content.entity.ToxicityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 전용 혐오표현 로그(ToxicityLog) JPA 리포지토리.
 *
 * <p>AI가 감지한 유해 콘텐츠 로그를 관리자 화면에서 조회하기 위한 쿼리 메서드를 제공한다.
 * 일반 사용자 도메인에서는 사용하지 않는다.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findAllByOrderByCreatedAtDesc}                             — 전체 목록 최신순 조회</li>
 *   <li>{@link #findByToxicityScoreGreaterThanEqualOrderByCreatedAtDesc}   — 점수 임계값 이상 필터 조회</li>
 * </ul>
 *
 * <h3>ToxicityLog 심각도(severity) 유효값</h3>
 * <ul>
 *   <li>"LOW"      — 경미 (toxicityScore 0.0~0.3)</li>
 *   <li>"MEDIUM"   — 중간 (toxicityScore 0.3~0.6)</li>
 *   <li>"HIGH"     — 심각 (toxicityScore 0.6~0.8)</li>
 *   <li>"CRITICAL" — 즉시 조치 필요 (toxicityScore 0.8~1.0)</li>
 * </ul>
 */
public interface AdminToxicityLogRepository extends JpaRepository<ToxicityLog, Long> {

    /**
     * 전체 혐오표현 로그를 최신순으로 페이징 조회한다.
     *
     * <p>관리자 화면에서 점수 필터 없이 전체 혐오표현 감지 내역을 조회할 때 사용한다.</p>
     *
     * @param pageable 페이지 정보 (page, size)
     * @return 전체 혐오표현 로그 페이지
     */
    Page<ToxicityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 지정한 점수 이상의 혐오표현 로그를 최신순으로 페이징 조회한다.
     *
     * <p>관리자 화면에서 심각도 필터(예: 0.6 이상 = HIGH+CRITICAL)를 적용할 때 사용한다.
     * toxicityScore가 null인 레코드는 결과에서 제외된다 (JPA null 비교 기본 동작).</p>
     *
     * @param minScore 최소 독성 점수 (0.0~1.0)
     * @param pageable 페이지 정보 (page, size)
     * @return 해당 점수 이상의 혐오표현 로그 페이지
     */
    Page<ToxicityLog> findByToxicityScoreGreaterThanEqualOrderByCreatedAtDesc(
            Float minScore, Pageable pageable);
}
