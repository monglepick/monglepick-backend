package com.monglepick.monglepickbackend.domain.roadmap.entity;

/* BaseAuditEntity 상속 — created_at/updated_at/created_by/updated_by 자동 관리 */
import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 도장깨기 인증 엔티티 — course_verification 테이블 매핑.
 *
 * <p>사용자가 도장깨기 코스({@code roadmap_courses}) 내 개별 영화를 실제로 시청했는지
 * 인증한 기록을 저장한다. 퀴즈 정답, 이미지 인증, 리뷰 작성 등 다양한 인증 방식을 지원한다.</p>
 *
 * <h3>인증 흐름</h3>
 * <pre>
 * 영화 시청 → 인증 요청(verificationType) → AI 검증(aiConfidence) → isVerified=true → verifiedAt 기록
 * </pre>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId}           — 인증 요청 사용자 ID</li>
 *   <li>{@code courseId}         — 인증이 속한 코스 ID (roadmap_courses.course_id)</li>
 *   <li>{@code movieId}          — 인증 대상 영화 ID (movies.movie_id)</li>
 *   <li>{@code verificationType} — 인증 방식 (QUIZ / IMAGE / REVIEW)</li>
 *   <li>{@code isVerified}       — 인증 완료 여부 (기본값: false)</li>
 *   <li>{@code aiConfidence}     — AI 인증 종합 신뢰도 점수 (0.0~1.0, nullable)</li>
 *   <li>{@code verifiedAt}       — 인증 완료 시각 (nullable)</li>
 * </ul>
 *
 * <h3>리뷰 인증 전용 필드 (2026-04-14 추가)</h3>
 * <p>{@code verificationType=REVIEW} 에서 AI 에이전트(추후 개발)가 "작성된 리뷰가 영화 줄거리와
 * 충분히 유사하여 실제 관람으로 볼 수 있는가"를 판정하고 관리자가 모니터링/오버라이드할 수 있도록
 * 추가된 메타데이터 필드. QUIZ/IMAGE 방식에서는 모두 {@code null} 로 둔다.</p>
 * <ul>
 *   <li>{@code similarityScore} — 영화 줄거리 ↔ 리뷰 본문 유사도 (0.0~1.0, 임베딩/BM25 기반)</li>
 *   <li>{@code matchedKeywords} — 공통으로 등장한 핵심 키워드 JSON 배열 문자열</li>
 *   <li>{@code reviewStatus}    — 리뷰 인증 세부 상태 (PENDING/AUTO_VERIFIED/NEEDS_REVIEW/AUTO_REJECTED/ADMIN_APPROVED/ADMIN_REJECTED)</li>
 *   <li>{@code decisionReason}  — AI/관리자 판정 사유 (운영 감사 목적)</li>
 *   <li>{@code reviewedBy}      — 관리자가 수동 판정한 경우 그 사용자 ID (NULL 이면 AI 자동)</li>
 *   <li>{@code reviewedAt}      — 판정 시각 (AI 또는 관리자)</li>
 * </ul>
 *
 * <h3>인증 방식 (verificationType)</h3>
 * <ul>
 *   <li>{@code QUIZ}   — 퀴즈 정답으로 시청 인증</li>
 *   <li>{@code IMAGE}  — 화면 캡처 이미지로 시청 인증 (AI 분석)</li>
 *   <li>{@code REVIEW} — 리뷰 작성으로 시청 인증</li>
 * </ul>
 *
 * <h3>제약 조건</h3>
 * <ul>
 *   <li>UNIQUE(user_id, course_id, movie_id) — 동일 사용자가 동일 코스의 동일 영화를 중복 인증 불가</li>
 * </ul>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>courseId는 roadmap_courses 테이블의 slug 형태 course_id(VARCHAR(50))를 참조한다.</li>
 *   <li>movieId는 movies 테이블의 movie_id(VARCHAR(50))를 참조한다.</li>
 *   <li>FK는 {@code @Column}으로만 선언 (JPA @ManyToOne 미사용 — 프로젝트 컨벤션).</li>
 *   <li>IMAGE 인증의 경우 AI Agent가 aiConfidence를 계산하여 설정하며,
 *       임계값(예: 0.8 이상) 초과 시 서비스 레이어에서 isVerified=true로 전환한다.</li>
 * </ul>
 */
