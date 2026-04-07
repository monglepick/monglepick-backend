package com.monglepick.monglepickbackend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 고객센터 API DTO 모음.
 *
 * <p>관리자 페이지 "고객센터" 탭의 5개 서브탭(공지사항/FAQ/도움말/티켓/비속어)에서 사용하는
 * 요청/응답 DTO 를 inner record 로 정의한다. 설계서 {@code docs/관리자페이지_설계서.md} §3.3
 * 고객센터(23 API) 범위의 데이터 모델을 담당한다.</p>
 */
public final class AdminSupportDto {

    /** 인스턴스 생성 방지 (유틸리티 클래스 패턴) */
    private AdminSupportDto() {
    }

    // ======================== 공지사항 ========================

    /**
     * 공지사항 응답 DTO.
     *
     * @param noticeId    공지 PK
     * @param title       제목
     * @param content     본문
     * @param noticeType  유형 (NOTICE/UPDATE/MAINTENANCE)
     * @param isPinned    상단 고정 여부
     * @param sortOrder   정렬 순서 (nullable)
     * @param publishedAt 공개 시각 (nullable)
     * @param createdAt   등록 시각
     * @param updatedAt   수정 시각
     */
    public record NoticeResponse(
            Long noticeId,
            String title,
            String content,
            String noticeType,
            Boolean isPinned,
            Integer sortOrder,
            LocalDateTime publishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /** 공지사항 등록 요청 DTO. */
    public record NoticeCreateRequest(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 200, message = "제목은 최대 200자입니다.")
            String title,

            @NotBlank(message = "본문은 필수입니다.")
            String content,

            @Size(max = 30, message = "유형 코드는 최대 30자입니다.")
            String noticeType,

            Boolean isPinned,

            Integer sortOrder,

            LocalDateTime publishedAt
    ) {}

    /** 공지사항 수정 요청 DTO. */
    public record NoticeUpdateRequest(
            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 200, message = "제목은 최대 200자입니다.")
            String title,

            @NotBlank(message = "본문은 필수입니다.")
            String content,

            @Size(max = 30, message = "유형 코드는 최대 30자입니다.")
            String noticeType,

            Boolean isPinned,

            Integer sortOrder,

