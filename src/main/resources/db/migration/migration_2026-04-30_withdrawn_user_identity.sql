-- 탈퇴 사용자 재가입 제한용 식별자 해시 이력 테이블
-- 원문 이메일/providerId는 저장하지 않고 HMAC-SHA256 해시만 보관한다.

CREATE TABLE IF NOT EXISTS withdrawn_user_identity (
    withdrawn_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    identity_type VARCHAR(20) NOT NULL,
    identity_hash VARCHAR(128) NOT NULL,
    withdrawn_user_id VARCHAR(50) NOT NULL,
    withdrawn_at DATETIME NOT NULL,
    blocked_until DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_withdrawn_identity_hash (identity_type, identity_hash),
    INDEX idx_withdrawn_blocked_until (blocked_until)
);
