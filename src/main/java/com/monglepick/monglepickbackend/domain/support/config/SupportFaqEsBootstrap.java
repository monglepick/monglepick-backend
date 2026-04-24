package com.monglepick.monglepickbackend.domain.support.config;

import com.monglepick.monglepickbackend.domain.support.entity.SupportFaq;
import com.monglepick.monglepickbackend.domain.support.infra.SupportFaqElasticsearchIndexer;
import com.monglepick.monglepickbackend.domain.support.repository.SupportFaqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 앱 기동 시 Elasticsearch {@code support_faq} 인덱스를 보장하고
 * DB 전체 FAQ 를 재색인하는 Bootstrap 컴포넌트.
 *
 * <h3>실행 순서</h3>
 * <ul>
 *   <li>{@code @Order(100)}: {@link SupportFaqInitializer} — DB 시드 주입</li>
 *   <li>{@code @Order(150)}: 이 컴포넌트 — ES 인덱스 생성 + 전체 재색인 <b>(현재)</b></li>
 * </ul>
 * <p>시드 주입 이후에 실행해야 시드 데이터까지 ES 에 포함된다.</p>
 *
 * <h3>멱등성 보장</h3>
 * <ul>
 *   <li>{@link SupportFaqElasticsearchIndexer#ensureIndex()} — 인덱스 없으면 생성, 있으면 no-op</li>
 *   <li>{@link SupportFaqElasticsearchIndexer#reindexAll(List)} — upsert 방식으로 덮어쓰기</li>
 * </ul>
 *
 * <h3>실패 처리</h3>
 * <p>ES 연결 실패, Nori 플러그인 미설치, 네트워크 오류 등 모든 예외는
 * {@link SupportFaqElasticsearchIndexer} 내부에서 흡수된다.
 * Bootstrap 자체도 {@code try/catch} 로 감싸 앱 기동을 막지 않는다.</p>
 *
 * <h3>운영 참고</h3>
 * <p>ES 인덱스가 손상되거나 문서 정합성이 깨진 경우 앱을 재시작하면
 * 이 Bootstrap 이 자동으로 전체 재색인을 수행한다.</p>
 */
@Slf4j
@Component
@Order(150)
@RequiredArgsConstructor
public class SupportFaqEsBootstrap implements ApplicationRunner {

    /** ES 색인 담당 컴포넌트 */
    private final SupportFaqElasticsearchIndexer esIndexer;

    /** FAQ JPA 리포지토리 — 전체 재색인 시 DB 전체 조회에 사용 */
    private final SupportFaqRepository faqRepository;

    /**
     * 앱 기동 후 ES 인덱스를 보장하고 DB 전체 FAQ 를 재색인한다.
     *
     * <ol>
     *   <li>{@code ensureIndex()} — 인덱스/매핑이 없으면 Nori 매핑으로 생성</li>
     *   <li>DB 에서 전체 FAQ 조회</li>
     *   <li>{@code reindexAll()} — bulk upsert 로 ES 와 DB 정합성 복원</li>
     * </ol>
     *
     * <p>전체 과정이 {@code try/catch} 로 보호되어 있어 어떤 이유로 실패해도
     * 앱 기동 자체는 계속 진행된다.</p>
     *
     * @param args 앱 실행 인수 (미사용)
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("[SupportFaqEsBootstrap] ES 인덱스 보장 + FAQ 전체 재색인 시작");

        try {
            // 1단계: 인덱스 없으면 Nori 매핑으로 생성 (있으면 no-op)
            esIndexer.ensureIndex();

            // 2단계: DB 에서 전체 FAQ 조회
            List<SupportFaq> all = faqRepository.findAll();
            log.info("[SupportFaqEsBootstrap] DB FAQ {}건 조회 완료", all.size());

            // 3단계: 전체 재색인 (멱등 upsert — 기존 문서 덮어씀)
            esIndexer.reindexAll(all);

            log.info("[SupportFaqEsBootstrap] FAQ ES 재색인 완료 — {}건 처리", all.size());

        } catch (Exception e) {
            // ES 관련 예외 전체 흡수 — 앱 기동을 막지 않는다
            log.warn("[SupportFaqEsBootstrap] ES 초기화 실패 — 앱 기동은 계속 진행됨. err={}",
                    e.getMessage());
        }
    }
}
