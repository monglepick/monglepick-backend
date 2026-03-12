package com.monglepick.monglepickbackend.domain.user.repository;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 사용자 JPA 리포지토리
 *
 * <p>MySQL users 테이블에 대한 데이터 접근 레이어입니다.
 * 회원가입 시 중복 검사, 로그인 시 사용자 조회 등에 사용됩니다.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 사용자를 조회합니다.
     * <p>로그인 시 이메일 기반 사용자 검증에 사용됩니다.</p>
     *
     * @param email 검색할 이메일 주소
     * @return 사용자 Optional (존재하지 않으면 empty)
     */
    Optional<User> findByEmail(String email);

    /**
     * 이메일 중복 여부를 확인합니다.
     * <p>회원가입 시 이미 가입된 이메일인지 검증합니다.</p>
     *
     * @param email 확인할 이메일 주소
     * @return 이미 존재하면 true
     */
    boolean existsByEmail(String email);

    /**
     * 닉네임 중복 여부를 확인합니다.
     * <p>회원가입 및 닉네임 변경 시 중복 여부를 검증합니다.</p>
     *
     * @param nickname 확인할 닉네임
     * @return 이미 존재하면 true
     */
    boolean existsByNickname(String nickname);
}
