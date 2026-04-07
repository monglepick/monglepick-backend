package com.monglepick.monglepickbackend.domain.auth.service;

import com.monglepick.monglepickbackend.domain.auth.entity.RefreshEntity;
import com.monglepick.monglepickbackend.domain.auth.mapper.RefreshMapper;
import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.mapper.UserMapper;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import com.monglepick.monglepickbackend.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JWT 토큰 라이프사이클 관리 서비스.
 *
 * <p>KMG 프로젝트의 JWTService 패턴을 적용하여
 * Refresh Token Rotation과 화이트리스트 관리를 처리한다.</p>
 *
 * <p>C-4 수정: RuntimeException → BusinessException으로 교체하여
 * GlobalExceptionHandler가 적절한 HTTP 상태 코드와 에러 응답을 반환하도록 함.</p>
 *
 * <p>쿠키 보안 수정: cookie2Header() 메서드 삭제.
 * 쿠키 읽기/쓰기 로직을 서비스 레이어에서 제거하고 JwtController에서 CookieUtil로 처리한다.
 * refreshRotate() 반환타입을 JwtResponseDto → JwtRefreshResult로 교체하여
 * refreshToken이 서비스 바깥으로 전달되지 않도록 명시적으로 분리한다.</p>
 *
 * <p><b>JPA/MyBatis 하이브리드 (2026-04-08, 설계서 §15)</b>: auth 도메인은 김민규 영역으로
 * MyBatis Mapper를 사용한다. {@link RefreshEntity}는 {@code @Entity}로 선언되어 있지만
 * Hibernate에 DDL 정의 메타모델만 등록될 뿐이며, 데이터 R/W는 100% {@link RefreshMapper}로
 * 처리한다. JpaRepository는 사용하지 않는다 (1차 캐시 충돌 방지).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JwtService {

    private final JwtTokenProvider jwtTokenProvider;
    /** Refresh Token 화이트리스트 — MyBatis Mapper로 직접 SQL 호출 */
    private final RefreshMapper refreshMapper;
    /** 사용자 조회 — MyBatis Mapper (JpaRepository 폐기, 설계서 §15) */
    private final UserMapper userMapper;

    /**
     * Refresh Token 로테이션 결과를 담는 불변 record.
     *
     * <p>JwtController가 이 결과를 받아
     * newAccessToken은 JSON body로, newRefreshToken은 CookieUtil을 통해 쿠키로 전달한다.
     * 이 record는 JwtService 내부에서만 생성되며, HTTP 응답 body에 직접 직렬화되지 않는다.</p>
     *
     * @param newAccessToken  새로 발급된 Access Token
     * @param newRefreshToken 새로 발급된 Refresh Token (쿠키 전달용)
     */
    public record JwtRefreshResult(String newAccessToken, String newRefreshToken) {
    }

    /**
     * Refresh Token을 갱신한다 (Rotation 패턴).
     *
     * <p>기존 Refresh Token을 DB 화이트리스트에서 삭제하고,
     * 새로운 Access Token + Refresh Token 쌍을 발급한다.
     * 호출자(JwtController)가 newRefreshToken을 쿠키로, newAccessToken을 body로 각각 전달한다.</p>
     *
     * <p>C-4 수정: RuntimeException → BusinessException (REFRESH_TOKEN_NOT_FOUND, USER_NOT_FOUND)</p>
     *
     * @param refreshToken 기존 Refresh Token (쿠키에서 추출)
     * @return 새로운 토큰 쌍 (JwtRefreshResult)
     * @throws BusinessException 유효하지 않은 토큰 또는 화이트리스트에 없는 토큰
     */
    @Transactional
    public JwtRefreshResult refreshRotate(String refreshToken) {
        /* 1. 토큰 서명 및 만료 검증 */
        JwtTokenProvider.ParsedToken parsed = jwtTokenProvider.parse(refreshToken);
        if (parsed == null || !parsed.isRefresh()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        /* 2. DB 화이트리스트 확인 (탈취된 토큰 재사용 방지) */
        if (!existsRefresh(refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        /* 3. userId 추출 및 사용자 조회 (MyBatis — null 반환 시 USER_NOT_FOUND) */
        String userId = parsed.userId();
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        /* 4. 새 토큰 쌍 생성 */
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, user.getUserRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        /* 5. 기존 토큰 삭제 + 새 토큰 저장 (토큰 로테이션) */
        removeRefresh(refreshToken);
        addRefresh(userId, newRefreshToken);

        log.info("Refresh Token 갱신 완료 — userId: {}", userId);

        /* newAccessToken: body 반환용, newRefreshToken: 쿠키 설정용 (Controller에서 분리 처리) */
        return new JwtRefreshResult(newAccessToken, newRefreshToken);
    }

    /**
     * Refresh Token을 DB 화이트리스트에 추가한다.
     *
     * <p>{@code RefreshMapper.insert}는 {@code useGeneratedKeys=true keyProperty="id"}로
     * 자동 증가 PK를 RefreshEntity에 set한다. {@code created_at}은 SQL {@code NOW()}로 채워진다.</p>
     */
    @Transactional
    public void addRefresh(String userId, String refreshToken) {
        RefreshEntity entity = RefreshEntity.builder()
                .userId(userId)
                .refreshToken(refreshToken)
                .build();
        refreshMapper.insert(entity);
    }

    /**
     * 해당 Refresh Token이 화이트리스트에 존재하는지 확인한다.
     */
    public boolean existsRefresh(String refreshToken) {
        return refreshMapper.existsByRefreshToken(refreshToken);
    }

    /**
     * Refresh Token을 화이트리스트에서 삭제한다 (무효화).
     */
    @Transactional
    public void removeRefresh(String refreshToken) {
        refreshMapper.deleteByRefreshToken(refreshToken);
    }

    /**
     * 특정 사용자의 모든 Refresh Token을 삭제한다 (계정 삭제/전체 로그아웃).
     */
    @Transactional
    public void removeAllRefreshByUser(String userId) {
        refreshMapper.deleteByUserId(userId);
    }
}
