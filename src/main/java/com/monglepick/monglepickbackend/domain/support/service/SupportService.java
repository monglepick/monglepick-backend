package com.monglepick.monglepickbackend.domain.support.service;

import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.FaqFeedbackRequest;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.FaqResponse;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.HelpArticleResponse;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.TicketCreateRequest;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.TicketResponse;
import com.monglepick.monglepickbackend.domain.support.entity.SupportCategory;
import com.monglepick.monglepickbackend.domain.support.entity.SupportFaq;
import com.monglepick.monglepickbackend.domain.support.entity.SupportFaqFeedback;
import com.monglepick.monglepickbackend.domain.support.entity.SupportHelpArticle;
import com.monglepick.monglepickbackend.domain.support.entity.SupportTicket;
import com.monglepick.monglepickbackend.domain.support.repository.SupportFaqFeedbackRepository;
import com.monglepick.monglepickbackend.domain.support.repository.SupportFaqRepository;
import com.monglepick.monglepickbackend.domain.support.repository.SupportHelpArticleRepository;
import com.monglepick.monglepickbackend.domain.support.repository.SupportTicketRepository;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 고객센터(Support) 통합 서비스.
 *
 * <p>FAQ 조회/피드백, 도움말 조회, 상담 티켓 CRUD를 담당한다.
 * 프론트엔드의 SupportPage(4개 탭: FAQ, 도움말, 문의하기, 내 문의)에 대응한다.</p>
 *
 * <h3>인증 구분</h3>
 * <ul>
 *   <li>비인증: FAQ 조회, 도움말 조회</li>
 *   <li>인증 필수: FAQ 피드백, 티켓 생성, 내 티켓 조회</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportService {

    private final SupportFaqRepository faqRepository;
    private final SupportHelpArticleRepository helpArticleRepository;
    private final SupportTicketRepository ticketRepository;
    private final SupportFaqFeedbackRepository faqFeedbackRepository;

    /*
     * users 테이블의 쓰기 소유는 김민규(MyBatis)이므로 UserRepository 의존성을 제거하고
     * String userId만 보관한다 (설계서 §15.4). 사용자 존재 검증은 JWT 인증 단계에서 완료된다.
     */

    /** 활동 리워드 서비스 — FAQ 피드백(FAQ_FEEDBACK), 티켓 생성(TICKET_CREATE) 리워드 지급 위임 */
    private final RewardService rewardService;

    // ─────────────────────────────────────────────
    // FAQ
    // ─────────────────────────────────────────────

    /**
     * FAQ 목록을 조회한다.
     *
     * <p>category가 null이면 전체 FAQ를 최신순으로 반환하고,
     * 값이 있으면 해당 카테고리만 필터링한다.</p>
     *
     * @param category 필터 카테고리 (null 허용 — 전체 조회)
     * @return FAQ 응답 목록
     */
    public List<FaqResponse> getFaqs(String category) {
        List<SupportFaq> faqs;

        if (category != null && !category.isBlank()) {
            /* 카테고리 문자열을 enum으로 변환 (잘못된 값이면 전체 조회로 대체) */
            try {
                SupportCategory cat = SupportCategory.valueOf(category.toUpperCase());
                faqs = faqRepository.findByCategory(cat, Sort.by(Sort.Direction.DESC, "createdAt"));
            } catch (IllegalArgumentException e) {
                faqs = faqRepository.findAllByOrderByCreatedAtDesc();
            }
        } else {
            faqs = faqRepository.findAllByOrderByCreatedAtDesc();
        }

        return faqs.stream().map(FaqResponse::from).toList();
    }

    /**
     * FAQ에 "도움됨/도움 안됨" 피드백을 제출한다.
     *
     * <p>동일 사용자가 같은 FAQ에 중복 피드백을 제출하면
     * {@link ErrorCode#FAQ_FEEDBACK_DUPLICATE} 예외를 던진다.</p>
     *
     * @param faqId   피드백 대상 FAQ ID
     * @param userId  피드백 제출 사용자 ID (JWT에서 추출)
     * @param request 피드백 요청 (helpful: true/false)
     */
    @Transactional
    public void submitFaqFeedback(Long faqId, String userId, FaqFeedbackRequest request) {
        /* FAQ 존재 확인 */
        SupportFaq faq = faqRepository.findById(faqId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAQ_NOT_FOUND));

        /* 중복 피드백 확인 (faq_id + user_id UK) */
        faqFeedbackRepository.findByFaq_FaqIdAndUserId(faqId, userId)
                .ifPresent(f -> {
                    throw new BusinessException(ErrorCode.FAQ_FEEDBACK_DUPLICATE);
                });

        /* 피드백 저장 */
        SupportFaqFeedback feedback = SupportFaqFeedback.builder()
                .faq(faq)
                .userId(userId)
                .helpful(request.helpful())
                .build();
        faqFeedbackRepository.save(feedback);

        /* FAQ 비정규화 카운터 업데이트 */
        if (request.helpful()) {
            faq.incrementHelpful();
        } else {
            faq.incrementNotHelpful();
        }

        log.info("[Support] FAQ 피드백 제출: faqId={}, userId={}, helpful={}", faqId, userId, request.helpful());

        /*
         * FAQ 피드백 활동 리워드 지급 (FAQ_FEEDBACK).
         * RewardService REQUIRES_NEW + 내부 try-catch로 동작하므로
         * 리워드 실패가 피드백 저장 트랜잭션에 영향을 주지 않는다.
         * referenceId: "faq_{faqId}" — 동일 FAQ 중복 지급 방지 키로 사용.
         */
        rewardService.grantReward(userId, "FAQ_FEEDBACK", "faq_" + faqId, 0);
    }

    // ─────────────────────────────────────────────
    // 도움말
    // ─────────────────────────────────────────────

    /**
     * 도움말 문서 목록을 조회한다.
     *
     * @param category 필터 카테고리 (null 허용 — 전체 조회)
     * @return 도움말 응답 목록
     */
    public List<HelpArticleResponse> getHelpArticles(String category) {
        List<SupportHelpArticle> articles;

        if (category != null && !category.isBlank()) {
            try {
                SupportCategory cat = SupportCategory.valueOf(category.toUpperCase());
                articles = helpArticleRepository.findByCategory(cat, Sort.by(Sort.Direction.DESC, "createdAt"));
            } catch (IllegalArgumentException e) {
                articles = helpArticleRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
            }
        } else {
            articles = helpArticleRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        return articles.stream().map(HelpArticleResponse::from).toList();
    }

    // ─────────────────────────────────────────────
    // 상담 티켓
    // ─────────────────────────────────────────────

    /**
     * 상담 티켓을 생성한다.
     *
     * <p>로그인한 사용자만 티켓을 생성할 수 있다.
     * 생성 시 상태는 자동으로 OPEN으로 설정된다.</p>
     *
     * @param userId  티켓 작성자 ID (JWT에서 추출)
     * @param request 티켓 생성 요청 (category, title, content)
     * @return 생성된 티켓 응답
     */
    @Transactional
    public TicketResponse createTicket(String userId, TicketCreateRequest request) {
        /* 사용자 존재 검증은 JWT 인증 단계에서 이미 처리됨 — String userId만 보관 (설계서 §15.4) */

        /* 카테고리 변환 */
        SupportCategory category;
        try {
            category = SupportCategory.valueOf(request.category().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 카테고리입니다: " + request.category());
        }

        /* 티켓 생성 */
        SupportTicket ticket = SupportTicket.builder()
                .userId(userId)
                .category(category)
                .title(request.title())
                .content(request.content())
                .build();
        ticketRepository.save(ticket);

        log.info("[Support] 상담 티켓 생성: ticketId={}, userId={}, category={}",
                ticket.getTicketId(), userId, category);

        /*
         * 티켓 생성 활동 리워드 지급 (TICKET_CREATE).
         * RewardService REQUIRES_NEW + 내부 try-catch로 동작하므로
         * 리워드 실패가 티켓 저장 트랜잭션에 영향을 주지 않는다.
         * referenceId: "ticket_{ticketId}" — 티켓 단위 중복 지급 방지 키로 사용.
         */
        rewardService.grantReward(userId, "TICKET_CREATE", "ticket_" + ticket.getTicketId(), 0);

        return TicketResponse.from(ticket);
    }

    /**
     * 현재 사용자의 상담 티켓 목록을 페이징으로 조회한다.
     *
     * @param userId 조회 대상 사용자 ID (JWT에서 추출)
     * @param page   페이지 번호 (0부터 시작)
     * @param size   페이지 크기 (최대 50)
     * @return 티켓 페이지 응답
     */
    public Page<TicketResponse> getMyTickets(String userId, int page, int size) {
        /* 페이지 크기 상한 제한 (DoS 방지) */
        int safeSize = Math.min(size, 50);
        PageRequest pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        return ticketRepository.findByUserId(userId, pageable)
                .map(TicketResponse::from);
    }
}
