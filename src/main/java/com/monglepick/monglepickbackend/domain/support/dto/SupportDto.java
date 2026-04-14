package com.monglepick.monglepickbackend.domain.support.dto;

import com.monglepick.monglepickbackend.domain.support.entity.SupportFaq;
import com.monglepick.monglepickbackend.domain.support.entity.SupportHelpArticle;
import com.monglepick.monglepickbackend.domain.support.entity.SupportTicket;
import com.monglepick.monglepickbackend.domain.support.entity.TicketReply;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 고객센터(Support) 도메인 DTO 모음.
 *
 * <p>모든 DTO는 불변 Java record 타입으로 선언된다.
 * 각 record는 해당 엔티티로부터 정적 팩토리 메서드 {@code from(Entity)}를 통해 생성된다.</p>
 *
 * <h3>포함된 DTO</h3>
 * <ul>
 *   <li>{@link FaqResponse}           — FAQ 단건/목록 응답</li>
 *   <li>{@link HelpArticleResponse}   — 도움말 문서 단건/목록 응답</li>
 *   <li>{@link TicketCreateRequest}   — 상담 티켓 생성 요청</li>
 *   <li>{@link TicketResponse}        — 상담 티켓 응답 (목록/단건)</li>
 *   <li>{@link FaqFeedbackRequest}    — FAQ 피드백 요청</li>
 * </ul>
 */
public class SupportDto {

    /** 외부에서 SupportDto 인스턴스 생성을 막는 private 생성자. */
    private SupportDto() {}

    // ─────────────────────────────────────────────
    // FAQ 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * FAQ 단건 응답 DTO.
     *
     * <p>목록 조회({@code GET /api/v1/support/faqs})와
     * 단건 조회({@code GET /api/v1/support/faqs/{id}}) 모두 이 record를 사용한다.</p>
     *
     * @param id             FAQ ID
     * @param category       카테고리 문자열 (예: "PAYMENT", "ACCOUNT")
     * @param question       질문 내용
     * @param answer         답변 내용
     * @param helpfulCount   "도움됨" 피드백 수
     * @param notHelpfulCount "도움 안됨" 피드백 수
     */
    public record FaqResponse(
            Long id,
            String category,
            String question,
            String answer,
            int helpfulCount,
            int notHelpfulCount
    ) {
        /**
         * {@link SupportFaq} 엔티티로부터 FaqResponse를 생성하는 정적 팩토리 메서드.
         *
         * @param faq FAQ 엔티티
         * @return FaqResponse 인스턴스
         */
        public static FaqResponse from(SupportFaq faq) {
            return new FaqResponse(
                    faq.getFaqId(),
                    faq.getCategory().name(),      // enum → 문자열 (예: "PAYMENT")
                    faq.getQuestion(),
                    faq.getAnswer(),
                    faq.getHelpfulCount(),
                    faq.getNotHelpfulCount()
            );
        }
    }

    // ─────────────────────────────────────────────
    // 도움말 문서 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * 도움말 문서 응답 DTO.
     *
     * <p>목록 조회({@code GET /api/v1/support/help})와
     * 단건 조회({@code GET /api/v1/support/help/{id}}) 모두 이 record를 사용한다.
     * 단건 조회 시에는 조회수 증가가 함께 처리된다.</p>
     *
     * @param id        도움말 문서 ID
     * @param category  카테고리 문자열
     * @param title     문서 제목
     * @param content   문서 본문
     * @param viewCount 조회수
     */
    public record HelpArticleResponse(
            Long id,
            String category,
            String title,
            String content,
            int viewCount
    ) {
        /**
         * {@link SupportHelpArticle} 엔티티로부터 HelpArticleResponse를 생성하는 정적 팩토리 메서드.
         *
         * @param article 도움말 문서 엔티티
         * @return HelpArticleResponse 인스턴스
         */
        public static HelpArticleResponse from(SupportHelpArticle article) {
            return new HelpArticleResponse(
                    article.getArticleId(),
                    article.getCategory().name(),  // enum → 문자열
                    article.getTitle(),
                    article.getContent(),
                    article.getViewCount()
            );
        }
    }

    // ─────────────────────────────────────────────
    // 티켓 생성 요청 DTO
    // ─────────────────────────────────────────────

