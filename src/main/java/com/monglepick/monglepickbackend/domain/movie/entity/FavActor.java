package com.monglepick.monglepickbackend.domain.movie.entity;

import com.monglepick.monglepickbackend.global.entity.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 선호 배우 엔티티 — fav_actors 테이블 매핑.
 *
 * <p>사용자가 온보딩 또는 프로필 설정에서 선택한 선호 배우 정보를 저장한다.
 * {@link FavGenre}, {@link FavDirector}와 동일한 패턴으로 설계되었다.</p>
 *
 * <h3>주요 필드</h3>
 * <ul>
 *   <li>{@code userId} — 사용자 ID (users.user_id 참조)</li>
 *   <li>{@code actorName} — 배우 이름 (예: "송강호", "톰 행크스")</li>
 * </ul>
 *
 * <h3>제약조건</h3>
 * <p>UNIQUE(user_id, actor_name) — 동일 사용자가 동일 배우를 중복 등록할 수 없다.</p>
 *
 * <h3>설계 결정</h3>
 * <ul>
 *   <li>FK는 {@code @Column}으로만 선언 (프로젝트 컨벤션: @ManyToOne 미사용).</li>
 *   <li>배우명은 TMDB/KMDb 데이터와 직접 매칭하므로 VARCHAR(200)으로 넉넉하게 설정.</li>
 * </ul>
 */
@Entity
@Table(
        name = "fav_actors",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "actor_name"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  /* JPA 프록시 생성용 protected 생성자 */
@AllArgsConstructor
@Builder
public class FavActor extends BaseAuditEntity {

    /**
     * 선호 배우 레코드 고유 ID (PK, BIGINT AUTO_INCREMENT, 컬럼명: fav_actor_id).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fav_actor_id")
    private Long favActorId;

    /**
     * 사용자 ID (VARCHAR(50), NOT NULL).
     * users.user_id를 참조한다.
     */
    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    /**
     * 배우 이름 (VARCHAR(200), NOT NULL).
     * 예: "송강호", "최민식", "레오나르도 디카프리오"
     */
    @Column(name = "actor_name", length = 200, nullable = false)
    private String actorName;

    /* created_at, updated_at → BaseTimeEntity에서 상속 */
    /* created_by, updated_by → BaseAuditEntity에서 상속 */
}
