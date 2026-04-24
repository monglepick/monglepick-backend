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
    // 2026-04-08: 구 AppNotice 통합으로 7개 앱 메인 노출 필드 흡수
    //   (displayType/linkUrl/imageUrl/startAt/endAt/priority/isActive)

    /**
     * 공지사항 응답 DTO.
     *
     * @param noticeId    공지 PK
     * @param title       제목
     * @param content     본문
     * @param noticeType  콘텐츠 카테고리 (NOTICE/UPDATE/MAINTENANCE/EVENT)
     * @param displayType 노출 방식 (LIST_ONLY/BANNER/POPUP/MODAL) — 구 AppNotice 흡수
     * @param isPinned    상단 고정 여부
     * @param sortOrder   정렬 순서 (nullable)
     * @param publishedAt 공개 시각 (nullable)
     * @param linkUrl     배너 클릭 시 이동 URL (nullable, 구 AppNotice 흡수)
     * @param imageUrl    배너/팝업 이미지 URL (nullable, 구 AppNotice 흡수)
     * @param startAt     앱 메인 노출 시작 (nullable, 구 AppNotice 흡수)
     * @param endAt       앱 메인 노출 종료 (nullable, 구 AppNotice 흡수)
     * @param priority    정렬 우선순위 (구 AppNotice 흡수)
     * @param isActive    앱 메인 노출 활성 토글 (구 AppNotice 흡수)
     * @param createdAt   등록 시각
     * @param updatedAt   수정 시각
     */
    public record NoticeResponse(
            Long noticeId,
            String title,
            String content,
            String noticeType,
            String displayType,
            Boolean isPinned,
            Integer sortOrder,
            LocalDateTime publishedAt,
            String linkUrl,
            String imageUrl,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Integer priority,
            Boolean isActive,
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

            /** 노출 방식 (LIST_ONLY/BANNER/POPUP/MODAL, 기본 LIST_ONLY). */
            @Size(max = 20)
            String displayType,

            Boolean isPinned,

            Integer sortOrder,

            LocalDateTime publishedAt,

            @Size(max = 500, message = "링크 URL은 500자 이하여야 합니다.")
            String linkUrl,

            @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
            String imageUrl,

            LocalDateTime startAt,
            LocalDateTime endAt,

            Integer priority,

            Boolean isActive
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

            @Size(max = 20)
            String displayType,

            Boolean isPinned,

            Integer sortOrder,

            LocalDateTime publishedAt,

            @Size(max = 500, message = "링크 URL은 500자 이하여야 합니다.")
            String linkUrl,

            @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
            String imageUrl,

            LocalDateTime startAt,
            LocalDateTime endAt,

            Integer priority,

            Boolean isActive
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

    /**
     * 공지 활성/비활성 토글 요청 DTO (앱 메인 노출 제어).
     *
     * 2026-04-08: 구 AppNotice.UpdateActiveRequest 흡수.
     */
    public record NoticeActiveUpdateRequest(
            Boolean isActive
    ) {}

    // ======================== FAQ ========================

    /**
     * FAQ 응답 DTO.
     *
     * @param faqId           FAQ PK
     * @param category        카테고리 (GENERAL/ACCOUNT/CHAT/RECOMMENDATION/COMMUNITY/PAYMENT)
     * @param question        질문
     * @param answer          답변
     * @param keywords        ES 검색 키워드 힌트 (쉼표 구분 동의어, nullable)
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
            String keywords,
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

            Integer sortOrder,

            /** ES 검색 키워드 힌트 (쉼표 구분 동의어, nullable). 예: "환불,반환,취소,돈" */
            String keywords
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

            Boolean isPublished,

            /** ES 검색 키워드 힌트 (쉼표 구분 동의어, nullable). 예: "비밀번호,패스워드,암호,재설정" */
            String keywords
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

    // 2026-04-08: 비속어 사전 DTO (ProfanityResponse/ProfanityCreateRequest/ProfanityImportResponse) 제거
}
