package com.monglepick.monglepickbackend.global.config;

import com.monglepick.monglepickbackend.admin.repository.AdminAccountRepository;
import com.monglepick.monglepickbackend.domain.payment.entity.SubscriptionPlan;
import com.monglepick.monglepickbackend.domain.payment.entity.SubscriptionPlan.PeriodType;
import com.monglepick.monglepickbackend.domain.payment.repository.SubscriptionPlanRepository;
import com.monglepick.monglepickbackend.domain.reward.entity.PointItem;
import com.monglepick.monglepickbackend.domain.reward.repository.PointItemRepository;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
import com.monglepick.monglepickbackend.domain.user.entity.Admin;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.constants.AdminRole;
import com.monglepick.monglepickbackend.global.constants.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 애플리케이션 기동 시 시드 데이터를 초기화하는 컴포넌트.
 *
 * <p>기존 data.sql의 INSERT IGNORE를 Java 코드로 대체한다.
 * 테이블에 데이터가 이미 존재하면 스킵하므로 중복 실행해도 안전하다.</p>
 *
 * <h3>초기화 대상</h3>
 * <ul>
 *   <li>관리자 테스트 계정 (users) — 1건 (monglepick_admin@monglepick.com / admin1234)</li>
 *   <li>포인트 교환 아이템 (point_items) — 5건</li>
 *   <li>구독 상품 (subscription_plans) — 4건</li>
 * </ul>
 */
