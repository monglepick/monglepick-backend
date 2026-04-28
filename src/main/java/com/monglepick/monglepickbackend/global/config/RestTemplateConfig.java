package com.monglepick.monglepickbackend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 공용 RestTemplate 빈 설정.
 *
 * <p>타임아웃 기준: recommend 서비스의 Tesseract 5-variant 처리 시간은
 * 실측 최대 120초. 150초 readTimeout 으로 여유를 둔다.
 * (프론트엔드 axios timeout 과 동일하게 맞춤)</p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 커넥션 타임아웃: 네트워크 수립까지. recommend 는 동일 내부망이라 짧게.
        factory.setConnectTimeout(5000);
        // 읽기 타임아웃: Tesseract 5-variant OCR 최대 처리 시간 + 여유.
        factory.setReadTimeout(150000);
        return new RestTemplate(factory);
    }
}