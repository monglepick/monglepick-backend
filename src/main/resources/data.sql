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
-- v3.2 기준 (2026-04-03): 6등급 체계, 가격 인하, AI 보너스 현실화, daily/monthly 한도 분리.
-- monthly_basic  : 2,900원/월, AI 30회/월, 300P/월
-- monthly_premium: 5,900원/월, AI 60회/월, 600P/월
-- yearly_basic   : 29,000원/년, AI 34회/월, 340P/월
-- yearly_premium : 59,000원/년, AI 67회/월, 670P/월
INSERT IGNORE INTO subscription_plans (plan_code, name, period_type, price, monthly_ai_bonus, points_per_period, description)
VALUES
    ('monthly_basic',   '월간 Basic',    'MONTHLY',  2900,  30,  300,  '월간 Basic 구독 — 매월 300 포인트 지급 (AI 추천 30회).'),
    ('monthly_premium', '월간 Premium',  'MONTHLY',  5900,  60,  600,  '월간 Premium 구독 — 매월 600 포인트 지급 (AI 추천 60회).'),
    ('yearly_basic',    '연간 Basic',    'YEARLY',  29000,  34,  340,  '연간 Basic 구독 — 연간 약 4,000 포인트 지급 (AI 추천 약400회). monthly_basic 대비 약 17% 할인.'),
    ('yearly_premium',  '연간 Premium',  'YEARLY',  59000,  67,  670,  '연간 Premium 구독 — 연간 약 8,000 포인트 지급 (AI 추천 약800회). monthly_premium 대비 약 17% 할인.');
