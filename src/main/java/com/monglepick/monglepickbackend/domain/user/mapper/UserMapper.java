package com.monglepick.monglepickbackend.domain.user.mapper;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 사용자 MyBatis Mapper.
 *
 * <p>users 테이블의 CRUD 및 인증 관련 조회를 담당한다.
 * AuthService, JwtService, LoginSuccessHandler 등에서 사용된다.</p>
 *
 * <p>SQL 정의: {@code resources/mapper/user/UserMapper.xml}</p>
 */
@Mapper
public interface UserMapper {

    /** PK(user_id)로 사용자 조회 */
    User findById(@Param("userId") String userId);

    /** 이메일로 사용자 조회 (로컬 로그인) */
    User findByEmail(@Param("email") String email);

    /** 소셜 제공자 + 제공자 ID로 사용자 조회 (소셜 로그인) */
    User findByProviderAndProviderId(@Param("provider") String provider,
                                     @Param("providerId") String providerId);

    /** 이메일 중복 여부 확인 */
    boolean existsByEmail(@Param("email") String email);

    /** 닉네임 중복 여부 확인 */
    boolean existsByNickname(@Param("nickname") String nickname);

    /** 사용자 신규 등록 (INSERT) */
    void insert(User user);

    /** 사용자 정보 수정 (UPDATE) — 닉네임, 프로필 이미지, 비밀번호 등 */
    void update(User user);

    /** 최종 로그인 시각 갱신 */
    void updateLastLoginAt(@Param("userId") String userId);
}
