package com.monglepick.monglepickbackend.domain.user.repository;

import com.monglepick.monglepickbackend.domain.user.entity.SocialProviderType;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByUserEmail(String userEmail); //조건을 만족하는 데이터 존재 시 종료

    Optional<User> findByUserEmailAndIsSocial(String useremail, boolean isSocial);

    Optional<User> findByUserEmail(String userEmail);

    @Transactional
    void deleteUserByUserEmail(String userEmail);

    Optional<User> findByUserEmailAndIsSocialAndSocialProviderType(
            String userEmail, Boolean isSocial, SocialProviderType socialProviderType
    );

}