package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.support.entity.SupportNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 관리자 전용 공지사항 리포지토리.
 *
 * <p>관리자 페이지 "고객센터 → 공지사항" 탭의 CRUD 쿼리 메서드를 제공한다.
 * 2026-04-08: 구 AppNoticeRepository의 사용자 노출 쿼리(findActiveNotices)를 흡수했다.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findAllByOrderByIsPinnedDescCreatedAtDesc} — 상단 고정 우선 + 최신순 페이징</li>
 *   <li>{@link #findByNoticeTypeOrderByIsPinnedDescCreatedAtDesc} — 유형별 필터 + 상단 고정 우선</li>
 *   <li>{@link #findActiveAppNotices} — 앱 메인 노출 중 공지 조회 (구 AppNotice 흡수)</li>
 * </ul>
 */
public interface AdminNoticeRepository extends JpaRepository<SupportNotice, Long> {

    /**
     * 상단 고정 여부(내림차순) + 생성일시(내림차순) 우선순위로 공지 목록을 페이징 조회한다.
     *
     * <p>고정 공지가 먼저 나오고, 그 다음으로 최신순 공지가 정렬된다.</p>
     *
     * @param pageable 페이지 정보
     * @return 공지사항 페이지
     */
    Page<SupportNotice> findAllByOrderByIsPinnedDescCreatedAtDesc(Pageable pageable);

    /**
     * 유형별 공지 목록을 상단 고정 우선 + 최신순으로 페이징 조회한다.
     *
     * @param noticeType 유형 코드 (NOTICE/UPDATE/MAINTENANCE/EVENT)
     * @param pageable   페이지 정보
     * @return 해당 유형의 공지사항 페이지
     */
    Page<SupportNotice> findByNoticeTypeOrderByIsPinnedDescCreatedAtDesc(
            String noticeType, Pageable pageable
    );

    /**
     * 앱 메인 노출 중인 공지 조회 (비로그인 API에서 사용, 구 AppNotice 흡수).
     *
     * <p>조건:<br>
     * - {@code display_type IN ('BANNER', 'POPUP', 'MODAL')} (LIST_ONLY 제외)<br>
     * - {@code is_active = true}<br>
     * - {@code start_at IS NULL OR start_at <= :now}<br>
     * - {@code end_at IS NULL OR end_at >= :now}<br>
     * - {@code :displayType IS NULL OR display_type = :displayType} (선택적 필터)
     * <br>
     * 정렬: priority DESC, createdAt DESC</p>
     *
     * @param now         현재 시각 (기간 비교)
     * @param displayType BANNER/POPUP/MODAL 필터 (null 이면 전체 앱 메인 노출)
     * @return 노출 중 공지 목록
     */
    @Query("""
            SELECT n FROM SupportNotice n
            WHERE n.displayType <> 'LIST_ONLY'
              AND n.isActive = true
              AND (n.startAt IS NULL OR n.startAt <= :now)
              AND (n.endAt IS NULL OR n.endAt >= :now)
              AND (:displayType IS NULL OR n.displayType = :displayType)
            ORDER BY n.priority DESC, n.createdAt DESC
            """)
    List<SupportNotice> findActiveAppNotices(@Param("now") LocalDateTime now,
                                             @Param("displayType") String displayType);

    /**
     * 특정 displayType 집합에 속하는 공지 총 건수 (NoticeDemoInitializer 멱등성 검사용).
     *
     * <p>앱 메인 노출 공지(BANNER/POPUP/MODAL)가 단 한 건이라도 존재하는지만 확인하기 위해
     * Spring Data JPA derived query 로 count 를 조회한다. 활성/기간 조건은 보지 않으므로
     * 운영자가 비활성화해 둔 공지가 있어도 데모 시드가 중복 삽입되지 않는다.</p>
     *
     * @param displayTypes BANNER/POPUP/MODAL 등 집합
     * @return 해당 displayType 을 가진 공지 총 건수
     */
    long countByDisplayTypeIn(Collection<String> displayTypes);
}
