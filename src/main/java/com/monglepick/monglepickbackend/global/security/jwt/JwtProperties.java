package com.monglepick.monglepickbackend.global.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 설정 프로퍼티 클래스
 *
 * <p>application.yml의 jwt 섹션을 자동으로 바인딩합니다.
 * 불변 상수가 아닌 외부 설정값이므로 환경별로 다르게 주입할 수 있습니다.</p>
 *
 * <p>설정 항목:</p>
 * <ul>
 *   <li>secret: JWT 서명에 사용할 비밀키 (HS256)</li>
 *   <li>expiration: 액세스 토큰 만료 시간 (밀리초)</li>
 *   <li>refreshExpiration: 리프레시 토큰 만료 시간 (밀리초)</li>
 * </ul>
 *
 * <p>주의: 운영 환경에서는 반드시 환경변수(JWT_SECRET)로 비밀키를 주입해야 합니다.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 서명 비밀키
     * <p>HS256 알고리즘 사용, 최소 256비트(32바이트) 이상 권장</p>
     */
    private String secret;

    /**
     * 액세스 토큰 만료 시간 (밀리초)
     * <p>기본값: 3,600,000ms (1시간)</p>
     */
    private long expiration;

    /**
     * 리프레시 토큰 만료 시간 (밀리초)
     * <p>기본값: 604,800,000ms (7일)</p>
     */
    private long refreshExpiration;
}
