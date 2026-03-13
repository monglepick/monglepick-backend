package com.monglepick.monglepickbackend.domain.user.service;


import com.monglepick.monglepickbackend.domain.auth.dto.CustomOAuth2User;
import com.monglepick.monglepickbackend.domain.auth.dto.UserRequestDTO;
import com.monglepick.monglepickbackend.domain.auth.service.JWTService;
import com.monglepick.monglepickbackend.domain.user.dto.UserResponseDTO;
import com.monglepick.monglepickbackend.domain.user.entity.SocialProviderType;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
public class UserService extends DefaultOAuth2UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;


    public UserService(PasswordEncoder passwordEncoder, UserRepository userRepository, StringHttpMessageConverter stringHttpMessageConverter, JWTService jwtService) {
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.jwtService =  jwtService;
    }


    // 자체 로그인 회원 가입(존재 여부)
    @Transactional(readOnly = true)
    public Boolean existUser(UserRequestDTO dto){
        return userRepository.existsByUserEmail(dto.getUseremail());
    }

    // 자체 로그인 회원 가입
    @Transactional
    public String addUser(UserRequestDTO dto){

        if (userRepository.existsByUserEmail(dto.getUseremail())){
            throw new IllegalArgumentException("이미 유저가 존재합니다.");
        }

        User entity = User.builder()
                .userEmail(dto.getUseremail())
                .userPassword(passwordEncoder.encode(dto.getUserpassword()))
                .isSocial(false)
                .userNickname(dto.getUsernickname())
                .build();
        return userRepository.save(entity).getUserId();
    }


    // 자체 로그인
    @Transactional(readOnly = true)
    @Override
    public UserDetails loadUserByUsername(String useremail) throws UsernameNotFoundException {

        User entity = userRepository.findByUserEmailAndIsSocial(useremail, false)
                .orElseThrow(()-> new UsernameNotFoundException(useremail));

        return org.springframework.security.core.userdetails.User.builder()
                .username(entity.getUserEmail())
                .password(entity.getUserPassword())
                .build();
    }

    // 자체 로그인 회원 정보 수정
    @Transactional
    public String updateUser(UserRequestDTO dto) throws AccessDeniedException {
        //본인만 수정 가능 검증

        String sessionUseremail = SecurityContextHolder.getContext().getAuthentication().getName();
        if(!sessionUseremail.equals(dto.getUseremail())){
            throw new AccessDeniedException("본인 계정만 수정 가능");
        }

        //조회
        User entity = userRepository.findByUserEmailAndIsSocial(dto.getUseremail(), false)
                .orElseThrow(()->new UsernameNotFoundException(dto.getUseremail()));

        //회원정보 수정

        entity.updateUser(dto);

        return userRepository.save(entity).getUserId();
    }



    // 자체/소설 로그인 회원 탈퇴
    @Transactional
    public void deleteUser(UserRequestDTO dto) throws AccessDeniedException{

        // 본인만 삭제 가능 검증
        SecurityContext context = SecurityContextHolder.getContext();
        String sessionUSerEmail = context.getAuthentication().getName();

        boolean isOwner = sessionUSerEmail.equals(dto.getUseremail());

        if(!isOwner){
            throw new AccessDeniedException("본인만 삭제할 수 있습니다.");
        }

        //유저 제거
        userRepository.deleteUserByUserEmail(dto.getUseremail());

        //Refresh 토큰 제거
        jwtService.removeRefreshUser(dto.getUseremail());

    }

    // 소셜 로그인(매 로그인시 : 신규 -가입, 기존 = 업데이트)
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // 부모메서드 호출(파싱)
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 데이터
        Map<String,Object> attributes;
        List<GrantedAuthority> authorities;

        String useremail;
        String role = "ROLE_USER";
        String usernickname;

        //provider 제공자별 데이터 획득

        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();

        if (registrationId.equals(SocialProviderType.NAVER.name())) {

            attributes = (Map<String, Object>) oAuth2User.getAttributes().get("response");
            useremail = attributes.get("email").toString();
            usernickname = attributes.get("nickname").toString();
        }else {
            throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인입니다: " + registrationId);
        }

        // 데이터베이스 조회 -> 존재하면 업데이트, 없으면 신규 가입
        Optional<User> entity = userRepository.findByUserEmailAndIsSocial(useremail, true);
        if (entity.isPresent()) {

            // 기존 유저 업데이트
            UserRequestDTO dto = new UserRequestDTO();
            dto.setUsernickname(usernickname);
            dto.setUseremail(useremail);
            entity.get().updateUser(dto);

            userRepository.save(entity.get());
        } else {
            // 신규 유저 추가
            User newUser = User.builder()
                    .userEmail(useremail)
                    .userPassword("")
                    .isSocial(true)
                    .socialProviderType(SocialProviderType.valueOf(registrationId))
                    .userNickname(usernickname)
                    .build();

            userRepository.save(newUser);
        }
        authorities = List.of(new SimpleGrantedAuthority(role));

        return new CustomOAuth2User(attributes, authorities, useremail);
    }


    // 자체/소셜 유저 정보 조회
    @Transactional(readOnly = true)
    public UserResponseDTO readUser() {
        String userEmail = SecurityContextHolder.getContext().getAuthentication().getName();

        User entity = userRepository.findByUserEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("해당 유저를 찾을 수 없습니다: " + userEmail));

        return new UserResponseDTO(entity.getUserEmail(), entity.getIsSocial(), entity.getUserNickname(), entity.getProfileImg());
    }

}
