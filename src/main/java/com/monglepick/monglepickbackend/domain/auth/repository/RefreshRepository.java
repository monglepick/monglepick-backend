package com.monglepick.monglepickbackend.domain.auth.repository;

import com.monglepick.monglepickbackend.domain.auth.domain.jwt.entity.RefreshEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshRepository extends JpaRepository<RefreshEntity, Long> {

    Boolean existsByRefresh(String refreshToken);

    // 삭제 메서드
    @Transactional
    void deleteByRefresh(String refresh);


    @Transactional
    void deleteByUserEmail(String useremail);
}
