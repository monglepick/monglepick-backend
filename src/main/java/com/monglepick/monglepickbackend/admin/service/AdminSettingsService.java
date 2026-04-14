package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AdminAccountCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AdminAccountResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AdminRoleUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.AuditLogResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.BannerCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.BannerResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.BannerUpdateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.TermsCreateRequest;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.TermsResponse;
import com.monglepick.monglepickbackend.admin.dto.SettingsDto.TermsUpdateRequest;
import com.monglepick.monglepickbackend.admin.repository.AdminAccountRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminAuditLogRepository;
import com.monglepick.monglepickbackend.admin.repository.AdminTermsRepository;
import com.monglepick.monglepickbackend.domain.admin.entity.AdminAuditLog;
import com.monglepick.monglepickbackend.domain.content.entity.Banner;
import com.monglepick.monglepickbackend.domain.content.entity.Terms;
import com.monglepick.monglepickbackend.global.constants.AdminRole;
import com.monglepick.monglepickbackend.domain.content.mapper.ContentMapper;
import com.monglepick.monglepickbackend.domain.user.entity.Admin;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.constants.UserRole;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 설정 서비스.
 *
 * <p>관리자 페이지 "설정" 탭의 하위 기능인 약관/정책, 배너, 감사 로그, 관리자 계정 관리의
 * 비즈니스 로직을 담당한다.</p>
 *
 * <h3>담당 기능</h3>
 * <ul>
 *   <li>약관: 목록 조회, 등록, 수정, 삭제</li>
 *   <li>배너: 목록 조회, 등록, 수정, 삭제</li>
 *   <li>감사 로그: 목록 조회 (actionType 필터 지원)</li>
 *   <li>관리자 계정: 목록 조회, 역할 수정</li>
 * </ul>
 *
 * <h3>트랜잭션 전략</h3>
 * <p>클래스 레벨 {@code @Transactional(readOnly = true)}로 기본 설정하고,
 * 쓰기(INSERT/UPDATE/DELETE) 메서드에만 {@code @Transactional}을 오버라이드한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSettingsService {

    /** 약관/정책 Repository */
    private final AdminTermsRepository adminTermsRepository;

    /** 콘텐츠 통합 Mapper — AdminBannerRepository 폐기, ContentMapper로 일원화 (§15) */
    private final ContentMapper contentMapper;

    /** 관리자 감사 로그 Repository */
    private final AdminAuditLogRepository adminAuditLogRepository;

    /** 관리자 계정 Repository */
    private final AdminAccountRepository adminAccountRepository;

    /**
     * 사용자 Mapper — 관리자 계정 응답 DTO 에 email/nickname 을 채우기 위해 주입한다.
     * (2026-04-14) 기존에는 admin 테이블 컬럼만 노출되어 운영자가 "누가 관리자인지"
     * 한 눈에 파악할 수 없었다.
     */
    private final UserMapper userMapper;

    /**
     * 관리자 감사 로그 서비스 — 관리자 신규 등록/역할 변경 시 admin_audit_logs 에
     * 이벤트를 남기기 위해 주입한다. (2026-04-14)
     */
    private final AdminAuditService adminAuditService;

    // ======================== 약관/정책 ========================

    /**
     * 약관 목록을 최신 등록순으로 페이지네이션하여 조회한다.
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 약관 응답 DTO 페이지
     */
    public Page<TermsResponse> getTerms(Pageable pageable) {
        log.debug("[settings] 약관 목록 조회 — page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return adminTermsRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toTermsResponse);
    }

    /**
     * 신규 약관을 등록한다.
     *
     * @param request 약관 등록 요청 DTO
     * @return 등록된 약관 응답 DTO
     */
    @Transactional
    public TermsResponse createTerm(TermsCreateRequest request) {
        log.info("[settings] 약관 등록 — type={}, version={}", request.type(), request.version());

        Terms terms = Terms.builder()
                .title(request.title())
                .content(request.content())
                .type(request.type())
                .version(request.version())
                .isRequired(request.isRequired())
                .build();

        Terms saved = adminTermsRepository.save(terms);
        log.info("[settings] 약관 등록 완료 — termsId={}", saved.getTermsId());
        return toTermsResponse(saved);
    }

    /**
     * 기존 약관을 수정한다.
     *
     * @param id      수정할 약관 ID
     * @param request 약관 수정 요청 DTO
     * @return 수정된 약관 응답 DTO
     * @throws BusinessException 약관을 찾을 수 없는 경우 (G002)
     */
    @Transactional
    public TermsResponse updateTerm(Long id, TermsUpdateRequest request) {
        log.info("[settings] 약관 수정 — termsId={}", id);

        Terms terms = adminTermsRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "약관을 찾을 수 없습니다. id=" + id));

        terms.update(request.title(), request.content(), request.type(),
                request.version(), request.isRequired());

        log.info("[settings] 약관 수정 완료 — termsId={}", id);
        return toTermsResponse(terms);
    }

    /**
     * 약관을 삭제한다.
     *
     * <p>물리적 삭제를 수행한다. 삭제 전 존재 여부를 검증한다.</p>
     *
     * @param id 삭제할 약관 ID
     * @throws BusinessException 약관을 찾을 수 없는 경우 (G002)
     */
    @Transactional
    public void deleteTerm(Long id) {
        log.info("[settings] 약관 삭제 — termsId={}", id);

        Terms terms = adminTermsRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "약관을 찾을 수 없습니다. id=" + id));

        adminTermsRepository.delete(terms);
        log.info("[settings] 약관 삭제 완료 — termsId={}", id);
    }

    // ======================== 배너 ========================

    /**
     * 배너 목록을 정렬 순서(sortOrder 오름차순)로 페이지네이션하여 조회한다.
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 배너 응답 DTO 페이지
     */
    public Page<BannerResponse> getBanners(Pageable pageable) {
        log.debug("[settings] 배너 목록 조회 — page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        int offset = (int) pageable.getOffset();
        int limit  = pageable.getPageSize();

        List<Banner> banners = contentMapper.findAllBanners(offset, limit);
        long total = contentMapper.countAllBanners();

        List<BannerResponse> content = banners.stream()
                .map(this::toBannerResponse)
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * 현재 노출 중인 활성 배너 목록을 조회한다 — 사용자 홈 슬라이드 배너용 (2026-04-14 신규).
     *
     * <p>ContentMapper 의 기존 {@code findActiveBannersByPosition} 쿼리를 그대로 사용하며,
     * {@code is_active=true} + 현재 시각이 {@code start_date~end_date} 범위 내인 레코드만 반환한다.
     * 정렬은 {@code sort_order ASC} (낮을수록 먼저 노출). 비로그인 접근을 허용하기 위해
     * 별도 공개 엔드포인트({@code GET /api/v1/banners})에서 호출한다.</p>
     *
     * @param position 위치 필터 (예: "MAIN"). null/빈 값이면 "MAIN" 기본값.
     * @return 노출 중 배너 응답 DTO 목록 (정렬순서 ASC)
     */
    public List<BannerResponse> getActiveBanners(String position) {
        // position 생략 시 기본값 "MAIN" — 홈 메인 영역 배너만 반환
        String effectivePosition = (position == null || position.isBlank()) ? "MAIN" : position;
        log.debug("[settings] 활성 배너 조회(공개) — position={}", effectivePosition);

        List<Banner> banners = contentMapper.findActiveBannersByPosition(effectivePosition);
        return banners.stream()
                .map(this::toBannerResponse)
                .toList();
    }

    /**
     * 신규 배너를 등록한다.
     *
     * @param request 배너 등록 요청 DTO
     * @return 등록된 배너 응답 DTO
     */
    @Transactional
    public BannerResponse createBanner(BannerCreateRequest request) {
        log.info("[settings] 배너 등록 — title={}, position={}", request.title(), request.position());

        Banner banner = Banner.builder()
                .title(request.title())
                .imageUrl(request.imageUrl())
                .linkUrl(request.linkUrl())
                /* position null이면 @Builder.Default "MAIN"으로 설정 */
                .position(request.position() != null ? request.position() : "MAIN")
                /* sortOrder null이면 @Builder.Default 0으로 설정 */
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .build();

        // MyBatis insert — useGeneratedKeys로 bannerId 자동 세팅
        contentMapper.insertBanner(banner);
        log.info("[settings] 배너 등록 완료 — bannerId={}", banner.getBannerId());
        return toBannerResponse(banner);
    }

    /**
     * 기존 배너를 수정한다.
     *
     * <p>기본 정보(제목/이미지/링크/위치/순서), 활성화 여부, 게시 기간을 함께 업데이트한다.</p>
     *
     * @param id      수정할 배너 ID
     * @param request 배너 수정 요청 DTO
     * @return 수정된 배너 응답 DTO
     * @throws BusinessException 배너를 찾을 수 없는 경우 (G002)
     */
    @Transactional
    public BannerResponse updateBanner(Long id, BannerUpdateRequest request) {
        log.info("[settings] 배너 수정 — bannerId={}", id);

        Banner banner = contentMapper.findBannerById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "배너를 찾을 수 없습니다. id=" + id);
        }

        /* 기본 정보 수정 */
        banner.update(request.title(), request.imageUrl(), request.linkUrl(),
                request.position(), request.sortOrder());

        /* 활성화 여부 수정 */
        banner.setActive(request.isActive());

        /* 게시 기간 수정 */
        banner.updateDateRange(request.startDate(), request.endDate());

        // MyBatis는 dirty checking 미지원 — 명시적 UPDATE 호출
        contentMapper.updateBanner(banner);

        log.info("[settings] 배너 수정 완료 — bannerId={}", id);
        return toBannerResponse(banner);
    }

    /**
     * 배너를 삭제한다.
     *
     * <p>물리적 삭제를 수행한다. 삭제 전 존재 여부를 검증한다.</p>
     *
     * @param id 삭제할 배너 ID
     * @throws BusinessException 배너를 찾을 수 없는 경우 (G002)
     */
    @Transactional
    public void deleteBanner(Long id) {
        log.info("[settings] 배너 삭제 — bannerId={}", id);

        Banner banner = contentMapper.findBannerById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "배너를 찾을 수 없습니다. id=" + id);
        }

        contentMapper.deleteBanner(id);
        log.info("[settings] 배너 삭제 완료 — bannerId={}", id);
    }

    // ======================== 감사 로그 ========================

    /**
     * 감사 로그 목록을 복합 필터로 조회한다 — 2026-04-09 P1-⑤ 확장 + P2-⑮ targetId 추가.
     *
     * <p>모든 필터 파라미터는 optional 이며 null/빈 값이면 해당 조건을 무시한다.
     * 실제 필터링 로직은 {@link AdminAuditLogRepository#searchByFilters} 의 JPQL 에서
     * 처리된다 (한 번의 쿼리로 모든 조건 처리).</p>
     *
     * <h3>필터 파라미터</h3>
     * <ul>
     *   <li>{@code actionType} — 행위 유형 부분 일치 (대소문자 무시)</li>
     *   <li>{@code targetType} — 대상 엔티티 유형 정확 일치 (예: "USER", "PAYMENT")</li>
     *   <li>{@code targetId}   — 대상 엔티티 식별자 정확 일치 (2026-04-09 P2-⑮ 추가).
     *       사용자 360도 뷰에서 "이 사용자에게 가해진 관리 조치" 를 조회하는 용도.</li>
     *   <li>{@code fromDate}   — 이 시각 이상 (inclusive)</li>
     *   <li>{@code toDate}     — 이 시각 미만 (exclusive)</li>
     * </ul>
     *
     * <p>빈 문자열이 들어오면 null 과 동일하게 취급하여 조건을 비활성화한다 — 프론트엔드가
     * 폼 리셋 시 빈 문자열을 전달하는 일반적 패턴을 수용하기 위함.</p>
     *
     * @param actionType 행위 유형 필터 키워드 (nullable/blank)
     * @param targetType 대상 유형 필터 (nullable/blank)
     * @param targetId   대상 엔티티 식별자 필터 (nullable/blank)
     * @param fromDate   시작 시각 inclusive (nullable)
     * @param toDate     종료 시각 exclusive (nullable)
     * @param pageable   페이지 정보 (ORDER BY 는 Repository 에서 고정)
     * @return 감사 로그 응답 DTO 페이지
     */
    public Page<AuditLogResponse> getAuditLogs(
            String actionType,
            String targetType,
            String targetId,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable
    ) {
        log.debug("[settings] 감사 로그 조회 — actionType={}, targetType={}, targetId={}, from={}, to={}, page={}",
                actionType, targetType, targetId, fromDate, toDate, pageable.getPageNumber());

        // 빈 문자열은 null 로 정규화 — JPQL :param IS NULL 조건이 반응하도록
        String normalizedActionType = StringUtils.hasText(actionType) ? actionType : null;
        String normalizedTargetType = StringUtils.hasText(targetType) ? targetType : null;
        String normalizedTargetId   = StringUtils.hasText(targetId) ? targetId : null;

        return adminAuditLogRepository
                .searchByFilters(
                        normalizedActionType,
                        normalizedTargetType,
                        normalizedTargetId,
                        fromDate,
                        toDate,
                        pageable
                )
                .map(this::toAuditLogResponse);
    }

    // ======================== 관리자 계정 ========================

    /**
     * 관리자 계정 목록을 최신 등록순으로 페이지네이션하여 조회한다.
     *
     * @param pageable 페이지 정보 (page, size, sort)
     * @return 관리자 계정 응답 DTO 페이지
     */
    public Page<AdminAccountResponse> getAdmins(Pageable pageable) {
        log.debug("[settings] 관리자 계정 목록 조회 — page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return adminAccountRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toAdminAccountResponse);
    }

    /**
     * 관리자 역할을 수정한다.
     *
     * <p>ADMIN ↔ SUPER_ADMIN 간 역할 변경 등에 사용한다.
     * Admin 엔티티에 별도 도메인 메서드가 없으므로 리플렉션 없이 새 엔티티를 저장하는
     * 방식 대신, 영속성 컨텍스트 내에서 직접 필드를 변경한다.</p>
     *
     * @param id      수정할 관리자 레코드 ID
     * @param request 역할 수정 요청 DTO
     * @return 수정된 관리자 계정 응답 DTO
     * @throws BusinessException 관리자 계정을 찾을 수 없는 경우 (G002)
     */
    @Transactional
    public AdminAccountResponse updateAdminRole(Long id, AdminRoleUpdateRequest request) {
        log.info("[settings] 관리자 역할 수정 — adminId={}, newRole={}", id, request.adminRole());

        /*
         * 2026-04-09 P2-⑫ 확장: 허용된 역할값 검증.
         *
         * 기존에는 request.adminRole() 을 그대로 저장하여 임의 문자열("foo" 등) 이 DB 에
         * 들어갈 수 있었다. 본 검증은 AdminRole enum 에 정의된 7종 코드만 허용한다.
         * 허용값이 아니면 BusinessException(INVALID_INPUT) 을 던져 저장을 차단한다.
         */
        if (!AdminRole.isAllowed(request.adminRole())) {
            log.warn("[settings] 관리자 역할 수정 실패 — 허용되지 않은 역할 코드: {}", request.adminRole());
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "허용되지 않은 역할 코드입니다: " + request.adminRole() +
                    " (허용값: " + AdminRole.allowedCodesAsString() + ")"
            );
        }

        Admin admin = adminAccountRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "관리자 계정을 찾을 수 없습니다. id=" + id));

        /* Admin 엔티티는 별도 역할 수정 메서드가 없으므로 Builder로 새 객체 생성 후 merge
         * JPA dirty checking이 동작하도록 동일 ID의 엔티티를 save()로 저장 */
        Admin updated = Admin.builder()
                .adminId(admin.getAdminId())
                .userId(admin.getUserId())
                .adminRole(request.adminRole())
                .isActive(admin.getIsActive())
                .lastLoginAt(admin.getLastLoginAt())
                .build();

        String previousRole = admin.getAdminRole();
        Admin saved = adminAccountRepository.save(updated);
        log.info("[settings] 관리자 역할 수정 완료 — adminId={}, role={}", id, saved.getAdminRole());

        /*
         * 2026-04-14 추가: 역할 변경 감사 로그.
         * SUPER_ADMIN ↔ STATS_ADMIN 같은 민감한 권한 조정은 반드시 추적 가능해야 한다.
         * REQUIRES_NEW 로 격리되어 있어 로그 기록 실패가 역할 수정 자체를 막지 않는다.
         */
        String description = String.format(
                "관리자 역할 변경 — adminId=%d, userId=%s, %s → %s",
                saved.getAdminId(), saved.getUserId(), previousRole, saved.getAdminRole()
        );
        String beforeData = String.format("{\"adminRole\":\"%s\"}", previousRole);
        String afterData  = String.format("{\"adminRole\":\"%s\"}", saved.getAdminRole());
        adminAuditService.log(
                AdminAuditService.ACTION_ADMIN_ROLE_UPDATE,
                AdminAuditService.TARGET_ADMIN,
                String.valueOf(saved.getAdminId()),
                description,
                beforeData,
                afterData
        );

        return toAdminAccountResponse(saved);
    }

    /**
     * 신규 관리자 계정을 생성한다 — 2026-04-14 신규.
     *
     * <p>SUPER_ADMIN 이 기존 일반 사용자를 관리자로 승격시키는 용도. 비밀번호는 기존
     * 사용자 계정의 것을 그대로 사용하므로 별도 설정이 필요 없다. 관리자 페이지 로그인은
     * {@code POST /api/v1/admin/auth/login} 에서 users.user_role 이 ADMIN 이면 통과된다.</p>
     *
     * <h3>처리 단계</h3>
     * <ol>
     *   <li>요청 DTO 의 adminRole 허용값 검증 (AdminRole enum).</li>
     *   <li>userId 또는 email 로 User 엔티티 조회 (둘 다 없으면 INVALID_INPUT).</li>
     *   <li>해당 user 가 이미 관리자인지 확인 (중복 방지).</li>
     *   <li>users.user_role 을 ADMIN 으로 승격 (MyBatis UPDATE).</li>
     *   <li>admin 테이블에 신규 레코드 INSERT.</li>
     *   <li>admin_audit_logs 에 ADMIN_ACCOUNT_CREATE 기록 (REQUIRES_NEW).</li>
     * </ol>
     *
     * @param request 신규 관리자 등록 요청 DTO
     * @return 생성된 관리자 계정 응답 DTO
     */
    @Transactional
    public AdminAccountResponse createAdminAccount(AdminAccountCreateRequest request) {
        log.info("[settings] 관리자 계정 신규 등록 요청 — userId={}, email={}, role={}",
                request.userId(), request.email(), request.adminRole());

        // ── 1. adminRole 허용값 검증 ──
        if (!AdminRole.isAllowed(request.adminRole())) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "허용되지 않은 역할 코드입니다: " + request.adminRole() +
                    " (허용값: " + AdminRole.allowedCodesAsString() + ")"
            );
        }

        // ── 2. userId 또는 email 로 User 조회 ──
        // userId 가 우선하며, 없으면 email 로 fallback. 둘 다 없으면 검증 실패.
        if (!StringUtils.hasText(request.userId()) && !StringUtils.hasText(request.email())) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "userId 또는 email 중 하나는 필수입니다."
            );
        }

        User user;
        if (StringUtils.hasText(request.userId())) {
            user = userMapper.findById(request.userId());
            if (user == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "userId 에 해당하는 사용자를 찾을 수 없습니다: " + request.userId());
            }
        } else {
            user = userMapper.findByEmail(request.email());
            if (user == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "email 에 해당하는 사용자를 찾을 수 없습니다: " + request.email());
            }
        }

        // ── 3. 중복 검사 — 이미 admin 레코드가 있는지 확인 ──
        if (adminAccountRepository.findByUserId(user.getUserId()).isPresent()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "이미 관리자로 등록된 사용자입니다: userId=" + user.getUserId()
            );
        }

        // ── 4. users.user_role 을 ADMIN 으로 승격 ──
        // user.updateUserRole() 은 엔티티 도메인 메서드지만 본 서비스는 MyBatis 경로이므로
        // 도메인 메서드 호출 + userMapper.update() 패턴 대신, UserRole 이 이미 ADMIN 이면
        // UPDATE 스킵하고, 아니면 엔티티를 직접 UPDATE 한다.
        if (user.getUserRole() != UserRole.ADMIN) {
            user.updateUserRole(UserRole.ADMIN);
            userMapper.update(user);
            log.info("[settings] users.user_role → ADMIN 승격 — userId={}", user.getUserId());
        }

        // ── 5. admin 테이블 신규 레코드 INSERT ──
        Admin newAdmin = Admin.builder()
                .userId(user.getUserId())
                .adminRole(request.adminRole())
                .isActive(true)
                .build();
        Admin saved = adminAccountRepository.save(newAdmin);
        log.info("[settings] admin 테이블 INSERT 완료 — adminId={}, userId={}, role={}",
                saved.getAdminId(), saved.getUserId(), saved.getAdminRole());

        // ── 6. 감사 로그 ──
        String description = String.format(
                "관리자 계정 신규 등록 — adminId=%d, userId=%s, email=%s, role=%s",
                saved.getAdminId(), saved.getUserId(), user.getEmail(), saved.getAdminRole()
        );
        String afterData = String.format(
                "{\"userId\":\"%s\",\"email\":\"%s\",\"adminRole\":\"%s\"}",
                saved.getUserId(), user.getEmail(), saved.getAdminRole()
        );
        adminAuditService.log(
                AdminAuditService.ACTION_ADMIN_ACCOUNT_CREATE,
                AdminAuditService.TARGET_ADMIN,
                String.valueOf(saved.getAdminId()),
                description,
                null,
                afterData
        );

        return toAdminAccountResponse(saved);
    }

    // ======================== 엔티티 → DTO 변환 헬퍼 ========================

    /**
     * Terms 엔티티를 TermsResponse DTO로 변환한다.
     *
     * @param terms 약관 엔티티
     * @return 약관 응답 DTO
     */
    private TermsResponse toTermsResponse(Terms terms) {
        return new TermsResponse(
                terms.getTermsId(),
                terms.getTitle(),
                terms.getContent(),
                terms.getType(),
                terms.getVersion(),
                terms.getIsRequired(),
                terms.getIsActive(),
                terms.getCreatedAt(),
                terms.getUpdatedAt()
        );
    }

    /**
     * Banner 엔티티를 BannerResponse DTO로 변환한다.
     *
     * @param banner 배너 엔티티
     * @return 배너 응답 DTO
     */
    private BannerResponse toBannerResponse(Banner banner) {
        return new BannerResponse(
                banner.getBannerId(),
                banner.getTitle(),
                banner.getImageUrl(),
                banner.getLinkUrl(),
                banner.getPosition(),
                banner.getSortOrder(),
                banner.getIsActive(),
                banner.getStartDate(),
                banner.getEndDate(),
                banner.getCreatedAt()
        );
    }

    /**
     * AdminAuditLog 엔티티를 AuditLogResponse DTO로 변환한다.
     *
     * @param log 감사 로그 엔티티
     * @return 감사 로그 응답 DTO
     */
    private AuditLogResponse toAuditLogResponse(AdminAuditLog log) {
        return new AuditLogResponse(
                log.getAuditLogId(),
                log.getAdminId(),
                log.getActionType(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDescription(),
                log.getIpAddress(),
                /* 2026-04-09 P2-⑲ 확장: JSON Diff 뷰어에 필요한 변경 전/후 스냅샷 */
                log.getBeforeData(),
                log.getAfterData(),
                log.getCreatedAt()
        );
    }

    /**
     * Admin 엔티티를 AdminAccountResponse DTO로 변환한다.
     *
     * <p>2026-04-14 확장: {@code users} 테이블에서 email/nickname 을 조회하여 응답에
     * 포함시킨다. 관리자 계정은 통상 페이지당 20건 이하이므로 N+1 오버헤드보다 코드
     * 단순성을 우선한다 (루프 내 {@code UserMapper.findById()}).</p>
     *
     * <p>users 레코드가 없는 경우(탈퇴·동기화 오류 등)에는 email/nickname 을 null 로
     * 채우고 계속 진행한다 — 관리자 목록 화면이 500 에러로 중단되지 않도록 방어.</p>
     *
     * @param admin 관리자 엔티티
     * @return 관리자 계정 응답 DTO
     */
    private AdminAccountResponse toAdminAccountResponse(Admin admin) {
        String email = null;
        String nickname = null;
        try {
            User user = userMapper.findById(admin.getUserId());
            if (user != null) {
                email = user.getEmail();
                nickname = user.getNickname();
            }
        } catch (Exception e) {
            // 조회 실패 시 null 유지하고 경고만 남긴다 — 화면 전체가 죽지 않도록 함
            log.warn("[settings] 관리자 계정 email/nickname 조회 실패 — userId={}, err={}",
                    admin.getUserId(), e.getMessage());
        }

        return new AdminAccountResponse(
                admin.getAdminId(),
                admin.getUserId(),
                email,
                nickname,
                admin.getAdminRole(),
                admin.getIsActive(),
                admin.getLastLoginAt(),
                admin.getCreatedAt()
        );
    }
}
