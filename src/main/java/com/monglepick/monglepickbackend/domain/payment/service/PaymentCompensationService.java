package com.monglepick.monglepickbackend.domain.payment.service;

import com.monglepick.monglepickbackend.domain.payment.entity.PaymentOrder;
import com.monglepick.monglepickbackend.domain.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 결제 보상(Compensation) 트랜잭션 전담 서비스.
 *
 * <p>결제 승인 후 포인트 지급이 실패했을 때, PG 환불 결과를 DB에 기록하는 책임을 담당한다.</p>
 *
 * <h3>왜 별도 클래스로 분리했는가?</h3>
 * <p>Spring의 {@code @Transactional}은 AOP 프록시 기반으로 동작한다.
 * 같은 클래스({@link PaymentService}) 내부에서 {@code this.someMethod()}를 호출하면
 * 프록시를 경유하지 않아 {@code @Transactional} 어노테이션이 무시된다.
 * 따라서 "{@code REQUIRES_NEW}로 독립 트랜잭션 실행"이 필요한 보상 로직은
 * <b>반드시 별도 Spring Bean으로 분리</b>해야 한다.</p>
 *
 * <h3>REQUIRES_NEW 전파 정책의 의미</h3>
 * <p>결제 승인 후 포인트 지급 실패 시 호출 흐름:</p>
 * <ol>
 *   <li>{@code PaymentService.confirmPayment()} — 원본 트랜잭션 (이미 rollback-only 상태)</li>
 *   <li>catch 블록에서 {@code this.recordCompensationFailed()} 호출 시도
 *       → 같은 클래스이므로 AOP 미적용, {@code REQUIRES_NEW} 무시됨 (이슈)</li>
 *   <li><b>해결</b>: 이 클래스의 {@link #recordCompensationFailed} 호출
 *       → 새 독립 트랜잭션 시작 → 원본 롤백과 무관하게 상태 저장</li>
 * </ol>
 *
 * <h3>사용 위치</h3>
 * <p>{@link PaymentService#confirmPayment} 의 catch 블록에서만 호출한다.</p>
 *
 * @see PaymentService 결제 서비스 (호출자)
 * @see PaymentOrder#markCompensationFailed(String) 상태 변경 도메인 메서드
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentCompensationService {

    /** 결제 주문 리포지토리 — 보상 실패 상태를 독립 트랜잭션으로 저장할 때 사용 */
    private final PaymentOrderRepository orderRepository;

    /**
     * 결제 보상 취소 최종 실패 상태를 독립 트랜잭션으로 DB에 기록한다.
     *
     * <h4>호출 조건</h4>
     * <p>아래 두 가지가 모두 실패했을 때만 호출한다:</p>
     * <ol>
     *   <li>Toss 결제 승인 성공 후 DB 저장(포인트 지급 포함) 실패</li>
     *   <li>보상 목적으로 시도한 Toss 결제 취소(cancelPayment)도 실패</li>
     * </ol>
     *
     * <h4>왜 REQUIRES_NEW인가?</h4>
     * <p>이 메서드는 {@code PaymentService.confirmPayment()} 의 catch 블록에서 호출된다.
     * 이 시점에 원본 트랜잭션은 이미 rollback-only로 마킹되어 있다.
     * {@code REQUIRED}(기본값)를 사용하면 rollback-only 트랜잭션에 합류하므로
     * {@code orderRepository.save()}가 실제로 커밋되지 않는다.
     * {@code REQUIRES_NEW}로 새 트랜잭션을 시작하면 원본 롤백과 무관하게 상태를 저장한다.</p>
     *
     * <h4>실패 시 처리</h4>
     * <p>이 메서드 자체도 실패하면 CRITICAL 로그를 남기고 예외를 전파하지 않는다.
     * 보상 상태 저장 실패가 원본 에러 전파를 막아서는 안 되기 때문이다.</p>
     *
     * @param order          상태를 변경할 결제 주문 엔티티
     * @param compensationMsg 보상 실패 사유 (failedReason 컬럼에 저장됨)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompensationFailed(PaymentOrder order, String compensationMsg) {
        try {
            // ── 관리자 즉시 알림 (설계서 §13.8) ──
            // COMPENSATION_FAILED는 사용자 카드가 청구됐으나 포인트가 미지급된 위험 상태이다.
            // 현재는 ERROR 로그로 알림하며, 향후 Slack/PagerDuty 연동 시 이 위치에 구현한다.
            // 모니터링 시스템(Grafana/Loki)에서 "[COMPENSATION_FAILED]" 패턴으로 알람 설정 권장.
            log.error("[COMPENSATION_FAILED] 즉시 관리자 확인 필요! " +
                            "orderId={}, userId={}, amount={}원, 시각={}",
                    order.getPaymentOrderId(),
                    order.getUserId(),
                    order.getAmount(),
                    LocalDateTime.now());

            // COMPENSATION_FAILED 상태로 변경 (도메인 메서드: failedReason 기록 포함)
            order.markCompensationFailed(compensationMsg);

            // REQUIRES_NEW 독립 트랜잭션으로 저장 — 원본 트랜잭션 롤백과 완전히 분리됨
            orderRepository.save(order);
            orderRepository.flush(); // 즉시 DB에 반영 (커밋 전 오류 조기 감지)

            log.error("[CRITICAL][C-B3] 주문 상태 COMPENSATION_FAILED 저장 완료: orderId={}, reason={}",
                    order.getPaymentOrderId(), compensationMsg);

        } catch (Exception statusEx) {
            // COMPENSATION_FAILED 상태 저장조차 실패한 경우.
            // 이 시점에서 할 수 있는 것은 로그를 남겨 운영팀이 수동 조치하게 하는 것뿐이다.
            // 예외를 전파하면 원본 예외가 억제(suppressed)되어 호출자가 올바른 에러를 받지 못한다.
            log.error("[CRITICAL][C-B3] 주문 상태 COMPENSATION_FAILED 저장 실패 " +
                            "— 운영팀 즉시 수동 조치 필요: orderId={}, saveError={}",
                    order.getPaymentOrderId(), statusEx.getMessage(), statusEx);
        }
    }
}
