package com.monglepick.monglepickbackend.domain.content.entity;

/* BaseAuditEntity 상속으로 created_at, updated_at, created_by, updated_by 자동 관리 */
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
 * 배너 엔티티 — banners 테이블 매핑.
 *
 * <p>메인 페이지, 서브 영역, 팝업 등에 노출되는 프로모션 배너 정보를 관리한다.
 * 관리자 페이지 "설정 → 배너 관리" 탭에서 CRUD 작업이 이루어지며,
 * 프론트엔드는 활성 배너 목록 API를 통해 현재 노출 배너를 조회한다.</p>
 *
 * <p>게시 기간({@code startDate} ~ {@code endDate})과 활성 여부({@code isActive})를
 * 함께 활용하여 노출 여부를 제어한다. 두 조건 모두 충족해야 실제 노출된다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code title} — 배너 제목 (관리자용 식별 명칭, 필수)</li>
 *   <li>{@code imageUrl} — 배너 이미지 URL (CDN 경로, 필수)</li>
 *   <li>{@code linkUrl} — 배너 클릭 시 이동할 URL (내부/외부 모두 가능, nullable)</li>
 *   <li>{@code position} — 노출 위치 코드 (기본값: "MAIN")</li>
 *   <li>{@code sortOrder} — 동일 위치 내 정렬 순서 (숫자가 작을수록 우선 노출)</li>
 *   <li>{@code isActive} — 활성화 여부 (관리자가 수동으로 ON/OFF 제어)</li>
 *   <li>{@code startDate} — 게시 시작일시 (NULL이면 즉시 노출 가능)</li>
 *   <li>{@code endDate} — 게시 종료일시 (NULL이면 기간 제한 없음)</li>
 * </ul>
 *
 * <h3>position 코드</h3>
 * <ul>
 *   <li>{@code MAIN} — 메인 페이지 상단 슬라이더 배너</li>
 *   <li>{@code SUB} — 서브 영역(사이드바, 중간 삽입) 배너</li>
 *   <li>{@code POPUP} — 팝업 레이어 배너 (방문 시 1회 표시)</li>
 * </ul>
 *
 * <h3>인덱스 설계 근거</h3>
 * <ul>
 *   <li>{@code idx_banner_position_active} — 위치+활성 복합 조건 조회 (프론트엔드 API 핵심 쿼리)</li>
 *   <li>{@code idx_banner_sort_order} — 동일 위치 내 정렬 순서 기반 정렬</li>
 *   <li>{@code idx_banner_date_range} — 게시 기간 필터링 (현재 시각이 start~end 사이인 배너 조회)</li>
 * </ul>
 *
 * <h3>도메인 메서드</h3>
 * <p>관리자 API 서비스에서 직접 setter 대신 도메인 메서드를 통해 상태를 변경한다.</p>
 */
@Entity
@Table(
        name = "banners",
        indexes = {
                /* 위치 + 활성 여부 복합 조회 — 프론트엔드 배너 목록 API의 핵심 필터 */
                @Index(name = "idx_banner_position_active", columnList = "position, is_active"),
                /* 동일 위치 내 정렬 순서 */
                @Index(name = "idx_banner_sort_order", columnList = "sort_order"),
                /* 게시 기간 범위 조회 — 현재 시각 기준 유효 배너 필터 */
                @Index(name = "idx_banner_date_range", columnList = "start_date, end_date")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Banner extends BaseAuditEntity {

    /**
     * 배너 고유 ID (BIGINT AUTO_INCREMENT PK).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "banner_id")
    private Long bannerId;

    /**
     * 배너 제목 (최대 200자, 필수).
     * 프론트엔드에 직접 노출되는 타이틀이 아닌 관리자용 식별 명칭이다.
     * 예: "2026 여름 신작 프로모션", "구독 할인 이벤트 팝업"
     */
    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /**
     * 배너 이미지 URL (최대 500자, 필수).
     * CDN 또는 오브젝트 스토리지의 전체 URL을 저장한다.
     * 예: "https://cdn.monglepick.com/banners/summer_2026.webp"
     */
    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;

    /**
     * 클릭 시 이동할 URL (최대 500자, nullable).
     * 내부 경로("/movies/trending"), 외부 URL("https://...") 모두 허용한다.
     * NULL이면 클릭 동작 없음.
     */
    @Column(name = "link_url", length = 500)
    private String linkUrl;

    /**
     * 배너 노출 위치 코드 (최대 50자, 기본값: "MAIN").
     * 예: "MAIN"(메인 슬라이더), "SUB"(서브 영역), "POPUP"(팝업 레이어)
     */
    @Column(name = "position", length = 50)
    @Builder.Default
    private String position = "MAIN";

    /**
     * 동일 위치 내 정렬 순서 (기본값: 0).
     * 숫자가 작을수록 먼저 노출된다. 0이 가장 우선순위가 높다.
     */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * 배너 활성화 여부 (기본값: true).
     * false로 설정하면 게시 기간 내라도 노출되지 않는다.
     * 관리자가 즉시 ON/OFF 제어에 활용한다.
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 게시 시작일시.
     * NULL이면 즉시 게시 가능 상태로 간주한다.
     * 프론트엔드 조회 시 현재 시각 >= startDate 조건으로 필터링한다.
     */
    @Column(name = "start_date")
    private LocalDateTime startDate;

    /**
     * 게시 종료일시.
     * NULL이면 종료 기간 제한 없음으로 간주한다.
     * 프론트엔드 조회 시 현재 시각 <= endDate 조건으로 필터링한다.
     */
    @Column(name = "end_date")
    private LocalDateTime endDate;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */

    // ======================== 도메인 메서드 ========================

    /**
     * 배너 기본 정보 수정.
     * 관리자 API 서비스에서 setter 대신 이 메서드를 통해 변경한다.
     *
     * @param title      새 배너 제목
     * @param imageUrl   새 이미지 URL
     * @param linkUrl    새 링크 URL (null 허용)
     * @param position   새 노출 위치 코드
     * @param sortOrder  새 정렬 순서
     */
    public void update(String title, String imageUrl, String linkUrl,
                       String position, Integer sortOrder) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.position = position;
        this.sortOrder = sortOrder;
    }

    /**
     * 배너 활성화 여부 토글.
     *
     * @param active true면 활성화, false면 비활성화
     */
    public void setActive(boolean active) {
        this.isActive = active;
    }

    /**
     * 배너 게시 기간 수정.
     *
     * @param startDate 게시 시작일시 (null 허용)
     * @param endDate   게시 종료일시 (null 허용)
     */
    public void updateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
