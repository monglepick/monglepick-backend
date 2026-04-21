package com.monglepick.monglepickbackend.admin.dto;

import java.time.LocalDateTime;

/**
 * 콘텐츠 관리 Admin API DTO 모음.
 *
 * <p>신고 처리, 혐오표현 조치, 게시글/리뷰 관리에 필요한
 * 요청(Request) 및 응답(Response) record를 모두 이 클래스 안에 내부 record로 정의한다.</p>
 *
 * <h3>포함 DTO 목록</h3>
 * <ul>
 *   <li>{@link ReportResponse}      — 신고 목록 조회 응답</li>
 *   <li>{@link ReportActionRequest} — 신고 조치 요청</li>
 *   <li>{@link ToxicityResponse}    — 혐오표현 로그 목록 응답</li>
 *   <li>{@link ToxicityActionRequest} — 혐오표현 조치 요청</li>
 *   <li>{@link PostResponse}        — 게시글 목록 조회 응답</li>
 *   <li>{@link PostUpdateRequest}   — 게시글 수정 요청</li>
 *   <li>{@link ReviewResponse}      — 리뷰 목록 조회 응답</li>
 * </ul>
 */
public class ContentDto {

    // ─────────────────────────────────────────────
    // 신고(Report) DTO
    // ─────────────────────────────────────────────

    /**
     * 신고 목록 단일 항목 응답 DTO.
     *
     * <p>PostDeclaration 엔티티의 주요 필드를 포함하여
     * 관리자 화면에서 신고 내역을 표시하는 데 사용한다.</p>
     *
     * @param id                 신고 레코드 고유 ID (post_declaration_id)
     * @param targetType         신고 대상 유형 ("post" 또는 "comment")
     * @param postId             신고 대상 게시글 ID
     * @param targetPreview      신고 대상 콘텐츠 미리보기 (서비스 레이어에서 조회하여 채움, 최대 100자)
     * @param declarationContent 신고자가 작성한 신고 사유
     * @param toxicityScore      AI 독성 분석 점수 (0.0~1.0, null 가능)
     * @param status             처리 상태 (pending/reviewed/resolved/dismissed)
     * @param userId             신고자 사용자 ID
     * @param reportedUserId     피신고자(게시글 작성자) 사용자 ID
     * @param createdAt          신고 접수 시각
     */
    public record ReportResponse(
            Long id,
            String targetType,
            Long postId,
            String targetPreview,
            String declarationContent,
            Float toxicityScore,
            String status,
            String userId,
            String reportedUserId,
            LocalDateTime createdAt
    ) {}

    /**
     * 신고 조치 요청 DTO.
     *
     * <p>관리자가 신고 건에 대해 취할 조치를 지정한다.</p>
     *
     * @param action 조치 코드:
     *               <ul>
     *                 <li>"blind"   — 대상 게시글 블라인드 처리 + 신고 상태 reviewed</li>
     *                 <li>"delete"  — 대상 게시글 소프트 삭제 + 신고 상태 reviewed</li>
     *                 <li>"dismiss" — 신고 기각 (게시글 미처리) + 신고 상태 dismissed</li>
     *               </ul>
     */
    public record ReportActionRequest(
            String action
    ) {}

    // ─────────────────────────────────────────────
    // 혐오표현(Toxicity) DTO
    // ─────────────────────────────────────────────

    /**
     * 혐오표현 로그 목록 단일 항목 응답 DTO.
     *
     * <p>ToxicityLog 엔티티의 실제 필드명 기준으로 작성됨:
     * toxicityLogId, contentType, contentId, userId, detectedWords,
     * toxicityScore, severity, actionTaken, processedAt</p>
     *
     * @param id             혐오표현 로그 고유 ID (toxicity_log_id)
     * @param contentType    감지된 콘텐츠 유형 (POST/COMMENT/REVIEW/CHAT)
     * @param contentId      감지된 콘텐츠 레코드 ID (해당 테이블 PK)
     * @param userId         콘텐츠 작성자 사용자 ID (nullable)
     * @param detectedWords  감지된 유해 단어 목록 (JSON 배열 문자열, nullable)
     * @param toxicityScore  AI 유해성 점수 (0.0~1.0, nullable)
     * @param severity       심각도 레벨 (LOW/MEDIUM/HIGH/CRITICAL)
     * @param actionTaken    취해진 조치 (NONE/WARN/BLIND/DELETE, nullable=미처리)
     * @param processedAt    조치 처리 시각 (nullable=미처리)
     * @param createdAt      로그 생성 시각
     * @param inputText      관리자 화면 미리보기용 원문 텍스트
     * @param toxicityType   관리자 화면 표시용 독성 유형/등급 문자열
     * @param targetType     관리자 화면 표시용 대상 유형 (contentType과 동일)
     */
    public record ToxicityResponse(
            Long id,
            String contentType,
            Long contentId,
            String userId,
            String detectedWords,
            Float toxicityScore,
            String severity,
            String actionTaken,
            LocalDateTime processedAt,
            LocalDateTime createdAt,
            String inputText,
            String toxicityType,
            String targetType
    ) {}

