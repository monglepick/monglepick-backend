package com.monglepick.monglepickbackend.domain.user.repository;

import com.monglepick.monglepickbackend.domain.user.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 사용자 선호도 JPA 리포지토리
 *
 * <p>MySQL user_preferences 테이블에 대한 데이터 접근 레이어입니다.
 * 사용자별 영화 선호도(장르, 분위기, 플랫폼 등)를 관리합니다.</p>
 */
@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    /**
     * 특정 사용자의 선호도를 조회합니다.
     * <p>마이페이지 선호도 표시 및 AI 추천 시 참조됩니다.</p>
     *
     * @param userId 사용자 ID
     * @return 사용자 선호도 Optional
     */
    Optional<UserPreference> findByUserId(Long userId);
}
