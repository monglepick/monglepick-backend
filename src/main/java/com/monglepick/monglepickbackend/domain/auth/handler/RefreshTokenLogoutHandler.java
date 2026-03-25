package com.monglepick.monglepickbackend.domain.auth.handler;

// Jackson 3.x: com.fasterxml.jackson → tools.jackson 패키지 경로 변경 (Spring Boot 4.x)
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.monglepick.monglepickbackend.domain.auth.service.JwtService;
import com.monglepick.monglepickbackend.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 로그아웃 시 Refresh Token을 DB 화이트리스트에서 삭제하는 핸들러.
 *
 * <p>KMG 프로젝트의 RefreshTokenLogoutHandler 패턴을 적용.
 * 로그아웃 요청 Body에서 refreshToken을 읽어 DB에서 삭제한다.
 * 이를 통해 로그아웃 후 기존 Refresh Token으로 토큰 갱신이 불가능해진다.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenLogoutHandler implements LogoutHandler {

    /** JSON 직렬화를 위한 ObjectMapper (스레드 안전, 클래스 로딩 시 1회 초기화) */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JwtService jwtService;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response,
                        Authentication authentication) {
        try {
            /* 요청 Body에서 JSON 파싱 */
            String body = new BufferedReader(new InputStreamReader(request.getInputStream()))
                    .lines()
                    .reduce("", String::concat);

            if (!StringUtils.hasText(body)) return;

            JsonNode jsonNode = OBJECT_MAPPER.readTree(body);
            String refreshToken = jsonNode.has("refreshToken")
                    ? jsonNode.get("refreshToken").asText()
                    : null;

            if (refreshToken == null) return;

            /* Refresh Token 유효성 검증 후 DB에서 삭제 */
            if (jwtTokenProvider.validateToken(refreshToken)) {
                jwtService.removeRefresh(refreshToken);
                log.info("로그아웃 — Refresh Token DB에서 삭제 완료");
            }

        } catch (IOException e) {
            log.error("로그아웃 핸들러에서 Refresh Token 읽기 실패", e);
        }
    }
}
