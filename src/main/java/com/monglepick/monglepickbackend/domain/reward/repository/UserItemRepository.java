package com.monglepick.monglepickbackend.domain.reward.repository;

import com.monglepick.monglepickbackend.domain.reward.constants.UserItemStatus;
import com.monglepick.monglepickbackend.domain.reward.entity.UserItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 보유 아이템 리포지토리 (2026-04-14 신규).
 *
 * <p>"내 아이템" 페이지 조회, 착용 상태 조회, 만료 배치 등에 사용된다.
 * 목록 조회 시 pointItem을 JOIN FETCH로 함께 로드하여 N+1을 방지한다.</p>
 */
public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    /**
     * 유저의 전체 보유 아이템 목록 — 페이지 기반.
     *
     * <p>JOIN FETCH로 pointItem을 즉시 로딩. Pageable의 정렬은 user_items 컬럼만 적용 가능
     * (pointItem 필드 정렬은 JPQL 기본 Pageable로 불가하므로 필요 시 별도 쿼리 추가).</p>
     *
     * @param userId 사용자 ID
     * @param pageable 페이징/정렬 정보
     * @return 보유 아이템 페이지 (PointItem이 fetch된 상태)
     */
    @Query(
            value = "SELECT ui FROM UserItem ui JOIN FETCH ui.pointItem WHERE ui.userId = :userId",
            countQuery = "SELECT COUNT(ui) FROM UserItem ui WHERE ui.userId = :userId"
    )
    Page<UserItem> findByUserIdWithItem(@Param("userId") String userId, Pageable pageable);

    /**
     * 유저의 특정 카테고리 보유 아이템 목록.
     *
     * <p>카테고리 필터를 PointItem.itemCategory 기준으로 걸고, JOIN FETCH로 N+1 방지.</p>
     *
     * @param userId 사용자 ID
     * @param category 카테고리 ("coupon", "avatar", "badge", "apply", "hint")
     * @param pageable 페이징/정렬
     * @return 필터링된 페이지
     */
    @Query(
            value = "SELECT ui FROM UserItem ui JOIN FETCH ui.pointItem pi "
                    + "WHERE ui.userId = :userId AND pi.itemCategory = :category",
            countQuery = "SELECT COUNT(ui) FROM UserItem ui WHERE ui.userId = :userId "
                    + "AND ui.pointItem.itemCategory = :category"
    )
    Page<UserItem> findByUserIdAndCategoryWithItem(@Param("userId") String userId,
                                                   @Param("category") String category,
                                                   Pageable pageable);

    /**
     * 유저가 특정 카테고리에서 현재 착용 중인 아이템 조회.
     *
     * <p>카테고리당 EQUIPPED 상태는 최대 1개만 허용되므로 단건 반환.
     * 2개 이상 존재하면 데이터 정합성 오류이며 서비스 레이어에서 보정한다.</p>
     *
     * @param userId 사용자 ID
     * @param category 카테고리
     * @return 착용 중인 아이템 (없으면 empty)
     */
    @Query(
            "SELECT ui FROM UserItem ui JOIN FETCH ui.pointItem pi "
                    + "WHERE ui.userId = :userId AND pi.itemCategory = :category "
                    + "AND ui.status = 'EQUIPPED' "
                    + "ORDER BY ui.equippedAt DESC"
    )
    List<UserItem> findEquippedByUserAndCategory(@Param("userId") String userId,
                                                  @Param("category") String category);

    /**
     * 다수 사용자의 EQUIPPED 아이템을 한 번에 조회 (2026-04-27 신설).
     *
     * <p>커뮤니티 게시글 목록·리뷰 목록 등 N개 작성자의 장착 아이템을 한 번에 가져와
     * N+1 쿼리를 방지한다. category 별 페치(`avatar` / `badge`)로 호출하면 페이지당 2 쿼리로 끝.</p>
     *
     * @param userIds  사용자 ID 배열 (보통 페이지당 20명 이내)
     * @param category PointItemCategory 정규값 ("avatar" | "badge")
     * @return EQUIPPED 아이템 목록 (각 사용자당 카테고리 1개 — 정합성 침해 시 복수)
     */
    @Query(
            "SELECT ui FROM UserItem ui JOIN FETCH ui.pointItem pi "
                    + "WHERE ui.userId IN :userIds "
                    + "AND pi.itemCategory = :category "
                    + "AND ui.status = 'EQUIPPED'"
    )
    List<UserItem> findEquippedByUserIdsAndCategory(@Param("userIds") java.util.Collection<String> userIds,
                                                    @Param("category") String category);

    /**
     * 유저의 상태별 보유 개수 집계 — 요약 카드 렌더링용.
     *
     * @param userId 사용자 ID
     * @param status 집계할 상태
     * @return 개수
     */
    long countByUserIdAndStatus(String userId, UserItemStatus status);

    /**
     * 유저의 카테고리·상태별 보유 개수 집계.
     *
     * @param userId 사용자 ID
     * @param category 카테고리
     * @param status 상태
     * @return 개수
     */
    @Query("SELECT COUNT(ui) FROM UserItem ui "
            + "WHERE ui.userId = :userId "
            + "AND ui.pointItem.itemCategory = :category "
            + "AND ui.status = :status")
    long countByUserAndCategoryAndStatus(@Param("userId") String userId,
                                         @Param("category") String category,
                                         @Param("status") UserItemStatus status);

    /**
     * 유저 소유 여부 검증 — 권한 체크용.
     *
     * <p>userItemId가 해당 userId의 소유인지 확인. 타인 아이템 조작 방지.</p>
     *
     * @param userItemId 보유 아이템 PK
     * @param userId 호출자 ID
     * @return 본인 소유이면 UserItem 반환
     */
    Optional<UserItem> findByUserItemIdAndUserId(Long userItemId, String userId);

    /**
     * 특정 itemType 중 가장 오래된 ACTIVE 보유 아이템 1건 조회 (2026-04-14 B' 후속).
     *
     * <p>"퀴즈 힌트 사용" 같이 유저가 userItemId를 직접 알 수 없는 호출 경로에서 사용된다.
     * 동일 타입을 여러 개 보유한 경우 FIFO(가장 오래 보유한 것부터 소비) 정책을 적용한다.</p>
     *
     * @param userId 사용자 ID
     * @param itemType PointItemType.name() 문자열 (예: "QUIZ_HINT")
     * @return ACTIVE 상태의 가장 오래된 보유 아이템 1건 (없으면 empty)
     */
    @Query(
            "SELECT ui FROM UserItem ui JOIN FETCH ui.pointItem pi "
                    + "WHERE ui.userId = :userId "
                    + "AND pi.itemType = :itemType "
                    + "AND ui.status = 'ACTIVE' "
                    + "ORDER BY ui.acquiredAt ASC"
    )
    List<UserItem> findActiveByUserAndType(@Param("userId") String userId,
                                           @Param("itemType") com.monglepick.monglepickbackend.domain.reward.constants.PointItemType itemType);

    /**
     * 만료 배치 스캔 — expires_at이 지났는데 아직 ACTIVE/EQUIPPED인 레코드.
     *
     * <p>@Scheduled 배치가 이 결과를 순회하며 markExpired()를 호출한다.
     * 한 번에 수천 건 이상이면 페이징으로 처리해야 하므로 Pageable 지원.</p>
     *
     * @param now 기준 시각 (보통 현재)
     * @param pageable 배치 페이징
     * @return 만료 대상 페이지
     */
    @Query("SELECT ui FROM UserItem ui "
            + "WHERE ui.expiresAt IS NOT NULL AND ui.expiresAt < :now "
            + "AND ui.status IN ('ACTIVE', 'EQUIPPED')")
    Page<UserItem> findExpirableBefore(@Param("now") LocalDateTime now, Pageable pageable);
}
