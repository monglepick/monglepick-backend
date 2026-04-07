package com.monglepick.monglepickbackend.domain.user.mapper;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 MyBatis Mapper.
 *
 * <p>users 테이블의 CRUD, 인증 조회, 관리자 통계용 집계 쿼리를 담당한다.
 * AuthService, JwtService, LoginSuccessHandler, UserService, AdminStatsService,
 * AdminDashboardService 등에서 사용된다.</p>
 *
 * <p>SQL 정의: {@code resources/mapper/user/UserMapper.xml}</p>
 *
 * <p><b>JPA/MyBatis 하이브리드 (§15)</b>: User {@code @Entity}는 DDL 정의 전용으로만 사용되며
 * 데이터 R/W는 이 Mapper로 100% 처리한다. JpaRepository는 작성/사용하지 않는다.</p>
 */
@Mapper
public interface UserMapper {

    // ═══ 기본 조회 ═══

    /** PK(user_id)로 사용자 조회 (없으면 null) */
    User findById(@Param("userId") String userId);

    /** 이메일로 사용자 조회 (로컬 로그인, 없으면 null) */
    User findByEmail(@Param("email") String email);

    /** 소셜 제공자 + 제공자 ID로 사용자 조회 (소셜 로그인, 없으면 null) */
    User findByProviderAndProviderId(@Param("provider") String provider,
                                     @Param("providerId") String providerId);

    /** 이메일 중복 여부 확인 */
    boolean existsByEmail(@Param("email") String email);

    /** 닉네임 중복 여부 확인 */
    boolean existsByNickname(@Param("nickname") String nickname);

    /** PK 존재 여부 확인 */
    boolean existsById(@Param("userId") String userId);

    // ═══ 쓰기 ═══

    /** 사용자 신규 등록 (INSERT) */
    void insert(User user);

    /** 사용자 정보 수정 (UPDATE) — 닉네임, 프로필 이미지, 비밀번호, 상태 등 변경 가능 필드 일괄 */
    void update(User user);

    /** 최종 로그인 시각 갱신 (단건 UPDATE — 전체 UPDATE 오버헤드 회피) */
    void updateLastLoginAt(@Param("userId") String userId);

    // ═══ 통계/집계 (관리자 통계/대시보드용) ═══

    /** 전체 사용자 수 (탈퇴 포함) */
    long count();

    /** 전체 사용자 페이징 조회 (정렬은 created_at DESC 고정). AdminStatsService 행동 통계용. */
    List<User> findAllLimited(@Param("limit") int limit);

    /** 지정 시각 이후 최종 로그인한 사용자 수 — DAU/MAU 집계용 */
    long countByLastLoginAtAfter(@Param("after") LocalDateTime after);

    /** 지정 기간 내 최종 로그인한 사용자 수 — 일별 DAU, 코호트 재방문 집계용 */
    long countByLastLoginAtBetween(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    /** 지정 시각 이후 신규 가입한 사용자 수 */
    long countByCreatedAtAfter(@Param("after") LocalDateTime after);

    /** 지정 기간 내 신규 가입한 사용자 수 — 일별/주별 신규 유입 집계용 */
    long countByCreatedAtBetween(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    /** 지정 기간 내 가입한 사용자 목록 — 코호트 리텐션 계산용 (탈퇴 제외) */
    List<User> findByCreatedAtBetween(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    /**
     * 특정 사용자 ID 목록 중 지정 기간 내 재방문한 사용자 수.
     *
     * <p>코호트 리텐션 계산: "N주차 코호트 중 M주차에 재방문한 사용자 수".</p>
     */
    long countCohortRetention(@Param("userIds") List<String> userIds,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);
}
