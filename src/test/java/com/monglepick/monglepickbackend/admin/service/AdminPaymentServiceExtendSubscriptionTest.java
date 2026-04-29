package com.monglepick.monglepickbackend.admin.service;

import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminExtendSubscriptionRequest;
import com.monglepick.monglepickbackend.admin.dto.AdminPaymentDto.AdminExtendSubscriptionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AdminPaymentService#extendSubscription} 회귀 안전망 (2026-04-29).
 *
 * <h3>회귀 배경</h3>
 * <p>운영 관리자 페이지 "결제/포인트 관리 → 구독 관리 → 연장" 버튼이 500 (서버 내부 오류) 로 회귀.
 * 근본 원인: {@link AdminPaymentService} 클래스 레벨 {@code @Transactional(readOnly = true)} 컨텍스트
 * 에서 {@code extendSubscription} 메서드가 메서드 레벨 {@code @Transactional} 오버라이드를 빠뜨려,
 * 내부 {@link com.monglepick.monglepickbackend.domain.payment.service.SubscriptionService#extendSubscription}
 * 가 REQUIRED 전파로 외부 read-only 트랜잭션에 합류 → {@code UserSubscription.renew()} 의
 * 영속성 변경이 read-only 컨텍스트에서 거부되어 500 발생.</p>
 *
 * <h3>회귀 차단</h3>
 * <p>{@code extendSubscription} 메서드에 {@link Transactional} 이 직접 부착되어 있고
 * {@code readOnly()} 가 false 임을 확인한다. 같은 패턴으로 {@code cancelSubscription} /
 * {@code compensateOrder} 가 이미 {@code @Transactional} 을 부착하고 있으므로 동일 컨벤션이
 * 적용되었는지 보장한다.</p>
 *
 * <h3>왜 reflection 인가</h3>
 * <p>readOnly TX 에서 inner write 가 거부되는 회귀는 통합 테스트 환경(Mockito 기반 단위 테스트로는
 * 트랜잭션 매니저가 동작하지 않음)에서만 재현되며, 매번 풀 컨텍스트 부팅 비용을 들이는 대신
 * 메서드의 트랜잭션 어노테이션을 직접 검사해 "오버라이드 누락" 자체를 차단한다.</p>
 */
class AdminPaymentServiceExtendSubscriptionTest {

    @Test
    @DisplayName("extendSubscription 은 @Transactional 오버라이드(쓰기) 가 있어야 한다 — 클래스 readOnly 회귀 차단")
    void extendSubscription_isAnnotatedWithWriteTransactional() throws NoSuchMethodException {
        Method method = AdminPaymentService.class.getDeclaredMethod(
                "extendSubscription", Long.class, AdminExtendSubscriptionRequest.class
        );

        Transactional tx = method.getAnnotation(Transactional.class);

        assertThat(tx)
                .as("AdminPaymentService.extendSubscription 에 @Transactional 이 직접 부착되어야 한다 "
                        + "(클래스 레벨 readOnly=true 를 쓰기로 오버라이드).")
                .isNotNull();

        assertThat(tx.readOnly())
                .as("extendSubscription 의 @Transactional 은 readOnly=false 여야 한다 "
                        + "(내부 SubscriptionService.extendSubscription 의 UserSubscription.renew() 가 "
                        + "read-only 컨텍스트에서 거부되어 500 이 발생하던 회귀).")
                .isFalse();
    }

    @Test
    @DisplayName("AdminExtendSubscriptionResponse 는 success/newExpiresAt/message 3-튜플로 회귀 시 컴파일 차단")
    void responseDtoShape_isStable() {
        // DTO 시그니처가 임의로 바뀌면 어드민 화면이 깨진다 — 컴파일 시점에서 차단
        AdminExtendSubscriptionResponse response = new AdminExtendSubscriptionResponse(
                true,
                LocalDateTime.of(2026, 5, 24, 0, 0),
                "구독이 1개월 연장되었습니다."
        );

        assertThat(response.success()).isTrue();
        assertThat(response.newExpiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 24, 0, 0));
        assertThat(response.message()).contains("연장");
    }
}
