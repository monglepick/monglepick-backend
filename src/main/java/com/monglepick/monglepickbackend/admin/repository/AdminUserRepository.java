package com.monglepick.monglepickbackend.admin.repository;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.global.constants.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 사용자 관리 전용 JPA 리포지토리 — users 테이블 접근 계층.
 *
 * <p>도메인 {@code UserRepository}가 담당하지 않는 관리자 전용 조회 쿼리를 제공한다.
 * 검색(닉네임·이메일 키워드), 상태 필터, 역할 필터, 복합 필터 등을 지원한다.</p>
 *
 * <p>소프트 삭제(is_deleted=true)된 탈퇴 회원은 기본 조회에서 제외한다.
 * 관리자가 탈퇴 회원 조회를 원하는 경우 별도 쿼리 메서드를 추가해야 한다.</p>
 *
 * <h3>PK 타입</h3>
 * <p>User 엔티티의 PK는 {@code user_id VARCHAR(50)}이므로
 * JpaRepository의 ID 타입은 {@code String}이다.</p>
 *
 * <h3>주요 메서드</h3>
 * <ul>
 *   <li>{@link #searchUsers(String, Pageable)} — 닉네임·이메일 키워드 검색 (탈퇴 제외)</li>
 *   <li>{@link #findByStatusAndIsDeletedFalse(User.UserStatus, Pageable)} — 상태 필터 조회</li>
 *   <li>{@link #findByUserRoleAndIsDeletedFalse(UserRole, Pageable)} — 역할 필터 조회</li>
 *   <li>{@link #findByStatusAndUserRoleAndIsDeletedFalse(User.UserStatus, UserRole, Pageable)} — 상태+역할 복합 필터</li>
 *   <li>{@link #findAllByIsDeletedFalse(Pageable)} — 전체 사용자 목록 (탈퇴 제외)</li>
 * </ul>
 */
public interface AdminUserRepository extends JpaRepository<User, String> {

    /**
     * 닉네임 또는 이메일에 키워드가 포함된 사용자를 검색한다 (탈퇴 회원 제외).
     *
     * <p>대소문자 구분 없이(LOWER 함수) 검색하며, 닉네임과 이메일 중 하나라도
     * 키워드를 포함하면 결과에 포함된다. 소프트 삭제된 탈퇴 회원은 제외한다.</p>
     *
     * <p>인덱스: users.email, users.nickname 각각 LIKE 검색이므로
     * 대규모 데이터에서는 full-text search 도입을 고려해야 한다.</p>
     *
     * @param keyword  검색 키워드 (닉네임 또는 이메일 부분 일치)
     * @param pageable 페이징 정보
     * @return 키워드에 매칭되는 사용자 페이지
     */
    @Query("SELECT u FROM User u " +
            "WHERE u.isDeleted = false " +
            "AND (LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 특정 계정 상태의 사용자 목록을 조회한다 (탈퇴 제외).
     *
     * <p>관리자 화면에서 "정지된 회원만 보기", "잠긴 회원만 보기" 필터 적용 시 사용한다.</p>
     *
     * @param status   조회할 계정 상태 (ACTIVE, SUSPENDED, LOCKED)
     * @param pageable 페이징 정보
     * @return 해당 상태의 사용자 페이지
     */
    Page<User> findByStatusAndIsDeletedFalse(User.UserStatus status, Pageable pageable);

    /**
     * 특정 역할의 사용자 목록을 조회한다 (탈퇴 제외).
     *
     * <p>관리자 화면에서 "관리자만 보기" 등 역할 필터 적용 시 사용한다.</p>
     *
     * @param userRole 조회할 역할 (USER, ADMIN)
     * @param pageable 페이징 정보
     * @return 해당 역할의 사용자 페이지
     */
    Page<User> findByUserRoleAndIsDeletedFalse(UserRole userRole, Pageable pageable);

    /**
     * 계정 상태와 역할을 모두 조건으로 사용자 목록을 조회한다 (탈퇴 제외).
     *
     * <p>관리자 화면에서 상태 필터와 역할 필터를 동시에 적용할 때 사용한다.</p>
     *
     * @param status   계정 상태 (ACTIVE, SUSPENDED, LOCKED)
     * @param userRole 역할 (USER, ADMIN)
     * @param pageable 페이징 정보
     * @return 상태+역할 조건에 맞는 사용자 페이지
     */
    Page<User> findByStatusAndUserRoleAndIsDeletedFalse(
            User.UserStatus status,
            UserRole userRole,
            Pageable pageable
    );

    /**
     * 전체 사용자 목록을 조회한다 (탈퇴 회원 제외).
     *
     * <p>검색 키워드·상태·역할 필터가 없는 기본 목록 조회에 사용한다.</p>
     *
     * @param pageable 페이징 정보
     * @return 탈퇴하지 않은 전체 사용자 페이지
     */
    Page<User> findAllByIsDeletedFalse(Pageable pageable);

    /**
     * 키워드 + 상태 복합 검색 (탈퇴 제외).
     *
     * <p>닉네임 또는 이메일 키워드 검색에 계정 상태 필터를 추가로 적용한다.</p>
     *
     * @param keyword  검색 키워드 (닉네임 또는 이메일 부분 일치)
     * @param status   계정 상태 필터 (ACTIVE, SUSPENDED, LOCKED)
     * @param pageable 페이징 정보
     * @return 조건에 맞는 사용자 페이지
     */
    @Query("SELECT u FROM User u " +
            "WHERE u.isDeleted = false " +
            "AND u.status = :status " +
            "AND (LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchUsersByStatus(
            @Param("keyword") String keyword,
            @Param("status") User.UserStatus status,
            Pageable pageable
    );

    /**
     * 키워드 + 역할 복합 검색 (탈퇴 제외).
     *
     * <p>닉네임 또는 이메일 키워드 검색에 역할 필터를 추가로 적용한다.</p>
     *
     * @param keyword  검색 키워드 (닉네임 또는 이메일 부분 일치)
     * @param userRole 역할 필터 (USER, ADMIN)
     * @param pageable 페이징 정보
     * @return 조건에 맞는 사용자 페이지
     */
    @Query("SELECT u FROM User u " +
            "WHERE u.isDeleted = false " +
            "AND u.userRole = :userRole " +
            "AND (LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchUsersByRole(
            @Param("keyword") String keyword,
            @Param("userRole") UserRole userRole,
            Pageable pageable
    );

    /**
     * 키워드 + 상태 + 역할 3중 복합 검색 (탈퇴 제외).
     *
     * <p>세 가지 조건을 동시에 적용하는 가장 세밀한 검색 메서드.
     * 키워드·상태·역할 필터가 모두 있을 때 사용한다.</p>
     *
     * @param keyword  검색 키워드 (닉네임 또는 이메일 부분 일치)
     * @param status   계정 상태 필터
     * @param userRole 역할 필터
     * @param pageable 페이징 정보
     * @return 조건에 맞는 사용자 페이지
     */
    @Query("SELECT u FROM User u " +
            "WHERE u.isDeleted = false " +
            "AND u.status = :status " +
            "AND u.userRole = :userRole " +
            "AND (LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<User> searchUsersByStatusAndRole(
            @Param("keyword") String keyword,
            @Param("status") User.UserStatus status,
            @Param("userRole") UserRole userRole,
            Pageable pageable
    );
}