            LocalDateTime publishedAt
    ) {}

    /**
     * 공지 순서 변경 요청 DTO.
     *
     * @param orderedIds 정렬된 공지 ID 리스트 (순서가 곧 sortOrder 인덱스)
     */
    public record NoticeReorderRequest(
            @NotNull(message = "공지 ID 리스트는 필수입니다.")
            List<Long> orderedIds
    ) {}

    // ======================== FAQ ========================

    /**
     * FAQ 응답 DTO.
     *
     * @param faqId           FAQ PK
     * @param category        카테고리 (GENERAL/ACCOUNT/CHAT/RECOMMENDATION/COMMUNITY/PAYMENT)
     * @param question        질문
     * @param answer          답변
     * @param helpfulCount    도움됨 카운트
     * @param notHelpfulCount 도움 안됨 카운트
     * @param sortOrder       표시 순서 (nullable)
     * @param isPublished     공개 여부
     * @param createdAt       등록 시각
     * @param updatedAt       수정 시각
     */
    public record FaqResponse(
            Long faqId,
            String category,
            String question,
            String answer,
            int helpfulCount,
            int notHelpfulCount,
            Integer sortOrder,
            boolean isPublished,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /** FAQ 등록 요청 DTO. */
    public record FaqCreateRequest(
            @NotBlank(message = "카테고리는 필수입니다.")
            String category,

            @NotBlank(message = "질문은 필수입니다.")
            @Size(max = 500, message = "질문은 최대 500자입니다.")
            String question,

            @NotBlank(message = "답변은 필수입니다.")
            String answer,

            Integer sortOrder
    ) {}

    /** FAQ 수정 요청 DTO. */
    public record FaqUpdateRequest(
            @NotBlank(message = "카테고리는 필수입니다.")
            String category,

            @NotBlank(message = "질문은 필수입니다.")
            @Size(max = 500, message = "질문은 최대 500자입니다.")
            String question,

            @NotBlank(message = "답변은 필수입니다.")
            String answer,

            Integer sortOrder,

            Boolean isPublished
    ) {}

    /** FAQ 순서 변경 요청 DTO. */
    public record FaqReorderRequest(
            @NotNull(message = "FAQ ID 리스트는 필수입니다.")
            List<Long> orderedIds
    ) {}

    // ======================== 도움말 ========================

    /** 도움말 문서 응답 DTO. */
    public record HelpArticleResponse(
            Long articleId,
            String category,
            String title,
            String content,
            int viewCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /** 도움말 등록 요청 DTO. */
    public record HelpArticleCreateRequest(
            @NotBlank(message = "카테고리는 필수입니다.")
            String category,

            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 200, message = "제목은 최대 200자입니다.")
            String title,

            @NotBlank(message = "본문은 필수입니다.")
            String content
    ) {}

    /** 도움말 수정 요청 DTO. */
    public record HelpArticleUpdateRequest(
            @NotBlank(message = "카테고리는 필수입니다.")
            String category,

            @NotBlank(message = "제목은 필수입니다.")
            @Size(max = 200, message = "제목은 최대 200자입니다.")
            String title,

            @NotBlank(message = "본문은 필수입니다.")
            String content
    ) {}

    // ======================== 티켓 ========================

    /**
     * 티켓 목록 요약 응답 DTO.
     *
     * @param ticketId   티켓 PK
     * @param userId     작성자 ID
     * @param category   카테고리
     * @param title      제목
     * @param status     상태 (OPEN/IN_PROGRESS/RESOLVED/CLOSED)
     * @param priority   우선순위
     * @param assignedTo 담당자 ID (nullable)
     * @param createdAt  등록 시각
     */
    public record TicketSummary(
            Long ticketId,
            String userId,
            String category,
            String title,
            String status,
            String priority,
            String assignedTo,
            LocalDateTime createdAt
    ) {}

    /** 단일 티켓 답변 응답 DTO. */
    public record TicketReplyItem(
            Long replyId,
            String authorId,
            String authorType,
            String content,
            LocalDateTime createdAt
    ) {}

    /**
     * 티켓 상세 응답 DTO (본문 + 답변 리스트 포함).
     *
     * @param ticketId   티켓 PK
     * @param userId     작성자 ID
     * @param category   카테고리
     * @param title      제목
     * @param content    본문
     * @param status     상태
     * @param priority   우선순위
     * @param assignedTo 담당자 ID (nullable)
     * @param resolvedAt 해결 시각 (nullable)
     * @param closedAt   종결 시각 (nullable)
     * @param replies    답변 리스트 (시간 오름차순)
     * @param createdAt  등록 시각
     */
    public record TicketDetail(
            Long ticketId,
            String userId,
            String category,
            String title,
            String content,
            String status,
            String priority,
            String assignedTo,
            LocalDateTime resolvedAt,
            LocalDateTime closedAt,
            List<TicketReplyItem> replies,
            LocalDateTime createdAt
    ) {}

    /** 티켓 답변 작성 요청 DTO. */
    public record TicketReplyRequest(
            @NotBlank(message = "답변 내용은 필수입니다.")
            String content
    ) {}

    /** 티켓 상태 변경 요청 DTO. */
    public record TicketStatusUpdateRequest(
            @NotBlank(message = "변경할 상태는 필수입니다.")
            String status
    ) {}

    /**
     * 티켓 통계 응답 DTO.
     *
     * @param total       총 티켓 수
     * @param open        OPEN 상태
     * @param inProgress  IN_PROGRESS 상태
     * @param resolved    RESOLVED 상태
     * @param closed      CLOSED 상태
     */
    public record TicketStats(
            long total,
            long open,
            long inProgress,
            long resolved,
            long closed
    ) {}

    // ======================== 비속어 사전 ========================

    /** 비속어 응답 DTO. */
    public record ProfanityResponse(
            Long profanityId,
            String word,
            String severity,
            String note,
            LocalDateTime createdAt
    ) {}

    /** 비속어 등록 요청 DTO. */
    public record ProfanityCreateRequest(
            @NotBlank(message = "단어는 필수입니다.")
            @Size(max = 100, message = "단어는 최대 100자입니다.")
            String word,

            @Size(max = 20, message = "유해 단계는 최대 20자입니다.")
            String severity,

            @Size(max = 300, message = "비고는 최대 300자입니다.")
            String note
    ) {}

    /**
     * 비속어 CSV 임포트 응답 DTO.
     *
     * @param inserted 신규 등록된 단어 수
     * @param skipped  중복으로 건너뛴 단어 수
     * @param message  처리 결과 안내 메시지
     */
    public record ProfanityImportResponse(
            int inserted,
            int skipped,
            String message
    ) {}
}