    /**
     * 상담 티켓 생성 요청 DTO.
     *
     * <p>{@code POST /api/v1/support/tickets} 요청 바디.</p>
     *
     * @param category 문의 카테고리 문자열 — SupportCategory enum 이름과 일치해야 함
     *                 (GENERAL / ACCOUNT / CHAT / RECOMMENDATION / COMMUNITY / PAYMENT)
     * @param title    문의 제목 — 공백 불가, 2자 이상 100자 이하
     * @param content  문의 내용 — 공백 불가, 10자 이상 2000자 이하
     */
    public record TicketCreateRequest(
            @NotBlank(message = "카테고리를 선택해주세요")
            String category,

            @NotBlank(message = "제목을 입력해주세요")
            @Size(min = 2, max = 100, message = "제목은 2자 이상 100자 이하로 입력해주세요")
            String title,

            @NotBlank(message = "문의 내용을 입력해주세요")
            @Size(min = 10, max = 2000, message = "문의 내용은 10자 이상 2000자 이하로 입력해주세요")
            String content
    ) {}

    // ─────────────────────────────────────────────
    // 티켓 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * 상담 티켓 응답 DTO.
     *
     * <p>티켓 목록({@code GET /api/v1/support/tickets})과
     * 티켓 생성({@code POST /api/v1/support/tickets}) 응답 모두 이 record를 사용한다.</p>
     *
     * @param ticketId  티켓 ID
     * @param title     문의 제목
     * @param category  카테고리 문자열
     * @param status    처리 상태 문자열 (OPEN / IN_PROGRESS / RESOLVED / CLOSED)
     * @param createdAt 티켓 생성 시각
     */
    public record TicketResponse(
            Long ticketId,
            String title,
            String category,
            String status,
            LocalDateTime createdAt
    ) {
        /**
         * {@link SupportTicket} 엔티티로부터 TicketResponse를 생성하는 정적 팩토리 메서드.
         *
         * @param ticket 상담 티켓 엔티티
         * @return TicketResponse 인스턴스
         */
        public static TicketResponse from(SupportTicket ticket) {
            return new TicketResponse(
                    ticket.getTicketId(),
                    ticket.getTitle(),
                    ticket.getCategory().name(),   // enum → 문자열
                    ticket.getStatus().name(),      // enum → 문자열
                    ticket.getCreatedAt()
            );
        }
    }

    // ─────────────────────────────────────────────
    // 티켓 답변 응답 DTO
    // ─────────────────────────────────────────────

    /**
     * 티켓 답변 응답 DTO (사용자용 상세 조회에 포함).
     *
     * <p>관리자 또는 사용자가 작성한 답변 한 건을 표현한다.
     * 프론트엔드에서는 {@code authorType} 값으로 말풍선 좌/우 및 색상을 구분한다
     * (ADMIN: 왼쪽 회색, USER: 오른쪽 브랜드 컬러).</p>
     *
     * @param replyId    답변 PK
     * @param authorType 작성자 유형 ("ADMIN" 또는 "USER")
     * @param content    답변 본문
     * @param createdAt  작성 시각
     */
    public record TicketReplyResponse(
            Long replyId,
            String authorType,
            String content,
            LocalDateTime createdAt
    ) {
        /**
         * {@link TicketReply} 엔티티로부터 TicketReplyResponse 를 생성한다.
         *
         * @param reply 티켓 답변 엔티티
         * @return DTO 인스턴스
         */
        public static TicketReplyResponse from(TicketReply reply) {
            return new TicketReplyResponse(
                    reply.getReplyId(),
                    reply.getAuthorType(),
                    reply.getContent(),
                    reply.getCreatedAt()
            );
        }
    }

    // ─────────────────────────────────────────────
    // 티켓 상세 응답 DTO (사용자용)
    // ─────────────────────────────────────────────

