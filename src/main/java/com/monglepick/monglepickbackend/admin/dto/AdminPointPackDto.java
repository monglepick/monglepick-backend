package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 관리자 포인트팩(PointPackPrice) 마스터 관리 DTO 모음.
 *
 * <p>포인트팩은 결제 검증의 핵심 가격표이다. 클라이언트가 임의의 (price, pointsAmount)를
 * 보내 무제한 포인트를 획득하지 못하도록, 본 마스터 데이터로 정확 매칭 검증한다.</p>
 *
 * <h3>운영 주의사항</h3>
 * <ul>
 *   <li>가격(price) 변경은 결제 안정성에 영향을 주므로 신중하게 사용</li>
 *   <li>활성 상태 토글이 권장되며, 폐지된 팩은 isActive=false로 비활성화</li>
 *   <li>1P=10원 통일 (v3.2) — 입력 시 일관성 유지</li>
 * </ul>
 */
public class AdminPointPackDto {

    /** 신규 포인트팩 등록 요청 */
    public record CreateRequest(
            @NotBlank(message = "팩 이름은 필수입니다.")
            @Size(max = 100, message = "팩 이름은 100자 이하여야 합니다.")
            String packName,

            @NotNull(message = "가격은 필수입니다.")
            @Positive(message = "가격은 양수여야 합니다.")
            Integer price,

            @NotNull(message = "지급 포인트는 필수입니다.")
            @Positive(message = "지급 포인트는 양수여야 합니다.")
            Integer pointsAmount,

            Boolean isActive,

            @PositiveOrZero(message = "정렬 순서는 0 이상이어야 합니다.")
            Integer sortOrder
    ) {}

    /** 포인트팩 메타 수정 요청 */
    public record UpdateRequest(
            @NotBlank(message = "팩 이름은 필수입니다.")
            @Size(max = 100, message = "팩 이름은 100자 이하여야 합니다.")
            String packName,

            @NotNull(message = "가격은 필수입니다.")
            @Positive(message = "가격은 양수여야 합니다.")
            Integer price,

            @NotNull(message = "지급 포인트는 필수입니다.")
            @Positive(message = "지급 포인트는 양수여야 합니다.")
            Integer pointsAmount,

            Boolean isActive,

            @PositiveOrZero(message = "정렬 순서는 0 이상이어야 합니다.")
            Integer sortOrder
    ) {}

    /** 활성 상태 토글 요청 */
    public record UpdateActiveRequest(
            Boolean isActive
    ) {}

    /** 포인트팩 단일 항목 응답 */
    public record PackResponse(
            Long packId,
            String packName,
            Integer price,
            Integer pointsAmount,
            Boolean isActive,
            Integer sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
