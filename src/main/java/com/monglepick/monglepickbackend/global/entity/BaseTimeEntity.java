package com.monglepick.monglepickbackend.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 생성일시(created_at)와 수정일시(updated_at) 공통 부모 엔티티.
 *
 * <p>대부분의 테이블이 created_at, updated_at 컬럼을 갖고 있으므로,
 * 이 클래스를 상속받아 자동으로 타임스탬프를 관리한다.</p>
 *
 * <ul>
 *   <li>{@code created_at} — 레코드 최초 생성 시각 (INSERT 시 자동 설정, 이후 변경 불가)</li>
 *   <li>{@code updated_at} — 레코드 최종 수정 시각 (UPDATE 시 자동 갱신)</li>
 * </ul>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    /**
     * 레코드 생성 시각.
     * INSERT 시 Hibernate가 자동으로 현재 시각을 설정하며, 이후 UPDATE 시에는 변경되지 않는다.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 레코드 최종 수정 시각.
     * UPDATE 시 Hibernate가 자동으로 현재 시각으로 갱신한다.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
