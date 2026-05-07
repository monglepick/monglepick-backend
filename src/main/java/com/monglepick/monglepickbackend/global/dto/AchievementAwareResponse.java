package com.monglepick.monglepickbackend.global.dto;

import java.util.List;

public record AchievementAwareResponse<T>(
        T data,
        List<UnlockedAchievementResponse> unlockedAchievements
) {

    public AchievementAwareResponse {
        unlockedAchievements = unlockedAchievements == null ? List.of() : List.copyOf(unlockedAchievements);
    }

    public static <T> AchievementAwareResponse<T> of(
            T data,
            List<UnlockedAchievementResponse> unlockedAchievements
    ) {
        return new AchievementAwareResponse<>(data, unlockedAchievements);
    }

    public static <T> AchievementAwareResponse<T> of(T data) {
        return new AchievementAwareResponse<>(data, List.of());
    }
}
