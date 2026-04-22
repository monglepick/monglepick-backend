package com.monglepick.monglepickbackend.domain.roadmap.dto;

import java.util.List;

/**
 * 프론트엔드 → Spring Boot AI 검증 결과 적용 요청 DTO.
 *
 * <p>프론트엔드가 FastAPI로부터 받은 AI 검증 결과를 Spring Boot에 전달하여
 * CourseVerification 엔티티를 업데이트하고 진행률을 반영하는 데 사용한다.</p>
 *
 * @param verificationId  CourseVerification PK (completeMovie 응답에서 수신)
 * @param reviewStatus    AI 판정 결과 (AUTO_VERIFIED / NEEDS_REVIEW / AUTO_REJECTED / PENDING)
 * @param rationale       AI 판정 근거 요약 (nullable)
 * @param similarityScore 영화 줄거리 ↔ 리뷰 유사도 0.0~1.0 (nullable)
 * @param matchedKeywords 공통 키워드 목록 (nullable)
 * @param confidence      최종 신뢰도 점수 0.0~1.0 (nullable)
 */
public record VerifyResultRequest(
        Long verificationId,
        String reviewStatus,
        String rationale,
        Float similarityScore,
        List<String> matchedKeywords,
        Float confidence
) {}