@Entity
@Table(
        name = "course_verification",
        uniqueConstraints = {
                /* 동일 사용자가 동일 코스의 동일 영화를 중복 인증 불가 */
                @UniqueConstraint(
                        name = "uk_course_verification_user_course_movie",
                        columnNames = {"user_id", "course_id", "movie_id"}
                )
        },
        indexes = {
                /* 사용자별 코스 인증 진행률 조회 최적화 */
                @Index(name = "idx_course_verification_user_course", columnList = "user_id, course_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class CourseVerification extends BaseAuditEntity {

    /**
     * 도장깨기 인증 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    /**
     * 인증 요청 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 인증이 속한 코스 ID (VARCHAR(50), NOT NULL).
     * roadmap_courses.course_id (slug 형태, 예: "nolan-filmography")를 참조한다.
     * FK는 @Column으로만 선언 (프로젝트 컨벤션: @ManyToOne 미사용).
     */
    @Column(name = "course_id", length = 50, nullable = false)
    private String courseId;

    /**
     * 인증 대상 영화 ID (VARCHAR(50), NOT NULL).
     * movies.movie_id를 참조한다.
     */
    @Column(name = "movie_id", length = 50, nullable = false)
    private String movieId;

    /**
     * 인증 방식 (VARCHAR(30), NOT NULL).
     * <ul>
     *   <li>"QUIZ"   — 퀴즈 정답으로 시청 인증</li>
     *   <li>"IMAGE"  — 화면 캡처 이미지로 시청 인증 (AI 분석)</li>
     *   <li>"REVIEW" — 리뷰 작성으로 시청 인증</li>
     * </ul>
     */
    @Column(name = "verification_type", length = 30, nullable = false)
    private String verificationType;

    /**
     * 인증 완료 여부 (기본값: false).
     * AI 또는 시스템 검증 통과 후 true로 전환된다.
     * isVerified=true가 되어야 코스 진행률에 반영된다.
     */
    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * AI 인증 신뢰도 점수 (nullable, 0.0~1.0).
     * IMAGE 인증 방식에서 AI Agent가 계산한 신뢰도 점수.
     * QUIZ/REVIEW 방식에서는 null이거나 1.0으로 설정된다.
     */
    @Column(name = "ai_confidence")
    private Float aiConfidence;

    /**
     * 인증 완료 시각 (nullable).
     * isVerified가 false인 동안은 null이며,
     * true로 전환될 때 현재 시각이 기록된다.
     */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // ─────────────────────────────────────────────
    // 리뷰 인증(REVIEW) 전용 메타데이터 — 2026-04-14 추가
    // AI 리뷰 검증 에이전트(추후 개발)와 관리자 모니터링 UI 가 함께 사용한다.
    // QUIZ/IMAGE 방식에서는 모두 null 로 저장된다.
    // ─────────────────────────────────────────────

    /**
     * 영화 줄거리 ↔ 리뷰 본문 유사도 점수 (0.0~1.0, nullable).
     *
     * <p>AI 에이전트가 영화의 plot synopsis 와 사용자가 작성한 리뷰 본문을 비교하여
     * 계산한 유사도(임베딩 코사인 / BM25 / 키워드 매칭 가중합 중 하나).
     * {@link #aiConfidence} 가 "최종 판정에 쓰인 종합 신뢰도"라면 본 필드는 "원시 유사도 성분"
     * 으로, 관리자 화면에서 디버깅 목적으로 별도 노출한다.</p>
     */
    @Column(name = "similarity_score")
    private Float similarityScore;

    /**
     * 영화 줄거리와 리뷰에서 공통으로 발견된 핵심 키워드 JSON 배열 문자열 (nullable).
     *
     * <p>예: {@code "[\"탑건\",\"공군\",\"매버릭\",\"공중전\"]"}.
     * 관리자 화면에서 "왜 이 리뷰가 통과/반려되었는가" 근거를 한눈에 보여주는 용도.
     * 저장 크기/인덱스 부담을 최소화하기 위해 TEXT 1 컬럼에 JSON 직렬화 형태로 보관한다.</p>
     */
    @Column(name = "matched_keywords", columnDefinition = "TEXT")
    private String matchedKeywords;

    /**
     * 리뷰 인증 세부 상태 (VARCHAR(30), 기본값 "PENDING").
     *
     * <p>{@link #isVerified} 만으로는 "AI 가 아직 판정 전"인지, "낮은 점수로 자동 반려"인지,
     * "관리자가 수동 승인"했는지 구분할 수 없어 운영 검수에 필요한 세부 상태를 별도로 보관한다.</p>
     *
     * <h4>허용 값</h4>
     * <ul>
     *   <li>{@code PENDING}         — AI 에이전트 판정 대기</li>
     *   <li>{@code AUTO_VERIFIED}   — AI 가 고신뢰도로 자동 승인 (isVerified=true)</li>
     *   <li>{@code NEEDS_REVIEW}    — AI 신뢰도 중간 — 관리자 검수 필요</li>
     *   <li>{@code AUTO_REJECTED}   — AI 가 저신뢰도로 자동 반려 (isVerified=false)</li>
     *   <li>{@code ADMIN_APPROVED}  — 관리자가 수동 승인 (isVerified=true)</li>
     *   <li>{@code ADMIN_REJECTED}  — 관리자가 수동 반려 (isVerified=false)</li>
     * </ul>
     */
    @Column(name = "review_status", length = 30)
    @Builder.Default
    private String reviewStatus = "PENDING";

    /**
     * 판정 사유 (VARCHAR(500), nullable).
     *
     * <p>AI 가 자동 판정한 경우: "유사도 0.82 + 핵심 키워드 6개 일치로 승인" 같은 요약.
     * 관리자가 수동 오버라이드한 경우: "리뷰가 타 영화 내용이라 반려" 같은 자유 서술.
     * 운영 감사 로그({@code admin_audit_logs})와 중복되지만 목록 조회 시 바로 보기 위해
     * 이 엔티티에도 요약 사유를 보관한다.</p>
     */
    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    /**
     * 관리자가 수동 판정한 경우 그 관리자의 user_id (VARCHAR(50), nullable).
     *
     * <p>AI 자동 판정된 경우에는 {@code null} 로 두고, 관리자가 수동 승인/반려한 경우에만
     * {@link org.springframework.security.core.context.SecurityContextHolder} 에서 얻은
     * user_id 를 기록한다. {@code created_by/updated_by} 와 달리 "최종 인증 판정자"를
     * 명시적으로 구분하기 위한 필드다.</p>
     */
    @Column(name = "reviewed_by", length = 50)
    private String reviewedBy;

    /**
     * AI 또는 관리자 판정 시각 (nullable).
     *
     * <p>{@link #verifiedAt} 은 "인증이 성공으로 확정된 시각"만 담는 데 반해,
     * 본 필드는 승인/반려/재검증 요청 등 모든 판정 이벤트의 최근 시각을 담는다.
     * 반려된 건도 판정 시각을 남겨야 관리자 타임라인에서 흐름을 추적할 수 있다.</p>
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 인증을 완료 처리한다.
     *
     * <p>isVerified를 true로 전환하고 verifiedAt을 현재 시각으로 설정한다.
     * aiConfidence가 제공된 경우 함께 기록한다.</p>
     *
     * @param confidence AI 신뢰도 점수 (null 허용, QUIZ/REVIEW 인증 시 null 전달)
     */
    public void verify(Float confidence) {
        this.isVerified = true;
        this.aiConfidence = confidence;
        this.verifiedAt = LocalDateTime.now();
    }

    /**
     * 인증을 취소(무효화)한다.
     *
     * <p>관리자가 부정 인증을 발견했을 때 사용한다.
     * verifiedAt은 그대로 두어 이력을 보존한다.</p>
     */
    public void invalidate() {
        this.isVerified = false;
    }

    // ─────────────────────────────────────────────
    // 리뷰 인증 전용 도메인 메서드 (2026-04-14 추가)
    // ─────────────────────────────────────────────

    /**
     * AI 에이전트 판정 결과를 반영한다.
     *
     * <p>에이전트가 계산한 유사도/키워드/종합 신뢰도를 기록하고 판정 상태(AUTO_VERIFIED/
     * NEEDS_REVIEW/AUTO_REJECTED 중 하나)를 반영한다. AUTO_VERIFIED 일 때만 {@link #isVerified}
     * 를 true 로 전환한다. 관리자가 사후에 오버라이드할 수 있으므로 reviewedBy 는 null 로 둔다.</p>
     *
     * @param similarityScore 줄거리 ↔ 리뷰 유사도 (0.0~1.0)
     * @param matchedKeywords 공통 키워드 JSON 배열 문자열
     * @param confidence      종합 신뢰도 점수 (aiConfidence 로 기록)
     * @param reviewStatus    판정 상태 (AUTO_VERIFIED / NEEDS_REVIEW / AUTO_REJECTED)
     * @param reason          판정 사유 요약
     */
    public void applyAiDecision(
            Float similarityScore,
            String matchedKeywords,
            Float confidence,
            String reviewStatus,
            String reason
    ) {
        this.similarityScore = similarityScore;
        this.matchedKeywords = matchedKeywords;
        this.aiConfidence = confidence;
        this.reviewStatus = reviewStatus;
        this.decisionReason = reason;
        this.reviewedBy = null;                       // AI 자동 판정이므로 관리자 기록 없음
        this.reviewedAt = LocalDateTime.now();

        if ("AUTO_VERIFIED".equals(reviewStatus)) {
            this.isVerified = true;
            this.verifiedAt = LocalDateTime.now();
        } else {
            // AUTO_REJECTED / NEEDS_REVIEW: 기존 승인 상태를 취소
            this.isVerified = false;
        }
    }

    /**
     * 관리자가 수동으로 승인 처리한다.
     *
     * <p>AUTO_REJECTED/NEEDS_REVIEW 건을 운영자가 검토 후 "실제로 시청한 것으로 인정"할 때
     * 호출한다. {@link #isVerified} 를 true 로 전환하고 {@link #verifiedAt} 에 현재 시각을
     * 기록한다.</p>
     *
     * @param adminUserId 처리한 관리자 user_id
     * @param reason      승인 사유 (운영 감사용)
     */
    public void approveByAdmin(String adminUserId, String reason) {
        this.isVerified = true;
        this.reviewStatus = "ADMIN_APPROVED";
        this.decisionReason = reason;
        this.reviewedBy = adminUserId;
        this.reviewedAt = LocalDateTime.now();
        this.verifiedAt = LocalDateTime.now();
    }

    /**
     * 관리자가 수동으로 반려 처리한다.
     *
     * <p>AUTO_VERIFIED/NEEDS_REVIEW 건에 부정 인증 혐의가 발견되었을 때 호출한다.
     * {@link #isVerified} 를 false 로 되돌리지만 {@link #verifiedAt} 은 남겨 이력을 보존한다.</p>
     *
     * @param adminUserId 처리한 관리자 user_id
     * @param reason      반려 사유
     */
    public void rejectByAdmin(String adminUserId, String reason) {
        this.isVerified = false;
        this.reviewStatus = "ADMIN_REJECTED";
        this.decisionReason = reason;
        this.reviewedBy = adminUserId;
        this.reviewedAt = LocalDateTime.now();
    }

    /**
     * AI 재검증을 요청한다.
     *
     * <p>관리자가 "AI 에이전트를 다시 돌려보자"고 판단한 경우 사용한다. 상태를 PENDING 으로
     * 되돌려 다음 에이전트 실행 대상에 포함되게 만든다. 기존 유사도/키워드/신뢰도는 그대로
     * 두어 이전 판정 기록을 보존한다(다음 실행에서 덮어씀).</p>
     */
    public void requestReverify() {
        this.reviewStatus = "PENDING";
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = null;
    }

    /**
     * 관리자 반려 후 사용자가 재인증을 제출할 때 인증 레코드를 초기화한다.
     *
     * <p>이전 AI 분석 결과(similarityScore/matchedKeywords/aiConfidence)와 반려 사유를 지우고
     * PENDING 상태로 리셋하여 새 리뷰가 다시 AI 에이전트 큐에 들어가게 한다.</p>
     */
    public void resetForResubmit() {
        this.isVerified = false;
        this.reviewStatus = "PENDING";
        this.similarityScore = null;
        this.matchedKeywords = null;
        this.aiConfidence = null;
        this.decisionReason = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.verifiedAt = null;
    }
}
