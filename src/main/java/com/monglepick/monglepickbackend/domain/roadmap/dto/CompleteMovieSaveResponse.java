package com.monglepick.monglepickbackend.domain.roadmap.dto;

/**
 * 도장깨기 영화 시청 인증 저장 응답 DTO.
 *
 * <p>Spring Boot는 리뷰 저장과 CourseVerification 생성만 담당하고,
 * AI 검증 호출은 프론트엔드가 FastAPI에 직접 수행한다.
 * 프론트엔드는 이 응답을 받아 verificationId/moviePlot으로 FastAPI를 호출한다.</p>
 *
 * @param verificationId  생성된 CourseVerification PK (FastAPI 요청 시 필요)
 * @param courseId        코스 슬러그
 * @param movieId         영화 ID
 * @param moviePlot       영화 줄거리 (FastAPI 검증용, 이미 인증된 경우 null)
 * @param reviewStatus    현재 인증 상태 — 신규/재인증은 "PENDING", 이미 인증된 경우 기존 상태
 */
public record CompleteMovieSaveResponse(
        Long verificationId,
        String courseId,
        String movieId,
        String moviePlot,
        String reviewStatus
) {}
