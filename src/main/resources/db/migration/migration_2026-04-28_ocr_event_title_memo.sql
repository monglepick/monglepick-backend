-- ============================================================
-- Migration: ocr_event 테이블 title / memo 컬럼 추가
-- 대상: ddl-auto=update 미적용 환경(컬럼 미생성) 수동 보정용
-- 실행: 백엔드 재시작 전 DB에서 직접 실행 (이미 컬럼 있으면 IF NOT EXISTS 로 skip)
-- ============================================================

ALTER TABLE ocr_event
    ADD COLUMN IF NOT EXISTS title VARCHAR(200) NULL AFTER movie_id,
    ADD COLUMN IF NOT EXISTS memo  TEXT         NULL AFTER title;