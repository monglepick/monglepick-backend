package com.monglepick.monglepickbackend.global.config;

import javax.sql.DataSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * MyBatis 설정 — 프로젝트 기본 데이터 접근 계층.
 *
 * <p>김민규/이민수 도메인은 MyBatis Mapper + XML로 SQL을 직접 작성한다.
 * {@code @MapperScan}으로 각 도메인의 mapper 패키지를 스캔하여
 * {@code @Mapper} 인터페이스를 자동 등록한다.</p>
 *
 * <p>윤형주 도메인은 JPA Repository를 사용하며,
 * 같은 DataSource/TransactionManager를 공유한다.</p>
 *
 * <h3>MyBatis와 JPA 공존 구조</h3>
 * <ul>
 *   <li>DataSource: HikariCP 커넥션 풀 공유</li>
 *   <li>TransactionManager: Spring의 PlatformTransactionManager 공유</li>
 *   <li>한 Service에서 Mapper와 JPA Repository를 함께 사용해도 같은 트랜잭션</li>
 * </ul>
 *
 * <h3>Mapper 패키지 규칙</h3>
 * <pre>
 * domain/{도메인}/mapper/{도메인명}Mapper.java  — @Mapper 인터페이스
 * resources/mapper/{도메인}/{도메인명}Mapper.xml — SQL 매퍼 XML
 * </pre>
 *
 * @see org.mybatis.spring.annotation.MapperScan
 * @see SqlSessionFactory
 */
/**
 * MyBatis 설정 — Spring Boot 4.x 호환을 위해 SqlSessionFactory/Template을 명시적으로 등록.
 *
 * <p>mybatis-spring-boot-starter 3.0.4는 Spring Boot 3.x 기반 자동 구성을 포함하므로
 * Spring Boot 4.x 환경에서 SqlSessionFactory 자동 등록이 누락될 수 있다.
 * 이를 방지하기 위해 @Bean으로 직접 정의한다.</p>
 */
@org.springframework.context.annotation.Configuration
@MapperScan(
    basePackages = {
        // ── 김민규 도메인 ──
        "com.monglepick.monglepickbackend.domain.auth.mapper",
        "com.monglepick.monglepickbackend.domain.user.mapper",
        "com.monglepick.monglepickbackend.domain.playlist.mapper",
        // ── 이민수 도메인 ──
        "com.monglepick.monglepickbackend.domain.community.mapper",
        "com.monglepick.monglepickbackend.domain.review.mapper",
        "com.monglepick.monglepickbackend.domain.content.mapper",
        // ── 관리자 전용 (윤형주 유지보수) ──
        //   AdminUserMapper 등 admin 전용 동적 검색 Mapper
        "com.monglepick.monglepickbackend.admin.mapper"
    },
    sqlSessionFactoryRef = "sqlSessionFactory"
)
public class MyBatisConfig {

    @Autowired
    private DataSource dataSource;

    /**
     * SqlSessionFactory 빈 — mybatis 전역 설정 + Mapper XML 경로 지정.
     *
     * <p>application.yml의 mybatis.* 설정 대신 여기서 직접 구성한다.</p>
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);

        // Mapper XML 위치 (classpath:mapper/**/*.xml)
        factory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath:mapper/**/*.xml")
        );

        // typeAliases 패키지 — XML에서 클래스명만으로 사용 가능
        factory.setTypeAliasesPackage("com.monglepick.monglepickbackend.domain");

        // MyBatis Configuration
        Configuration config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);       // snake_case → camelCase
        config.setJdbcTypeForNull(JdbcType.NULL);        // null 파라미터 타입
        config.setDefaultFetchSize(100);
        config.setDefaultStatementTimeout(30);
        factory.setConfiguration(config);

        return factory.getObject();
    }

    /**
     * SqlSessionTemplate 빈 — thread-safe SqlSession 구현체.
     */
    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
