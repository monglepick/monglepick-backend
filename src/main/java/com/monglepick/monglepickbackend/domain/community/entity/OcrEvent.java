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
    public OcrEvent(String movieId, LocalDateTime startDate, LocalDateTime endDate, String adminId) {
        this.movieId = movieId;
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
