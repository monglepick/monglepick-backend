package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 관리자 전용 퀴즈 리포지토리.
 *
 * <p>관리자 페이지 "AI 운영 → 퀴즈" 탭에서 상태별·전체 퀴즈 목록을 페이징 조회하기 위한
 * 쿼리 메서드를 제공한다. 도메인 레이어의 {@code QuizRepository}는 리스트 기반 조회에
 * 특화되어 있으므로 페이징 검색은 별도 리포지토리로 분리한다.</p>
 *
 * <h3>주요 쿼리</h3>
 * <ul>
 *   <li>{@link #findByStatusOrderByCreatedAtDesc} — 상태별 퀴즈 최신순 페이징</li>
 *   <li>{@link #findAllByOrderByCreatedAtDesc} — 전체 퀴즈 최신순 페이징 (필터 없음)</li>
 * </ul>
 */
public interface AdminQuizRepository extends JpaRepository<Quiz, Long> {

    /**
     * 특정 상태의 퀴즈 목록을 최신순으로 페이징 조회한다.
     *
     * @param status   퀴즈 상태 (PENDING / APPROVED / REJECTED / PUBLISHED)
     * @param pageable 페이지 정보
     * @return 해당 상태의 퀴즈 페이지
     */
    Page<Quiz> findByStatusOrderByCreatedAtDesc(Quiz.QuizStatus status, Pageable pageable);

    /**
     * 전체 퀴즈 목록을 최신순으로 페이징 조회한다.
     *
     * @param pageable 페이지 정보
     * @return 전체 퀴즈 페이지
     */
    Page<Quiz> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
