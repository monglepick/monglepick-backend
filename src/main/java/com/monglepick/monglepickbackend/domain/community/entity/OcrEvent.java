package com.monglepick.monglepickbackend.domain.community.entity;

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

import java.time.LocalDateTime;

/**
 * 실관람 인증 이벤트 엔티티 — ocr_event 테이블
 *
 * <p>엑셀 설계 첫 번째 시트 40번 테이블 기준 (담당: 이민수).
 * 관리자가 특정 영화에 대해 실관람 인증 이벤트를 생성합니다.
 * 사용자는 해당 기간 내에 영수증 OCR로 관람을 인증하고 리워드를 받습니다.</p>
 */
@Entity
@Table(
        name = "ocr_event",
        indexes = {
                @Index(name = "idx_ocr_event_movie",  columnList = "movie_id"),
                @Index(name = "idx_ocr_event_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OcrEvent extends BaseAuditEntity {

    /** 이벤트 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    /** 인증 대상 영화 ID (movies.movie_id 참조) */
    @Column(name = "movie_id", nullable = false, length = 50)
    private String movieId;

    /**
     * 이벤트 제목 (2026-04-14 신규 추가).
     *
     * <p>유저 페이지 "커뮤니티 → 실관람인증" 탭 카드 상단에 노출되는 제목.
     * 관리자가 이벤트를 구분하기 쉽도록 직접 입력한다
     * (예: "봄맞이 실관람 인증 이벤트", "독립영화 지원 캠페인 시즌 1").</p>
     *
     * <p>{@code ddl-auto=update} 호환을 위해 DB 레벨은 nullable 허용하되,
     * 요청 DTO(@NotBlank) 에서 필수 입력으로 강제한다.</p>
     */
    @Column(name = "title", length = 200)
    private String title;

    /**
     * 이벤트 상세 설명/메모 (2026-04-14 신규 추가).
     *
     * <p>유저 페이지 카드 본문·관리자 메모로 공용 사용. 마크다운 미지원, 순수 텍스트.
     * TEXT 컬럼으로 충분한 길이 수용(이벤트 상세 안내, 인증 방법, 리워드 설명 등).</p>
     */
    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    /** 이벤트 시작일 */
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    /** 이벤트 종료일 */
    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    /** 이벤트를 생성한 관리자 ID (users.user_id 참조) */
    @Column(name = "admin_id", length = 50)
    private String adminId;

    /** 이벤트 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OcrEventStatus status = OcrEventStatus.READY;

    @Builder
    public OcrEvent(String movieId,
                    String title,
                    String memo,
                    LocalDateTime startDate,
                    LocalDateTime endDate,
                    String adminId) {
        this.movieId = movieId;
        this.title = title;
        this.memo = memo;
        this.startDate = startDate;
        this.endDate = endDate;
        this.adminId = adminId;
        this.status = OcrEventStatus.READY;
    }

    /** 이벤트 활성화 */
    public void activate() {
        this.status = OcrEventStatus.ACTIVE;
    }

    /** 이벤트 종료 */
    public void close() {
        this.status = OcrEventStatus.CLOSED;
    }

    /**
     * 이벤트 메타 정보 수정 (관리자 마스터 관리용).
     *
     * <p>이벤트 ID(event_id)와 생성자 admin_id는 변경 불가.
     * 영화/제목/메모/시작일/종료일만 수정 가능하다.
     * 2026-04-14 title/memo 필드를 추가해 관리자 편집 범위를 확장.</p>
     *
     * @param movieId   대상 영화 ID
     * @param title     이벤트 제목
     * @param memo      이벤트 상세 메모
     * @param startDate 이벤트 시작일
     * @param endDate   이벤트 종료일
     */
    public void updateInfo(String movieId,
                           String title,
                           String memo,
                           LocalDateTime startDate,
                           LocalDateTime endDate) {
        this.movieId = movieId;
        this.title = title;
        this.memo = memo;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 이벤트 상태를 임의로 변경한다 (관리자 토글).
     *
     * <p>activate/close 도메인 메서드와 달리, 관리자가 임의로 READY ↔ ACTIVE ↔ CLOSED
     * 상태를 변경할 수 있도록 허용한다. 잘못된 전이 검증은 호출자(Service)가 책임진다.</p>
     */
    public void changeStatus(OcrEventStatus newStatus) {
        this.status = newStatus;
    }

    /**
     * 실관람 인증 이벤트 상태
     *
     * <ul>
     *   <li>READY — 대기 (시작 전)</li>
     *   <li>ACTIVE — 진행 중</li>
     *   <li>CLOSED — 종료</li>
     * </ul>
     */
    public enum OcrEventStatus {
        READY, ACTIVE, CLOSED
    }
}
