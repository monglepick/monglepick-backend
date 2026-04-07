package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.AchievementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 업적 유형 마스터 레포지토리 — achievement_types 테이블 접근.
 *
 * <p>업적 유형 코드 조회와 활성 목록 조회가 주 용도이다.
 * 서비스 레이어에서 업적 달성 처리 시 {@code findByAchievementCode}로
 * {@link AchievementType} 엔티티를 조회한 뒤 {@link com.monglepick.monglepickbackend.domain.roadmap.entity.UserAchievement}에 FK로 연결한다.</p>
 */
public interface AchievementTypeRepository extends JpaRepository<AchievementType, Long> {

    /**
     * 업적 코드(achievement_code)로 업적 유형을 단건 조회한다.
     *
     * <p>업적 달성 처리 시 코드 문자열로 마스터 엔티티를 찾을 때 사용한다.
     * achievement_code 컬럼에 UNIQUE 제약이 있으므로 결과는 0건 또는 1건이다.</p>
     *
     * @param achievementCode 업적 코드 (예: "course_complete", "quiz_perfect")
     * @return 업적 유형 Optional (코드가 없으면 empty)
     */
    Optional<AchievementType> findByAchievementCode(String achievementCode);

    /**
     * 활성화된 업적 유형 전체 목록을 코드 오름차순으로 조회한다.
     *
     * <p>관리자 페이지 목록 표시, 사용자 업적 목록 API 등에서 사용한다.
     * {@code is_active = true}인 항목만 반환하며, {@code achievement_code} 기준으로 정렬한다.</p>
     *
     * @return 활성 업적 유형 리스트 (코드 오름차순)
     */
    List<AchievementType> findAllByIsActiveTrueOrderByAchievementCodeAsc();

    /**
     * 활성화된 업적 유형 전체 목록을 조회한다 (카테고리 필터 없음).
     *
     * <p>프론트엔드에서 카테고리 파라미터 없이 전체 업적 목록을 요청할 때 사용한다.
     * is_active = true인 항목만 반환한다.</p>
     *
     * @return 활성 업적 유형 리스트
     */
    List<AchievementType> findByIsActiveTrue();

    /**
     * 특정 카테고리의 활성화된 업적 유형 목록을 조회한다.
     *
     * <p>프론트엔드에서 VIEWING/SOCIAL/COLLECTION/CHALLENGE 카테고리별 필터링 시 사용한다.
     * is_active = true AND category = ? 조건으로 조회한다.</p>
     *
     * @param category 필터링할 카테고리 값 (VIEWING/SOCIAL/COLLECTION/CHALLENGE)
     * @return 해당 카테고리의 활성 업적 유형 리스트
     */
    List<AchievementType> findByCategoryAndIsActiveTrue(String category);

    /**
     * 업적 코드 중복 확인 (관리자 신규 등록 시 사전 검증).
     *
     * <p>achievement_code 컬럼에 UNIQUE 제약이 있으므로, INSERT 전에 이 메서드로
     * 중복 여부를 사전 확인하여 사용자 친화적인 409 응답을 반환한다.</p>
     *
     * @param achievementCode 검사할 업적 코드
     * @return 이미 존재하면 true
     */
    boolean existsByAchievementCode(String achievementCode);

    /**
     * 관리자용 — 업적 유형 전체 페이징 조회 (활성/비활성 모두 포함).
     *
     * <p>활성 상태에 관계없이 모든 업적 유형을 페이징하여 조회한다.
     * 정렬은 호출자의 {@link Pageable}이 결정한다.</p>
     *
     * @param pageable 페이지 정보 (page/size/sort)
     * @return 업적 유형 페이지
     */
    Page<AchievementType> findAll(Pageable pageable);
}
