package com.monglepick.monglepickbackend.global.dto;

import com.monglepick.monglepickbackend.domain.roadmap.entity.AchievementType;

public record UnlockedAchievementResponse(
        Long achievementId,
        String code,
        String name,
        String description,
        String iconUrl,
        Integer rewardPoints
) {

    public static UnlockedAchievementResponse from(AchievementType type) {
        return new UnlockedAchievementResponse(
                type.getAchievementTypeId(),
                type.getAchievementCode(),
                type.getAchievementName(),
                type.getDescription(),
                type.getIconUrl(),
                type.getRewardPoints()
        );
    }
}
