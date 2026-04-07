package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqReorderRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.FaqUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.HelpArticleCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.HelpArticleResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.HelpArticleUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeReorderRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.NoticeUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.ProfanityCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.ProfanityImportResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.ProfanityResponse;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketDetail;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketReplyItem;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketReplyRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketStats;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketStatusUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminSupportDto.TicketSummary;
import com.monglepick.monglepickbackend.admin.repository.AdminFaqRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminHelpArticleRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminNoticeRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminProfanityRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminTicketReplyRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminTicketRepository;
import com.monglepick.monglepickbackend.domain.support.entity.SupportCategory;
import com.monglepick.monglepickbackend.domain.support.entity.SupportFaq;
import com.monglepick.monglepickbackend.domain.support.entity.SupportHelpArticle;
import com.monglepick.monglepickbackend.domain.support.entity.SupportNotice;
import com.monglepick.monglepickbackend.domain.support.entity.SupportProfanity;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 관리자 고객센터 서비스.
 *
 * <p>관리자 페이지 "고객센터" 탭의 23개 기능에 대한 비즈니스 로직을 담당한다.
 * 설계서 {@code docs/관리자페이지_설계서.md} §3.3 고객센터(23 API).</p>
 *
 * <h3>담당 기능</h3>
 * <ul>
 *   <li>공지사항: 목록 / 등록 / 수정 / 삭제 / 순서변경 (5)</li>
 *   <li>FAQ: 목록 / 등록 / 수정 / 삭제 / 순서변경 (5)</li>
 *   <li>도움말: 목록 / 등록 / 수정 / 삭제 (4)</li>
 *   <li>티켓: 목록 / 상세 / 답변작성 / 상태변경 / 통계 (5)</li>
 *   <li>비속어: 목록 / 추가 / 삭제 / CSV 임포트 (4)</li>
 * </ul>
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

    /** 비속어 리포지토리 */
    private final AdminProfanityRepository adminProfanityRepository;

    // ======================== 공지사항 ========================

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

    /** 공지사항 등록. */
    @Transactional
    public NoticeResponse createNotice(NoticeCreateRequest request) {
        log.info("[AdminSupport] 공지 등록 — title={}", request.title());
        SupportNotice notice = SupportNotice.builder()
                .title(request.title())
                .content(request.content())
                .noticeType(request.noticeType() != null ? request.noticeType().toUpperCase() : "NOTICE")
                .isPinned(request.isPinned() != null ? request.isPinned() : false)
                .sortOrder(request.sortOrder())
                .publishedAt(request.publishedAt())
                .build();
        SupportNotice saved = adminNoticeRepository.save(notice);
        return toNoticeResponse(saved);
    }

    /** 공지사항 수정. */
    @Transactional
    public NoticeResponse updateNotice(Long id, NoticeUpdateRequest request) {
        log.info("[AdminSupport] 공지 수정 — noticeId={}", id);
        SupportNotice notice = adminNoticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "공지사항을 찾을 수 없습니다: id=" + id));

        notice.update(request.title(), request.content(), request.noticeType());
        if (request.isPinned() != null) {
            notice.setPinned(request.isPinned());
        }
        if (request.sortOrder() != null) {
            notice.updateSortOrder(request.sortOrder());
        }
        if (request.publishedAt() != null) {
            notice.publish(request.publishedAt());
        }
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

    // ======================== 비속어 ========================

    /** 비속어 목록 조회. */
    public Page<ProfanityResponse> getProfanities(Pageable pageable) {
        log.debug("[AdminSupport] 비속어 목록 조회 — page={}", pageable.getPageNumber());
        return adminProfanityRepository.findAllByOrderByWordAsc(pageable)
                .map(this::toProfanityResponse);
    }

    /** 비속어 추가. */
    @Transactional
    public ProfanityResponse addProfanity(ProfanityCreateRequest request) {
        log.info("[AdminSupport] 비속어 추가 — word={}", request.word());
        if (adminProfanityRepository.findByWord(request.word()).isPresent()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미 등록된 단어입니다: " + request.word());
        }
        SupportProfanity profanity = SupportProfanity.builder()
                .word(request.word())
                .severity(request.severity() != null ? request.severity().toUpperCase() : "MEDIUM")
                .note(request.note())
                .build();
        return toProfanityResponse(adminProfanityRepository.save(profanity));
    }

    /** 비속어 삭제. */
    @Transactional
    public void deleteProfanity(Long id) {
        log.info("[AdminSupport] 비속어 삭제 — id={}", id);
        if (!adminProfanityRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "비속어를 찾을 수 없습니다: id=" + id);
        }
        adminProfanityRepository.deleteById(id);
    }

    /**
     * 비속어 CSV 임포트.
     *
     * <p>CSV 형식: {@code word,severity,note}. 첫 줄이 헤더이면 건너뛴다.
     * 이미 등록된 단어는 skipped 카운트에 포함된다.</p>
     *
     * @param csvContent CSV 파일 바이트 배열
     * @return 임포트 결과
     */
    @Transactional
    public ProfanityImportResponse importProfanityCsv(byte[] csvContent) {
        log.info("[AdminSupport] 비속어 CSV 임포트 — size={}B", csvContent.length);
        int inserted = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(csvContent), StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // 헤더 행 건너뛰기 (word 로 시작하는 경우)
                if (isFirstLine) {
                    isFirstLine = false;
                    if (line.toLowerCase().startsWith("word")) {
                        continue;
                    }
                }

                String[] parts = line.split(",", 3);
                String word = parts[0].trim().replaceAll("^\"|\"$", "");
                if (word.isEmpty()) {
                    continue;
                }

                if (adminProfanityRepository.findByWord(word).isPresent()) {
                    skipped++;
                    continue;
                }

                String severity = parts.length > 1 ? parts[1].trim().toUpperCase() : "MEDIUM";
                String note = parts.length > 2 ? parts[2].trim() : null;

                SupportProfanity profanity = SupportProfanity.builder()
                        .word(word)
                        .severity(severity.isEmpty() ? "MEDIUM" : severity)
                        .note(note)
                        .build();
                adminProfanityRepository.save(profanity);
                inserted++;
            }
        } catch (IOException e) {
            log.error("[AdminSupport] CSV 임포트 실패", e);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "CSV 파일 읽기 실패: " + e.getMessage());
        }

        String message = String.format("%d건 등록, %d건 중복 건너뜀", inserted, skipped);
        log.info("[AdminSupport] 비속어 CSV 임포트 완료 — {}", message);
        return new ProfanityImportResponse(inserted, skipped, message);
    }

    /** 비속어 전체 목록을 CSV 문자열로 반환 (익스포트용). */
    public String exportProfanityCsv() {
        log.debug("[AdminSupport] 비속어 CSV 익스포트");
        List<SupportProfanity> all = adminProfanityRepository.findAllByOrderByWordAsc();
        StringBuilder sb = new StringBuilder();
        sb.append("word,severity,note\n");
        for (SupportProfanity p : all) {
            sb.append(escapeCsv(p.getWord())).append(",")
                    .append(escapeCsv(p.getSeverity())).append(",")
                    .append(escapeCsv(p.getNote() != null ? p.getNote() : ""))
                    .append("\n");
        }
        return sb.toString();
    }

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

    /** CSV 필드 이스케이프 (간단한 구현 — 쉼표/개행 포함 시 쌍따옴표 감쌈) */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\n") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ======================== DTO 변환 ========================

    private NoticeResponse toNoticeResponse(SupportNotice notice) {
        return new NoticeResponse(
                notice.getNoticeId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getNoticeType(),
                notice.getIsPinned(),
                notice.getSortOrder(),
                notice.getPublishedAt(),
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

    private ProfanityResponse toProfanityResponse(SupportProfanity profanity) {
        return new ProfanityResponse(
                profanity.getProfanityId(),
                profanity.getWord(),
                profanity.getSeverity(),
                profanity.getNote(),
                profanity.getCreatedAt()
        );
    }

}
