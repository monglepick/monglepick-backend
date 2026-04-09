package com.monglepick.monglepickbackend.domain.userwatchhistory.dto;

import com.monglepick.monglepickbackend.domain.userwatchhistory.entity.UserWatchHistory;

import java.time.LocalDateTime;

/**
 * 시청 이력 응답 DTO.
 *
 * <p>{@link UserWatchHistory} 엔티티 대신 API 응답에 사용하여
 * 엔티티 내부 구조 노출과 Jackson 직렬화 문제를 방지한다.</p>
 *
 * <p>{@code createdAt} 은 BaseAuditEntity 가 자동 관리하는 "기록이 시스템에 등록된 시각"이며,
 * {@code watchedAt} 은 사용자가 명시한 "실제 시청 시각"이다. 둘은 의미가 다르므로 둘 다 노출한다.</p>
 *
 * @param userWatchHistoryId   PK
 * @param movieId              영화 ID
 * @param watchedAt            사용자가 명시한 시청 일시
 * @param rating               평점 (nullable)
 * @param watchSource          시청 경로 (nullable)
 * @param watchDurationSeconds 시청 시간 초 (nullable)
 * @param completionStatus     시청 완료 상태 (nullable)
 * @param createdAt            레코드 생성 일시 (BaseAuditEntity)
 */
public record UserWatchHistoryResponse(
        Long userWatchHistoryId,
        String movieId,
        LocalDateTime watchedAt,
        Double rating,
        String watchSource,
        Integer watchDurationSeconds,
        String completionStatus,
        LocalDateTime createdAt
) {
    /** 엔티티 → DTO 변환 팩토리 메서드 */
    public static UserWatchHistoryResponse from(UserWatchHistory entity) {
        return new UserWatchHistoryResponse(
                entity.getUserWatchHistoryId(),
                entity.getMovieId(),
                entity.getWatchedAt(),
                entity.getRating(),
                entity.getWatchSource(),
                entity.getWatchDurationSeconds(),
                entity.getCompletionStatus(),
                entity.getCreatedAt()
        );
    }
}
