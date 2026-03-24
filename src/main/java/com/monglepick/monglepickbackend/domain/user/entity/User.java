package com.monglepick.monglepickbackend.domain.user.entity;



import com.monglepick.monglepickbackend.domain.auth.dto.UserRequestDTO;
import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;


@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name="users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @UuidGenerator(style= UuidGenerator.Style.RANDOM)
    @Column(name="user_id", nullable = false)
    private String userId;

    @Column(name="user_email", nullable = true)
    private String userEmail;

    @Column(name="user_password", nullable = false)
    private String userPassword;

    @Column(name="is_social", nullable = false)
    private Boolean isSocial;

    @Column(name="user_birth, nullable=false")
    private String userBirth;

    @Enumerated(EnumType.STRING)
    @Column(name="social_provider_type")
    private SocialProviderType socialProviderType;

    @Enumerated(EnumType.STRING)
    @Column(name="role_type", nullable = false)
    private UserRoleType roleType;

    @Column(name ="user_nickname")
    private String userNickname;


    @Column(name="profile_img")
    private String profileImg;

    @CreatedDate
    @Column(name="created_at",updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name="updated_at")
    private LocalDateTime updateDate;


    public void updateUser(UserRequestDTO dto) {
        this.userNickname = dto.getUsernickname();
    }
}


