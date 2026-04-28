package com.monglepick.monglepickbackend.domain.community.ocrevent;

import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent;
import com.monglepick.monglepickbackend.domain.community.entity.OcrEvent.OcrEventStatus;
import com.monglepick.monglepickbackend.domain.movie.entity.Movie;
import com.monglepick.monglepickbackend.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 유저 전용 OCR 이벤트 조회 서비스.
 *
 * <p>관리자 서비스({@code AdminOcrEventService})와 분리된 유저 노출 전용 서비스.
 * 관리자 등록 이벤트 중 "현재 진행 중이거나 곧 시작하는" 이벤트만 커뮤니티
 * "실관람인증" 탭으로 내려준다.</p>
 *
 * <h3>영화 메타 조인</h3>
 * <p>현재 스키마에 FK 가 없으므로 JPA 연관관계 대신 2-step fetch 로 처리한다:</p>
 * <ol>
 *   <li>{@link OcrEventRepository#findPublicEvents} 로 이벤트 조회</li>
 *   <li>이벤트의 {@code movieId} 목록을 한 번에 {@link MovieRepository#findAllById} 로 in-query 조회</li>
 *   <li>Map 기반 매핑 → {@link OcrEventPublicResponse} 생성</li>
 * </ol>
 * <p>N+1 회피 + 존재하지 않는 movieId 에 대한 null 허용.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OcrEventService {

    /** 유저 공개 이벤트 리포지토리 */
    private final OcrEventRepository ocrEventRepository;

    /** 영화 메타 조회용 리포지토리 (title/posterPath 조인) */
    private final MovieRepository movieRepository;

    /** 유저 응답에 노출할 상태 — CLOSED 는 포함 X */
    private static final List<OcrEventStatus> PUBLIC_STATUSES =
            List.of(OcrEventStatus.ACTIVE, OcrEventStatus.READY);

    /**
     * 유저 커뮤니티 "실관람인증" 탭용 이벤트 목록 조회.
     *
     * @return 노출 가능한 이벤트 목록 (영화 메타 포함)
     */
    public List<OcrEventPublicResponse> getPublicEvents() {
        LocalDateTime now = LocalDateTime.now();

        // 1) 노출 조건을 만족하는 이벤트 조회
        List<OcrEvent> events = ocrEventRepository.findPublicEvents(PUBLIC_STATUSES, now);
        if (events.isEmpty()) {
            return List.of();
        }

        // 2) movieId 중복 제거 후 한 번에 영화 메타 fetch (N+1 회피)
        Set<String> movieIds = new HashSet<>();
        for (OcrEvent e : events) {
            if (e.getMovieId() != null) {
                movieIds.add(e.getMovieId());
            }
        }
        Map<String, Movie> movieMap = movieRepository.findAllById(movieIds).stream()
                .collect(Collectors.toMap(Movie::getMovieId, Function.identity()));

        // 3) 응답 DTO 매핑 (영화 메타가 없는 경우 title/posterPath 를 null 로 전달 — 프론트에서 fallback 표시)
        return events.stream()
                .map(e -> toPublicResponse(e, movieMap.get(e.getMovieId())))
                .collect(Collectors.toList());
    }

    /**
     * 특정 영화의 진행 중 OCR 인증 이벤트 1건 조회 (2026-04-14 신규).
     *
     * <p>영화 상세 페이지 상단에 "실관람 인증 진행중" 배너를 띄우기 위해 호출한다.
     * 추천·검색·찜 등 어디서든 영화 상세로 진입했을 때 배너 노출 여부를 결정한다.</p>
     *
     * <p>반환 조건은 {@link #getPublicEvents()}와 동일하다:
     * {@code status IN (ACTIVE, READY)} AND {@code endDate > now()}.
     * 한 영화에 동시에 여러 이벤트가 있다면 {@link OcrEventRepository#findActiveByMovieId}
     * 의 정렬 규칙(ACTIVE 우선 → 종료 임박) 상위 1건만 반환한다.</p>
     *
     * @param movieId 영화 ID (movies.movie_id)
     * @return 활성 이벤트가 있으면 DTO, 없으면 {@link Optional#empty()}
     */
    public Optional<OcrEventPublicResponse> getActiveEventByMovie(String movieId) {
        if (movieId == null || movieId.isBlank()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();

        // 정렬된 리스트에서 상위 1건만 필요 — LIMIT 1 대신 stream().findFirst() 로 구현
        List<OcrEvent> events = ocrEventRepository.findActiveByMovieId(movieId, PUBLIC_STATUSES, now);
        if (events.isEmpty()) {
            return Optional.empty();
        }
        OcrEvent event = events.get(0);

        // 영화 메타(title/posterPath) 단건 조회 — 배너 표시에는 필요하지 않지만 일관성을 위해 포함
        Movie movie = movieRepository.findById(event.getMovieId()).orElse(null);
        return Optional.of(toPublicResponse(event, movie));
    }

    /**
     * 관리자 서비스와 공용되는 엔티티 조회 헬퍼.
     *
     * <p>유저 인증 제출 서비스({@code UserVerificationService})가 이벤트 존재성과
     * 라이프사이클(ACTIVE/기간 내)을 검증할 때 사용한다. readOnly 가 아닌 작업
     * 컨텍스트에서 호출될 수 있으므로 propagation 기본값을 따른다.</p>
     *
     * @param eventId 이벤트 PK
     * @return 이벤트 엔티티 (없으면 {@link Optional#empty()})
     */
    public Optional<OcrEvent> findEntityById(Long eventId) {
        return ocrEventRepository.findById(eventId);
    }

    /**
     * eventId 로 이벤트 대상 영화 제목을 조회한다 (신뢰도 부스트 판단용).
     *
     * @param eventId 이벤트 PK
     * @return 영화 한국어 제목, 이벤트/영화가 없으면 null
     */
    public String getMovieTitleByEventId(Long eventId) {
        if (eventId == null) return null;
        return ocrEventRepository.findById(eventId)
                .flatMap(e -> movieRepository.findById(e.getMovieId()))
                .map(Movie::getTitle)
                .orElse(null);
    }

    /**
     * 엔티티 + 영화 메타 → 유저 응답 DTO.
     *
     * @param event 이벤트 엔티티
     * @param movie 연관 영화 (null 가능)
     */
    private OcrEventPublicResponse toPublicResponse(OcrEvent event, Movie movie) {
        return new OcrEventPublicResponse(
                event.getEventId(),
                event.getMovieId(),
                movie != null ? movie.getTitle() : null,
                movie != null ? movie.getPosterPath() : null,
                event.getTitle(),
                event.getMemo(),
                event.getStartDate(),
                event.getEndDate(),
                event.getStatus().name()
        );
    }
}
