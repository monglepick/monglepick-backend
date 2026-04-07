package com.monglepick.monglepickbackend.global.config;

import com.p6spy.engine.logging.P6LogOptions;
import com.p6spy.engine.spy.P6SpyOptions;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import jakarta.annotation.PostConstruct;
import org.hibernate.engine.jdbc.internal.BasicFormatterImpl;
import org.hibernate.engine.jdbc.internal.Formatter;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * P6Spy SQL 로그 포맷터.
 *
 * <p>P6Spy 가 인터셉트한 모든 JDBC 호출(SELECT/INSERT/UPDATE/DELETE/DDL/commit/rollback)을
 * Hibernate {@link BasicFormatterImpl} 로 가독성 있게 정리한 뒤 SLF4J 로거(p6spy)에 출력한다.
 * 실행 시간(ms), 카테고리, 커넥션 ID, 호출 위치(com.monglepick 패키지의 첫 스택 프레임)를
 * 함께 표기한다. 결과셋(result/resultset) 카테고리는 노이즈 제거를 위해 P6Spy 단에서 제외한다.</p>
 *
 * <p>출력 예시:
 * <pre>
 * ┌── P6Spy [statement] | 12 ms | conn=3 | UserRepository.findByEmail:42
 * |
 * |    select
 * |        u1_0.id,
 * |        u1_0.email,
 * |        u1_0.nickname
 * |    from
 * |        users u1_0
 * |    where
 * |        u1_0.email=?
 * |
 * └────────────────────────────────────────────────────────────────────
 * </pre>
 * </p>
 *
 * <p>{@code com.github.gavlyukovskiy:p6spy-spring-boot-starter} 가 자동으로
 * {@code DataSource} 를 P6SpyDataSource 로 래핑해 주므로, 본 클래스는 추가적으로
 * 메시지 포맷 전략과 카테고리 필터만 등록한다.</p>
 *
 * <p>주의: P6Spy 는 본 클래스를 리플렉션으로 별도 인스턴스화하므로
 * 인스턴스 필드를 두지 말 것. 모든 의존 객체는 {@code static} 으로 두어 안전하게 공유한다.</p>
 */
@Configuration
public class P6SpyConfig implements MessageFormattingStrategy {

    /** Hibernate 가 제공하는 SQL 포맷터 (스레드세이프). */
    private static final Formatter FORMATTER = new BasicFormatterImpl();

    /** 호출 위치 추출 시 무시할 패키지/클래스 prefix (P6Spy 자체 + 본 포맷터). */
    private static final String CALLER_BASE_PACKAGE = "com.monglepick";

    /** 결과셋 fetch 마다 출력되어 노이즈를 만드는 카테고리. P6Spy 단에서 사전 제외. */
    private static final String EXCLUDED_CATEGORIES = "info,debug,result,resultset";

    /**
     * Spring Boot 컨텍스트 초기화 시 P6Spy 옵션 싱글턴에 본 클래스를 로그 포맷터로 등록하고
     * 결과셋 노이즈 카테고리를 사전에 차단한다.
     * 이후 P6Spy 가 SQL 을 인터셉트할 때마다 {@link #formatMessage} 가 호출된다.
     */
    @PostConstruct
    public void registerFormatter() {
        // 메시지 포맷 전략 등록 (P6SpyOptions)
        P6SpyOptions.getActiveInstance().setLogMessageFormat(this.getClass().getName());
        // 결과셋 등 노이즈 카테고리 제외 (P6LogOptions — Logging 모듈 옵션)
        // result/resultset = SELECT 결과 row 마다 한 번씩 호출되어 로그가 폭증
        P6LogOptions.getActiveInstance().setExcludecategories(EXCLUDED_CATEGORIES);
    }

    /**
     * P6Spy 가 SQL 한 건마다 호출하는 포맷 콜백.
     *
     * @param connectionId P6Spy 가 부여한 커넥션 식별자
     * @param now          실행 시각 (P6Spy 내부 포맷)
     * @param elapsed      실행 시간(ms)
     * @param category     SQL 카테고리 (statement/commit/rollback/batch 등)
     * @param prepared     바인딩 전 PreparedStatement 원본 (placeholder ? 그대로)
     * @param sql          바인딩 적용된 실제 실행 SQL
     * @param url          DataSource URL
     * @return             SLF4J 로거에 전달될 박스 포맷 메시지
     */
    @Override
    public String formatMessage(int connectionId, String now, long elapsed,
                                 String category, String prepared, String sql, String url) {
        String caller = extractCallerLocation();

        // commit/rollback/connection 등 실제 SQL 이 비어 있는 호출은 한 줄로 압축
        if (sql == null || sql.trim().isEmpty()) {
            return String.format(Locale.ROOT,
                "%n┌── P6Spy [%s] | %d ms | conn=%d%s%n└──",
                category, elapsed, connectionId, caller);
        }

        // SELECT/INSERT/UPDATE/DELETE/DDL 은 Hibernate 포맷터로 들여쓰기 정리
        String formatted = FORMATTER.format(sql);
        return String.format(Locale.ROOT,
            "%n┌── P6Spy [%s] | %d ms | conn=%d%s%s%n└────────────────────────────────────────────────────────────────────",
            category, elapsed, connectionId, caller, formatted);
    }

    /**
     * 현재 스택 트레이스에서 본 프로젝트 패키지({@code com.monglepick.*})의 첫 프레임을 찾아
     * "ClassName.methodName:line" 형태로 반환한다. 어떤 Repository / Service / Mapper 에서
     * 이 SQL 이 발생했는지 즉시 확인할 수 있게 해 준다.
     *
     * <p>P6Spy 자신의 스택은 {@code CALLER_BASE_PACKAGE} 필터로 자연스럽게 걸러지고,
     * 본 포맷터({@code P6SpyConfig}) 자기 자신은 클래스 단순명 매칭으로 추가 제외한다.</p>
     *
     * @return 위치 표기 (앞에 " | " 구분자 포함). 매칭 프레임이 없으면 빈 문자열.
     */
    private static String extractCallerLocation() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (StackTraceElement el : stack) {
            String cls = el.getClassName();
            if (!cls.startsWith(CALLER_BASE_PACKAGE)) continue;
            if (cls.endsWith("P6SpyConfig")) continue;
            // CGLIB 프록시(클래스명에 $$ 포함)는 원본 클래스명으로 정리
            int proxyIdx = cls.indexOf("$$");
            if (proxyIdx > 0) cls = cls.substring(0, proxyIdx);
            int dot = cls.lastIndexOf('.');
            String simple = dot >= 0 ? cls.substring(dot + 1) : cls;
            return String.format(Locale.ROOT, " | %s.%s:%d",
                simple, el.getMethodName(), el.getLineNumber());
        }
        return "";
    }
}
