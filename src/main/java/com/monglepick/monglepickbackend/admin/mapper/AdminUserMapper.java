package com.monglepick.monglepickbackend.admin.mapper;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.global.constants.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 관리자 사용자 관리 전용 MyBatis Mapper — admin 패키지 소유.
 *
 * <p>도메인 {@link com.monglepick.monglepickbackend.domain.user.mapper.UserMapper}가
 * 담당하지 않는 관리자 전용 동적 검색/필터 쿼리를 제공한다.
 * 닉네임·이메일 키워드 검색, 상태 필터, 역할 필터, 복합 필터를 지원한다.</p>
 *
 * <p>소프트 삭제({@code is_deleted=true})된 탈퇴 회원은 모든 조회에서 기본적으로 제외한다.</p>
 *
 * <h3>JPA/MyBatis 하이브리드 (§15.5)</h3>
 * <p>기존 {@code admin/repository/AdminUserRepository.java} (JpaRepository)를 대체한다.
 * 동적 SQL은 MyBatis XML의 {@code <if>} 태그로 구성하여 WHERE 절 조합을 단순화한다.</p>
 *
 * <h3>페이징 방식</h3>
 * <p>Spring Data의 {@code Pageable}+{@code Page<T>} 대신 명시적인 {@code offset}+{@code size}
 * 파라미터를 받고 List를 반환한다. 전체 개수가 필요한 경우 별도 count 메서드를 호출하여
 * Service 레이어에서 {@code PageImpl}을 조립한다.</p>
 *
 * <h3>통합 동적 쿼리</h3>
 * <p>기존 Repository의 8개 메서드(searchUsers/searchUsersByStatus/… /findAllByIsDeletedFalse)는
 * 모두 같은 형태의 WHERE 절 조합이었으므로 단일 메서드로 통합했다:</p>
 * <ul>
 *   <li>{@link #searchUsers(String, User.UserStatus, UserRole, int, int)} — 목록 조회 (동적 WHERE)</li>
 *   <li>{@link #countSearchUsers(String, User.UserStatus, UserRole)} — 동일 조건 총 개수</li>
 * </ul>
 *
 * <p>SQL 정의: {@code resources/mapper/admin/AdminUserMapper.xml}</p>
 */
@Mapper
public interface AdminUserMapper {

    /**
     * 관리자 사용자 목록 검색 (탈퇴 제외, 동적 필터).
     *
     * <p>키워드(닉네임·이메일 부분 일치, 대소문자 무시), 계정 상태, 역할 필터를 선택적으로 적용한다.
     * 각 파라미터가 null 또는 빈 문자열이면 해당 필터를 건너뛴다.</p>
     *
     * @param keyword  검색 키워드 (닉네임 또는 이메일 부분 일치, null/빈값이면 미적용)
     * @param status   계정 상태 필터 (ACTIVE/SUSPENDED/LOCKED, null이면 미적용)
     * @param userRole 역할 필터 (USER/ADMIN, null이면 미적용)
     * @param offset   건너뛸 레코드 수 (0부터 시작)
     * @param size     가져올 최대 레코드 수
     * @return 조건에 매칭되는 사용자 목록 (created_at DESC 정렬)
     */
    List<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("status") User.UserStatus status,
            @Param("userRole") UserRole userRole,
            @Param("offset") int offset,
            @Param("size") int size
    );

    /**
     * 관리자 사용자 목록 검색 조건의 총 개수를 반환한다.
     *
     * <p>{@link #searchUsers}와 동일한 동적 WHERE 절을 사용한다.
     * Service 레이어에서 {@code PageImpl}의 totalElements로 사용한다.</p>
     *
     * @param keyword  검색 키워드 (null/빈값이면 미적용)
     * @param status   계정 상태 필터 (null이면 미적용)
     * @param userRole 역할 필터 (null이면 미적용)
     * @return 조건에 매칭되는 전체 사용자 수
     */
    long countSearchUsers(
            @Param("keyword") String keyword,
            @Param("status") User.UserStatus status,
            @Param("userRole") UserRole userRole
    );
}
