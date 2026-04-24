package com.monglepick.monglepickbackend.global.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Elasticsearch Java Client 설정.
 *
 * <p>Spring Data Elasticsearch 는 사용하지 않는다 (저장소 패턴 오버헤드 불필요).
 * 순수 {@code co.elastic.clients.elasticsearch.ElasticsearchClient} 동기 API만 사용하며,
 * 고객센터 FAQ 색인({@code support_faq} 인덱스) 전용으로 사용된다.</p>
 *
 * <h3>연결 체인</h3>
 * <pre>
 *   ELASTICSEARCH_URL (환경변수 또는 application.yml)
 *     → org.elasticsearch.client.RestClient (Low-level HTTP 클라이언트)
 *     → co.elastic.clients.transport.rest_client.RestClientTransport (JacksonJsonpMapper 직렬화)
 *     → co.elastic.clients.elasticsearch.ElasticsearchClient (High-level API)
 * </pre>
 *
 * <h3>보안</h3>
 * <p>운영 ES 컨테이너는 {@code xpack.security.enabled=false} 설정이므로 Basic Auth 불필요.
 * 보안이 활성화된 환경에서 사용하려면 {@link org.apache.http.impl.client.BasicCredentialsProvider}
 * 를 RestClient 빌더에 추가해야 한다.</p>
 *
 * <h3>ES URI 환경변수</h3>
 * <ul>
 *   <li>로컬 개발: {@code ELASTICSEARCH_URL=http://localhost:9200} (application.yml 기본값)</li>
 *   <li>VM4 운영: {@code ELASTICSEARCH_URL=http://10.20.0.10:9200} (.env.prod)</li>
 * </ul>
 */
@Configuration
public class ElasticsearchConfig {

    /**
     * ES 연결 URI.
     * {@code application.yml spring.elasticsearch.uris} 또는
     * {@code ELASTICSEARCH_URL} 환경변수로 주입된다.
     */
    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUri;

    /**
     * {@link ElasticsearchClient} Bean 등록.
     *
     * <p>JacksonJsonpMapper 를 사용하여 Spring 의 Jackson ObjectMapper 와
     * 같은 시리얼라이제이션 컨벤션을 유지한다.
     * RestClient 는 Apache HttpClient 기반으로 커넥션 풀링을 내부적으로 처리한다.</p>
     *
     * @return 동기 방식 ElasticsearchClient 인스턴스
     */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        // URI 파싱 — "http://host:port" 형태를 HttpHost 로 변환
        URI uri = URI.create(elasticsearchUri.trim());
        HttpHost httpHost = new HttpHost(
                uri.getHost(),
                uri.getPort() > 0 ? uri.getPort() : 9200,
                uri.getScheme() != null ? uri.getScheme() : "http"
        );

        // Low-level REST 클라이언트 (Apache HttpClient 기반)
        RestClient restClient = RestClient.builder(httpHost).build();

        // JacksonJsonpMapper: Jackson ObjectMapper 기반 JSON ↔ JSONP 변환
        // Spring Boot 의 기본 ObjectMapper 를 재사용하지 않고 내부 기본 인스턴스 사용
        // (ES 전용 직렬화 규칙을 Spring 전역 설정과 분리하기 위함)
        RestClientTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );

        return new ElasticsearchClient(transport);
    }
}
