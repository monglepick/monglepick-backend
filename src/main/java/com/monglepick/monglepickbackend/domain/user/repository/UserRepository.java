package com.monglepick.monglepickbackend.domain.user.repository;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 사용자 리포지토리.
 *
 * <p>users 테이블에 대한 기본 CRUD를 지원한다.
 * PK는 user_id (VARCHAR(50))이므로 JpaRepository의 ID 타입은 String이다.</p>
 *
 * <h3>인증 관련 쿼리 메서드</h3>
 * <ul>
 *   <li>{@link #findByEmail(String)} — 이메일로 사용자 조회 (로그인 시 사용)</li>
 *   <li>{@link #findByProviderAndProviderId(User.Provider, String)} — 소셜 로그인 제공자+ID로 조회</li>
 *   <li>{@link #existsByEmail(String)} — 이메일 중복 확인 (회원가입 시 사용)</li>
 *   <li>{@link #existsByNickname(String)} — 닉네임 중복 확인 (회원가입 시 사용)</li>
 * </ul>
 *
 * <h3>관리자 통계 쿼리 메서드</h3>
 * <ul>
 *   <li>{@link #countByLastLoginAtAfter(LocalDateTime)} — DAU/MAU 집계 (최종 로그인 기준)</li>
 *   <li>{@link #countByCreatedAtBetween(LocalDateTime, LocalDateTime)} — 신규 가입자 기간 집계</li>
 *   <li>{@link #findByCreatedAtBetween(LocalDateTime, LocalDateTime)} — 코호트 리텐션용 가입자 목록 조회</li>
 *   <li>{@link #countByLastLoginAtBetween(LocalDateTime, LocalDateTime)} — 기간 내 재방문자 집계</li>
 * </ul>
 */
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * 이메일로 사용자를 조회한다.
     *
     * <p>로컬 로그인 시 이메일+비밀번호 검증과
     * 소셜 로그인 시 이메일 중복 확인에 사용된다.</p>
     *
     * @param email 조회할 이메일 주소
     * @return 해당 이메일의 사용자 (없으면 빈 Optional)
     */
    Optional<User> findByEmail(String email);

    /**
     * 소셜 로그인 제공자와 제공자 고유 ID로 사용자를 조회한다.
     *
     * <p>소셜 로그인 시 기존 가입 여부를 확인할 때 사용된다.
     * 동일한 소셜 제공자+ID 조합이면 기존 사용자로 판별한다.</p>
     *
     * @param provider   소셜 로그인 제공자 (GOOGLE, KAKAO, NAVER)
     * @param providerId 제공자가 발급한 사용자 고유 ID
     * @return 해당 소셜 계정의 사용자 (없으면 빈 Optional)
     */
    Optional<User> findByProviderAndProviderId(User.Provider provider, String providerId);

    /**
     * 해당 이메일이 이미 존재하는지 확인한다.
     *
     * <p>회원가입 시 이메일 중복 검사에 사용된다.
     * COUNT 쿼리 대신 EXISTS 서브쿼리로 최적화된다.</p>
     *
     * @param email 확인할 이메일 주소
     * @return 존재하면 true, 없으면 false
     */
    boolean existsByEmail(String email);

    /**
     * 해당 닉네임이 이미 존재하는지 확인한다.
     *
     * <p>회원가입 시 닉네임 중복 검사에 사용된다.</p>
     *
     * @param nickname 확인할 닉네임
     * @return 존재하면 true, 없으면 false
     */
    boolean existsByNickname(String nickname);

    /**
     * 지정된 시간 범위 내에 생성된 사용자 수를 카운트한다.
     *
     * <p>관리자 대시보드 KPI 카드 및 추이 차트에서 일별 신규 가입 수 집계에 사용된다.
     * BaseTimeEntity의 createdAt 필드를 기준으로 조회하며,
     * {@code start} 이상 {@code end} 미만 (반열린 구간) 으로 처리한다.</p>
     *
     * @param start 범위 시작 시각 (inclusive)
     * @param end   범위 종료 시각 (exclusive)
     * @return 해당 범위에 생성된 사용자 수
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 지정 시각 이후 최종 로그인한 사용자 수를 집계한다.
     *
     * <p>DAU(일간 활성 사용자) 및 MAU(월간 활성 사용자) 집계에 사용된다.
     * lastLoginAt이 null인 경우(가입 후 한 번도 로그인하지 않은 사용자)는 제외된다.</p>
     *
     * <p>활용 예시:</p>
     * <ul>
     *   <li>DAU: after = 오늘 자정 (LocalDate.now().atStartOfDay())</li>
     *   <li>MAU: after = 30일 전 (LocalDateTime.now().minusDays(30))</li>
     * </ul>
     *
     * @param after 기준 시각 (이 시각 이후에 로그인한 사용자만 포함)
     * @return 해당 기간 내 활성 사용자 수
     */
    long countByLastLoginAtAfter(LocalDateTime after);

    /**
     * 지정 시각 이후 생성된(가입한) 사용자 수를 집계한다.
     *
     * <p>특정 기간 내 신규 가입자 수(신규 유입) 집계에 사용된다.</p>
     *
     * @param after 기준 시각
     * @return 해당 기간 내 신규 가입자 수
     */
    long countByCreatedAtAfter(LocalDateTime after);

    /**
     * 지정된 시간 범위 내에 가입한 사용자 목록을 조회한다.
     *
     * <p>코호트 리텐션 분석에서 특정 주간에 가입한 사용자 코호트를 구성할 때 사용한다.
     * 조회 대상: isDeleted=false이고 가입 시각이 범위 내인 사용자.
     * 대용량 조회 가능성이 있으므로 서비스 레이어에서 주간 단위로 나누어 호출한다.</p>
     *
     * @param start 범위 시작 시각 (inclusive)
     * @param end   범위 종료 시각 (exclusive)
     * @return 해당 범위에 가입한 사용자 목록 (userId, lastLoginAt만 필요하므로 프로젝션 고려 가능)
     */
    @Query("SELECT u FROM User u WHERE u.createdAt BETWEEN :start AND :end AND u.isDeleted = false")
    List<User> findByCreatedAtBetween(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /**
     * 지정된 시간 범위 내에 최종 로그인한 사용자 수를 집계한다.
     *
     * <p>코호트 리텐션 분석에서 특정 코호트 그룹의 특정 주간 재방문 여부를 판단할 때
     * userId 목록을 IN 절로 필터링하여 사용한다.</p>
     *
     * @param start 범위 시작 시각 (inclusive)
     * @param end   범위 종료 시각 (exclusive)
     * @return 해당 범위에 재방문한 사용자 수
     */
    long countByLastLoginAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 특정 사용자 ID 목록 중에서 지정 시간 범위 내에 로그인한 사용자 수를 집계한다.
     *
     * <p>코호트 리텐션 계산에서 "N주차 코호트 중 M주차에 재방문한 사용자 비율"을
     * 계산할 때 사용한다.</p>
     *
     * @param userIds 코호트에 속하는 사용자 ID 목록
     * @param start   재방문 판정 범위 시작 시각
     * @param end     재방문 판정 범위 종료 시각
     * @return 해당 코호트 중 재방문한 사용자 수
     */
    @Query("SELECT COUNT(u) FROM User u " +
           "WHERE u.userId IN :userIds " +
           "AND u.lastLoginAt BETWEEN :start AND :end")
    long countCohortRetention(@Param("userIds") List<String> userIds,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);
}
