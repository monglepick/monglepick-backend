package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.ContentDto.PostResponse;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.PostUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.ReportActionRequest;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.ReportResponse;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.ReviewResponse;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.ToxicityActionRequest;
import com.monglepick.monglepickbackend.admin.dto.ContentDto.ToxicityResponse;
import com.monglepick.monglepickbackend.admin.repository.AdminReportRepository;
import com.monglepick.monglepickbackend.domain.content.mapper.ContentMapper;
import com.monglepick.monglepickbackend.domain.community.entity.Post;
import com.monglepick.monglepickbackend.domain.community.entity.PostDeclaration;
import com.monglepick.monglepickbackend.domain.community.entity.PostStatus;
import com.monglepick.monglepickbackend.domain.community.mapper.PostMapper;
import com.monglepick.monglepickbackend.domain.content.entity.ToxicityLog;
import com.monglepick.monglepickbackend.domain.review.entity.Review;
import com.monglepick.monglepickbackend.domain.review.mapper.ReviewMapper;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 콘텐츠 관리 서비스.
 *
 * <p>신고 처리, 혐오표현 조치, 게시글·리뷰 관리 비즈니스 로직을 담당한다.</p>
 *
 * <h3>담당 기능</h3>
 * <ol>
 *   <li>신고 목록 조회 / 신고 조치 (blind·delete·dismiss)</li>
 *   <li>혐오표현 로그 목록 조회 / 혐오표현 조치 (restore·delete·warn)</li>
 *   <li>게시글 목록 조회 (키워드·카테고리·상태 필터) / 수정 / 소프트 삭제</li>
 *   <li>리뷰 목록 조회 (영화 ID·최소 평점 필터) / 소프트 삭제</li>
 * </ol>
 *
 * <h3>트랜잭션 전략</h3>
 * <ul>
 *   <li>클래스 레벨: {@code @Transactional(readOnly=true)} — 모든 조회 메서드 기본 적용</li>
 *   <li>쓰기 메서드: {@code @Transactional} 개별 오버라이드</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContentService {

    /** 신고 내역 조회 리포지토리 (관리자 전용) */
    private final AdminReportRepository adminReportRepository;

    /** 콘텐츠 통합 Mapper — AdminToxicityLogRepository 폐기, ContentMapper로 일원화 (§15) */
    private final ContentMapper contentMapper;

    /** 게시글 Mapper — AdminPostRepository 폐기, 모든 조회·수정은 PostMapper로 일원화 (설계서 §15) */
    private final PostMapper postMapper;

    /** 리뷰 Mapper — AdminReviewRepository + ReviewRepository 폐기, 모든 리뷰 쿼리 일원화 (§15) */
    private final ReviewMapper reviewMapper;

    // ─────────────────────────────────────────────
    // 신고(Report) 관리
    // ─────────────────────────────────────────────

    /**
     * 신고 목록을 조회한다.
     *
     * <p>{@code status}가 null 또는 빈 문자열이면 전체 신고를 반환하고,
     * 값이 있으면 해당 처리 상태의 신고만 필터링하여 반환한다.</p>
     *
     * <p>targetPreview: 신고 대상 게시글 제목을 조회해서 미리보기로 제공한다.
     * 게시글이 삭제된 경우 "(삭제된 게시글)"로 대체한다.</p>
     *
     * @param status   처리 상태 필터 (pending/reviewed/resolved/dismissed, null이면 전체)
     * @param pageable 페이지 정보
     * @return 신고 목록 페이지
     */
    public Page<ReportResponse> getReports(String status, Pageable pageable) {
        // status 필터 유무에 따라 분기 조회
        Page<PostDeclaration> page = (status == null || status.isBlank())
                ? adminReportRepository.findAllByOrderByCreatedAtDesc(pageable)
                : adminReportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);

        return page.map(declaration -> {
            // 신고 대상 게시글 제목을 미리보기로 조회 (없으면 대체 문구) — MyBatis, null fallback
            Post target = postMapper.findById(declaration.getPostId());
            String targetPreview = (target != null) ? target.getTitle() : "(삭제된 게시글)";

            return new ReportResponse(
                    declaration.getPostDeclarationId(),
                    declaration.getTargetType(),
                    declaration.getPostId(),
                    targetPreview,
                    declaration.getDeclarationContent(),
                    declaration.getToxicityScore(),
                    declaration.getStatus(),
                    declaration.getUserId(),
                    declaration.getReportedUserId(),
                    declaration.getCreatedAt()
            );
        });
    }

    /**
     * 신고 건에 대해 조치를 처리한다.
     *
     * <h3>action별 처리 흐름</h3>
     * <ul>
     *   <li>"blind"   — 대상 게시글 블라인드({@code Post.blind()}) + 신고 상태 "reviewed" 갱신</li>
     *   <li>"delete"  — 대상 게시글 소프트 삭제({@code Post.softDelete()}) + 신고 상태 "reviewed" 갱신</li>
     *   <li>"dismiss" — 게시글 미처리, 신고 상태 "dismissed" 갱신 (기각)</li>
     * </ul>
     *
     * <p>PostDeclaration에는 status 필드를 직접 변경하는 도메인 메서드가 없으므로
     * 새 인스턴스를 빌더로 재생성하지 않고 리플렉션 대신 {@code @AllArgsConstructor}를
     * 활용하지 않는다. 대신 리포지토리 save 시 변경 감지(dirty checking)가 동작하도록
     * 엔티티에 {@code updateStatus()} 도메인 메서드가 필요하다.
     * 현재 엔티티에 해당 메서드가 없으므로 임시로 새 엔티티를 저장하는 방식 대신
     * JPQL UPDATE를 사용하지 않고, 아래 updateDeclarationStatus() 내부 헬퍼를 통해
     * 리플렉션 없이 @Builder 재생성 후 ID 유지 방식으로 처리한다.</p>
     *
     * <p><b>주의:</b> PostDeclaration 엔티티에 status 변경 도메인 메서드(updateStatus)가
     * 추가되면 해당 메서드를 직접 호출하는 방식으로 리팩터링을 권장한다.</p>
     *
     * @param reportId 신고 ID (post_declaration_id)
     * @param request  조치 요청 DTO (action: blind/delete/dismiss)
     * @throws BusinessException 신고 레코드가 존재하지 않는 경우 (POST_NOT_FOUND)
     * @throws BusinessException 유효하지 않은 action 값인 경우 (INVALID_INPUT)
     */
    @Transactional
    public void processReport(Long reportId, ReportActionRequest request) {
        // 신고 레코드 조회 — 없으면 404
        PostDeclaration declaration = adminReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND, "신고 ID " + reportId + "를 찾을 수 없습니다"));

        String action = request.action();
        log.info("[관리자] 신고 조치 요청 — reportId={}, action={}", reportId, action);

        switch (action) {
            case "blind" -> {
                // 대상 게시글 블라인드 처리 (MyBatis: 도메인 메서드 후 명시 UPDATE)
                Post postToBlind = postMapper.findById(declaration.getPostId());
                if (postToBlind != null) {
                    postToBlind.blind();
                    postMapper.updateAdminStatus(postToBlind);
                    log.info("[관리자] 게시글 블라인드 처리 — postId={}", postToBlind.getPostId());
                } else {
                    log.warn("[관리자] 블라인드 대상 게시글 없음 — postId={}", declaration.getPostId());
                }
                // 신고 상태 갱신
                saveDeclarationWithNewStatus(declaration, "reviewed");
            }
            case "delete" -> {
                // 대상 게시글 소프트 삭제 (MyBatis: 도메인 메서드 후 명시 UPDATE)
                Post postToDelete = postMapper.findById(declaration.getPostId());
                if (postToDelete != null) {
                    postToDelete.softDelete();
                    postMapper.updateAdminStatus(postToDelete);
                    log.info("[관리자] 게시글 소프트 삭제 — postId={}", postToDelete.getPostId());
                } else {
                    log.warn("[관리자] 삭제 대상 게시글 없음 — postId={}", declaration.getPostId());
                }
                // 신고 상태 갱신
                saveDeclarationWithNewStatus(declaration, "reviewed");
            }
            case "dismiss" -> {
                // 게시글 미처리, 신고만 기각
                saveDeclarationWithNewStatus(declaration, "dismissed");
                log.info("[관리자] 신고 기각 — reportId={}", reportId);
            }
            default -> throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "유효하지 않은 action 값입니다: '" + action + "'. 허용 값: blind, delete, dismiss");
        }
    }

    /**
     * PostDeclaration의 status를 갱신하여 저장한다.
     *
     * <p>PostDeclaration 엔티티에 status 변경 도메인 메서드가 없으므로
     * @Builder를 이용해 status만 교체한 새 인스턴트를 저장한다.
     * save()는 postDeclarationId가 있으면 merge(UPDATE)로 동작한다.</p>
     *
     * @param original  원본 PostDeclaration 엔티티
     * @param newStatus 변경할 상태 문자열 (reviewed/dismissed)
     */
    private void saveDeclarationWithNewStatus(PostDeclaration original, String newStatus) {
        PostDeclaration updated = PostDeclaration.builder()
                .postDeclarationId(original.getPostDeclarationId())   // PK 유지 → UPDATE
                .postId(original.getPostId())
                .categoryId(original.getCategoryId())
                .userId(original.getUserId())
                .reportedUserId(original.getReportedUserId())
                .targetType(original.getTargetType())
                .declarationContent(original.getDeclarationContent())
                .toxicityScore(original.getToxicityScore())
                .status(newStatus)
                .build();
        adminReportRepository.save(updated);
    }

    // ─────────────────────────────────────────────
    // 혐오표현(Toxicity) 관리
    // ─────────────────────────────────────────────

    /**
     * 혐오표현 로그 목록을 조회한다.
     *
     * <p>{@code minScore}가 null이면 전체 로그를 반환하고,
     * 값이 있으면 해당 독성 점수 이상의 로그만 필터링하여 반환한다.</p>
     *
     * @param minScore 최소 독성 점수 필터 (0.0~1.0, null이면 전체)
     * @param pageable 페이지 정보
     * @return 혐오표현 로그 목록 페이지
     */
    public Page<ToxicityResponse> getToxicityLogs(Double minScore, Pageable pageable) {
        // MyBatis: minScore는 Float으로 변환 후 전달 (null이면 필터 미적용)
        Float minScoreFloat = (minScore != null) ? minScore.floatValue() : null;
        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        List<ToxicityLog> logs = contentMapper.findAllToxicityLogs(minScoreFloat, offset, limit);
        long total = contentMapper.countAllToxicityLogs(minScoreFloat);

        List<ToxicityResponse> content = logs.stream()
                .map(item -> new ToxicityResponse(
                        item.getToxicityLogId(),
                        item.getContentType(),
                        item.getContentId(),
                        item.getUserId(),
                        item.getDetectedWords(),
                        item.getToxicityScore(),
                        item.getSeverity(),
                        item.getActionTaken(),
                        item.getProcessedAt(),
                        item.getCreatedAt()
                ))
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 혐오표현 로그에 조치를 처리한다.
     *
     * <p>{@link ToxicityLog#processAction(String)}을 호출하여
     * actionTaken과 processedAt을 한 번에 기록한다.</p>
     *
     * <h3>action→actionTaken 매핑</h3>
     * <ul>
     *   <li>"restore" → "NONE"   (콘텐츠 복원 — 별도 조치 없음으로 기록)</li>
     *   <li>"delete"  → "DELETE" (콘텐츠 삭제)</li>
     *   <li>"warn"    → "WARN"   (작성자 경고)</li>
     * </ul>
     *
     * @param toxicityLogId 혐오표현 로그 ID
     * @param request       조치 요청 DTO (action: restore/delete/warn)
     * @throws BusinessException 로그 레코드가 존재하지 않는 경우 (POST_NOT_FOUND 재활용)
     * @throws BusinessException 유효하지 않은 action 값인 경우 (INVALID_INPUT)
     */
    @Transactional
    public void processToxicity(Long toxicityLogId, ToxicityActionRequest request) {
        // 혐오표현 로그 조회 — MyBatis, null → 404
        ToxicityLog toxicityLog = contentMapper.findToxicityLogById(toxicityLogId);
        if (toxicityLog == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "혐오표현 로그 ID " + toxicityLogId + "를 찾을 수 없습니다");
        }

        String action = request.action();
        log.info("[관리자] 혐오표현 조치 요청 — toxicityLogId={}, action={}", toxicityLogId, action);

        // action 문자열을 ToxicityLog의 actionTaken 유효값으로 매핑
        String actionTaken = switch (action) {
            case "restore" -> "NONE";
            case "delete"  -> "DELETE";
            case "warn"    -> "WARN";
            default -> throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "유효하지 않은 action 값입니다: '" + action + "'. 허용 값: restore, delete, warn");
        };

        // ContentMapper.processAction — actionTaken + processedAt 동시 UPDATE (§15)
        contentMapper.processAction(toxicityLogId, actionTaken);
        log.info("[관리자] 혐오표현 조치 완료 — toxicityLogId={}, actionTaken={}", toxicityLogId, actionTaken);
    }

    // ─────────────────────────────────────────────
    // 게시글(Post) 관리
    // ─────────────────────────────────────────────

    /**
     * 게시글 목록을 키워드·카테고리·상태 조합 필터로 조회한다.
     *
     * <p>각 파라미터가 null 또는 빈 문자열이면 해당 조건을 무시한다.</p>
     *
     * @param keyword  검색어 (제목·본문 LIKE, null이면 전체)
     * @param category 카테고리 문자열 (FREE/DISCUSSION/RECOMMENDATION/NEWS, null이면 전체)
     * @param status   게시 상태 문자열 (DRAFT/PUBLISHED, null이면 전체)
     * @param pageable 페이지 정보
     * @return 게시글 목록 페이지
     */
    public Page<PostResponse> getPosts(String keyword, String category, String status,
                                       Pageable pageable) {
        // 빈 문자열은 null로 통일하여 MyBatis <if> 동적 필터 조건 적용
        String keywordParam  = (keyword  != null && !keyword.isBlank())  ? keyword  : null;
        String categoryParam = (category != null && !category.isBlank()) ? category : null;
        String statusParam   = (status   != null && !status.isBlank())   ? status   : null;

        // 문자열 → enum 정규화 (MyBatis에는 enum.name() 문자열 전달)
        String categoryStr = (categoryParam != null)
                ? Post.Category.fromValue(categoryParam).name() : null;
        String statusStr = (statusParam != null)
                ? PostStatus.valueOf(statusParam.toUpperCase()).name() : null;

        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        List<Post> posts = postMapper.searchAdminPosts(keywordParam, categoryStr, statusStr, offset, limit);
        long total = postMapper.countAdminPosts(keywordParam, categoryStr, statusStr);

        List<PostResponse> content = posts.stream()
                .map(post -> new PostResponse(
                        post.getPostId(),
                        post.getUserId(),   // String FK 직접 보관 (§15.4)
                        post.getTitle(),
                        post.getContent(),
                        post.getCategory().name(),
                        post.getViewCount(),
                        post.getLikeCount(),
                        post.getCommentCount(),
                        post.isDeleted(),
                        post.isBlinded(),
                        post.getStatus().name(),
                        post.getCreatedAt()
                ))
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 관리자가 게시글 제목·본문·카테고리를 수정한다.
     *
     * <p>{@link Post#update(String, Category)}를 호출하여 변경 감지(dirty checking)로 저장한다.
     * null 파라미터는 기존 값을 유지한다.</p>
     *
     * @param postId  수정 대상 게시글 ID
     * @param request 수정 요청 DTO (title/content/category/editReason)
     * @return 수정된 게시글 응답 DTO
     * @throws BusinessException 게시글이 존재하지 않는 경우 (POST_NOT_FOUND)
     */
    @Transactional
    public PostResponse updatePost(Long postId, PostUpdateRequest request) {
        // 게시글 조회 — MyBatis, null → 404
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND, "게시글 ID " + postId + "를 찾을 수 없습니다");
        }

        // null 필드는 기존 값 유지 (부분 수정 지원)
        String newTitle    = (request.title()    != null) ? request.title()    : post.getTitle();
        String newContent  = (request.content()  != null) ? request.content()  : post.getContent();
        Post.Category newCategory = (request.category() != null)
                ? Post.Category.fromValue(request.category())
                : post.getCategory();

        // 도메인 메서드로 변경 후 MyBatis UPDATE 명시 호출 (dirty checking 미지원)
        post.update(newTitle, newContent, newCategory);
        postMapper.update(post);

        log.info("[관리자] 게시글 수정 — postId={}, editReason={}", postId, request.editReason());

        return new PostResponse(
                post.getPostId(),
                post.getUserId(),   // String FK 직접 보관 (JPA/MyBatis 하이브리드 §15.4)
                post.getTitle(),
                post.getContent(),
                post.getCategory().name(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.isDeleted(),
                post.isBlinded(),
                post.getStatus().name(),
                post.getCreatedAt()
        );
    }

    /**
     * 관리자가 게시글을 소프트 삭제한다.
     *
     * <p>{@link Post#softDelete()}를 호출하여 is_deleted=true, deleted_at=now()를 기록한다.
     * 실제 DB에서 행을 제거하지 않으며, 30일 후 스케줄러가 물리 삭제한다.</p>
     *
     * @param postId 삭제 대상 게시글 ID
     * @throws BusinessException 게시글이 존재하지 않는 경우 (POST_NOT_FOUND)
     */
    @Transactional
    public void deletePost(Long postId) {
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND, "게시글 ID " + postId + "를 찾을 수 없습니다");
        }

        // 도메인 메서드 + 명시 UPDATE (MyBatis §15)
        post.softDelete();
        postMapper.updateAdminStatus(post);

        log.info("[관리자] 게시글 소프트 삭제 — postId={}", postId);
    }

    // ─────────────────────────────────────────────
    // 리뷰(Review) 관리
    // ─────────────────────────────────────────────

    /**
     * 리뷰 목록을 영화 ID·최소 평점 필터로 조회한다.
     *
     * <p>필터 조합:
     * <ul>
     *   <li>movieId만 있음 → 해당 영화의 전체 리뷰 페이징</li>
     *   <li>movieId + minRating → 해당 영화의 평점 이상 리뷰</li>
     *   <li>movieId=null, minRating=null → 전체 리뷰 최신순</li>
     * </ul>
     * ReviewRepository에 복합 필터 메서드가 없으므로 movieId 단독 필터는 기존 메서드를,
     * 전체 조회는 findAll(Pageable)을 활용하고 minRating 필터는 Java 스트림으로 후처리한다.</p>
     *
     * <p><b>성능 주의:</b> minRating 필터는 현재 인메모리 필터링이므로,
     * 데이터가 많아지면 JPQL 쿼리로 전환을 권장한다.</p>
     *
     * @param movieId   영화 ID 필터 (null이면 전체 영화)
     * @param minRating 최소 평점 필터 (null이면 무제한)
     * @param pageable  페이지 정보
     * @return 리뷰 목록 페이지
     */
    public Page<ReviewResponse> getReviews(String movieId, Double minRating, Pageable pageable) {
        // 빈 문자열은 null로 통일하여 MyBatis <if> 동적 필터 조건 적용
        String movieIdParam = (movieId != null && !movieId.isBlank()) ? movieId : null;

        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        List<Review> reviews = reviewMapper.searchAdminReviews(movieIdParam, minRating, offset, limit);
        long total = reviewMapper.countAdminReviews(movieIdParam, minRating);

        List<ReviewResponse> content = reviews.stream()
                .map(review -> new ReviewResponse(
                        review.getReviewId(),
                        review.getUserId(),   // String FK 직접 보관 (§15.4)
                        review.getMovieId(),
                        review.getRating(),
                        review.getContent(),
                        review.isDeleted(),
                        review.isBlinded(),
                        review.isSpoiler(),
                        review.getLikeCount(),
                        review.getCreatedAt()
                ))
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 관리자가 리뷰를 소프트 삭제한다.
     *
     * <p>{@link Review#softDelete()}를 호출하여 is_deleted=true로 표시한다.</p>
     *
     * @param reviewId 삭제 대상 리뷰 ID
     * @throws BusinessException 리뷰가 존재하지 않는 경우 (INVALID_INPUT 활용)
     */
    @Transactional
    public void deleteReview(Long reviewId) {
        Review review = reviewMapper.findById(reviewId);
        if (review == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "리뷰 ID " + reviewId + "를 찾을 수 없습니다");
        }

        // MyBatis softDelete 쿼리 직접 호출
        reviewMapper.softDelete(reviewId);

        log.info("[관리자] 리뷰 소프트 삭제 — reviewId={}", reviewId);
    }
}
