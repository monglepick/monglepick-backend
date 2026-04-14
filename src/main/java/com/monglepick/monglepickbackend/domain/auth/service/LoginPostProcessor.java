package com.monglepick.monglepickbackend.domain.auth.service;

import com.monglepick.monglepickbackend.admin.repository.AdminAccountRepository;
import com.monglepick.monglepickbackend.admin.service.AdminAuditService;
import com.monglepick.monglepickbackend.domain.user.entity.Admin;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.constants.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 로그인 성공 후 부가 처리 전담 서비스 — 2026-04-14 신규.
 *
 * <p>로그인 성공 핸들러({@link com.monglepick.monglepickbackend.domain.auth.handler.LoginSuccessHandler})
 * 는 Spring Security 필터 체인에서 호출되며, 기본적으로 트랜잭션 컨텍스트 밖에 있다.
 * 그 상태에서 JPA {@code save()}/MyBatis UPDATE 여러 개를 직접 호출하면 각각 별도
 * 트랜잭션으로 실행되거나 {@code Transaction is not active} 예외가 발생할 수 있다.</p>
 *
 * <p>본 서비스는 로그인 성공 이후 수행되어야 할 부가 작업(최종 로그인 시각 갱신, 관리자
 * 접속 감사 로그 기록)을 단일 트랜잭션 경계 내에서 모아 처리한다. 핸들러는 이 서비스의
 * 메서드 한 번만 호출하면 되므로 책임 분리와 테스트 용이성도 확보된다.</p>
 *
 * <h3>왜 LoginSuccessHandler 에 직접 @Transactional 을 달지 않았는가</h3>
 * <ul>
 *   <li>{@code onAuthenticationSuccess} 는 {@code response.getWriter()} 로 HTTP 응답을
 *       기록하는 I/O 를 포함한다. 이 I/O 구간까지 트랜잭션이 열려 있으면 커넥션 보유
 *       시간이 불필요하게 길어진다.</li>
 *   <li>부가 처리(DB 쓰기) 구간만 짧은 트랜잭션으로 분리하는 편이 커넥션 풀 효율에
 *       유리하다.</li>
 * </ul>
 *
 * <h3>실패 내성 정책</h3>
 * <p>로그인 자체는 이미 성공한 상태이므로, 부가 처리 실패가 로그인 자체를 실패시키면
 * 안 된다. 모든 쓰기 시도는 try/catch 로 감싸 예외를 삼키고 {@code log.warn} 만 남긴다.
 * 감사 로그({@link AdminAuditService}) 는 이미 REQUIRES_NEW 로 자체 격리되어 있다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginPostProcessor {

    /** 사용자 정보 조회/갱신 MyBatis Mapper */
    private final UserMapper userMapper;

    /** 관리자 계정 JPA Repository (Admin.lastLoginAt 갱신용) */
    private final AdminAccountRepository adminAccountRepository;

    /** 관리자 감사 로그 기록 서비스 (ACTION_ADMIN_LOGIN) */
    private final AdminAuditService adminAuditService;

    /**
     * 로그인 성공 후처리 엔트리포인트.
     *
     * <p>수행 작업:</p>
     * <ol>
     *   <li>users.last_login_at 갱신 — 모든 로그인에서 DAU/MAU 집계를 위해 필수.</li>
     *   <li>ROLE_ADMIN 사용자인 경우:
     *     <ul>
     *       <li>admin.last_login_at 갱신 (관리자 마지막 접속 시각).</li>
     *       <li>{@code admin_audit_logs} 테이블에 ADMIN_LOGIN 이벤트 기록.</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p>각 작업은 독립적으로 try/catch 로 감싸 일부 실패가 다른 작업을 막지 않도록 한다.</p>
     *
     * @param user 로그인에 성공한 사용자 엔티티
     */
    @Transactional
    public void processLogin(User user) {
        if (user == null) return;

        String userId = user.getUserId();

        // ── 1. users.last_login_at 갱신 ──
        try {
            userMapper.updateLastLoginAt(userId);
        } catch (Exception e) {
            log.warn("[LoginPostProcessor] users.last_login_at 갱신 실패 — userId={}, err={}",
                    userId, e.getMessage());
        }

        // ── 2. ADMIN 사용자 전용 부가 처리 ──
        if (user.getUserRole() == UserRole.ADMIN) {
            updateAdminLastLoginAt(userId);
            // 관리자 접속 감사 로그 — REQUIRES_NEW 로 자체 격리
            String description = "관리자 로그인 성공 — email=" + user.getEmail()
                    + ", nickname=" + user.getNickname();
            adminAuditService.log(
                    AdminAuditService.ACTION_ADMIN_LOGIN,
                    AdminAuditService.TARGET_ADMIN,
                    userId,
                    description
            );
        }
    }

    /**
     * Admin 엔티티의 lastLoginAt 를 현재 시각으로 갱신한다.
     *
     * <p>Admin 레코드가 존재하지 않으면(예: users.user_role=ADMIN 이지만 admin 테이블
     * INSERT 누락) 조용히 무시한다. 이는 운영 중 데이터 정합성 문제가 로그인 자체를
     * 막지 않도록 하기 위함이다.</p>
     *
     * @param userId 관리자 사용자 ID
     */
    private void updateAdminLastLoginAt(String userId) {
        try {
            adminAccountRepository.findByUserId(userId).ifPresent(admin -> {
                // Builder 로 새 엔티티를 만들어 save() — dirty checking 을 활용하는 방식과
                // 동일한 결과를 얻되, 기존 AdminSettingsService.updateAdminRole() 와 같은
                // 패턴을 유지하여 코드 일관성을 확보한다.
                Admin updated = Admin.builder()
                        .adminId(admin.getAdminId())
                        .userId(admin.getUserId())
                        .adminRole(admin.getAdminRole())
                        .isActive(admin.getIsActive())
                        .lastLoginAt(LocalDateTime.now())
                        .build();
                adminAccountRepository.save(updated);
            });
        } catch (Exception e) {
            log.warn("[LoginPostProcessor] admin.last_login_at 갱신 실패 — userId={}, err={}",
                    userId, e.getMessage());
        }
    }
}
