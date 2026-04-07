package com.monglepick.monglepickbackend.domain.roadmap.repository;

import com.monglepick.monglepickbackend.domain.roadmap.entity.AchievementType;
import com.monglepick.monglepickbackend.domain.roadmap.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 업적 달성 이력 레포지토리 — user_achievements 테이블 접근.
 *
 * <p>사용자별 달성 기록 조회, 중복 달성 여부 확인, 전체 목록 조회 등을 담당한다.
 * {@code achievementType} 필드가 FK로 변경되었으므로 쿼리 메서드도 엔티티 참조 방식을 따른다.</p>
 *
 * <h3>JPA/MyBatis 하이브리드 (§15.4) — 2026-04-08 변경</h3>
 * <p>UserAchievement 엔티티는 users 테이블 참조를 String FK 로 직접 보관하므로
 * (User 엔티티 ManyToOne 미사용), 모든 쿼리 메서드도 String userId 를 받도록 수정.</p>
 */
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    /**
     * 특정 사용자의 업적 달성 기록 전체를 조회한다.
     *
     * <p>마이페이지 업적 목록 API 등에서 사용한다.</p>
     *
     * @param userId 조회할 사용자 ID (VARCHAR(50))
     * @return 해당 사용자의 업적 달성 기록 리스트
     */
    List<UserAchievement> findAllByUserId(String userId);

    /**
     * 특정 사용자 + 업적 유형 + 업적 키 조합으로 단건 조회한다.
     *
     * <p>업적 달성 처리 전 중복 여부를 확인할 때 사용한다.
     * (user_id, achievement_type_id, achievement_key) UNIQUE 제약에 대응하는 조회 메서드이다.</p>
     *
     * @param userId          조회할 사용자 ID
     * @param achievementType 업적 유형 엔티티 (FK)
     * @param achievementKey  업적 식별 키
     * @return 달성 기록 Optional (없으면 empty → 미달성, 있으면 → 이미 달성)
     */
    Optional<UserAchievement> findByUserIdAndAchievementTypeAndAchievementKey(
            String userId,
            AchievementType achievementType,
            String achievementKey
    );

    /**
     * 특정 사용자 + 업적 유형 조합으로 달성 기록 전체를 조회한다.
     *
     * <p>같은 유형 내 여러 키의 달성 여부를 확인할 때 사용한다.
     * 예: genre_explorer 유형 중 사용자가 달성한 장르 목록 조회.</p>
     *
     * @param userId          조회할 사용자 ID
     * @param achievementType 업적 유형 엔티티 (FK)
     * @return 해당 유형의 달성 기록 리스트
     */
    List<UserAchievement> findAllByUserIdAndAchievementType(
            String userId,
            AchievementType achievementType
    );
}
