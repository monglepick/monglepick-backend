package com.monglepick.monglepickbackend.domain.support.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객센터 FAQ 엔티티.
 *
 * <p>MySQL {@code support_faq} 테이블과 매핑된다.
 * 자주 묻는 질문(FAQ)과 답변을 카테고리별로 관리하며,
 * 사용자 피드백(도움됨/도움 안됨) 집계 카운터를 비정규화하여 보관한다.</p>
 *
 * <h3>피드백 카운터 비정규화</h3>
 * <p>{@code helpful_count}와 {@code not_helpful_count}는 {@link SupportFaqFeedback}
 * 레코드를 매번 COUNT 집계하는 대신, FAQ 엔티티에 직접 보관하여 목록 조회 성능을 최적화한다.
 * 실제 변경은 {@link #incrementHelpful()} / {@link #incrementNotHelpful()} 메서드를 통해서만 수행한다.</p>
 */
@Entity
@Table(name = "support_faq", indexes = {
        // 카테고리별 FAQ 목록 조회 시 사용
        @Index(name = "idx_support_faq_category", columnList = "category"),
        // 최신순 정렬 시 사용
        @Index(name = "idx_support_faq_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportFaq extends BaseAuditEntity {

    /**
     * FAQ 고유 식별자 (BIGINT AUTO_INCREMENT PK).
     * DB가 자동 생성하며 변경 불가.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "faq_id")
    private Long faqId;

    /**
     * FAQ 카테고리.
     * GENERAL, ACCOUNT, CHAT, RECOMMENDATION, COMMUNITY, PAYMENT 중 하나.
     * EnumType.STRING으로 DB에 문자열로 저장된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SupportCategory category;

    /**
     * 질문 내용 (VARCHAR 500).
     * "비밀번호를 잊어버렸어요. 어떻게 재설정하나요?" 형태의 자연어 질문.
     */
    @Column(nullable = false, length = 500)
    private String question;

    /**
     * 답변 내용 (TEXT).
     * HTML 태그를 포함할 수 있으며, 마크다운 형식도 허용한다.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    /**
     * "도움됨" 피드백 카운터 (기본값 0).
     * SupportFaqFeedback.helpful=true 레코드 수를 비정규화하여 저장한다.
     * {@link #incrementHelpful()} 메서드로만 증가시킨다.
     */
    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount = 0;

    /**
     * "도움 안됨" 피드백 카운터 (기본값 0).
     * SupportFaqFeedback.helpful=false 레코드 수를 비정규화하여 저장한다.
     * {@link #incrementNotHelpful()} 메서드로만 증가시킨다.
     */
    @Column(name = "not_helpful_count", nullable = false)
    private int notHelpfulCount = 0;

    /** 표시 순서 (관리자 제어용, 낮은 값이 상위에 노출) */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** 공개 여부 (기본값: true, false이면 사용자에게 노출되지 않음) */
    @Column(name = "is_published", nullable = false)
    private boolean isPublished = true;

    /**
     * ES 검색 키워드 힌트 (TEXT, nullable).
     *
     * <p>질문/답변 본문 외에 검색 품질을 높이기 위한 쉼표 구분 동의어 태그.
     * 예: "환불,반환,취소,회수,돈,결제,돌려받기".
     * Elasticsearch {@code keywords} 필드로 색인되어 Agent 측 multi_match 부스트 ^2 가 적용된다.
     * null 이면 ES 색인 시 해당 필드를 누락으로 처리한다.</p>
     *
     * <p>Agent 팀 합의 스펙(인덱스 매핑 {@code keywords: text, analyzer=nori_analyzer})
     * 과 연동되므로 컬럼명·타입 변경 시 반드시 양측 동기화 필요.</p>
     */
    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords;

    /**
     * 생성자 (빌더 패턴).
     *
     * <p>helpfulCount / notHelpfulCount는 항상 0으로 초기화된다.
     * 피드백 카운터는 비즈니스 메서드를 통해서만 변경 가능하다.</p>
     *
     * @param category  카테고리
     * @param question  질문 내용
     * @param answer    답변 내용
     * @param sortOrder 표시 순서 (nullable)
     * @param keywords  ES 검색 키워드 힌트 (쉼표 구분 동의어, nullable)
     */
    @Builder
    public SupportFaq(SupportCategory category, String question, String answer,
                      Integer sortOrder, String keywords) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.helpfulCount = 0;
        this.notHelpfulCount = 0;
        this.sortOrder = sortOrder;
        this.isPublished = true; // 생성 시 기본 공개 상태
        this.keywords = keywords;
    }

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /**
     * 질문/답변/카테고리/키워드 수정.
     *
     * @param category 변경할 카테고리
     * @param question 변경할 질문
     * @param answer   변경할 답변
     */
    public void update(SupportCategory category, String question, String answer) {
        this.category = category;
        this.question = question;
        this.answer = answer;
    }

    /**
     * ES 검색 키워드 힌트 수정.
     *
     * <p>쉼표 구분 동의어 태그를 갱신한다.
     * null 로 설정하면 ES 색인 시 keywords 필드가 누락 처리된다.</p>
     *
     * @param keywords 쉼표 구분 키워드 문자열 (nullable)
     */
    public void updateKeywords(String keywords) {
        this.keywords = keywords;
    }

    /**
     * 표시 순서 변경 (관리자 제어용).
     *
     * @param sortOrder 새 표시 순서 (낮은 값이 상위 노출)
     */
    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * 공개/비공개 전환.
     * 관리자가 FAQ를 임시 비공개 처리하거나 다시 공개할 때 호출한다.
     *
     * @param published true이면 공개, false이면 비공개
     */
    public void setPublished(boolean published) {
        this.isPublished = published;
    }

    /**
     * "도움됨" 카운터 1 증가.
     * SupportFaqFeedbackService에서 피드백 저장 시 호출한다.
     */
    public void incrementHelpful() {
        this.helpfulCount++;
    }

    /**
     * "도움 안됨" 카운터 1 증가.
     * SupportFaqFeedbackService에서 피드백 저장 시 호출한다.
     */
    public void incrementNotHelpful() {
        this.notHelpfulCount++;
    }
}
