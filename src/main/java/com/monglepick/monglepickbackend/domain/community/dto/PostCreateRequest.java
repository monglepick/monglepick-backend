package com.monglepick.monglepickbackend.domain.community.dto;

import com.monglepick.monglepickbackend.domain.community.entity.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 게시글 작성/수정 요청 DTO.
 *
 * <p>category 필드는 {@link Post.Category} 타입으로 선언하여
 * Jackson이 역직렬화 시 {@link Post.Category#fromValue(String)}를 호출한다.
 * 따라서 "general", "FREE", "discussion" 등 대소문자를 가리지 않고 모두 수용한다.</p>
 *
 * @param title      게시글 제목 (최대 200자, 필수)
 * @param content    게시글 본문 (필수)
 * @param category   카테고리 — FREE / DISCUSSION / RECOMMENDATION / NEWS / PLAYLIST_SHARE
 *                   (소문자·혼합 대소문자 허용, "general" → FREE 자동 변환)
 * @param playlistId 공유할 플레이리스트 ID (PLAYLIST_SHARE 카테고리 전용, 나머지는 null)
 */
public record PostCreateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content,

        /* String → Post.Category 타입으로 변경.
         * Jackson이 @JsonCreator(fromValue)를 통해 대소문자 무관하게 변환하므로
         * 서비스 계층에서 별도 valueOf() 호출이 불필요하다. */
        @NotNull(message = "카테고리는 필수입니다.")
        Post.Category category,

        /** 공유할 플레이리스트 ID (PLAYLIST_SHARE 전용, 다른 카테고리에서는 null) */
        Long playlistId,

        List<String> imageUrls  // ✅ 추가 — null 허용 (이미지 없는 글도 가능)
) {}
