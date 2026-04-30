package com.monglepick.monglepickbackend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 탈퇴 사용자 재가입 제한용 식별자 해시 이력.
 *
 * <p>원문 이메일/providerId를 저장하지 않고 HMAC 해시만 보관한다.
 * 데이터 R/W는 MyBatis Mapper가 담당하며, Entity는 ddl-auto 스키마 정의용이다.</p>
 */
@Entity
@Table(name = "withdrawn_user_identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawnUserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "withdrawn_id")
    private Long withdrawnId;

    @Column(name = "identity_type", length = 20, nullable = false)
    private String identityType;

    @Column(name = "identity_hash", length = 128, nullable = false)
    private String identityHash;

    @Column(name = "withdrawn_user_id", length = 50, nullable = false)
    private String withdrawnUserId;

    @Column(name = "withdrawn_at", nullable = false)
    private LocalDateTime withdrawnAt;

    @Column(name = "blocked_until", nullable = false)
    private LocalDateTime blockedUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public WithdrawnUserIdentity(String identityType, String identityHash, String withdrawnUserId,
                                 LocalDateTime withdrawnAt, LocalDateTime blockedUntil) {
        this.identityType = identityType;
        this.identityHash = identityHash;
        this.withdrawnUserId = withdrawnUserId;
        this.withdrawnAt = withdrawnAt;
        this.blockedUntil = blockedUntil;
        this.createdAt = LocalDateTime.now();
    }
}
