package com.monglepick.monglepickbackend.domain.movie.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.monglepick.monglepickbackend.domain.movie.service.OnboardingService;
import com.monglepick.monglepickbackend.global.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 기존 프론트 /api/v2 온보딩 저장 경로 호환 컨트롤러.
 *
 * <p>배포 라우팅상 /api/v2가 Spring Boot로 들어오는 환경에서 프론트 수정 없이
 * 기존 요청이 fav_genre/fav_movie 저장과 onboard_complete 업적 갱신까지 수행하도록 한다.</p>
 */
@Tag(name = "온보딩 v2 호환 API", description = "기존 프론트 선호 장르/인생 영화 저장 경로")
@Slf4j
@RestController
@RequiredArgsConstructor
public class OnboardingV2CompatController extends BaseController {

    private final OnboardingService onboardingService;

    @Operation(
            summary = "선호 장르 저장 v2 호환",
            description = "기존 프론트 PUT /api/v2/users/me/favorite-genres 요청을 처리합니다."
    )
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/api/v2/users/me/favorite-genres")
    public ResponseEntity<V2OnboardingStatusResponse> saveFavoriteGenres(
            Principal principal,
            @RequestBody FavoriteGenresRequest request) {

        String userId = resolveUserId(principal);
        List<Long> genreIds = request == null ? List.of() : request.genreIds();
        onboardingService.saveGenres(userId, genreIds);
        return ResponseEntity.ok(V2OnboardingStatusResponse.from(onboardingService.getStatus(userId)));
    }

    @Operation(
            summary = "인생 영화 저장 v2 호환",
            description = "기존 프론트 PUT /api/v2/users/me/favorite-movies 요청을 처리합니다."
    )
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/api/v2/users/me/favorite-movies")
    public ResponseEntity<V2OnboardingStatusResponse> saveFavoriteMovies(
            Principal principal,
            @RequestBody FavoriteMoviesRequest request) {

        String userId = resolveUserId(principal);
        List<String> movieIds = request == null ? List.of() : request.movieIds();
        onboardingService.saveMovies(userId, movieIds);
        return ResponseEntity.ok(V2OnboardingStatusResponse.from(onboardingService.getStatus(userId)));
    }

    @Operation(
            summary = "온보딩 상태 조회 v2 호환",
            description = "월드컵, 선호 장르, 인생 영화 기준 온보딩 완료 상태를 반환합니다."
    )
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/api/v2/onboarding/status")
    public ResponseEntity<V2OnboardingStatusResponse> getStatus(Principal principal) {
        String userId = resolveUserId(principal);
        return ResponseEntity.ok(V2OnboardingStatusResponse.from(onboardingService.getStatus(userId)));
    }

    public record FavoriteGenresRequest(
            @JsonProperty("genre_ids") List<Long> genreIds
    ) {}

    public record FavoriteMoviesRequest(
            @JsonProperty("movie_ids") List<String> movieIds
    ) {}

    public record V2OnboardingStatusResponse(
            @JsonProperty("worldcup_completed") boolean worldcupCompleted,
            @JsonProperty("favorite_genres_completed") boolean favoriteGenresCompleted,
            @JsonProperty("favorite_movies_completed") boolean favoriteMoviesCompleted,
            int progress,
            @JsonProperty("max_progress") int maxProgress,
            boolean completed
    ) {
        static V2OnboardingStatusResponse from(OnboardingService.OnboardingStatus status) {
            return new V2OnboardingStatusResponse(
                    status.worldcupCompleted(),
                    status.favoriteGenresCompleted(),
                    status.favoriteMoviesCompleted(),
                    status.progress(),
                    status.maxProgress(),
                    status.completed()
            );
        }
    }
}
