package com.monglepick.monglepickbackend.domain.chat.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅 환영 화면 추천 칩 엔티티 — chat_suggestions 테이블 매핑.
 *
 * <p>클라이언트 채팅 시작 화면에 표시되는 추천 질문 칩(버튼)의 문구를 DB에서 관리한다.
 * 관리자 페이지에서 CRUD 가능하며, Public API를 통해 활성 칩을 랜덤으로 제공한다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code text} — 채팅창에 삽입될 추천 질문 문구 (최대 200자, 필수)</li>
 *   <li>{@code category} — 칩 분류 코드 (mood/genre/trending/family/seasonal 등, nullable)</li>
 *   <li>{@code isActive} — 활성 여부 (기본값: false). true 일 때만 Public API에서 노출된다.</li>
 *   <li>{@code startAt} — 노출 시작 시각 (nullable, null 이면 즉시)</li>
 *   <li>{@code endAt} — 노출 종료 시각 (nullable, null 이면 무기한)</li>
 *   <li>{@code displayOrder} — 정렬 우선순위 (낮을수록 앞). 관리자 목록 정렬에 사용.</li>
 *   <li>{@code clickCount} — 클릭 수 누적 카운터. trackClick() 으로 원자적으로 증가시킨다.</li>
 *   <li>{@code adminId} — 등록 관리자 ID (VARCHAR(50), nullable)</li>
 * </ul>
 *
 * <h3>인덱스 설계</h3>
 * <ul>
 *   <li>{@code idx_chat_suggestions_active} — 활성 칩 조회(is_active, start_at, end_at) 복합 커버링</li>
 * </ul>
 *
 * <h3>변경 이력</h3>
 * <ul>
 *   <li>2026-04-20: 채팅 환영 화면 추천 칩 동적화 기능 최초 생성</li>
 * </ul>
 */
@Entity
@Table(
        name = "chat_suggestions",
        indexes = {
                /* 활성 칩 목록 조회: is_active + 기간 필터 커버링 인덱스 */
                @Index(name = "idx_chat_suggestions_active", columnList = "is_active, start_at, end_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatSuggestion extends BaseAuditEntity {

    /**
     * 추천 칩 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "suggestion_id")
    private Long suggestionId;

    /**
     * 추천 질문 문구 (VARCHAR(200), NOT NULL).
     * 클라이언트 채팅창에 바로 삽입될 자연어 한 문장.
     * 예: "오늘 기분이 우울한데 영화 추천해줘"
     */
    @Column(name = "text", length = 200, nullable = false)
    private String text;

    /**
     * 칩 분류 코드 (VARCHAR(50), nullable).
     * 허용 값 예시: mood, genre, trending, family, seasonal, similar, personal
     * 관리자 필터 및 분류 통계에 활용한다.
     */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * 활성 여부 (기본값: false).
     * true 일 때만 Public API에서 노출된다.
     * activate() / deactivate() 메서드를 통해 변경한다.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    /**
     * 노출 시작 시각 (nullable).
     * null 이면 isActive=true 설정 즉시 노출.
     * null 이 아니면 해당 시각 이후부터 노출.
     */
    @Column(name = "start_at")
    private LocalDateTime startAt;

    /**
     * 노출 종료 시각 (nullable).
     * null 이면 무기한 노출.
     * null 이 아니면 해당 시각까지만 노출.
     */
    @Column(name = "end_at")
    private LocalDateTime endAt;

    /**
     * 정렬 우선순위 (기본값: 0).
     * 관리자 목록에서 오름차순으로 정렬된다 (낮은 값 = 앞에 표시).
     * 클라이언트 칩 노출 순서는 별도로 셔플하므로 이 값이 직접 영향을 주지 않는다.
     */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * 클릭 수 (기본값: 0).
     * 사용자가 채팅창에서 칩을 클릭할 때마다 원자적으로 증가한다.
     * 메모리 내 증가는 incrementClickCount(), DB 원자적 증가는 Repository의 @Modifying 쿼리를 사용한다.
     */
    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    /**
     * 등록 관리자 ID (VARCHAR(50), nullable).
     * users.user_id를 논리적으로 참조한다.
     */
    @Column(name = "admin_id", length = 50)
    private String adminId;

    /* created_at, updated_at, created_by, updated_by → BaseAuditEntity에서 상속 */

    // ─────────────────────────────────────────────
    // 도메인 메서드 (setter 대신 의미 있는 메서드명 사용)
    // ─────────────────────────────────────────────

    /**
     * 추천 칩을 활성 상태로 전환한다.
     * isActive=true 로 설정되며, 기간 조건이 맞으면 Public API에서 노출된다.
     */
    public void activate() {
        this.isActive = true;
    }

    /**
     * 추천 칩을 비활성 상태로 전환한다.
     * isActive=false 로 설정되며, Public API에서 즉시 숨겨진다.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * 메모리 내 클릭 수를 1 증가시킨다.
     * 단위 테스트에서 도메인 로직 검증 시 사용한다.
     * 실제 운영에서는 Repository의 @Modifying incrementClickCount 쿼리로 원자적 처리한다.
     */
    public void incrementClickCount() {
        this.clickCount = (this.clickCount == null ? 0L : this.clickCount) + 1L;
    }

    /**
     * 추천 칩 내용을 일괄 수정한다.
     * null 파라미터는 해당 필드를 변경하지 않는다.
     *
     * @param text         새 문구 (null이면 기존 값 유지)
     * @param category     새 카테고리 (null이면 기존 값 유지)
     * @param startAt      새 노출 시작 시각 (null이면 기존 값 유지)
     * @param endAt        새 노출 종료 시각 (null이면 기존 값 유지)
     * @param displayOrder 새 정렬 우선순위 (null이면 기존 값 유지)
     */
    public void update(String text, String category,
                       LocalDateTime startAt, LocalDateTime endAt, Integer displayOrder) {
        if (text != null) {
            this.text = text;
        }
        if (category != null) {
            this.category = category;
        }
        if (startAt != null) {
            this.startAt = startAt;
        }
        if (endAt != null) {
            this.endAt = endAt;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }
}
