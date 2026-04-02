package com.monglepick.monglepickbackend.domain.user.mapper;

import com.monglepick.monglepickbackend.domain.user.entity.UserPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 사용자 선호도 MyBatis Mapper.
 *
 * <p>user_preferences 테이블의 CRUD를 담당한다.
 * AI 추천 에이전트가 참조하는 선호 장르, 분위기, 플랫폼 등을 관리한다.</p>
 *
 * <p>SQL 정의: {@code resources/mapper/user/UserPreferenceMapper.xml}</p>
 */
@Mapper
public interface UserPreferenceMapper {

    /** PK로 선호도 조회 */
    UserPreference findById(@Param("preferenceId") Long preferenceId);

    /** 사용자 ID로 선호도 조회 */
    UserPreference findByUserId(@Param("userId") String userId);

    /** 선호도 신규 등록 (INSERT) */
    void insert(UserPreference preference);

    /** 선호도 수정 (UPDATE) */
    void update(UserPreference preference);

    /** 선호도 삭제 */
    void deleteById(@Param("preferenceId") Long preferenceId);
}
