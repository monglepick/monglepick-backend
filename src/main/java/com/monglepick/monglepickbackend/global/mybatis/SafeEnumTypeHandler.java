package com.monglepick.monglepickbackend.global.mybatis;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Enum 컬럼 안전 변환 TypeHandler — 모든 MyBatis Enum 매핑의 default 로 사용된다.
 *
 * <p>배경
 * <ul>
 *   <li>MyBatis 기본 {@code EnumTypeHandler} 는 컬럼 값이 빈 문자열 또는 invalid enum name 일 때
 *       {@code IllegalArgumentException} 을 던지고, 이는 {@code ResultMapException} 으로 감싸져
 *       admin 화면 500 에러로 노출된다.</li>
 *   <li>실제 운영에서 MySQL VARCHAR 컬럼은 NOT NULL + DEFAULT '' 로 정의된 경우가 있어
 *       빈 문자열이 정상적인 값으로 저장될 수 있다. 또한 enum 값을 ALTER 하면서 기존 row 가
 *       남으면 invalid value 가 발생할 수도 있다.</li>
 *   <li>샘플 데이터(시드) 적재 시 잘못된 값이 들어가도 운영 화면이 깨지지 않아야 한다.</li>
 * </ul>
 *
 * <p>동작
 * <ol>
 *   <li>{@code null} 또는 빈 문자열 → {@code null} 반환 (예외 없음)</li>
 *   <li>유효 enum name → {@code Enum.valueOf} 반환</li>
 *   <li>invalid enum name → {@code null} 반환 + {@code WARN} 로그</li>
 * </ol>
 *
 * <p>등록
 * <pre>
 * # application.yml
 * mybatis:
 *   configuration:
 *     default-enum-type-handler: com.monglepick.monglepickbackend.global.mybatis.SafeEnumTypeHandler
 * </pre>
 *
 * <p>위 설정으로 모든 {@code @Enumerated(EnumType.STRING)} JPA 컬럼이 MyBatis SELECT 결과
 * 매핑 시 자동으로 안전 변환된다. 개별 Mapper 에 typeHandler 명시 불필요.</p>
 *
 * <p>주의: INSERT/UPDATE 시에는 enum 값이 정상이라는 전제를 유지한다. 즉 application 코드에서
 * invalid enum name 을 직접 set 하는 일은 그대로 fail-fast 한다 (도메인 invariant 보호).</p>
 *
 * @param <E> 매핑 대상 enum 타입
 */
@Slf4j
public class SafeEnumTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

    private final Class<E> type;

    /**
     * MyBatis 가 default-enum-type-handler 로 등록 시 호출하는 생성자.
     *
     * <p>MyBatis 는 enum 컬럼을 매핑할 때마다 이 생성자에 enum class 를 전달한다.</p>
     *
     * @param type 매핑 대상 enum class — null 이면 IllegalArgumentException
     */
    public SafeEnumTypeHandler(Class<E> type) {
        if (type == null) {
            throw new IllegalArgumentException("Type argument cannot be null");
        }
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType)
            throws SQLException {
        // 쓰기는 정상 enum 만 받는다 (도메인 invariant). null 은 BaseTypeHandler 가 처리.
        if (jdbcType == null) {
            ps.setString(i, parameter.name());
        } else {
            ps.setObject(i, parameter.name(), jdbcType.TYPE_CODE);
        }
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return safeValueOf(rs.getString(columnName), columnName);
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return safeValueOf(rs.getString(columnIndex), "col[" + columnIndex + "]");
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return safeValueOf(cs.getString(columnIndex), "col[" + columnIndex + "]");
    }

    /**
     * 문자열 → enum 안전 변환.
     *
     * @param value DB 에서 읽은 문자열 (null/빈문자열/유효/invalid 가능)
     * @param columnHint 로그용 컬럼 식별자
     * @return 변환 결과 enum 또는 null (변환 불가능 시)
     */
    private E safeValueOf(String value, String columnHint) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            // invalid enum name. WARN 레벨 — 시드/마이그레이션 등 정상 시나리오에서도 발생 가능.
            log.warn("[SafeEnumTypeHandler] Invalid enum value for {} (column: {}): '{}' → null",
                    type.getSimpleName(), columnHint, value);
            return null;
        }
    }
}
