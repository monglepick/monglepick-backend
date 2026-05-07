package com.monglepick.monglepickbackend.domain.movie.controller;

import com.monglepick.monglepickbackend.domain.movie.service.OnboardingService;
import com.monglepick.monglepickbackend.global.dto.AchievementAwareResponse;
import com.monglepick.monglepickbackend.global.dto.UnlockedAchievementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 온보딩 컨트롤러 — 선호 감독/배우 저장 및 조회 API.
 *
 * <p>사용자가 온보딩 단계에서 선호하는 감독과 배우를 등록하고 조회할 수 있는
 * REST API를 제공한다. 모든 엔드포인트는 JWT 인증이 필요하다.</p>
 *
 * <h3>엔드포인트 목록</h3>
 * <ul>
 *   <li>{@code POST /api/v1/onboarding/directors} — 선호 감독 일괄 저장</li>
 *   <li>{@code POST /api/v1/onboarding/actors}    — 선호 배우 일괄 저장</li>
 *   <li>{@code POST /api/v1/onboarding/genres}    — 선호 장르 일괄 저장</li>
 *   <li>{@code POST /api/v1/onboarding/movies}    — 인생 영화 일괄 저장</li>
 *   <li>{@code GET  /api/v1/onboarding/directors} — 선호 감독 목록 조회</li>
 *   <li>{@code GET  /api/v1/onboarding/actors}    — 선호 배우 목록 조회</li>
 *   <li>{@code GET  /api/v1/onboarding/genres}    — 선호 장르 목록 조회</li>
 *   <li>{@code GET  /api/v1/onboarding/movies}    — 인생 영화 목록 조회</li>
 * </ul>
 *
 * <h3>인증</h3>
 * <p>모든 요청에 {@code Authorization: Bearer {JWT}} 헤더가 필요하다.
 * {@code @AuthenticationPrincipal String userId}로 토큰에서 사용자 ID를 추출한다.</p>
 */
