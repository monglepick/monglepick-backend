package com.monglepick.monglepickbackend.domain.support.config;

import com.monglepick.monglepickbackend.admin.repository.AdminNoticeRepository;
import com.monglepick.monglepickbackend.domain.support.entity.SupportNotice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 홈 공지 배너 데모 시드 초기화 컴포넌트 — 2026-04-14 신규.
 *
 * <p>{@code GET /api/v1/notices} 는 {@code displayType IN (BANNER/POPUP/MODAL)} 이고
 * 활성/기간 조건을 만족하는 공지만 반환한다. 신규 개발 환경에서는 이 조건을
 * 만족하는 공지가 0건이므로 홈 화면({@code HomePage}) 의 공지 배너 섹션이
 * 전혀 노출되지 않는 문제가 있었다.</p>
 *
 * <p>본 Initializer 는 앱 기동 시 {@code displayType IN (BANNER/POPUP/MODAL)} 조건
 * 공지가 하나도 없을 때만 데모 공지 2건을 INSERT 한다. 운영자가 관리자 페이지에서
 * BANNER/POPUP/MODAL 공지를 이미 등록했다면 전체 스킵한다(멱등성 보장).
 * LIST_ONLY 공지만 있는 경우에는 시드를 진행하여 홈 화면이 비지 않도록 한다.</p>
 *
 * <h3>시드 데이터</h3>
 * <ol>
 *   <li>[BANNER] 몽글픽에 오신 것을 환영합니다 — priority=10</li>
 *   <li>[BANNER] 서비스 안정화 작업 안내 — priority=5</li>
 * </ol>
 *
 * <h3>실행 순서</h3>
 * <p>{@code @Order(210)} — FAQ/도움말 시드 이후, 대부분의 도메인 초기화 뒤에 실행한다.</p>
 */
@Slf4j
@Component
@Order(210)
@RequiredArgsConstructor
public class NoticeDemoInitializer implements ApplicationRunner {

    /** 앱 메인 노출 조건을 만족하는 displayType 집합 — LIST_ONLY 는 제외 */
    private static final Set<String> APP_MAIN_DISPLAY_TYPES =
            Set.of("BANNER", "POPUP", "MODAL");

    /** 관리자 공지 JPA 리포지토리 — SupportNotice 단일 진실 원본 (2026-04-08 통합) */
    private final AdminNoticeRepository noticeRepository;

    /**
     * 앱 기동 후 BANNER/POPUP/MODAL 공지가 한 건도 없으면 데모 시드를 INSERT 한다.
     *
     * <p>트랜잭션 내에서 일괄 저장되므로 중간 실패 시 전체 롤백된다.
     * LIST_ONLY 공지만 있는 경우에는 데모를 진행하여 홈 화면이 비지 않도록 한다.</p>
     *
     * @param args 애플리케이션 시작 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = noticeRepository.countByDisplayTypeIn(APP_MAIN_DISPLAY_TYPES);
        if (existing > 0) {
            log.debug("[NoticeDemoInitializer] 앱 메인 노출 공지 {}건 존재 — 데모 시드 스킵", existing);
            return;
        }

        // 공개 시각 = 현재 시각. 종료 시각은 null 로 두어 무기한 노출.
        LocalDateTime now = LocalDateTime.now();

        // ───────── 시드 데이터 정의 ─────────
        List<SupportNotice> seeds = List.of(
                SupportNotice.builder()
                        .title("몽글픽에 오신 것을 환영합니다")
                        .content("AI 가 취향에 맞는 영화를 추천해 드립니다. "
                                + "상단의 'AI 추천'을 눌러 대화를 시작해 보세요.")
                        .noticeType("NOTICE")
                        .displayType("BANNER")
                        .isPinned(true)
                        .sortOrder(0)
                        .publishedAt(now)
                        .priority(10)
                        .isActive(true)
                        .linkUrl("/chat")
                        .build(),
                SupportNotice.builder()
                        .title("서비스 안정화 작업 진행 안내")
                        .content("더 나은 사용자 경험을 위해 지속적으로 개선하고 있습니다. "
                                + "불편을 드려 죄송합니다.")
                        .noticeType("MAINTENANCE")
                        .displayType("BANNER")
                        .isPinned(false)
                        .sortOrder(1)
                        .publishedAt(now)
                        .priority(5)
                        .isActive(true)
                        .build()
        );

        noticeRepository.saveAll(seeds);
        log.info("[NoticeDemoInitializer] 데모 공지 {}건 시드 완료", seeds.size());
    }
}
