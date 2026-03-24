package com.monglepick.monglepickbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 몽글픽 백엔드 애플리케이션 진입점.
 *
 * <p>Spring Boot 4.0.3 기반의 영화 추천 서비스 백엔드 서버.
 * JPA + MySQL 연동, JWT 인증, REST API 제공.</p>
 *
 * <p>{@code @EnableScheduling}은 구독 만료 처리 스케줄러
 * ({@link com.monglepick.monglepickbackend.domain.payment.service.SubscriptionService#processExpiredSubscriptions()})를
 * 활성화하기 위해 선언되었다. 매일 새벽 2시에 만료된 구독을 자동 EXPIRED 처리한다.</p>
 */
@SpringBootApplication
@EnableScheduling
public class MonglepickBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonglepickBackendApplication.class, args);
    }
}
