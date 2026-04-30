package com.monglepick.monglepickbackend.domain.movie.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monglepick.monglepickbackend.domain.movie.service.OnboardingService;
import com.monglepick.monglepickbackend.domain.roadmap.service.AchievementService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 온보딩 내부 동기화 API.
 *
 * <p>Recommend/FastAPI 등 외부 서비스가 기존 /api/v2 선호 저장을 처리한 뒤
 * Spring Boot 업적 도메인에 온보딩 완료 여부 재계산을 요청할 때 사용한다.</p>
 */
@Tag(name = "온보딩 내부 API", description = "ServiceKey 전용 온보딩 업적 동기화")
@Slf4j
@RestController
@RequestMapping("/api/v1/onboarding/internal")
@RequiredArgsConstructor
public class OnboardingInternalController extends BaseController {

    private final AchievementService achievementService;
    private final OnboardingService onboardingService;

    @Operation(
            summary = "온보딩 업적 동기화",
            description = "외부 서비스의 선호 장르/인생 영화 저장 성공 후 onboard_complete 업적을 재계산합니다."
    )
    @SecurityRequirement(name = "ServiceKey")
    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> sync(
            Principal principal,
            @Valid @RequestBody SyncRequest request) {

        String userId = resolveUserIdWithServiceKey(principal, request.userId());
        if (request.genreIds() != null) {
            onboardingService.saveGenres(userId, request.genreIds());
        } else if (request.movieIds() != null) {
            onboardingService.saveMovies(userId, request.movieIds());
        } else {
            achievementService.checkOnboardComplete(userId);
        }
        AchievementService.OnboardCompleteProgress progress =
                achievementService.getOnboardCompleteProgress(userId);

        log.info("온보딩 업적 내부 동기화 완료: userId={}, type={}, progress={}/{}",
                userId, request.type(), progress.progress(), progress.maxProgress());

        return ResponseEntity.ok(SyncResponse.from(progress));
    }

    public record SyncRequest(
            String userId,
            String type,
            @JsonProperty("genre_ids") List<Long> genreIds,
            @JsonProperty("movie_ids") List<String> movieIds
    ) {}

    public record SyncResponse(
            boolean worldcupCompleted,
            boolean favoriteGenresCompleted,
            boolean favoriteMoviesCompleted,
            int progress,
            int maxProgress,
            boolean completed
    ) {
        static SyncResponse from(AchievementService.OnboardCompleteProgress progress) {
            return new SyncResponse(
                    progress.worldcupCompleted(),
                    progress.favoriteGenresCompleted(),
                    progress.favoriteMoviesCompleted(),
                    progress.progress(),
                    progress.maxProgress(),
                    progress.completed()
            );
        }
    }
}
