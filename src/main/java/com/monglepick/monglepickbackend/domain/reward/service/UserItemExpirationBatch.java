package com.monglepick.monglepickbackend.domain.reward.service;

import com.monglepick.monglepickbackend.domain.reward.entity.UserItem;
import com.monglepick.monglepickbackend.domain.reward.repository.UserItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 보유 아이템 만료 배치 (2026-04-14 신규, C 방향).
 *
 * <p>{@code expires_at}이 현재 시각보다 이전이고 아직 ACTIVE 또는 EQUIPPED인 UserItem을 찾아
 * status=EXPIRED로 전환하고 착용 상태를 자동 해제한다. {@link com.monglepick.monglepickbackend.MonglepickBackendApplication}
 * 에 {@code @EnableScheduling}이 이미 선언되어 있어 추가 설정 없이 동작한다.</p>
 *
 * <h3>스케줄 전략</h3>
 * <ul>
 *   <li>주기: 15분 (cron {@code 0 * /15 * * * *}) — 배지 만료가 즉시 반영될 필요는 없지만 "몇 분 이내"는 기대됨.</li>
 *   <li>페이지 크기: 500 — 대량 만료 시 한 번의 트랜잭션이 너무 커지는 것 방지.</li>
 *   <li>최대 반복: 20 (= 1회 실행당 최대 10,000건) — 무한 루프 가드.</li>
 * </ul>
 *
 * <h3>구현 노트</h3>
 * <p>findExpirableBefore()는 Page를 반환하지만 상태를 같은 트랜잭션 내에서 UPDATE하므로
 * 다음 조회에서 이미 만료된 레코드는 제외된다. 페이지 번호를 0으로 고정하고 반복하면서
 * 남은 건이 없을 때 종료하는 전형적인 "drain loop" 패턴을 사용한다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserItemExpirationBatch {

    /** 한 번에 처리할 아이템 개수 */
    private static final int BATCH_SIZE = 500;

    /** 1회 실행당 최대 반복 횟수 (= 최대 500*20=10,000건) */
    private static final int MAX_ITERATIONS = 20;

    private final UserItemRepository userItemRepository;

    /**
     * 15분 주기로 만료 아이템을 스캔하여 status=EXPIRED로 전환한다.
     *
     * <p>cron 표현식: 초 분 시 일 월 요일. 매 15분마다 0초 기준.</p>
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void expireOverdueItems() {
        LocalDateTime now = LocalDateTime.now();
        log.info("보유 아이템 만료 배치 시작: now={}", now);

        int totalExpired = 0;
        int iterations = 0;

        while (iterations < MAX_ITERATIONS) {
            int expired = processOnePage(now);
            totalExpired += expired;
            iterations++;

            if (expired < BATCH_SIZE) {
                break; // 페이지가 가득 차지 않았다면 더 이상 만료 대상 없음
            }
        }

        if (iterations >= MAX_ITERATIONS) {
            log.warn("보유 아이템 만료 배치 상한 도달 — 만료 대상이 MAX_ITERATIONS*BATCH_SIZE={}건을 초과함. "
                    + "다음 배치에서 계속 처리됨.", MAX_ITERATIONS * BATCH_SIZE);
        }

        log.info("보유 아이템 만료 배치 완료: 총 {}건 만료 처리, 반복={}회", totalExpired, iterations);
    }

    /**
     * 단일 페이지 처리 — 독립 트랜잭션으로 쓰기를 격리.
     *
     * <p>각 페이지마다 별도 트랜잭션을 열어 중간 실패 시 일부만 커밋되어도
     * 다음 배치 실행에서 재시도된다. 대량 만료 시 긴 단일 트랜잭션보다 안전.</p>
     *
     * @param now 기준 시각
     * @return 이번 페이지에서 만료 처리된 개수
     */
    @Transactional
    protected int processOnePage(LocalDateTime now) {
        Page<UserItem> page = userItemRepository.findExpirableBefore(
                now, PageRequest.of(0, BATCH_SIZE));

        for (UserItem item : page.getContent()) {
            item.markExpired();
            log.debug("아이템 만료: userItemId={}, userId={}, expiresAt={}",
                    item.getUserItemId(), item.getUserId(), item.getExpiresAt());
        }

        return page.getNumberOfElements();
    }
}