/*
 * 2026-04-23: MyBatis UserMapper.insertUser 가 `user_birth` 컬럼을 INSERT 하는데
 * User 엔티티에는 해당 필드가 없어 H2 `ddl-auto=create-drop` 환경에서 컬럼 자체가 생성되지
 * 않는다 (mapper XML 과 엔티티 간 schema drift). 운영은 수동 ALTER 로 이 컬럼이 존재하지만
 * 테스트 환경에선 ApplicationContext 로딩 중 insertUser() 호출이 실패해 contextLoads 깨짐.
 * flag 로 DataInitializer 를 test 에서 비활성화한다 (운영은 matchIfMissing=true 로 항상 활성).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.data-initializer.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DataInitializer implements ApplicationRunner {

    private final PointItemRepository pointItemRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    /** 사용자 CRUD — MyBatis Mapper (JpaRepository 폐기, 설계서 §15) */
    private final UserMapper userMapper;
    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    /**
     * 관리자 시드 계정용 포인트/쿼터 초기화 전용 주입.
     *
     * <p>관리자 시드는 회원가입 경로를 타지 않으므로, {@code user_points}/{@code user_ai_quota}
     * 레코드가 생성되지 않는다. 이 상태에서 관리자 본인이 수동 포인트 지급 등을 시도하면
     * {@code POINT_NOT_FOUND} 로 실패하므로 시드 생성 직후 초기 레코드를 함께 만든다.</p>
     */
    private final PointService pointService;

    /** 관리자 시드 계정 이메일 (환경변수: ADMIN_EMAIL) */
    @Value("${app.admin.email}")
    private String adminEmail;

    /** 관리자 시드 계정 비밀번호 (환경변수: ADMIN_PASSWORD) */
    @Value("${app.admin.password}")
    private String adminPassword;

    /** 관리자 시드 계정 닉네임 (환경변수: ADMIN_NICKNAME) */
    @Value("${app.admin.nickname}")
    private String adminNickname;

    /**
     * 관리자 시드 계정 세부 역할 (환경변수: ADMIN_ROLE) — 2026-04-09 P2-⑫ 신규.
     *
     * <p>{@code admin.admin_role} 컬럼에 저장되는 세부 역할 코드. 기본값은 "SUPER_ADMIN"
     * (시드 계정은 관리자 계정 관리 권한까지 보유해야 부팅 직후 다른 관리자 추가가 가능).
     * 운영 환경에서는 {@code ADMIN_ROLE=SUPER_ADMIN} 그대로 두고, 추가 관리자 생성 시
     * UI 에서 MODERATOR/FINANCE_ADMIN 등 세분화 역할을 지정한다.</p>
     *
     * <p>{@link AdminRole} 에 정의되지 않은 값이 들어오면 부팅 시 에러 로그 후
     * "SUPER_ADMIN" 으로 fallback 한다 (부팅 중단하지 않고 운영 복구 여지 확보).</p>
     */
    @Value("${app.admin.role:SUPER_ADMIN}")
    private String adminRole;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initAdminAccount();
        initPointItems();
        initSubscriptionPlans();
    }

    /**
     * 관리자 시드 계정 초기화.
     *
     * <p>application.yml의 app.admin 설정(환경변수 오버라이드 가능)을 기반으로
     * 관리자 계정이 존재하지 않을 때만 생성한다.
     * 비밀번호는 BCrypt로 해싱되어 저장된다.</p>
     *
     * <p>환경변수: ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NICKNAME</p>
     */
    private void initAdminAccount() {
        if (userMapper.existsByEmail(adminEmail)) {
            log.info("관리자 테스트 계정 이미 존재 — 스킵 (email: {})", adminEmail);
            return;
        }

        User admin = User.builder()
                .userId(UUID.randomUUID().toString())
                .email(adminEmail)
                .nickname(adminNickname)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .provider(User.Provider.LOCAL)
                .userRole(UserRole.ADMIN)
                .requiredTerm(true)
                .optionTerm(false)
                .marketingAgreed(false)
                .build();

        // MyBatis insert — PK는 수동 생성한 UUID(userId)로 세팅되어 그대로 저장됨
        userMapper.insert(admin);

        /*
         * admin 테이블 레코드 생성 — 2026-04-09 P2-⑫ 환경변수 기반 역할 지정.
         *
         * 기존에는 "ADMIN" 하드코딩이었으나, 세분화된 역할 체계(AdminRole enum)가 도입되어
         * 시드 계정은 관리자 계정 관리 권한까지 가진 SUPER_ADMIN 으로 생성하는 것이 기본.
         * 운영자가 ADMIN_ROLE 환경변수로 오버라이드 가능하며, 허용되지 않은 값은
         * SUPER_ADMIN 으로 fallback 하여 부팅 중단을 방지한다 (잘못된 env 설정으로 서버
         * 다운되는 상황 예방).
         */
        String resolvedRole = AdminRole.isAllowed(adminRole)
                ? adminRole
                : AdminRole.SUPER_ADMIN.getCode();
        if (!resolvedRole.equals(adminRole)) {
            log.warn("관리자 시드 역할이 허용되지 않음 — 입력값: {}, fallback: {} (허용값: {})",
                    adminRole, resolvedRole, AdminRole.allowedCodesAsString());
        }

        Admin adminRecord = Admin.builder()
                .userId(admin.getUserId())
                .adminRole(resolvedRole)
                .isActive(true)
                .build();
        adminAccountRepository.save(adminRecord);

        /*
         * user_points / user_ai_quota 초기화.
         *
         * <p>회원가입 경로(AuthService) 에서만 {@link PointService#initializePoint} 가 호출되므로
         * 관리자 시드는 누락된다. 이 상태에서 관리자 페이지 "결제/포인트 → 수동 지급" 등을 시도하면
         * {@code POINT_NOT_FOUND(P002)} 로 실패한다. 시드 생성 직후 0P 레코드를 생성하여 이후
         * 모든 포인트 경로가 정상 동작하도록 보장한다.</p>
         *
         * <p>{@code initializePoint} 는 {@code REQUIRES_NEW} + 존재 검사 + UNIQUE 제약 2중 방어가
         * 걸려 있어 멱등적이다. 재부팅 시 이미 user_points 행이 있으면 no-op 으로 빠져나온다.</p>
         */
        pointService.initializePoint(admin.getUserId(), 0);

        log.info("관리자 테스트 계정 초기화 완료 — email: {}, role: {}, userId: {}",
                adminEmail, resolvedRole, admin.getUserId());
    }

    /**
     * 포인트 교환 아이템 시드 데이터 초기화.
     *
     * <p>point_items 테이블이 비어있을 때만 5건의 기본 아이템을 적재한다.</p>
     */
    private void initPointItems() {
        if (pointItemRepository.count() > 0) {
            log.info("포인트 아이템 시드 데이터 이미 존재 — 스킵 ({}건)", pointItemRepository.count());
            return;
        }

        List<PointItem> items = List.of(
                PointItem.builder()
                        .itemName("AI 추천 1회")
                        .itemDescription("AI 영화 추천 1회 이용")
                        .itemPrice(100)
                        .itemCategory("ai_feature")
                        .build(),
                PointItem.builder()
                        .itemName("AI 추천 5회 팩")
                        .itemDescription("AI 영화 추천 5회 이용 (10% 할인)")
                        .itemPrice(450)
                        .itemCategory("ai_feature")
                        .build(),
                PointItem.builder()
                        .itemName("프로필 테마")
                        .itemDescription("프로필 커스텀 테마 적용")
                        .itemPrice(200)
                        .itemCategory("profile")
                        .build(),
                PointItem.builder()
                        .itemName("칭호 변경")
                        .itemDescription("커뮤니티 닉네임 칭호 변경")
                        .itemPrice(150)
                        .itemCategory("profile")
                        .build(),
                PointItem.builder()
                        .itemName("도장깨기 힌트")
                        .itemDescription("퀴즈 힌트 1회 사용")
                        .itemPrice(50)
                        .itemCategory("roadmap")
                        .build()
        );

        pointItemRepository.saveAll(items);
        log.info("포인트 아이템 시드 데이터 초기화 완료 — {}건", items.size());
    }

    /**
     * 구독 상품 시드 데이터 초기화.
     *
     * <p>subscription_plans 테이블이 비어있을 때만 4건의 기본 상품을 적재한다.</p>
     */
    private void initSubscriptionPlans() {
        if (subscriptionPlanRepository.count() > 0) {
            log.info("구독 상품 시드 데이터 이미 존재 — 스킵 ({}건)", subscriptionPlanRepository.count());
            return;
        }

        List<SubscriptionPlan> plans = List.of(
                SubscriptionPlan.builder()
                        .planCode("monthly_basic")
                        .name("월간 기본")
                        .periodType(PeriodType.MONTHLY)
                        .price(3900)
                        .pointsPerPeriod(3000)
                        .description("매월 3,000 포인트 지급 (AI 추천 30회)")
                        .build(),
                SubscriptionPlan.builder()
                        .planCode("monthly_premium")
                        .name("월간 프리미엄")
                        .periodType(PeriodType.MONTHLY)
                        .price(7900)
                        .pointsPerPeriod(8000)
                        .description("매월 8,000 포인트 지급 (AI 추천 80회)")
                        .build(),
                SubscriptionPlan.builder()
                        .planCode("yearly_basic")
                        .name("연간 기본")
                        .periodType(PeriodType.YEARLY)
                        .price(39000)
                        .pointsPerPeriod(40000)
                        .description("연간 40,000 포인트 지급 (AI 추천 400회)")
                        .build(),
                SubscriptionPlan.builder()
                        .planCode("yearly_premium")
                        .name("연간 프리미엄")
                        .periodType(PeriodType.YEARLY)
                        .price(79000)
                        .pointsPerPeriod(100000)
                        .description("연간 100,000 포인트 지급 (AI 추천 1,000회)")
                        .build()
        );

        subscriptionPlanRepository.saveAll(plans);
        log.info("구독 상품 시드 데이터 초기화 완료 — {}건", plans.size());
    }
}
