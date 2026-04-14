package com.monglepick.monglepickbackend.domain.reward.dto;

import com.monglepick.monglepickbackend.domain.reward.constants.PointItemType;
import com.monglepick.monglepickbackend.domain.reward.constants.UserItemStatus;
import com.monglepick.monglepickbackend.domain.reward.entity.UserItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 보유 아이템(UserItem) DTO 모음 (2026-04-14 신규, C 방향).
 *
 * <p>"내 아이템" 페이지(클라이언트) 및 프로필 착용 아이템 표시에 사용되는 응답 DTO를 정의한다.
 * record 기반 불변 객체로 관리한다.</p>
 */
public final class UserItemDto {

    private UserItemDto() {
    }

    /**
     * 보유 아이템 단건 응답.
     *
     * <p>프론트엔드가 카드 한 장을 렌더링할 때 필요한 필드를 모아 반환한다.
     * PointItem의 이미지·카테고리·타입과 UserItem의 상태·만료일을 합쳐 보여준다.</p>
     *
     * @param userItemId       user_items PK
     * @param pointItemId      구매 원본 아이템 PK
     * @param itemName         아이템명
     * @param itemDescription  아이템 설명 (없으면 null)
     * @param category         카테고리 ("coupon"/"avatar"/"badge"/"apply"/"hint")
     * @param itemType         지급 타입 문자열 (PointItemType.name())
     * @param imageUrl         이미지 URL (null 가능)
     * @param status           현재 상태 (ACTIVE/EQUIPPED/USED/EXPIRED)
     * @param acquiredAt       획득 시각
     * @param expiresAt        만료 시각 (null이면 무기한)
     * @param equippedAt       착용 시작 시각 (null이면 미착용)
     * @param remainingQuantity 잔여 수량 (힌트 등)
     * @param equipped         착용 여부 (status=EQUIPPED 판단 결과)
     * @param expired          만료 여부 (status=EXPIRED)
     */
    public record UserItemResponse(
            Long userItemId,
            Long pointItemId,
            String itemName,
            String itemDescription,
            String category,
            String itemType,
            String imageUrl,
            UserItemStatus status,
            LocalDateTime acquiredAt,
            LocalDateTime expiresAt,
            LocalDateTime equippedAt,
            Integer remainingQuantity,
            boolean equipped,
            boolean expired
    ) {
        /**
         * UserItem 엔티티 + 연관된 PointItem을 기반으로 DTO 생성.
         *
         * <p>pointItem이 LAZY로 초기화되지 않은 경우를 방어하기 위해 서비스 레이어에서
         * JOIN FETCH로 미리 로드된 엔티티만 전달해야 한다.</p>
         *
         * @param ui 보유 아이템 엔티티 (pointItem 초기화됨)
         * @return 응답 DTO
         */
        public static UserItemResponse from(UserItem ui) {
            PointItemType type = ui.getPointItem().getItemType();
            return new UserItemResponse(
                    ui.getUserItemId(),
                    ui.getPointItem().getPointItemId(),
                    ui.getPointItem().getItemName(),
                    ui.getPointItem().getItemDescription(),
                    ui.getPointItem().getItemCategory(),
                    type != null ? type.name() : null,
                    ui.getPointItem().getImageUrl(),
                    ui.getStatus(),
                    ui.getAcquiredAt(),
                    ui.getExpiresAt(),
                    ui.getEquippedAt(),
                    ui.getRemainingQuantity(),
                    ui.getStatus() == UserItemStatus.EQUIPPED,
                    ui.getStatus() == UserItemStatus.EXPIRED
            );
        }
    }

    /**
     * 보유 아이템 요약 응답 — 카테고리별 개수 집계.
     *
     * <p>"내 아이템" 페이지 상단 요약 카드 렌더링용. AI 이용권 잔여 횟수는 UserAiQuota에서 별도로 가져온다
     * (인벤토리가 아니므로 여기서는 제공하지 않음).</p>
     *
     * @param totalActive     ACTIVE + EQUIPPED 총 개수
     * @param avatarCount     아바타 보유 개수 (ACTIVE + EQUIPPED)
     * @param badgeCount      배지 보유 개수 (ACTIVE + EQUIPPED)
     * @param couponCount     쿠폰 보유 개수 (비-AI 쿠폰 — 현재는 AI 이용권 외에 없음)
     * @param applyCount      응모권 보유 개수 (ACTIVE)
     * @param hintCount       힌트 보유 개수 (ACTIVE)
     * @param equippedAvatar  현재 착용 아바타 (없으면 null)
     * @param equippedBadge   현재 착용 배지 (없으면 null)
     */
    public record UserItemSummaryResponse(
            long totalActive,
            long avatarCount,
            long badgeCount,
            long couponCount,
            long applyCount,
            long hintCount,
            UserItemResponse equippedAvatar,
            UserItemResponse equippedBadge
    ) {
    }

    /**
     * 페이지 기반 응답 (Spring Page 호환) — content + 페이징 메타.
     *
     * <p>Spring Page 직렬화 포맷을 record로 감싸 Swagger 스키마 정의를 명확하게 한다.</p>
     *
     * @param content        현재 페이지 아이템 리스트
     * @param page           현재 페이지 (0-indexed)
     * @param size           페이지 크기
     * @param totalElements  총 개수
     * @param totalPages     총 페이지 수
     */
    public record UserItemPageResponse(
            List<UserItemResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