    /**
     * 혐오표현 조치 요청 DTO.
     *
     * <p>관리자가 혐오표현 로그에 대해 취할 조치를 지정한다.</p>
     *
     * @param action 조치 코드:
     *               <ul>
     *                 <li>"restore" — 원본 콘텐츠 복원 (NONE으로 기록)</li>
     *                 <li>"delete"  — 콘텐츠 삭제 (DELETE로 기록)</li>
     *                 <li>"warn"    — 작성자 경고 (WARN으로 기록)</li>
     *               </ul>
     */
    public record ToxicityActionRequest(
            String action
    ) {}

    // ─────────────────────────────────────────────
    // 게시글(Post) DTO
    // ─────────────────────────────────────────────

    /**
     * 게시글 목록 단일 항목 응답 DTO.
     *
     * <p>Post 엔티티의 실제 필드 기준:
     * postId, user(→userId), title, content, category(→문자열),
     * viewCount, likeCount, commentCount, isDeleted, isBlinded, status(→문자열), createdAt</p>
     *
     * @param postId       게시글 고유 ID
     * @param userId       작성자 사용자 ID
     * @param title        게시글 제목
     * @param content      게시글 본문 (관리자 화면 표시용, 전체 내용)
     * @param category     카테고리 문자열 (FREE/DISCUSSION/RECOMMENDATION/NEWS)
     * @param viewCount    조회수
     * @param likeCount    좋아요 수
     * @param commentCount 댓글 수
     * @param isDeleted    소프트 삭제 여부
     * @param isBlinded    신고 블라인드 여부
     * @param status       게시글 상태 (DRAFT/PUBLISHED)
     * @param createdAt    작성 시각
     */
    public record PostResponse(
            Long postId,
            String userId,
            String title,
            String content,
            String category,
            int viewCount,
            int likeCount,
            int commentCount,
            boolean isDeleted,
            boolean isBlinded,
            String status,
            LocalDateTime createdAt
    ) {}

    /**
     * 게시글 수정 요청 DTO (관리자).
     *
     * <p>관리자가 게시글의 제목·본문·카테고리를 직접 수정할 때 사용한다.
     * editReason은 관리자 내부 수정 사유로 현재 버전에서는 로그로만 기록한다.</p>
     *
     * @param title      수정할 제목 (null이면 미수정)
     * @param content    수정할 본문 (null이면 미수정)
     * @param category   수정할 카테고리 문자열 (null이면 미수정)
     * @param editReason 수정 사유 (관리자 내부 기록용, nullable)
     */
    public record PostUpdateRequest(
            String title,
            String content,
            String category,
            String editReason
    ) {}

    // ─────────────────────────────────────────────
    // 리뷰(Review) DTO
    // ─────────────────────────────────────────────

    /**
     * 리뷰 목록 단일 항목 응답 DTO.
     *
     * <p>Review 엔티티의 실제 필드 기준:
     * reviewId, user(→userId), movieId, rating, content,
     * isDeleted, isBlinded, spoiler, likeCount, reviewSource, reviewCategoryCode, createdAt</p>
     *
     * @param reviewId           리뷰 고유 ID
     * @param userId             작성자 사용자 ID
     * @param movieId            대상 영화 ID (movies.movie_id VARCHAR(50))
     * @param rating             평점 (1.0~5.0, 0.5 단위)
     * @param content            리뷰 본문
     * @param isDeleted          소프트 삭제 여부
     * @param isBlinded          신고 블라인드 여부
     * @param spoiler            스포일러 포함 여부
     * @param likeCount          좋아요 수 (비정규화)
     * @param reviewSource       리뷰 작성 출처(참조 ID, 예: chat_ses_001 / cup_mch_005, nullable)
     * @param reviewCategoryCode 작성 카테고리 enum 이름 (THEATER_RECEIPT/COURSE/WORLDCUP/WISHLIST/AI_RECOMMEND/PLAYLIST, nullable)
     * @param createdAt          작성 시각
     */
    public record ReviewResponse(
            Long reviewId,
            String userId,
            String movieId,
            Double rating,
            String content,
            boolean isDeleted,
            boolean isBlinded,
            boolean spoiler,
            int likeCount,
            String reviewSource,
            String reviewCategoryCode,
            LocalDateTime createdAt
    ) {}
}
