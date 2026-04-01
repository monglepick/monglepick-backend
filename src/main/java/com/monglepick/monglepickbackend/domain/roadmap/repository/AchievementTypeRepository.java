package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.AchievementType;
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
}
