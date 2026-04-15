package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqReorderRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.HelpArticleCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.HelpArticleResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.HelpArticleUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeActiveUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeReorderRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketDetail;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketReplyItem;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketReplyRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketStats;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketStatusUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketSummary;
import com.monglepick.monglepickbackend.admin.repository.AdminFaqRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminHelpArticleRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminNoticeRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminTicketReplyRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminTicketRepository;
import com.monglepick.monglepickbackend.domain.support.entity.SupportCategory;
import com.monglepick.monglepickbackend.domain.support.entity.SupportFaq;
import com.monglepick.monglepickbackend.domain.support.entity.SupportHelpArticle;
import com.monglepick.monglepickbackend.domain.support.entity.SupportNotice;
import com.monglepick.monglepickbackend.domain.support.entity.SupportTicket;
import com.monglepick.monglepickbackend.domain.support.entity.TicketReply;
import com.monglepick.monglepickbackend.domain.support.entity.TicketStatus;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 고객센터 서비스.
 *
 * <p>관리자 페이지 "고객센터" 탭의 비즈니스 로직을 담당한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.3 고객센터 참조.</p>
 *
 * <h3>담당 기능 (19개)</h3>
 * <ul>
 *   <li>공지사항: 목록 / 등록 / 수정 / 삭제 / 순서변경 (5)</li>
 *   <li>FAQ: 목록 / 등록 / 수정 / 삭제 / 순서변경 (5)</li>
 *   <li>도움말: 목록 / 등록 / 수정 / 삭제 (4)</li>
 *   <li>티켓: 목록 / 상세 / 답변작성 / 상태변경 / 통계 (5)</li>
 * </ul>
 *
 * <p>2026-04-08: 비속어 사전(Profanity) 섹션 제거 — 관리자 요청으로 기능 삭제.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSupportService {

    /** 공지사항 리포지토리 */
    private final AdminNoticeRepository adminNoticeRepository;

    /** FAQ 리포지토리 */
    private final AdminFaqRepository adminFaqRepository;

    /** 도움말 리포지토리 */
    private final AdminHelpArticleRepository adminHelpArticleRepository;

    /** 티켓 리포지토리 */
    private final AdminTicketRepository adminTicketRepository;

    /** 티켓 답변 리포지토리 */
    private final AdminTicketReplyRepository adminTicketReplyRepository;

    // ======================== 공지사항 ========================
    // 2026-04-08: 구 AppNotice 통합 — displayType/linkUrl/imageUrl/startAt/endAt/
    //             priority/isActive 7개 필드가 SupportNotice로 흡수되었다.

    /** 공지사항 목록 조회 (유형 필터 선택). */
    public Page<NoticeResponse> getNotices(String noticeType, Pageable pageable) {
        log.debug("[AdminSupport] 공지 목록 조회 — type={}, page={}", noticeType, pageable.getPageNumber());
        Page<SupportNotice> result;
        if (noticeType != null && !noticeType.isBlank()) {
            result = adminNoticeRepository
                    .findByNoticeTypeOrderByIsPinnedDescCreatedAtDesc(noticeType.toUpperCase(), pageable);
        } else {
            result = adminNoticeRepository.findAllByOrderByIsPinnedDescCreatedAtDesc(pageable);
        }
        return result.map(this::toNoticeResponse);
    }

    /** 공지사항 단건 조회. */
    public NoticeResponse getNotice(Long id) {
        return toNoticeResponse(findNoticeOrThrow(id));
    }

    /**
     * 커뮤니티 공지 탭(유저 공개) 용 활성 공지 페이징 조회.
     *
     * <p>{@link #getActiveAppNotices} 가 홈 메인용(BANNER/POPUP/MODAL만)인 것과 달리
     * 커뮤니티 "공지사항" 탭에서는 LIST_ONLY 포함 전체 활성/기간 내 공지를 보여준다.
     * 정렬: isPinned DESC (고정 우선), createdAt DESC (최신). 비로그인 허용.</p>
     *
     * @param pageable 페이지 정보 (page/size)
     * @return 노출 중 공지 페이지
     */
    public Page<NoticeResponse> getActivePublicNotices(Pageable pageable) {
        log.debug("[AdminSupport] 공개 공지 페이지 조회 — page={}, size={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return adminNoticeRepository
                .findActivePublicNotices(LocalDateTime.now(), pageable)
                .map(this::toNoticeResponse);
    }

    /**
     * 커뮤니티 공지 탭용 단건 조회 (비로그인 허용).
     *
     * <p>관리자용 {@link #getNotice(Long)} 과 달리 <b>활성 + 기간 내</b> 공지만 반환한다.
     * 비활성/기간 외 공지는 일반 유저에게 노출되지 않아야 하므로 동일한
     * "찾을 수 없음" 예외로 응답해 정보 누출을 차단한다.</p>
     *
     * @param id 공지 PK
     * @return 공개 가능한 공지 상세
     * @throws BusinessException 공지 없음/비활성/기간 외 모두 동일한 INVALID_INPUT 으로 응답
     */
    public NoticeResponse getPublicNotice(Long id) {
        log.debug("[AdminSupport] 공개 공지 단건 조회 — noticeId={}", id);
        SupportNotice notice = findNoticeOrThrow(id);

        // 비활성 / 공개 전 / 공개 종료 — 보안상 모두 동일 메시지로 반환
        LocalDateTime now = LocalDateTime.now();
        boolean inactive = !Boolean.TRUE.equals(notice.getIsActive());
        boolean notYetStarted = notice.getStartAt() != null && notice.getStartAt().isAfter(now);
        boolean alreadyEnded = notice.getEndAt() != null && notice.getEndAt().isBefore(now);
        if (inactive || notYetStarted || alreadyEnded) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "공지사항을 찾을 수 없습니다: id=" + id);
        }
        return toNoticeResponse(notice);
    }

    /**
     * 앱 메인 노출 중 공지 목록 조회 (비로그인 API 용, 구 AppNotice 흡수).
     *
     * <p>2026-04-15: {@code pinned} 파라미터 추가. 홈 화면에서는 고정 공지만 보고자 할 때
     * {@code pinned=true} 로 호출. null 이면 고정 여부 무관 전체 노출.</p>
     *
     * @param displayType BANNER/POPUP/MODAL 필터 (null 이면 전체 앱 메인 노출)
     * @param pinned      고정 여부 필터 (null/true/false)
     * @return 노출 중 공지 목록
     */
    public List<NoticeResponse> getActiveAppNotices(String displayType, Boolean pinned) {
        String filter = null;
        if (displayType != null && !displayType.isBlank()) {
            String upper = displayType.trim().toUpperCase();
            if (!"BANNER".equals(upper) && !"POPUP".equals(upper) && !"MODAL".equals(upper)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "허용되지 않은 노출 방식: " + displayType + " (BANNER/POPUP/MODAL)");
            }
            filter = upper;
        }
        return adminNoticeRepository
                .findActiveAppNotices(LocalDateTime.now(), filter, pinned)
                .stream().map(this::toNoticeResponse).toList();
    }

    /**
     * 하위 호환 시그니처 — pinned 필터 없이 전체 앱 메인 공지를 조회한다.
     *
     * <p>2026-04-15: 신규 {@link #getActiveAppNotices(String, Boolean)} 추가로
     * 기존 한 인자 호출부가 깨지지 않도록 유지. 내부적으로 null 을 전달한다.</p>
     */
    public List<NoticeResponse> getActiveAppNotices(String displayType) {
        return getActiveAppNotices(displayType, null);
    }

    /** 공지사항 등록. */
    @Transactional
    public NoticeResponse createNotice(NoticeCreateRequest request) {
        log.info("[AdminSupport] 공지 등록 — title={}, displayType={}",
                request.title(), request.displayType());
        validatePeriod(request.startAt(), request.endAt());

        String displayType = normalizeDisplayType(request.displayType());
        SupportNotice notice = SupportNotice.builder()
                .title(request.title())
                .content(request.content())
                .noticeType(request.noticeType() != null ? request.noticeType().toUpperCase() : "NOTICE")
                .displayType(displayType != null ? displayType : "LIST_ONLY")
                .isPinned(request.isPinned() != null ? request.isPinned() : false)
                .sortOrder(request.sortOrder())
                .publishedAt(request.publishedAt())
                .linkUrl(request.linkUrl())
                .imageUrl(request.imageUrl())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .priority(request.priority() != null ? request.priority() : 0)
                .isActive(request.isActive() == null || request.isActive())
                .build();
        SupportNotice saved = adminNoticeRepository.save(notice);
        return toNoticeResponse(saved);
    }

    /** 공지사항 수정 (기본 필드 + 앱 메인 노출 필드 통합 업데이트). */
    @Transactional
    public NoticeResponse updateNotice(Long id, NoticeUpdateRequest request) {
        log.info("[AdminSupport] 공지 수정 — noticeId={}", id);
        validatePeriod(request.startAt(), request.endAt());

        SupportNotice notice = findNoticeOrThrow(id);
        notice.updateAll(
                request.title(),
                request.content(),
                request.noticeType() != null ? request.noticeType().toUpperCase() : null,
                normalizeDisplayType(request.displayType()),
                request.isPinned(),
                request.sortOrder(),
                request.publishedAt(),
                request.linkUrl(),
                request.imageUrl(),
                request.startAt(),
                request.endAt(),
                request.priority(),
                request.isActive()
        );
        return toNoticeResponse(notice);
    }

    /** 공지 활성/비활성 토글 (앱 메인 노출 제어, 구 AppNotice 흡수). */
    @Transactional
    public NoticeResponse updateNoticeActive(Long id, NoticeActiveUpdateRequest request) {
        log.info("[AdminSupport] 공지 활성 토글 — noticeId={}, isActive={}",
                id, request.isActive());
        SupportNotice notice = findNoticeOrThrow(id);
        notice.updateActive(Boolean.TRUE.equals(request.isActive()));
        return toNoticeResponse(notice);
    }

    /** 공지사항 삭제. */
    @Transactional
    public void deleteNotice(Long id) {
        log.info("[AdminSupport] 공지 삭제 — noticeId={}", id);
        if (!adminNoticeRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "공지사항을 찾을 수 없습니다: id=" + id);
        }
        adminNoticeRepository.deleteById(id);
    }

    /**
     * 공지사항 순서 변경.
     *
     * <p>orderedIds 의 인덱스를 그대로 sortOrder 로 반영한다.</p>
     */
    @Transactional
    public void reorderNotices(NoticeReorderRequest request) {
        log.info("[AdminSupport] 공지 순서 변경 — count={}", request.orderedIds().size());
        List<Long> ids = request.orderedIds();
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            int sortOrder = i;
            adminNoticeRepository.findById(id)
                    .ifPresent(notice -> notice.updateSortOrder(sortOrder));
        }
    }

    // ── 공지 헬퍼 ─────────────────────────────────────────

    /** 공지 단건 조회 헬퍼 (없으면 예외). */
    private SupportNotice findNoticeOrThrow(Long id) {
        return adminNoticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "공지사항을 찾을 수 없습니다: id=" + id));
    }

    /**
     * displayType 정규화.
     * null/blank → null (도메인 메서드에서 무시됨) / 유효값 → 대문자 / 잘못된 값 → 예외.
     */
    private String normalizeDisplayType(String displayType) {
        if (displayType == null || displayType.isBlank()) return null;
        String upper = displayType.trim().toUpperCase();
        switch (upper) {
            case "LIST_ONLY":
            case "BANNER":
            case "POPUP":
            case "MODAL":
                return upper;
            default:
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "허용되지 않은 노출 방식: " + displayType
                                + " (LIST_ONLY/BANNER/POPUP/MODAL)");
        }
    }

    /** startAt/endAt 유효성 검증 — endAt은 startAt 이후여야 함 (둘 다 지정된 경우). */
    private void validatePeriod(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BusinessException(ErrorCode.INVALID_NOTICE_PERIOD);
        }
    }

    // ======================== FAQ ========================

    /** FAQ 목록 조회 (카테고리 필터 선택). */
    public Page<FaqResponse> getFaqs(String category, Pageable pageable) {
        log.debug("[AdminSupport] FAQ 목록 조회 — category={}, page={}", category, pageable.getPageNumber());
        Page<SupportFaq> result;
        if (category != null && !category.isBlank()) {
            SupportCategory categoryEnum = parseSupportCategory(category);
            result = adminFaqRepository.findByCategoryOrderBySortOrderAscCreatedAtDesc(categoryEnum, pageable);
        } else {
            result = adminFaqRepository.findAllByOrderBySortOrderAscCreatedAtDesc(pageable);
        }
        return result.map(this::toFaqResponse);
    }

    /** FAQ 등록. */
    @Transactional
    public FaqResponse createFaq(FaqCreateRequest request) {
        log.info("[AdminSupport] FAQ 등록 — category={}", request.category());
        SupportFaq faq = SupportFaq.builder()
                .category(parseSupportCategory(request.category()))
                .question(request.question())
                .answer(request.answer())
                .sortOrder(request.sortOrder())
                .build();
        SupportFaq saved = adminFaqRepository.save(faq);
        return toFaqResponse(saved);
    }

    /** FAQ 수정. */
    @Transactional
    public FaqResponse updateFaq(Long id, FaqUpdateRequest request) {
        log.info("[AdminSupport] FAQ 수정 — faqId={}", id);
        SupportFaq faq = adminFaqRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "FAQ를 찾을 수 없습니다: id=" + id));

        faq.update(parseSupportCategory(request.category()), request.question(), request.answer());
        if (request.sortOrder() != null) {
            faq.updateSortOrder(request.sortOrder());
        }
        if (request.isPublished() != null) {
            faq.setPublished(request.isPublished());
        }
        return toFaqResponse(faq);
    }

    /** FAQ 삭제. */
    @Transactional
    public void deleteFaq(Long id) {
        log.info("[AdminSupport] FAQ 삭제 — faqId={}", id);
        if (!adminFaqRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "FAQ를 찾을 수 없습니다: id=" + id);
        }
        adminFaqRepository.deleteById(id);
    }

    /** FAQ 순서 변경 (orderedIds 인덱스 기반). */
    @Transactional
    public void reorderFaqs(FaqReorderRequest request) {
        log.info("[AdminSupport] FAQ 순서 변경 — count={}", request.orderedIds().size());
        List<Long> ids = request.orderedIds();
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            int sortOrder = i;
            adminFaqRepository.findById(id)
                    .ifPresent(faq -> faq.updateSortOrder(sortOrder));
        }
    }

    // ======================== 도움말 ========================

    /** 도움말 목록 조회. */
    public Page<HelpArticleResponse> getHelpArticles(String category, Pageable pageable) {
        log.debug("[AdminSupport] 도움말 목록 조회 — category={}, page={}", category, pageable.getPageNumber());
        Page<SupportHelpArticle> result;
        if (category != null && !category.isBlank()) {
            result = adminHelpArticleRepository.findByCategoryOrderByCreatedAtDesc(
                    parseSupportCategory(category), pageable);
        } else {
            result = adminHelpArticleRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return result.map(this::toHelpArticleResponse);
    }

    /** 도움말 등록. */
    @Transactional
    public HelpArticleResponse createHelpArticle(HelpArticleCreateRequest request) {
        log.info("[AdminSupport] 도움말 등록 — category={}", request.category());
        SupportHelpArticle article = SupportHelpArticle.builder()
                .category(parseSupportCategory(request.category()))
                .title(request.title())
                .content(request.content())
                .build();
        return toHelpArticleResponse(adminHelpArticleRepository.save(article));
    }

    /** 도움말 수정. */
    @Transactional
    public HelpArticleResponse updateHelpArticle(Long id, HelpArticleUpdateRequest request) {
        log.info("[AdminSupport] 도움말 수정 — articleId={}", id);
        SupportHelpArticle article = adminHelpArticleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "도움말을 찾을 수 없습니다: id=" + id));
        article.update(parseSupportCategory(request.category()), request.title(), request.content());
        return toHelpArticleResponse(article);
    }

    /** 도움말 삭제. */
    @Transactional
    public void deleteHelpArticle(Long id) {
        log.info("[AdminSupport] 도움말 삭제 — articleId={}", id);
        if (!adminHelpArticleRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "도움말을 찾을 수 없습니다: id=" + id);
        }
        adminHelpArticleRepository.deleteById(id);
    }

    // ======================== 티켓 ========================

    /** 티켓 목록 조회 (상태 필터 선택). */
    public Page<TicketSummary> getTickets(String status, Pageable pageable) {
        log.debug("[AdminSupport] 티켓 목록 조회 — status={}, page={}", status, pageable.getPageNumber());
        Page<SupportTicket> result;
        if (status != null && !status.isBlank()) {
            TicketStatus statusEnum;
            try {
                statusEnum = TicketStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "허용되지 않은 티켓 상태: " + status);
            }
            result = adminTicketRepository.findByStatusOrderByCreatedAtDesc(statusEnum, pageable);
        } else {
            result = adminTicketRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return result.map(this::toTicketSummary);
    }

    /** 티켓 상세 조회 (답변 포함). */
    public TicketDetail getTicketDetail(Long ticketId) {
        log.debug("[AdminSupport] 티켓 상세 조회 — ticketId={}", ticketId);
        SupportTicket ticket = adminTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "티켓을 찾을 수 없습니다: id=" + ticketId));

        List<TicketReplyItem> replies = adminTicketReplyRepository
                .findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream()
                .map(this::toTicketReplyItem)
                .toList();

        // SupportTicket 은 String FK 직접 보관 (JPA/MyBatis 하이브리드 §15.4)
        return new TicketDetail(
                ticket.getTicketId(),
                ticket.getUserId(),
                ticket.getCategory().name(),
                ticket.getTitle(),
                ticket.getContent(),
                ticket.getStatus().name(),
                ticket.getPriority(),
                ticket.getAssignedTo(),
                ticket.getResolvedAt(),
                ticket.getClosedAt(),
                replies,
                ticket.getCreatedAt()
        );
    }

    /** 티켓 답변 작성. */
    @Transactional
    public TicketReplyItem replyToTicket(Long ticketId, String adminUserId, TicketReplyRequest request) {
        log.info("[AdminSupport] 티켓 답변 작성 — ticketId={}, admin={}", ticketId, adminUserId);
        // 티켓 존재 확인
        SupportTicket ticket = adminTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "티켓을 찾을 수 없습니다: id=" + ticketId));

        TicketReply reply = TicketReply.builder()
                .ticketId(ticketId)
                .authorId(adminUserId != null ? adminUserId : "admin")
                .authorType("ADMIN")
                .content(request.content())
                .build();
        TicketReply saved = adminTicketReplyRepository.save(reply);

        // 상태 자동 전이: OPEN → IN_PROGRESS (처음 답변 시)
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.startProcessing();
        }

        return toTicketReplyItem(saved);
    }

    /** 티켓 상태 변경. */
    @Transactional
    public TicketSummary updateTicketStatus(Long ticketId, TicketStatusUpdateRequest request) {
        log.info("[AdminSupport] 티켓 상태 변경 — ticketId={}, status={}", ticketId, request.status());
        SupportTicket ticket = adminTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "티켓을 찾을 수 없습니다: id=" + ticketId));

        TicketStatus newStatus;
        try {
            newStatus = TicketStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "허용되지 않은 티켓 상태: " + request.status());
        }

        // 도메인 메서드를 통한 상태 전이
        switch (newStatus) {
            case IN_PROGRESS -> ticket.startProcessing();
            case RESOLVED -> ticket.resolve();
            case CLOSED -> ticket.close();
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "OPEN 으로의 되돌리기는 지원하지 않습니다.");
        }

        return toTicketSummary(ticket);
    }

    /** 티켓 통계 조회. */
    public TicketStats getTicketStats() {
        log.debug("[AdminSupport] 티켓 통계 조회");
        long total = adminTicketRepository.count();
        long open = adminTicketRepository.countByStatus(TicketStatus.OPEN);
        long inProgress = adminTicketRepository.countByStatus(TicketStatus.IN_PROGRESS);
        long resolved = adminTicketRepository.countByStatus(TicketStatus.RESOLVED);
        long closed = adminTicketRepository.countByStatus(TicketStatus.CLOSED);
        return new TicketStats(total, open, inProgress, resolved, closed);
    }

    // 2026-04-08: 비속어 사전(Profanity) 섹션 전체 제거 — 관리자 요청으로 기능 삭제.
    //             (getProfanities/addProfanity/deleteProfanity/importProfanityCsv/exportProfanityCsv)

    // ======================== 헬퍼 ========================

    /** SupportCategory 문자열을 enum 으로 변환한다. 잘못된 값이면 INVALID_INPUT. */
    private SupportCategory parseSupportCategory(String category) {
        try {
            return SupportCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "허용되지 않은 카테고리: " + category);
        }
    }

    // ======================== DTO 변환 ========================

    /**
     * SupportNotice → NoticeResponse 변환.
     * 2026-04-08: 구 AppNotice 흡수 필드 7종(displayType/linkUrl/imageUrl/startAt/
     * endAt/priority/isActive) 포함.
     */
    private NoticeResponse toNoticeResponse(SupportNotice notice) {
        return new NoticeResponse(
                notice.getNoticeId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getNoticeType(),
                notice.getDisplayType(),
                notice.getIsPinned(),
                notice.getSortOrder(),
                notice.getPublishedAt(),
                notice.getLinkUrl(),
                notice.getImageUrl(),
                notice.getStartAt(),
                notice.getEndAt(),
                notice.getPriority(),
                notice.getIsActive(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }

    private FaqResponse toFaqResponse(SupportFaq faq) {
        return new FaqResponse(
                faq.getFaqId(),
                faq.getCategory().name(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.getHelpfulCount(),
                faq.getNotHelpfulCount(),
                faq.getSortOrder(),
                faq.isPublished(),
                faq.getCreatedAt(),
                faq.getUpdatedAt()
        );
    }

    private HelpArticleResponse toHelpArticleResponse(SupportHelpArticle article) {
        return new HelpArticleResponse(
                article.getArticleId(),
                article.getCategory().name(),
                article.getTitle(),
                article.getContent(),
                article.getViewCount(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }

    private TicketSummary toTicketSummary(SupportTicket ticket) {
        // SupportTicket 은 String FK 직접 보관 (JPA/MyBatis 하이브리드 §15.4)
        return new TicketSummary(
                ticket.getTicketId(),
                ticket.getUserId(),
                ticket.getCategory().name(),
                ticket.getTitle(),
                ticket.getStatus().name(),
                ticket.getPriority(),
                ticket.getAssignedTo(),
                ticket.getCreatedAt()
        );
    }

    private TicketReplyItem toTicketReplyItem(TicketReply reply) {
        return new TicketReplyItem(
                reply.getReplyId(),
                reply.getAuthorId(),
                reply.getAuthorType(),
                reply.getContent(),
                reply.getCreatedAt()
        );
    }

}
