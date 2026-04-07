package com.monglepick.monglepickbackend.domain.auth.service;

import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.AuthResponse;
import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.SignupRequest;
import com.monglepick.monglepickbackend.domain.auth.dto.AuthDto.UserInfo;
import com.monglepick.monglepickbackend.domain.auth.dto.CustomOAuth2User;
import com.monglepick.monglepickbackend.domain.reward.service.PointService;
import com.monglepick.monglepickbackend.domain.reward.service.RewardService;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 인증 서비스 — 회원가입, OAuth2 소셜 로그인, UserDetailsService 통합.
 *
 * <p>KMG 프로젝트의 UserService 패턴을 적용하여
 * DefaultOAuth2UserService + UserDetailsService를 모두 구현한다.</p>
 *
 * <h3>C-2 수정: signup() 완료 후 Refresh Token을 RefreshMapper에 저장 (2026-04-08 Repository → Mapper 전환)</h3>
 * <h3>C-3 수정: Kakao loadUser()에서 kakaoAccount/profile null 체크 추가</h3>
 * <h3>R-1 수정: signup() 가입 보너스를 RewardService.grantReward()로 위임</h3>
 * <p>기존 {@code pointService.initializePoint(userId, 500)} 방식에서
 * {@code rewardService.grantReward(userId, "SIGNUP_BONUS", "signup", 0)} 방식으로 변경.
 * {@code max_count=1} 정책으로 중복 지급이 자동 차단된다.
 * 포인트 레코드 초기화는 {@code pointService.initializePoint(userId, 0)} (잔액 0)으로만 수행.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService extends DefaultOAuth2UserService implements UserDetailsService {

    /** 사용자 조회/등록/수정 — MyBatis Mapper (JpaRepository 폐기, 설계서 §15) */
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PointService pointService;

    /** C-2: Refresh Token DB 저장을 위한 JwtService 의존성 추가 */
    private final JwtService jwtService;

    /**
     * R-1: 회원가입 보너스 지급을 위한 RewardService 주입.
     *
     * <p>SIGNUP_BONUS 정책(max_count=1)을 통해 500P를 지급한다.
     * 기존 {@code pointService.initializePoint(userId, 500)} 하드코딩을 대체하며,
     * RewardService 내부에서 중복 지급 방지(max_count 한도 검사)가 자동 처리된다.</p>
     */
    private final RewardService rewardService;

    // ──────────────────────────────────────────────
    // 로컬 회원가입
    // ──────────────────────────────────────────────

    /**
     * 로컬 회원가입을 처리한다.
     *
     * <p>이메일/닉네임 중복 확인 → BCrypt 해싱 → 사용자 생성 → 포인트 레코드 초기화(잔액 0)
     * → 회원가입 보너스 지급(RewardService) → JWT 발급</p>
     *
     * <p>C-2 수정: Refresh Token을 DB 화이트리스트에 저장하여
     * 회원가입 직후 발급된 토큰이 갱신 가능하도록 한다.</p>
     *
     * <p>R-1 수정: 가입 보너스 500P를 {@code rewardService.grantReward()}로 위임.
     * {@code pointService.initializePoint(userId, 0)}으로 포인트 레코드만 생성(잔액 0)하고,
     * 실제 500P 지급은 SIGNUP_BONUS 정책(max_count=1)을 통해 처리된다.
     * 이로써 보너스 금액 변경은 reward_policy 테이블에서만 관리된다.</p>
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        log.info("로컬 회원가입 요청 — email: {}", request.email());

        if (userMapper.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userMapper.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        String userId = UUID.randomUUID().toString();

        User user = User.builder()
                .userId(userId)
                .email(request.email())
                .nickname(request.nickname())
                .passwordHash(passwordHash)
                .provider(User.Provider.LOCAL)
                .requiredTerm(request.requiredTerm())
                .name(request.name())
                .userBirth(request.userBirth())
                .profileImage(request.profileImage())
                .optionTerm(request.optionTerm() != null ? request.optionTerm() : false)
                .marketingAgreed(request.marketingAgreed() != null ? request.marketingAgreed() : false)
                .build();

        userMapper.insert(user);

        // R-1: 포인트 레코드를 잔액 0으로 초기화 (보너스 지급은 rewardService가 담당)
        //      기존 initializePoint(userId, 500) 방식에서 변경 — 하드코딩 500P 제거
        pointService.initializePoint(userId, 0);

        // R-1: 회원가입 보너스 — SIGNUP_BONUS 정책 기반 500P 지급
        //      max_count=1 정책으로 중복 지급 자동 차단
        //      referenceId "signup" 고정으로 동일 사용자의 중복 요청도 방지
        rewardService.grantReward(userId, "SIGNUP_BONUS", "signup", 0);

        /* C-2: Refresh Token을 DB 화이트리스트에 저장 */
        AuthResponse authResponse = buildAuthResponse(user);
        jwtService.addRefresh(userId, authResponse.refreshToken());

        log.info("로컬 회원가입 완료 — userId: {}, email: {}", userId, request.email());

        return authResponse;
    }

    // ──────────────────────────────────────────────
    // UserDetailsService 구현 (LoginFilter용)
    // ──────────────────────────────────────────────

    /**
     * 로컬 로그인 시 이메일로 사용자를 조회한다.
     *
     * @param email 로그인 이메일 (username 파라미터로 전달됨)
     * @return Spring Security UserDetails
     * @throws UsernameNotFoundException 사용자 미존재 시
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email);
        }

        /* 소셜 로그인 사용자는 로컬 로그인 불가 */
        if (user.getProvider() != User.Provider.LOCAL) {
            throw new UsernameNotFoundException("소셜 로그인 계정입니다. " + user.getProvider() + " 로그인을 사용하세요.");
        }

        /* userRole에서 실제 권한을 읽어 설정 (ADMIN 계정이 ROLE_USER로 로드되는 버그 수정) */
        String role = "ROLE_" + (user.getUserRole() != null ? user.getUserRole().name() : "USER");
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(role)
                .build();
    }

    // ──────────────────────────────────────────────
    // DefaultOAuth2UserService 구현 (Spring Security OAuth2 흐름)
    // ──────────────────────────────────────────────

    /**
     * Spring Security OAuth2 흐름에서 소셜 제공자의 사용자 정보를 처리한다.
     *
     * <p>C-3 수정: Kakao 제공자 처리 시 kakaoAccount/profile이 null인 경우
     * 기본값을 사용하여 NPE를 방지한다.</p>
     *
     * @param userRequest OAuth2 사용자 요청 (제공자 정보 + 액세스 토큰)
     * @return CustomOAuth2User (principal = userId)
     * @throws OAuth2AuthenticationException 지원하지 않는 제공자 또는 이메일 충돌 시
     */
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        /* 부모 메서드 호출로 제공자 응답 파싱 */
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration()
                .getRegistrationId().toUpperCase();

        String email;
        String nickname;
        String providerId;
        Map<String, Object> attributes = oAuth2User.getAttributes();

        /* 제공자별 사용자 정보 추출 */
        switch (registrationId) {
            case "NAVER" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> naverResponse = (Map<String, Object>) attributes.get("response");
                email = naverResponse.get("email") != null ? naverResponse.get("email").toString() : null;
                nickname = naverResponse.get("name") != null
                        ? naverResponse.get("name").toString()
                        : (email != null ? email.split("@")[0] : "네이버유저");
                providerId = naverResponse.get("id") != null ? naverResponse.get("id").toString() : "";
            }
            case "GOOGLE" -> {
                email = attributes.get("email") != null ? attributes.get("email").toString() : null;
                nickname = attributes.get("name") != null
                        ? attributes.get("name").toString()
                        : (email != null ? email.split("@")[0] : "구글유저");
                providerId = attributes.get("sub") != null ? attributes.get("sub").toString() : "";
            }
            case "KAKAO" -> {
                /*
                 * C-3: kakaoAccount, profile null 체크 추가 (NPE 방지).
                 * Kakao API가 권한 미동의 등으로 kakao_account나 profile을
                 * 반환하지 않을 수 있으므로 null-safe하게 처리한다.
                 */
                @SuppressWarnings("unchecked")
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");

                /* kakaoAccount가 null인 경우 기본값 사용 */
                if (kakaoAccount == null) {
                    log.warn("Kakao OAuth2: kakao_account가 null입니다. 기본값 사용");
                    String kakaoId = attributes.get("id") != null ? attributes.get("id").toString() : UUID.randomUUID().toString();
                    email = kakaoId + "@kakao.com";
                    nickname = "카카오사용자";
                    providerId = kakaoId;
                } else {
                    /* profile null 체크 */
                    @SuppressWarnings("unchecked")
                    Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

                    email = kakaoAccount.get("email") != null
                            ? kakaoAccount.get("email").toString()
                            : (attributes.get("id") != null
                                    ? attributes.get("id").toString() + "@kakao.com"
                                    : UUID.randomUUID().toString() + "@kakao.com");

                    if (profile != null && profile.get("nickname") != null) {
                        nickname = profile.get("nickname").toString();
                    } else {
                        /* C-3: profile 또는 nickname이 null인 경우 기본값 사용 */
                        log.warn("Kakao OAuth2: profile 또는 nickname이 null입니다. 기본값 사용");
                        nickname = email.contains("@") ? email.split("@")[0] : "카카오사용자";
                    }

                    providerId = attributes.get("id") != null ? attributes.get("id").toString() : "";
                }
            }
            default -> throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다: " + registrationId);
        }

        User.Provider provider = User.Provider.valueOf(registrationId);

        /* provider+providerId로 기존 사용자 조회 (MyBatis — Provider enum을 String으로 전달) */
        User existingUser = userMapper.findByProviderAndProviderId(provider.name(), providerId);

        /* 이메일 중복 확인 (다른 제공자로 가입된 이메일인지) */
        if (existingUser == null && email != null && userMapper.existsByEmail(email)) {
            throw new OAuth2AuthenticationException("이미 해당 이메일로 가입된 계정이 있습니다.");
        }

        User user;
        if (existingUser != null) {
            /* 기존 사용자: 닉네임 업데이트 (도메인 메서드로 in-memory 변경 후 MyBatis update 명시 호출) */
            user = existingUser;
            user.updateNickname(nickname);
            userMapper.update(user);
            log.info("소셜 로그인 — 기존 사용자: userId={}, provider={}", user.getUserId(), provider);
        } else {
            /* 신규 사용자: 생성 + 포인트 레코드 초기화(잔액 0) + 가입 보너스 지급 */
            String userId = UUID.randomUUID().toString();
            user = User.builder()
                    .userId(userId)
                    .email(email)
                    .nickname(nickname)
                    .provider(provider)
                    .providerId(providerId)
                    .requiredTerm(true)
                    .build();
            userMapper.insert(user);

            // R-1: 포인트 레코드를 잔액 0으로 초기화 (보너스 지급은 rewardService가 담당)
            pointService.initializePoint(userId, 0);

            // R-1: 회원가입 보너스 — SIGNUP_BONUS 정책 기반 500P 지급 (max_count=1 중복 차단)
            rewardService.grantReward(userId, "SIGNUP_BONUS", "signup", 0);

            log.info("소셜 로그인 — 신규 사용자 생성: userId={}, provider={}", userId, provider);
        }

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

        /* CustomOAuth2User의 getName()이 userId를 반환하도록 설정 */
        return new CustomOAuth2User(attributes, authorities, user.getUserId(), user.getEmail());
    }

    // ──────────────────────────────────────────────
    // Private 헬퍼
    // ──────────────────────────────────────────────

    /**
     * User 엔티티로부터 인증 응답을 빌드한다.
     * (회원가입 시 사용 — 로그인은 LoginSuccessHandler에서 처리)
     */
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getUserId(), user.getUserRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        UserInfo userInfo = new UserInfo(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage(),
                user.getProvider().name(),
                user.getUserRole().name()
        );

        return new AuthResponse(accessToken, refreshToken, userInfo);
    }
}