    /**
     * 사용자용 티켓 상세 응답 DTO.
     *
     * <p>{@code GET /api/v1/support/tickets/{ticketId}} 응답 바디.
     * 티켓 본문에 답변 목록(replies)을 함께 반환하여 한 번의 호출로 전체 대화를 조회할 수 있게 한다.</p>
     *
     * <p>보안 주의: 이 DTO 는 본인 소유 티켓만 접근 가능한 엔드포인트에서만 사용되며,
     * 서비스 레이어에서 {@code ticket.userId == 요청자 userId} 검증을 완료한 뒤 생성된다.</p>
     *
     * @param ticketId   티켓 PK
     * @param category   카테고리 문자열 (GENERAL/ACCOUNT/CHAT/RECOMMENDATION/COMMUNITY/PAYMENT)
     * @param title      문의 제목
     * @param content    문의 본문
     * @param status     처리 상태 문자열 (OPEN/IN_PROGRESS/RESOLVED/CLOSED)
     * @param createdAt  생성 시각
     * @param resolvedAt 해결 시각 (null 허용)
     * @param closedAt   종료 시각 (null 허용)
     * @param replies    답변 목록 (시간순, 빈 배열 가능)
     */
    public record TicketDetailResponse(
            Long ticketId,
            String category,
            String title,
            String content,
            String status,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            LocalDateTime closedAt,
            List<TicketReplyResponse> replies
    ) {
        /**
         * {@link SupportTicket} 엔티티와 답변 목록으로부터 TicketDetailResponse 를 생성한다.
         *
         * @param ticket  상담 티켓 엔티티 (본인 소유 검증 완료 상태)
         * @param replies 답변 엔티티 목록 (호출측에서 시간순 정렬 보장)
         * @return DTO 인스턴스
         */
        public static TicketDetailResponse from(SupportTicket ticket, List<TicketReply> replies) {
            List<TicketReplyResponse> replyDtos = replies.stream()
                    .map(TicketReplyResponse::from)
                    .toList();

            return new TicketDetailResponse(
                    ticket.getTicketId(),
                    ticket.getCategory().name(),
                    ticket.getTitle(),
                    ticket.getContent(),
                    ticket.getStatus().name(),
                    ticket.getCreatedAt(),
                    ticket.getResolvedAt(),
                    ticket.getClosedAt(),
                    replyDtos
            );
        }
    }

    // ─────────────────────────────────────────────
    // FAQ 피드백 요청 DTO
    // ─────────────────────────────────────────────

    /**
     * FAQ 피드백 요청 DTO.
     *
     * <p>{@code POST /api/v1/support/faqs/{id}/feedback} 요청 바디.</p>
     *
     * @param helpful true: "도움됨", false: "도움 안됨"
     */
    public record FaqFeedbackRequest(
            boolean helpful
    ) {}

    // ─────────────────────────────────────────────
    // AI 챗봇 요청/응답 DTO
    // ─────────────────────────────────────────────

    /**
     * AI 고객센터 챗봇 요청 DTO.
     *
     * <p>{@code POST /api/v1/support/chatbot} 요청 바디.
     * 비로그인 사용자도 사용할 수 있으므로 userId는 필수가 아니다.</p>
     *
     * @param message   사용자 입력 메시지 (공백 불가, 1~500자)
     * @param sessionId 대화 맥락 유지용 세션 ID (최초 호출 시 null, 서버가 발급)
     */
    public record ChatbotRequest(
            @NotBlank(message = "메시지를 입력해주세요")
            @Size(min = 1, max = 500, message = "메시지는 1자 이상 500자 이하로 입력해주세요")
            String message,

            String sessionId
    ) {}

    /**
     * AI 고객센터 챗봇 응답 DTO.
     *
     * <p>FAQ 키워드 매칭 결과를 기반으로 봇 답변을 구성한다.
     * 매칭된 FAQ가 없거나 사용자 추가 요청 의도가 감지되면
     * {@code needsHumanAgent=true}로 설정하여 상담원 이관 배너를 노출시킨다.</p>
     *
     * @param answer          봇 답변 본문 (항상 존재)
     * @param matchedFaqs     매칭된 FAQ 카드 목록 (0~3건)
     * @param needsHumanAgent 상담원 연결이 필요한지 여부
     * @param sessionId       세션 ID (최초 호출 시 서버가 발급한 UUID)
     */
    public record ChatbotResponse(
            String answer,
            java.util.List<ChatbotFaqMatch> matchedFaqs,
            boolean needsHumanAgent,
            String sessionId
    ) {}

    /**
     * 챗봇 응답에 포함되는 FAQ 매칭 카드 DTO.
     *
     * <p>프론트엔드 ChatbotTab이 카드 클릭 시 해당 FAQ 질문을 재전송하여
     * 상세 답변을 다시 조회할 수 있도록 id/question만 최소한으로 반환한다.</p>
     *
     * @param id       매칭된 FAQ ID
     * @param question 질문 본문 (카드에 표시)
     * @param category 카테고리 문자열
     */
    public record ChatbotFaqMatch(
            Long id,
            String question,
            String category
    ) {
        public static ChatbotFaqMatch from(SupportFaq faq) {
            return new ChatbotFaqMatch(
                    faq.getFaqId(),
                    faq.getQuestion(),
                    faq.getCategory().name()
            );
        }
    }
}
