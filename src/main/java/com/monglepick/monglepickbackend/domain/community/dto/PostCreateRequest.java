package com.monglepick.monglepickbackend.domain.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시글 작성/수정 요청 DTO
 *
 * @param title 게시글 제목 (최대 200자)
 * @param content 게시글 본문
 * @param category 카테고리 (FREE, DISCUSSION, RECOMMENDATION, NEWS)
 */
public record PostCreateRequest(
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content,

        @NotBlank(message = "카테고리는 필수입니다.")
        String category
) {}
