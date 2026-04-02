-- ============================================================
-- 몽글픽 시드 데이터 (Spring Boot 기동 시 자동 실행)
-- ============================================================
-- ddl-auto=update로 테이블 생성 후, 초기 시드 데이터를 적재한다.
-- INSERT IGNORE로 중복 실행해도 안전하다.
-- ============================================================

-- ── 포인트 교환 아이템 ──
INSERT IGNORE INTO point_items (item_name, item_description, item_price, item_category)
VALUES
    ('AI 추천 1회',     'AI 영화 추천 1회 이용',        100, 'ai_feature'),
    ('AI 추천 5회 팩',  'AI 영화 추천 5회 이용 (10% 할인)', 450, 'ai_feature'),
    ('프로필 테마',     '프로필 커스텀 테마 적용',       200, 'profile'),
    ('칭호 변경',       '커뮤니티 닉네임 칭호 변경',     150, 'profile'),
    ('도장깨기 힌트',   '퀴즈 힌트 1회 사용',            50, 'roadmap');

-- ── 구독 상품 ──
-- v2.4 기준: 10P = AI 추천 1회.
-- 구 값(v1.0 크레딧 체계 잔재)은 아래로 교체:
--   monthly_basic  3,900원 → 4,900원 / 3,000P → 300P
--   monthly_premium 7,900원 → 9,900원 / 8,000P → 1,000P
--   yearly_basic   39,000원 → 49,000원 / 40,000P → 4,000P
--   yearly_premium 79,000원 → 99,000원 / 100,000P → 12,000P
-- INSERT IGNORE로 최초 기동 시에만 삽입된다.
-- 이미 삽입된 레코드 수정이 필요한 경우 아래 UPDATE 블록을 수동 실행하거나
-- 마이그레이션 스크립트를 별도 적용한다.
INSERT IGNORE INTO subscription_plans (plan_code, name, period_type, price, points_per_period, description)
VALUES
    ('monthly_basic',   '월간 기본',    'MONTHLY',  4900,   300,  '매월 300 포인트 지급 (AI 추천 30회 + 등급 혜택)'),
    ('monthly_premium', '월간 프리미엄', 'MONTHLY',  9900,  1000,  '매월 1,000 포인트 지급 (AI 추천 100회 + 등급 혜택)'),
    ('yearly_basic',    '연간 기본',    'YEARLY',  49000,  4000,  '연간 4,000 포인트 지급 (AI 추천 400회 + 등급 혜택, 월간 대비 16% 절약)'),
    ('yearly_premium',  '연간 프리미엄', 'YEARLY',  99000, 12000,  '연간 12,000 포인트 지급 (AI 추천 1,200회 + 등급 혜택, 월간 대비 17% 절약)');

-- ── 기존 레코드 업데이트 (이미 INSERT IGNORE로 삽입된 경우 수동 실행) ──
-- 운영 DB에 구 v1.0 값이 이미 들어있다면 아래 UPDATE를 한 번 실행한다.
-- UPDATE subscription_plans SET price =  4900, points_per_period =   300, description = '매월 300 포인트 지급 (AI 추천 30회 + 등급 혜택)'                                WHERE plan_code = 'monthly_basic';
-- UPDATE subscription_plans SET price =  9900, points_per_period =  1000, description = '매월 1,000 포인트 지급 (AI 추천 100회 + 등급 혜택)'                              WHERE plan_code = 'monthly_premium';
-- UPDATE subscription_plans SET price = 49000, points_per_period =  4000, description = '연간 4,000 포인트 지급 (AI 추천 400회 + 등급 혜택, 월간 대비 16% 절약)'          WHERE plan_code = 'yearly_basic';
-- UPDATE subscription_plans SET price = 99000, points_per_period = 12000, description = '연간 12,000 포인트 지급 (AI 추천 1,200회 + 등급 혜택, 월간 대비 17% 절약)'      WHERE plan_code = 'yearly_premium';
