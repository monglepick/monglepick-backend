package com.monglepick.monglepickbackend.domain.support.infra;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import com.monglepick.monglepickbackend.domain.support.entity.SupportFaq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 고객센터 FAQ Elasticsearch 색인 담당 컴포넌트.
 *
 * <p>Agent 팀과 합의된 인덱스 스펙({@code support_faq})을 기준으로
 * DB CRUD 이벤트 발생 시 실시간으로 ES 에 반영한다.</p>
 *
 * <h3>인덱스 스펙 (Agent 팀 합의 — 변경 금지)</h3>
 * <ul>
 *   <li>인덱스명: {@value #INDEX_NAME}</li>
 *   <li>Nori analyzer 이름: {@value #NORI_ANALYZER}</li>
 *   <li>검색 부스트: {@code question^3, keywords^2, answer^1} (Agent 측 multi_match)</li>
 *   <li>ES {@code _id} = {@code faq_id} 와 동일값 사용 (upsert 멱등)</li>
 * </ul>
 *
 * <h3>ES 실패 처리 원칙</h3>
 * <p>모든 ES 호출은 {@code try/catch} 로 감싸 실패해도 DB 트랜잭션을 롤백하지 않는다.
 * 실패 시 {@code WARN} 레벨 로그만 남기고 반환한다.
 * 재색인이 필요한 경우 운영자가 {@link #reindexAll(List)} 를 Bootstrap 에서 수동 트리거하거나
 * 앱 재시작으로 {@link com.monglepick.monglepickbackend.domain.support.config.SupportFaqEsBootstrap}
 * 이 자동으로 전체 재색인을 수행한다.</p>
 *
 * <h3>Nori 플러그인 전제</h3>
 * <p>ES Docker 이미지에 {@code analysis-nori} 플러그인이 사전 설치되어 있어야 한다.
 * 플러그인이 없으면 {@link #ensureIndex()} 실패 후 warn 로그만 남기고 계속 진행된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportFaqElasticsearchIndexer {

    // ─────────────────────────────────────────────
    // Agent 팀 합의 상수 — 절대 변경 금지
    // ─────────────────────────────────────────────

    /** ES 인덱스명 */
    public static final String INDEX_NAME = "support_faq";

    /** Nori 커스텀 analyzer 이름 */
    private static final String NORI_ANALYZER = "nori_analyzer";

    /** Nori tokenizer 이름 (settings 내 정의용) */
    private static final String NORI_TOKENIZER = "nori_tokenizer";

    /** ES 날짜 필드 포맷 */
    private static final DateTimeFormatter ES_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ─────────────────────────────────────────────

    private final ElasticsearchClient esClient;

    // ─────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────

    /**
     * {@code support_faq} 인덱스가 없으면 Nori 매핑으로 생성한다.
     *
     * <p>이미 존재하면 no-op. 멱등 보장.
     * 인덱스 생성 실패(Nori 플러그인 미설치 포함)는 warn 로그만 남기고 계속 진행한다.</p>
     */
    public void ensureIndex() {
        try {
            // 인덱스 존재 여부 확인
            boolean exists = esClient.indices()
                    .exists(ExistsRequest.of(r -> r.index(INDEX_NAME)))
                    .value();

            if (exists) {
                log.info("[ES] '{}' 인덱스가 이미 존재합니다 — 생성 스킵", INDEX_NAME);
                return;
            }

            log.info("[ES] '{}' 인덱스 생성 시작 (Nori analyzer 포함)", INDEX_NAME);

            // Nori 는 analysis-nori 플러그인 타입이라 Elasticsearch Java Client 8.17 의
            // Tokenizer.Builder 빌더 API 로 직접 지정할 수 없다. 따라서 settings/mappings 를
            // 순수 JSON 으로 선언해 CreateIndexRequest.withJson() 으로 파싱한다.
            // 이 JSON 은 Agent 팀과 합의된 공유 스펙(필드/analyzer 이름)을 100% 반영한다.
            final String indexJson = "{"
                    + "\"settings\":{"
                    +   "\"analysis\":{"
                    +     "\"tokenizer\":{"
                    +       "\"" + NORI_TOKENIZER + "\":{"
                    +         "\"type\":\"nori_tokenizer\","
                    +         "\"decompound_mode\":\"mixed\""
                    +       "}"
                    +     "},"
                    +     "\"analyzer\":{"
                    +       "\"" + NORI_ANALYZER + "\":{"
                    +         "\"type\":\"custom\","
                    +         "\"tokenizer\":\"" + NORI_TOKENIZER + "\","
                    +         "\"filter\":[\"nori_readingform\",\"lowercase\"]"
                    +       "}"
                    +     "}"
                    +   "}"
                    + "},"
                    + "\"mappings\":{"
                    +   "\"properties\":{"
                    +     "\"faq_id\":{\"type\":\"long\"},"
                    +     "\"category\":{\"type\":\"keyword\"},"
                    +     "\"question\":{\"type\":\"text\",\"analyzer\":\"" + NORI_ANALYZER + "\"},"
                    +     "\"keywords\":{\"type\":\"text\",\"analyzer\":\"" + NORI_ANALYZER + "\"},"
                    +     "\"answer\":{\"type\":\"text\",\"analyzer\":\"" + NORI_ANALYZER + "\"},"
                    +     "\"is_published\":{\"type\":\"boolean\"},"
                    +     "\"helpful_count\":{\"type\":\"integer\"},"
                    +     "\"sort_order\":{\"type\":\"integer\"},"
                    +     "\"updated_at\":{\"type\":\"date\",\"format\":\"yyyy-MM-dd'T'HH:mm:ss||epoch_millis\"}"
                    +   "}"
                    + "}"
                    + "}";

            // withJson 은 CreateIndexRequest.Builder 에 settings + mappings 를 한번에 주입한다
            CreateIndexRequest createReq = CreateIndexRequest.of(r -> r
                    .index(INDEX_NAME)
                    .withJson(new StringReader(indexJson))
            );
            esClient.indices().create(createReq);

            log.info("[ES] '{}' 인덱스 생성 완료 (Nori analyzer: {})", INDEX_NAME, NORI_ANALYZER);

        } catch (Exception e) {
            // ES 인덱스 생성 실패 — 앱 기동을 막지 않는다
            log.warn("[ES] '{}' 인덱스 생성 실패 (ES 미연결 or Nori 미설치) — FAQ 색인 비활성. err={}",
                    INDEX_NAME, e.getMessage());
        }
    }

    /**
     * 단건 FAQ 색인 (create or update).
     *
     * <p>ES {@code _id} = {@code faq.faqId} 로 upsert 한다.
     * 동일 ID 가 이미 존재하면 덮어쓴다 (멱등).
     * 실패해도 DB 트랜잭션에 영향을 주지 않는다.</p>
     *
     * @param faq 색인할 FAQ 엔티티 (DB 저장 완료 후 전달)
     */
    public void index(SupportFaq faq) {
        if (faq == null || faq.getFaqId() == null) {
            log.warn("[ES] index() 호출 시 faq 또는 faqId 가 null — 색인 스킵");
            return;
        }

        try {
            Map<String, Object> doc = toEsDocument(faq);

            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(faq.getFaqId()))
                    .document(doc)
            );

            log.debug("[ES] FAQ 단건 색인 완료 — faqId={}, category={}",
                    faq.getFaqId(), faq.getCategory());

        } catch (Exception e) {
            log.warn("[ES] FAQ 단건 색인 실패 — faqId={}, err={}", faq.getFaqId(), e.getMessage());
        }
    }

    /**
     * 단건 FAQ 색인 삭제.
     *
     * <p>DB 삭제 직후 호출된다. ES 에 해당 문서가 없어도 예외 없이 종료한다.
     * 실패해도 DB 트랜잭션에 영향을 주지 않는다.</p>
     *
     * @param faqId 삭제할 FAQ PK
     */
    public void delete(Long faqId) {
        if (faqId == null) {
            log.warn("[ES] delete() 호출 시 faqId 가 null — 삭제 스킵");
            return;
        }

        try {
            esClient.delete(d -> d
                    .index(INDEX_NAME)
                    .id(String.valueOf(faqId))
            );

            log.debug("[ES] FAQ 단건 삭제 완료 — faqId={}", faqId);

        } catch (Exception e) {
            log.warn("[ES] FAQ 단건 삭제 실패 — faqId={}, err={}", faqId, e.getMessage());
        }
    }

    /**
     * 전체 FAQ 재색인 (bulk API).
     *
     * <p>앱 기동 시 {@link com.monglepick.monglepickbackend.domain.support.config.SupportFaqEsBootstrap}
     * 이 호출하거나, 관리자가 운영 중 수동으로 실행할 때 사용한다.
     * 기존 인덱스를 삭제하지 않고 upsert 방식으로 덮어쓴다 (멱등).
     * ES 에서 삭제되었지만 DB 에 여전히 존재하는 문서도 재색인되어 정합성이 복원된다.</p>
     *
     * <p>bulk 요청 단위는 최대 100건으로 분할하여 단일 요청 크기를 제한한다.</p>
     *
     * @param all DB 에서 조회한 전체 FAQ 목록
     */
    public void reindexAll(List<SupportFaq> all) {
        if (all == null || all.isEmpty()) {
            log.info("[ES] reindexAll() — FAQ 없음, 재색인 스킵");
            return;
        }

        log.info("[ES] 전체 재색인 시작 — 총 {}건", all.size());

        // 100건 단위로 분할하여 bulk 전송
        int batchSize = 100;
        int successCount = 0;

        for (int i = 0; i < all.size(); i += batchSize) {
            List<SupportFaq> batch = all.subList(i, Math.min(i + batchSize, all.size()));
            successCount += bulkIndex(batch);
        }

        log.info("[ES] 전체 재색인 완료 — 성공 {}건 / 전체 {}건", successCount, all.size());
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────

    /**
     * 단일 배치(최대 100건) bulk index 전송.
     *
     * @param batch FAQ 목록 (100건 이하)
     * @return 성공적으로 색인된 건수
     */
    private int bulkIndex(List<SupportFaq> batch) {
        try {
            List<BulkOperation> operations = new ArrayList<>(batch.size());

            for (SupportFaq faq : batch) {
                if (faq.getFaqId() == null) continue;

                Map<String, Object> doc = toEsDocument(faq);
                String docId = String.valueOf(faq.getFaqId());

                operations.add(BulkOperation.of(op -> op
                        .index(IndexOperation.of(i -> i
                                .index(INDEX_NAME)
                                .id(docId)
                                .document(doc)
                        ))
                ));
            }

            if (operations.isEmpty()) return 0;

            BulkResponse response = esClient.bulk(
                    BulkRequest.of(r -> r.operations(operations))
            );

            // 개별 오류 집계 — bulk 는 부분 성공이 가능하므로 errors 플래그 확인
            long errorCount = response.items().stream()
                    .filter(item -> item.error() != null)
                    .count();

            if (errorCount > 0) {
                log.warn("[ES] bulk 색인 일부 실패 — 배치 {}건 중 {}건 오류", batch.size(), errorCount);
            }

            return (int) (batch.size() - errorCount);

        } catch (Exception e) {
            log.warn("[ES] bulk 색인 실패 — 배치 {}건 전체 실패, err={}", batch.size(), e.getMessage());
            return 0;
        }
    }

    /**
     * {@link SupportFaq} 엔티티를 ES 문서 Map 으로 변환한다.
     *
     * <p>Agent 합의 필드명({@code faq_id, category, question, keywords, answer,
     * is_published, helpful_count, sort_order, updated_at}) 을 그대로 사용한다.</p>
     *
     * @param faq FAQ 엔티티
     * @return ES 색인용 Map (null 값 포함 — ES 가 null 을 누락 필드로 처리)
     */
    private Map<String, Object> toEsDocument(SupportFaq faq) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("faq_id",        faq.getFaqId());
        doc.put("category",      faq.getCategory() != null ? faq.getCategory().name() : null);
        doc.put("question",      faq.getQuestion());
        doc.put("keywords",      faq.getKeywords());   // null 허용 — Nori 색인 시 누락 필드로 처리
        doc.put("answer",        faq.getAnswer());
        doc.put("is_published",  faq.isPublished());
        doc.put("helpful_count", faq.getHelpfulCount());
        doc.put("sort_order",    faq.getSortOrder());
        // updated_at: BaseAuditEntity 에서 자동 관리 (null 이면 현재 시각 대신 null 저장)
        doc.put("updated_at",    faq.getUpdatedAt() != null
                ? faq.getUpdatedAt().format(ES_DATE_FORMAT) : null);
        return doc;
    }
}
