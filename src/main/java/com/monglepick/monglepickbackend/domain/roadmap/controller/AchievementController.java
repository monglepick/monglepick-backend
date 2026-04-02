package com.monglepick.monglepickbackend.domain.roadmap.controller;

import com.monglepick.monglepickbackend.domain.roadmap.entity.AchievementType;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserAchievement;
import com.monglepick.monglepickbackend.domain.roadmap.service.AchievementService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 업적 컨트롤러 — 사용자 달성 업적 목록 조회 REST API.
 *
 * <h3>엔드포인트</h3>
 * <ul>
 *   <li>GET /api/v1/users/me/achievements — 내 업적 달성 목록 조회 (JWT 필수)</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>JWT Bearer 토큰이 필요하다. 본인 업적만 조회 가능하다.</p>
 *
 * <h3>응답 형태</h3>
 * <p>open-in-view=false 환경이므로 LazyInitializationException 방지를 위해
 * 서비스 레이어에서 조회한 엔티티를 컨트롤러에서 응답 DTO로 변환하여 반환한다.</p>
 */
@Tag(name = "업적", description = "사용자 달성 업적/배지 목록 조회")
@RestController
@RequestMapping("/api/v1/users/me/achievements")
@RequiredArgsConstructor
@Slf4j
public class AchievementController extends BaseController {

    /** 업적 서비스 — 달성 목록 조회 및 업적 부여 비즈니스 로직 */
    private final AchievementService achievementService;

    /**
     * 현재 로그인한 사용자의 달성 업적 목록을 조회한다.
     *
     * <p>달성 시각(achievedAt) 내림차순으로 정렬하여 반환한다.
     * 업적 유형 정보(코드, 이름, 설명, 아이콘, 보상 포인트)를 함께 제공한다.</p>
     *
     * @param principal JWT 인증 정보
     * @return 200 OK — 달성 업적 응답 DTO 목록 (최신 달성순)
     */
    @Operation(
            summary = "내 업적 달성 목록 조회",
            description = "로그인한 사용자의 달성 업적/배지 목록을 최신 달성순으로 반환합니다. " +
                    "업적 유형 코드, 이름, 설명, 아이콘, 보상 포인트, 달성 시각을 포함합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업적 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping
    public ResponseEntity<List<AchievementResponse>> getAchievements(Principal principal) {
        String userId = resolveUserId(principal);
        log.debug("업적 목록 조회: userId={}", userId);

        List<UserAchievement> achievements = achievementService.getAchievements(userId);

        // open-in-view=false 환경 — 트랜잭션 내에서 LAZY 필드 초기화 완료된 엔티티를 DTO로 변환
        List<AchievementResponse> responses = achievements.stream()
                .sorted((a, b) -> {
                    // achievedAt이 null인 경우 가장 오래된 것으로 처리 (최신순 정렬)
                    LocalDateTime timeA = a.getAchievedAt() != null ? a.getAchievedAt() : LocalDateTime.MIN;
                    LocalDateTime timeB = b.getAchievedAt() != null ? b.getAchievedAt() : LocalDateTime.MIN;
                    return timeB.compareTo(timeA);
                })
                .map(AchievementResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // ────────────────────────────────────────────────────────────────
    // 내부 응답 DTO (컨트롤러 전용)
    // ────────────────────────────────────────────────────────────────

    /**
     * 업적 달성 이력 응답 DTO.
     *
     * <p>UserAchievement 엔티티와 연관된 AchievementType 정보를 함께 담는다.
     * open-in-view=false 환경이므로 트랜잭션 내에서 LAZY 초기화 후 변환한다.</p>
     *
     * @param userAchievementId  달성 이력 고유 ID
     * @param achievementCode    업적 코드 (예: "course_complete")
     * @param achievementName    업적 표시명 (예: "코스 완주")
     * @param description        업적 설명
     * @param iconUrl            업적 아이콘 URL (nullable)
     * @param rewardPoints       업적 달성 시 지급된 포인트
     * @param achievementKey     업적 식별 키 (예: 코스 ID, "default")
     * @param achievedAt         달성 시각
     */
    @Getter
    public static class AchievementResponse {

        private final Long userAchievementId;
        private final String achievementCode;
        private final String achievementName;
        private final String description;
        private final String iconUrl;
        private final Integer rewardPoints;
        private final String achievementKey;
        private final LocalDateTime achievedAt;

        private AchievementResponse(Long userAchievementId, String achievementCode,
                                     String achievementName, String description,
                                     String iconUrl, Integer rewardPoints,
                                     String achievementKey, LocalDateTime achievedAt) {
            this.userAchievementId = userAchievementId;
            this.achievementCode = achievementCode;
            this.achievementName = achievementName;
            this.description = description;
            this.iconUrl = iconUrl;
            this.rewardPoints = rewardPoints;
            this.achievementKey = achievementKey;
            this.achievedAt = achievedAt;
        }

        /**
         * UserAchievement 엔티티로부터 응답 DTO를 생성한다.
         *
         * <p>achievementType은 LAZY이므로 반드시 트랜잭션 내에서 호출해야 한다.
         * AchievementService.getAchievements()가 트랜잭션 내에서 조회하므로 안전하다.</p>
         *
         * @param achievement UserAchievement 엔티티 (achievementType LAZY 초기화 완료)
         * @return AchievementResponse DTO
         */
        public static AchievementResponse from(UserAchievement achievement) {
            AchievementType type = achievement.getAchievementType();
            return new AchievementResponse(
                    achievement.getUserAchievementId(),
                    type.getAchievementCode(),
                    type.getAchievementName(),
                    type.getDescription(),
                    type.getIconUrl(),
                    type.getRewardPoints(),
                    achievement.getAchievementKey(),
                    achievement.getAchievedAt()
            );
        }
    }
}
