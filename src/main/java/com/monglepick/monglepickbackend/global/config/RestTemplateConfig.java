package com.monglepick.monglepickbackend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 공용 RestTemplate 빈 설정.
 *
 * <p>타임아웃 기준: recommend 서비스의 PaddleOCR 단일 변형(gray) 처리 시간은
 * 실측 10~30초. 콜드 스타트(모델 로딩 포함) 시 최대 50초까지 관측되어
 * 60초 readTimeout 으로 상한을 둔다. 과거 EasyOCR 3-variant 기준 120초
 * 설정은 2026-04-23 PaddleOCR 단일 변형 리팩토링에 맞춰 축소했다.</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 커넥션 타임아웃: 네트워크 수립까지. recommend 는 동일 내부망이라 짧게.
        factory.setConnectTimeout(5000);
        // 읽기 타임아웃: PaddleOCR gray 단일 변형 처리 + 콜드 스타트 여유.
        factory.setReadTimeout(60000);
        return new RestTemplate(factory);
    }
}