package com.monglepick.monglepickbackend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 설정.
 *
 * <p>{@code @EnableJpaAuditing}을 선언하여 엔티티의
 * {@code @CreationTimestamp}, {@code @UpdateTimestamp},
 * {@code @CreatedDate}, {@code @LastModifiedDate} 어노테이션이
 * 자동으로 동작하도록 한다.</p>
 *
 * <p>{@link com.monglepick.monglepickbackend.global.entity.BaseTimeEntity}의
 * created_at, updated_at 필드가 이 설정에 의해 자동 관리된다.</p>
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
