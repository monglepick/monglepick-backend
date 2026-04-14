package com.monglepick.monglepickbackend.domain.community.ocrevent;

import java.time.LocalDateTime;

/**
 * 유저 커뮤니티 "실관람인증" 탭에 노출되는 OCR 이벤트 카드 응답.
 *
 * <p>관리자 응답({@code AdminOcrEventDto.OcrEventResponse})과 다른 점:</p>
 * <ul>
 *   <li>{@code adminId} 제거 — 유저에게 관리자 계정 노출 불필요</li>
 *   <li>{@code createdAt / updatedAt} 제거 — 내부 감사 로그는 유저 UI에 불필요</li>
 *   <li>{@code movieTitle / moviePosterPath} 추가 — 영화 메타 조인 결과 포함</li>
 * </ul>
 *
 * <p>프론트 카드는 movieTitle + moviePosterPath + title(이벤트 제목) + memo(설명)
 * + 시작/종료일 + 상태를 한 번에 렌더링한다.</p>
 */
public record OcrEventPublicResponse(
        /** 이벤트 PK */
        Long eventId,
        /** 인증 대상 영화 ID (movies.movie_id) */
        String movieId,
        /** 영화 한국어 제목 (movies.title, null 가능 — 영화 메타 없는 이벤트) */
        String movieTitle,
        /** TMDB 포스터 경로 (movies.poster_path, null 가능) */
        String moviePosterPath,
        /** 이벤트 제목 (2026-04-14 신규 필드) */
        String title,
        /** 이벤트 상세 메모 (2026-04-14 신규 필드) */
        String memo,
        /** 이벤트 시작일 */
        LocalDateTime startDate,
        /** 이벤트 종료일 */
        LocalDateTime endDate,
        /** 이벤트 상태 (READY/ACTIVE) — CLOSED 는 유저 응답에 포함되지 않음 */
        String status
) {}
