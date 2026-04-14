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
