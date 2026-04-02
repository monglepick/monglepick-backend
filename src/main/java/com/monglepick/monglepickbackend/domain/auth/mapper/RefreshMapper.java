package com.monglepick.monglepickbackend.domain.auth.mapper;

import com.monglepick.monglepickbackend.domain.auth.entity.RefreshEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Refresh Token MyBatis Mapper.
 *
 * <p>Refresh Token 화이트리스트를 관리한다.
 * 토큰 발급/갱신/삭제 시 사용되며, Refresh Token Rotation 패턴을 지원한다.</p>
 *
 * <p>SQL 정의: {@code resources/mapper/auth/RefreshMapper.xml}</p>
 */
@Mapper
public interface RefreshMapper {

    /** PK로 Refresh Token 조회 */
    RefreshEntity findById(@Param("id") Long id);

    /** Refresh Token 신규 저장 (INSERT) */
    void insert(RefreshEntity entity);

    /** 특정 Refresh Token이 화이트리스트에 존재하는지 확인 */
    boolean existsByRefreshToken(@Param("refreshToken") String refreshToken);

    /** 특정 Refresh Token 삭제 (토큰 무효화) */
    void deleteByRefreshToken(@Param("refreshToken") String refreshToken);

    /** 특정 사용자의 모든 Refresh Token 삭제 (전체 로그아웃 / 계정 삭제) */
    void deleteByUserId(@Param("userId") String userId);
}
