package com.monglepick.monglepickbackend.domain.auth.repository;

import com.monglepick.monglepickbackend.domain.auth.entity.RefreshEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Refresh Token 화이트리스트 리포지토리.
 *
 * <p>Refresh Token Rotation 패턴에서 토큰 존재 여부 확인,
 * 삭제(무효화), 사용자별 전체 삭제 기능을 제공한다.</p>
 */
public interface RefreshRepository extends JpaRepository<RefreshEntity, Long> {

    /** 해당 Refresh Token이 화이트리스트에 존재하는지 확인 */
    boolean existsByRefreshToken(String refreshToken);

    /** 특정 Refresh Token을 화이트리스트에서 삭제 (토큰 무효화) */
    void deleteByRefreshToken(String refreshToken);

    /** 특정 사용자의 모든 Refresh Token 삭제 (로그아웃, 계정 삭제 시) */
    void deleteByUserId(String userId);
}
