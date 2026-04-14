package com.monglepick.monglepickbackend.domain.content.config;

import com.monglepick.monglepickbackend.domain.content.entity.Banner;
import com.monglepick.monglepickbackend.domain.content.mapper.ContentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 홈 슬라이드 배너 데모 시드 초기화 컴포넌트 — 2026-04-14 신규.
 *
 * <p>사용자 홈 우측하단 슬라이드 배너 위젯({@code SideSlideBanner}) 은
 * {@code GET /api/v1/banners?position=MAIN} 엔드포인트가 활성 배너를 하나라도
 * 반환해야 화면에 노출된다. 신규 개발 환경 또는 로컬 DB 리셋 직후에는
 * banners 테이블이 비어 있어 배너 위젯이 전혀 보이지 않는 문제가 있었다.</p>
 *
 * <p>본 Initializer 는 앱 기동 시 banners 테이블이 비어 있을 때만 데모 배너
 * 2건을 INSERT 한다. 이미 운영자가 관리자 페이지에서 배너를 등록했다면
 * 전체 스킵하여 사용자 데이터를 보호한다(멱등성 보장).</p>
 *
 * <h3>시드 데이터</h3>
 * <ol>
 *   <li>AI 영화 추천 시작하기 — 링크 /chat, sortOrder=0</li>
 *   <li>회원 전용 포인트 상점 오픈 — 링크 /point, sortOrder=1</li>
 * </ol>
 *
 * <p>이미지 URL 은 Client 정적 리소스({@code /mongle-transparent.png}) 를 가리키도록
 * 설정한다. banners 테이블의 {@code image_url} 컬럼이 NOT NULL 이므로 null 불가.
 * 만약 해당 경로의 이미지를 로드하지 못해도 SideSlideBanner 가 {@code onError}
 * 에서 primary 그라데이션 fallback + 제목 텍스트로 대체한다.</p>
 *
 * <h3>실행 순서</h3>
 * <p>{@code @Order(200)} — 다른 초기화(FAQ/업적/리워드 등) 이후 늦게 실행한다.</p>
 */
@Slf4j
@Component
@Order(200)
@RequiredArgsConstructor
public class BannerDemoInitializer implements ApplicationRunner {

    /** MyBatis ContentMapper — 배너 CRUD 전담 (JPA 미사용 도메인) */
    private final ContentMapper contentMapper;

    /**
     * 앱 기동 후 banners 테이블이 비어 있으면 데모 배너 시드를 INSERT 한다.
     *
     * <p>트랜잭션 내에서 일괄 저장되므로 중간 실패 시 전체 롤백된다.
     * 이미 배너가 한 건이라도 존재하는 경우(운영 중 재시작 시) 조기 반환하여
     * 운영 데이터를 보호한다.</p>
     *
     * @param args 애플리케이션 시작 인자 (미사용)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // countAllBanners 는 전체 건수(활성/비활성 무관) — 1건이라도 있으면 시드 스킵
        long existing = contentMapper.countAllBanners();
        if (existing > 0) {
            log.debug("[BannerDemoInitializer] 기존 배너 {}건 존재 — 데모 시드 스킵", existing);
            return;
        }

        // ───────── 시드 데이터 정의 ─────────
        // banners.image_url 은 NOT NULL 이므로 Client 정적 리소스 경로 사용.
        // 이미지 로드 실패 시 SideSlideBanner 의 onError 가 primary 그라데이션 + 제목 fallback 으로 대체.
        final String placeholderImage = "/mongle-transparent.png";
        List<Banner> seeds = List.of(
                Banner.builder()
                        .title("몽글픽 AI 에게 영화 추천받기")
                        .imageUrl(placeholderImage)
                        .linkUrl("/chat")
                        .position("MAIN")
                        .sortOrder(0)
                        .isActive(true)
                        .build(),
                Banner.builder()
                        .title("포인트 상점에서 아이템 교환")
                        .imageUrl(placeholderImage)
                        .linkUrl("/point")
                        .position("MAIN")
                        .sortOrder(1)
                        .isActive(true)
                        .build()
        );

        // ───────── 순차 INSERT ─────────
        for (Banner banner : seeds) {
            contentMapper.insertBanner(banner);
        }
        log.info("[BannerDemoInitializer] 데모 배너 {}건 시드 완료", seeds.size());
    }
}