@Tag(name = "온보딩", description = "온보딩 선호 감독/배우 등록 및 조회")
@Slf4j
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    /**
     * 선호 감독 일괄 저장 API.
     *
     * <p>요청된 감독 이름 목록으로 기존 데이터를 교체한다 (Replace All).
     * 빈 리스트를 전달하면 기존 선호 감독이 모두 삭제된다.</p>
     *
     * @param userId       JWT에서 추출한 사용자 ID
     * @param directorNames 저장할 감독 이름 목록
     * @return 200 OK (본문 없음)
     */
    @Operation(
            summary = "선호 감독 저장",
            description = "온보딩에서 선택한 선호 감독 목록을 일괄 저장합니다. 기존 데이터는 교체됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "선호 감독 저장 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/directors")
    public ResponseEntity<Void> saveDirectors(
            @AuthenticationPrincipal String userId,
            @RequestBody List<String> directorNames) {

        log.info("선호 감독 저장 요청 - userId: {}, 건수: {}", userId, directorNames.size());
        onboardingService.saveDirectors(userId, directorNames);
        return ResponseEntity.ok().build();
    }

    /**
     * 선호 배우 일괄 저장 API.
     *
     * <p>요청된 배우 이름 목록으로 기존 데이터를 교체한다 (Replace All).
     * 빈 리스트를 전달하면 기존 선호 배우가 모두 삭제된다.</p>
     *
     * @param userId     JWT에서 추출한 사용자 ID
     * @param actorNames 저장할 배우 이름 목록
     * @return 200 OK (본문 없음)
     */
    @Operation(
            summary = "선호 배우 저장",
            description = "온보딩에서 선택한 선호 배우 목록을 일괄 저장합니다. 기존 데이터는 교체됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "선호 배우 저장 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/actors")
    public ResponseEntity<Void> saveActors(
            @AuthenticationPrincipal String userId,
            @RequestBody List<String> actorNames) {

        log.info("선호 배우 저장 요청 - userId: {}, 건수: {}", userId, actorNames.size());
        onboardingService.saveActors(userId, actorNames);
        return ResponseEntity.ok().build();
    }

    /**
     * 선호 장르 일괄 저장 API.
     *
     * @param userId   JWT에서 추출한 사용자 ID
     * @param genreIds 저장할 장르 ID 목록
     * @return 200 OK
     */
    @Operation(
            summary = "선호 장르 저장",
            description = "온보딩에서 선택한 선호 장르 ID 목록을 일괄 저장합니다. 기존 데이터는 교체됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "선호 장르 저장 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/genres")
    public ResponseEntity<AchievementAwareResponse<Void>> saveGenres(
            @AuthenticationPrincipal String userId,
            @RequestBody List<Long> genreIds) {

        log.info("선호 장르 저장 요청 - userId: {}, 건수: {}", userId, genreIds.size());
        List<UnlockedAchievementResponse> unlockedAchievements = onboardingService.saveGenres(userId, genreIds);
        return ResponseEntity.ok(AchievementAwareResponse.of(null, unlockedAchievements));
    }

    /**
     * 인생 영화 일괄 저장 API.
     *
     * @param userId   JWT에서 추출한 사용자 ID
     * @param movieIds 저장할 영화 ID 목록
     * @return 200 OK
     */
    @Operation(
            summary = "인생 영화 저장",
            description = "온보딩에서 선택한 인생 영화 ID 목록을 일괄 저장합니다. 기존 데이터는 교체됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인생 영화 저장 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/movies")
    public ResponseEntity<AchievementAwareResponse<Void>> saveMovies(
            @AuthenticationPrincipal String userId,
            @RequestBody List<String> movieIds) {

        log.info("인생 영화 저장 요청 - userId: {}, 건수: {}", userId, movieIds.size());
        List<UnlockedAchievementResponse> unlockedAchievements = onboardingService.saveMovies(userId, movieIds);
        return ResponseEntity.ok(AchievementAwareResponse.of(null, unlockedAchievements));
    }

    /**
     * 선호 감독 목록 조회 API.
     *
     * <p>현재 로그인한 사용자가 등록한 선호 감독 이름 목록을 반환한다.</p>
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @return 200 OK + 감독 이름 문자열 목록 (없으면 빈 배열)
     */
    @Operation(
            summary = "선호 감독 목록 조회",
            description = "현재 사용자의 선호 감독 이름 목록을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "선호 감독 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/directors")
    public ResponseEntity<List<String>> getDirectors(
            @AuthenticationPrincipal String userId) {

        List<String> directors = onboardingService.getDirectors(userId);
        return ResponseEntity.ok(directors);
    }

    /**
     * 선호 배우 목록 조회 API.
     *
     * <p>현재 로그인한 사용자가 등록한 선호 배우 이름 목록을 반환한다.</p>
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @return 200 OK + 배우 이름 문자열 목록 (없으면 빈 배열)
     */
    @Operation(
            summary = "선호 배우 목록 조회",
            description = "현재 사용자의 선호 배우 이름 목록을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "선호 배우 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/actors")
    public ResponseEntity<List<String>> getActors(
            @AuthenticationPrincipal String userId) {

        List<String> actors = onboardingService.getActors(userId);
        return ResponseEntity.ok(actors);
    }

    /**
     * 선호 장르 목록 조회 API.
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @return 200 OK + 장르 ID 목록
     */
    @Operation(
            summary = "선호 장르 목록 조회",
            description = "현재 사용자의 선호 장르 ID 목록을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "선호 장르 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/genres")
    public ResponseEntity<List<Long>> getGenres(
            @AuthenticationPrincipal String userId) {

        List<Long> genres = onboardingService.getGenres(userId);
        return ResponseEntity.ok(genres);
    }

    /**
     * 인생 영화 목록 조회 API.
     *
     * @param userId JWT에서 추출한 사용자 ID
     * @return 200 OK + 영화 ID 목록
     */
    @Operation(
            summary = "인생 영화 목록 조회",
            description = "현재 사용자의 인생 영화 ID 목록을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인생 영화 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/movies")
    public ResponseEntity<List<String>> getMovies(
            @AuthenticationPrincipal String userId) {

        List<String> movies = onboardingService.getMovies(userId);
        return ResponseEntity.ok(movies);
    }
}
