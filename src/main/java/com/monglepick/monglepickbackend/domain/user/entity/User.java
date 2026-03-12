package com.monglepick.monglepickbackend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 엔티티
 *
 * <p>MySQL users 테이블과 매핑됩니다.
 * 회원가입, 로그인, 프로필 관리에 사용됩니다.</p>
 *
 * <p>사용자 역할(Role):</p>
 * <ul>
 *   <li>USER: 일반 사용자 (기본값)</li>
 *   <li>ADMIN: 관리자 (커뮤니티 관리, 콘텐츠 관리 권한)</li>
 * </ul>
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /** 사용자 고유 식별자 (AUTO_INCREMENT) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이메일 주소 (로그인 ID로 사용, 유니크) */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** 닉네임 (커뮤니티 표시명, 유니크) */
    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    /** 비밀번호 (BCrypt 해시값 저장) */
    @Column(nullable = false, length = 255)
    private String password;

    /** 프로필 이미지 URL (선택사항) */
    @Column(name = "profile_image", length = 500)
    private String profileImage;

    /** 사용자 역할 (USER 또는 ADMIN) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** 계정 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 정보 수정 시각 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 사용자 역할 열거형
     */
    public enum Role {
        /** 일반 사용자 */
        USER,
        /** 관리자 */
        ADMIN
    }

    /**
     * 빌더 패턴을 통한 사용자 생성
     *
     * @param email 이메일 주소
     * @param nickname 닉네임
     * @param password BCrypt로 암호화된 비밀번호
     * @param profileImage 프로필 이미지 URL (선택)
     * @param role 사용자 역할 (기본: USER)
     */
    @Builder
    public User(String email, String nickname, String password,
                String profileImage, Role role) {
        this.email = email;
        this.nickname = nickname;
        this.password = password;
        this.profileImage = profileImage;
        // 역할이 지정되지 않으면 기본값 USER 사용
        this.role = role != null ? role : Role.USER;
    }

    /**
     * 엔티티 저장 전 자동으로 생성/수정 시각을 설정합니다.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 엔티티 수정 전 자동으로 수정 시각을 갱신합니다.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 닉네임을 변경합니다.
     *
     * @param nickname 새로운 닉네임
     */
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 프로필 이미지를 변경합니다.
     *
     * @param profileImage 새로운 프로필 이미지 URL
     */
    public void updateProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    /**
     * 비밀번호를 변경합니다.
     *
     * @param password BCrypt로 암호화된 새 비밀번호
     */
    public void updatePassword(String password) {
        this.password = password;
    }
}
