package com.monglepick.monglepickbackend.domain.support.service;

import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.ChatbotFaqMatch;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.ChatbotRequest;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.ChatbotResponse;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.FaqFeedbackRequest;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.FaqResponse;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.HelpArticleResponse;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.TicketCreateRequest;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.TicketDetailResponse;
import com.monglepick.monglepickbackend.domain.support.dto.SupportDto.TicketResponse;
import com.monglepick.monglepickbackend.domain.support.entity.TicketReply;
import com.monglepick.monglepickbackend.domain.support.repository.TicketReplyRepository;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final TicketReplyRepository ticketReplyRepository;

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

    /**
     * 본인 소유의 상담 티켓 상세를 조회한다 (답변 이력 포함).
     *
     * <p>보안 검증 절차:</p>
     * <ol>
     *   <li>{@code ticketId} 로 티켓 존재 여부 확인 — 없으면 {@link ErrorCode#TICKET_NOT_FOUND}</li>
     *   <li>티켓의 {@code userId} 가 요청자 {@code userId} 와 일치하는지 확인
     *       — 불일치 시 {@link ErrorCode#TICKET_ACCESS_DENIED} (타인 티켓 접근 차단)</li>
     *   <li>답변 목록을 {@code createdAt} 오름차순으로 조회하여 대화 흐름 유지</li>
     * </ol>
     *
     * <p>타인의 티켓 열람 시도 시 404 가 아닌 403 을 명시적으로 반환하여
     * 존재 여부 자체는 드러나도록 한다(서비스 약관상 본인 외 접근 금지가 분명함).</p>
     *
     * @param ticketId 조회 대상 티켓 PK
     * @param userId   요청자 사용자 ID (JWT 에서 추출)
     * @return 티켓 상세 + 답변 목록 응답
     * @throws BusinessException {@link ErrorCode#TICKET_NOT_FOUND} 티켓이 존재하지 않을 때
     * @throws BusinessException {@link ErrorCode#TICKET_ACCESS_DENIED} 본인 소유 티켓이 아닐 때
     */
    public TicketDetailResponse getTicketDetail(Long ticketId, String userId) {
        /* 1) 티켓 존재 확인 */
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));

        /* 2) 본인 소유 검증 — 타인 티켓 열람 차단 */
        if (!ticket.getUserId().equals(userId)) {
            log.warn("[Support] 타인 티켓 열람 시도 차단: ticketId={}, requesterId={}, ownerId={}",
                    ticketId, userId, ticket.getUserId());
            throw new BusinessException(ErrorCode.TICKET_ACCESS_DENIED);
        }

        /* 3) 답변 목록 조회 (시간 오름차순) */
        List<TicketReply> replies = ticketReplyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

        return TicketDetailResponse.from(ticket, replies);
    }

    // ─────────────────────────────────────────────
    // AI 챗봇 (FAQ 키워드 매칭 기반 자동응답)
    // ─────────────────────────────────────────────

    /**
     * 챗봇 응답에 포함될 최대 FAQ 매칭 건수.
     * 너무 많으면 답변이 산만해지고 한글 UI의 카드 렌더링이 길어지므로 3건으로 제한.
     */
    private static final int CHATBOT_MAX_MATCHES = 3;

    /**
     * 키워드 검색 시 후보로 가져올 Top-K FAQ (매칭 집계 전 단계).
     * 여러 키워드가 중복 매칭될 수 있어 최종 3건보다 더 크게 잡는다.
     */
    private static final int CHATBOT_CANDIDATE_SIZE = 10;

    /**
     * 사용자 메시지에서 제거할 한국어 불용어(조사/어미/일반 대화 토큰).
     * 챗봇 키워드 추출 품질을 높이기 위해 사전에 걸러낸다.
     */
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "저는", "제가", "저희", "우리", "그리고", "하지만", "그런데",
            "그래서", "그러면", "무엇", "무엇을", "어떻게", "어떤", "어디",
            "언제", "누가", "왜", "얼마", "얼마나", "좀", "좀만", "조금",
            "많이", "너무", "정말", "진짜", "please", "hello", "안녕",
            "안녕하세요", "문의", "질문", "궁금", "궁금해요", "궁금합니다",
            "알려", "알려주세요", "알려주시겠어요", "싶어요", "싶습니다",
            "해요", "해주세요", "하는", "하나요", "인가요", "이에요", "예요",
            "입니다", "됩니다", "되나요", "가요", "가나요",
            "the", "a", "an", "is", "are", "was", "were",
            "do", "does", "did", "how", "what", "when", "where", "why"
    ));

    /**
     * 키워드 안내 문구 매핑 — 특정 의도 키워드에 대해 기본 안내 문구를 보강한다.
     * FAQ 매칭이 없을 때 일반 안내 메시지에 함께 표시한다.
     */
    private static final Map<String, String> INTENT_HINTS = new LinkedHashMap<>();
    static {
        INTENT_HINTS.put("환불", "결제 후 7일 이내, 미사용 상태의 이용권은 환불이 가능합니다.");
        INTENT_HINTS.put("결제", "결제/구독 관련 문의는 '결제·구독' 페이지에서도 직접 확인하실 수 있어요.");
        INTENT_HINTS.put("포인트", "포인트는 출석 체크/활동 리워드/구독으로 지급됩니다. 1P = 10원입니다.");
        INTENT_HINTS.put("비밀번호", "로그인 페이지 하단의 '비밀번호 찾기'에서 이메일로 재설정 링크를 받을 수 있습니다.");
        INTENT_HINTS.put("탈퇴", "마이페이지 → 계정 설정 → 회원 탈퇴에서 진행하실 수 있습니다.");
        INTENT_HINTS.put("추천", "AI 채팅 추천에서 기분/상황을 자유롭게 알려주시면 맞춤 추천을 드립니다.");
        INTENT_HINTS.put("오류", "오류가 반복되면 스크린샷과 함께 '문의하기' 탭에 남겨 주세요.");
        INTENT_HINTS.put("상담", "아래 '상담원 연결' 버튼을 눌러 직접 상담원에게 문의하실 수 있습니다.");
    }

    /**
     * 챗봇에 사용자 메시지를 처리하여 응답을 반환한다.
     *
     * <p>처리 절차:</p>
     * <ol>
     *   <li>메시지에서 한국어/영문 키워드 후보를 추출한다(불용어 제거, 2자 이상).</li>
     *   <li>각 키워드로 {@code support_faq} 를 LIKE 검색하여 매칭 점수를 합산한다.</li>
     *   <li>상위 {@value #CHATBOT_MAX_MATCHES} 건을 matchedFaqs 로 구성한다.</li>
     *   <li>최상위 매칭이 있으면 해당 answer 를 그대로 답변 본문으로 사용한다.</li>
     *   <li>매칭이 없으면 상담원 이관 배너를 노출한다(needsHumanAgent=true).</li>
     * </ol>
     *
     * <p>비로그인 사용자도 사용할 수 있도록 userId 검증이 없다.
     * sessionId 는 클라이언트가 보존하여 재전송하며, 없으면 서버가 UUID 를 발급한다.
     * 현 구현은 stateless 하므로 sessionId 는 에코 용도이나 향후 대화 로깅 시 활용된다.</p>
     *
     * @param request 챗봇 요청 (message, sessionId)
     * @return 챗봇 응답 (answer, matchedFaqs, needsHumanAgent, sessionId)
     */
    public ChatbotResponse processChatbotMessage(ChatbotRequest request) {
        /* 세션 ID 보정 — 없으면 UUID 신규 발급 */
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.sessionId();

        String rawMessage = request.message() == null ? "" : request.message().trim();
        if (rawMessage.isEmpty()) {
            return new ChatbotResponse(
                    "메시지를 입력해 주세요.",
                    List.of(),
                    false,
                    sessionId
            );
        }

        /* 1) 키워드 토큰 추출 (2자 이상, 불용어 제거, 소문자 정규화) */
        List<String> keywords = extractKeywords(rawMessage);

        /* 2) 키워드별 FAQ 매칭 집계 — id 기준으로 중복 제거하며 점수 합산 */
        Map<Long, SupportFaq> faqById = new LinkedHashMap<>();
        Map<Long, Integer> scoreById = new LinkedHashMap<>();

        if (!keywords.isEmpty()) {
            PageRequest topK = PageRequest.of(0, CHATBOT_CANDIDATE_SIZE);
            for (String kw : keywords) {
                List<SupportFaq> found = faqRepository.searchByKeyword(kw, topK);
                for (SupportFaq f : found) {
                    faqById.putIfAbsent(f.getFaqId(), f);
                    scoreById.merge(f.getFaqId(), 1, Integer::sum);
                }
            }
        }

        /* 3) 점수 내림차순 정렬 후 상위 N 개 선택 */
        List<SupportFaq> topMatches = scoreById.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(CHATBOT_MAX_MATCHES)
                .map(e -> faqById.get(e.getKey()))
                .toList();

        List<ChatbotFaqMatch> matchedFaqs = topMatches.stream()
                .map(ChatbotFaqMatch::from)
                .toList();

        /* 4) 답변 본문 생성 */
        String answer;
        boolean needsHumanAgent;

        if (!topMatches.isEmpty()) {
            /* 최상위 매칭 FAQ 의 답변을 그대로 사용 + 추가 FAQ 안내 */
            SupportFaq best = topMatches.get(0);
            StringBuilder sb = new StringBuilder();
            sb.append(best.getAnswer());

            if (topMatches.size() > 1) {
                sb.append("\n\n아래 FAQ 도 도움이 될 수 있어요:");
            }
            answer = sb.toString();
            needsHumanAgent = false;
        } else {
            /* 매칭 실패 — 의도 기반 힌트 + 상담원 이관 배너 */
            StringBuilder sb = new StringBuilder();
            sb.append("죄송해요, 관련된 FAQ 를 찾지 못했어요.");

            /* 의도 힌트 매칭 — 첫 번째 매칭 힌트만 추가 (장황함 방지) */
            for (Map.Entry<String, String> entry : INTENT_HINTS.entrySet()) {
                if (rawMessage.contains(entry.getKey())) {
                    sb.append("\n\n").append(entry.getValue());
                    break;
                }
            }
            sb.append("\n\n더 자세한 도움이 필요하시면 아래 '상담원 연결' 버튼을 눌러 문의해 주세요.");

            answer = sb.toString();
            needsHumanAgent = true;
        }

        log.info("[Support] 챗봇 응답: sessionId={}, keywords={}, matches={}, needsHuman={}",
                sessionId, keywords, matchedFaqs.size(), needsHumanAgent);

        return new ChatbotResponse(answer, matchedFaqs, needsHumanAgent, sessionId);
    }

    /**
     * 사용자 메시지에서 검색용 키워드 토큰을 추출한다.
     *
     * <p>1) 소문자 정규화 → 2) 특수문자 제거(공백 대체) → 3) 공백 기준 토큰화
     * → 4) 2자 미만/불용어 제거 → 5) 중복 제거.
     * 한글은 조사 부착을 간단히 무시(접미사 미분해)하며, 실제 형태소 분석은 도입하지 않는다(비용/의존성 절감).</p>
     *
     * @param message 원본 사용자 메시지
     * @return 검색용 키워드 목록 (중복 없음)
     */
    private List<String> extractKeywords(String message) {
        /* 1) 소문자 + 특수문자 정리 */
        String normalized = message.toLowerCase()
                .replaceAll("[^0-9a-z\\uAC00-\\uD7A3\\s]", " ");

        /* 2) 공백 토큰화 + 필터 */
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() < 2) continue;
            if (STOPWORDS.contains(token)) continue;
            if (seen.add(token)) {
                result.add(token);
            }
        }
        return result;
    }
}
