package com.monglepick.monglepickbackend.domain.community.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 유저 실관람 인증 OCR 엔티티 — user_verification 테이블
 *
 * <p>엑셀 설계 첫 번째 시트 39번 테이블 기준 (담당: 이민수).
 * 사용자가 영화관 영수증 등을 업로드하면 OCR로 관람 사실을 인증합니다.</p>
 *
 * <p>ocr_event 테이블과 연동되며, event_id로 어떤 인증 이벤트에 참가했는지 식별합니다.</p>
 */
@Entity
@Table(
        name = "user_verification",
        indexes = {
                @Index(name = "idx_user_verification_user",  columnList = "user_id"),
                @Index(name = "idx_user_verification_movie", columnList = "movie_id"),
                @Index(name = "idx_user_verification_event", columnList = "event_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserVerification extends BaseAuditEntity {

    /** 인증 요청 고유 ID (BIGINT AUTO_INCREMENT PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    /** 인증 요청 사용자 ID (users.user_id 참조) */
    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    /** 인증 대상 영화 ID (movies.movie_id 참조) */
    @Column(name = "movie_id", nullable = false, length = 50)
    private String movieId;

    /** 참가 인증 이벤트 ID (ocr_event.event_id 참조) */
    @Column(name = "event_id", length = 50)
    private String eventId;

    /** 업로드된 이미지 경로 (서버 저장 경로 또는 URL) */
    @Column(name = "image_id", length = 500)
    private String imageId;

    /** OCR로 추출된 영화명 */
    @Column(name = "extracted_movie_name", length = 200)
    private String extractedMovieName;

    /** OCR로 추출된 관람일 (문자열, 포맷 가변) */
    @Column(name = "extracted_watch_date", length = 50)
    private String extractedWatchDate;

    /** OCR 정제 전체 텍스트 (영수증 원문) */
    @Column(name = "parsed_text", columnDefinition = "TEXT")
    private String parsedText;

    @Builder
    public UserVerification(String userId, String movieId, String eventId,
                            String imageId, String extractedMovieName,
                            String extractedWatchDate, String parsedText) {
        this.userId = userId;
        this.movieId = movieId;
        this.eventId = eventId;
        this.imageId = imageId;
        this.extractedMovieName = extractedMovieName;
        this.extractedWatchDate = extractedWatchDate;
        this.parsedText = parsedText;
    }
}
