package com.monglepick.monglepickbackend.domain.user.service;

import com.monglepick.monglepickbackend.domain.user.entity.User;
import com.monglepick.monglepickbackend.domain.user.entity.WithdrawnUserIdentity;
import com.monglepick.monglepickbackend.domain.user.mapper.WithdrawnUserIdentityMapper;
import com.monglepick.monglepickbackend.global.exception.BusinessException;
import com.monglepick.monglepickbackend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 탈퇴 사용자 재가입 제한용 HMAC 식별자 서비스.
 */
@Service
@RequiredArgsConstructor
public class WithdrawnUserIdentityService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TYPE_EMAIL = "EMAIL";
    private static final String TYPE_PROVIDER = "PROVIDER";

    private final WithdrawnUserIdentityMapper mapper;

    @Value("${app.withdrawal.hmac-secret:monglepick-dev-withdrawal-hmac-secret-key}")
    private String hmacSecret;

    @Value("${app.withdrawal.rejoin-block-days:30}")
    private long rejoinBlockDays;

    public void recordWithdrawal(User user, LocalDateTime withdrawnAt) {
        LocalDateTime blockedUntil = withdrawnAt.plusDays(rejoinBlockDays);

        if (StringUtils.hasText(user.getEmail())) {
            mapper.insert(WithdrawnUserIdentity.builder()
                    .identityType(TYPE_EMAIL)
                    .identityHash(hashEmail(user.getEmail()))
                    .withdrawnUserId(user.getUserId())
                    .withdrawnAt(withdrawnAt)
                    .blockedUntil(blockedUntil)
                    .build());
        }

        if (user.getProvider() != null && StringUtils.hasText(user.getProviderId())) {
            mapper.insert(WithdrawnUserIdentity.builder()
                    .identityType(TYPE_PROVIDER)
                    .identityHash(hashProvider(user.getProvider().name(), user.getProviderId()))
                    .withdrawnUserId(user.getUserId())
                    .withdrawnAt(withdrawnAt)
                    .blockedUntil(blockedUntil)
                    .build());
        }
    }

    public void assertEmailNotBlocked(String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        assertNotBlocked(TYPE_EMAIL, hashEmail(email));
    }

    public void assertProviderNotBlocked(String provider, String providerId) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(providerId)) {
            return;
        }
        assertNotBlocked(TYPE_PROVIDER, hashProvider(provider, providerId));
    }

    public void clearWithdrawalBlocks(String userId) {
        if (StringUtils.hasText(userId)) {
            mapper.deleteByWithdrawnUserId(userId);
        }
    }

    private void assertNotBlocked(String identityType, String identityHash) {
        if (mapper.existsActiveBlock(identityType, identityHash, LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.WITHDRAWN_REJOIN_BLOCKED);
        }
    }

    private String hashEmail(String email) {
        return hmac(normalizeEmail(email));
    }

    private String hashProvider(String provider, String providerId) {
        return hmac(provider.trim().toUpperCase() + ":" + providerId.trim());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("탈퇴 식별자 HMAC 생성 실패", e);
        }
    }
}
