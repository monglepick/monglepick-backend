-- =======================================================================
-- fav_genre 테이블 genre_name VARCHAR(50) → genre_id BIGINT 전환 마이그레이션
-- =======================================================================
-- 배경:
--   H4NN4N PR (monglepick-backend commit 5bb0153, 2026-04-22) 이
--   FavGenre.java 를 다음과 같이 변경함:
--     - @Column(name = "genre_name") String genreName
--     + @Column(name = "genre_id")   Long   genreId
--     - @UniqueConstraint(columnNames = {"user_id", "genre_name"})
--     + @UniqueConstraint(columnNames = {"user_id", "genre_id"})
--
--   JPA `ddl-auto=update` 는 **컬럼 rename + 타입 변경** 을 안전히 처리하지 못하므로
--   (신규 BIGINT 컬럼만 추가하고 기존 VARCHAR 컬럼을 남겨 둠 → 이후 INSERT 실패)
--   운영/스테이징 재시작 전에 반드시 이 SQL 을 수동 실행해야 한다.
--
-- 실행 주체 / 순서:
--   1) Backend/Recommend 트래픽 차단 (스케일 0 또는 유지보수 모드)
--   2) 이 SQL 실행 (DB 직접 접속)
--   3) Backend 재시작 (ddl-auto=update 은 이미 스키마가 맞아 no-op)
--   4) Recommend 재시작
--   5) 트래픽 재개
--
-- 실행 환경:
--   - MySQL 8.0 (VM4 10.20.0.10:3306)
--   - 권한: CREATE/ALTER/DROP/INDEX (DBA 계정)
--
-- 롤백:
--   README: 이 파일 맨 아래 "ROLLBACK" 섹션 참조. 다만 기존 genre_name '액션' 같은
--   text 데이터는 genre_id 매핑 과정에서 소실되므로 "재생성" 이 아닌 "드롭" 에 가깝다.
--
-- 영향 테이블: fav_genre  (종속 FK 없음 — genre_master 는 이미 존재한다고 가정)
-- 전제 조건:   genre_master 에 기존 genre_name 이 존재해야 매핑 성공.
--              존재하지 않는 장르명을 저장해 둔 레코드는 단계 3 에서 삭제됨.
-- =======================================================================

-- -----------------------------------------------------------------------
-- 0. 프리체크 — genre_master 가 준비되어 있는지 확인
-- -----------------------------------------------------------------------
-- 실행 전 반드시 다음 쿼리로 genre_master 가 채워져 있음을 확인:
--   SELECT COUNT(*) FROM genre_master;
-- (GenreMasterInitializer.java 가 Backend 기동 시 seed 하지만, 이 마이그레이션은
--  Backend 재시작 전에 실행하므로 수동 seed 또는 기존 값이 필요할 수 있음.)
--
-- 만약 genre_master 가 비어 있다면:
--   1) Backend 를 임시로 한 번 기동시켜 GenreMasterInitializer 가 INSERT 되도록 한 뒤
--      (이 시점은 아직 fav_genre.genre_id 컬럼이 없으므로 Backend 는 오류 발생 가능)
--   2) 혹은 genre_master seed SQL 을 직접 먼저 실행.
--
-- 권장 방법: 아래 단계를 **단일 트랜잭션** 으로 묶어 검증 실패 시 ROLLBACK.

-- -----------------------------------------------------------------------
-- 1. 안전 장치 — 스냅샷 백업
-- -----------------------------------------------------------------------
-- 운영 DB 에서는 반드시 먼저:
--   mysqldump -u user -p monglepick fav_genre > fav_genre_backup_20260422.sql

-- -----------------------------------------------------------------------
-- 2. 트랜잭션 시작
-- -----------------------------------------------------------------------
START TRANSACTION;

-- -----------------------------------------------------------------------
-- 3. genre_id BIGINT 컬럼 추가 (NULL 허용 임시)
-- -----------------------------------------------------------------------
-- Hibernate ddl-auto=update 가 실행되면 이미 생성되어 있을 수도 있으므로
-- IF NOT EXISTS 로 보호 (MySQL 8 지원).
ALTER TABLE fav_genre
  ADD COLUMN IF NOT EXISTS genre_id BIGINT NULL AFTER user_id;

-- -----------------------------------------------------------------------
-- 4. genre_name → genre_id 매핑 (genre_master JOIN)
-- -----------------------------------------------------------------------
-- 동일 user_id + genre_name 조합이 genre_master 에 존재하면 genre_id 채움.
-- genre_master.genre_name 은 UNIQUE 가정 (GenreMasterInitializer 시드 기준).
UPDATE fav_genre fg
  JOIN genre_master gm ON gm.genre_name = fg.genre_name
  SET fg.genre_id = gm.genre_id
  WHERE fg.genre_id IS NULL;

-- -----------------------------------------------------------------------
-- 5. 매핑 실패 레코드(= genre_master 에 없는 과거 장르) 삭제
-- -----------------------------------------------------------------------
-- 예: "에로", "동성애" 등 EXCLUDED_GENRE_NAMES 에 해당하는 장르
-- (favorite_genre_service.py 에서 제외하기로 한 장르) 혹은 오타로 저장된 장르.
-- 삭제 건수는 로그로 확인 후 이상 시 ROLLBACK.
DELETE FROM fav_genre WHERE genre_id IS NULL;

-- -----------------------------------------------------------------------
-- 6. genre_id NOT NULL + UNIQUE 재설정
-- -----------------------------------------------------------------------
ALTER TABLE fav_genre MODIFY COLUMN genre_id BIGINT NOT NULL;

-- 기존 UNIQUE(user_id, genre_name) 제약 이름 확인:
--   SHOW CREATE TABLE fav_genre;
-- Hibernate 가 자동 부여하는 경우 보통 'UK<hex>' 또는 'fav_genre_user_id_genre_name_key'.
-- 이 SQL 은 "anywhere in the name contains genre_name" 로 식별한다.
SET @old_unique_name = (
  SELECT INDEX_NAME
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'fav_genre'
    AND COLUMN_NAME  = 'genre_name'
    AND NON_UNIQUE   = 0
  LIMIT 1
);

SET @drop_unique_sql = IF(
  @old_unique_name IS NOT NULL,
  CONCAT('ALTER TABLE fav_genre DROP INDEX ', @old_unique_name),
  'SELECT "no old unique index to drop" AS info'
);

PREPARE drop_stmt FROM @drop_unique_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;

-- 새 UNIQUE(user_id, genre_id) 생성 — 이름 중복 시 IF NOT EXISTS 지원 안되므로 CREATE 실패해도 수동 조정.
ALTER TABLE fav_genre
  ADD CONSTRAINT uk_fav_genre_user_genre UNIQUE (user_id, genre_id);

-- -----------------------------------------------------------------------
-- 7. genre_name 컬럼 드롭
-- -----------------------------------------------------------------------
ALTER TABLE fav_genre DROP COLUMN genre_name;

-- -----------------------------------------------------------------------
-- 8. 검증
-- -----------------------------------------------------------------------
-- 아래 값이 의도와 일치하는지 눈으로 확인 후 COMMIT:
--   SELECT COUNT(*) FROM fav_genre;                           -- 총 레코드 수
--   SELECT COUNT(*) FROM fav_genre WHERE genre_id IS NULL;    -- 0 이어야 함
--   SHOW CREATE TABLE fav_genre;                              -- 구조 확인

-- -----------------------------------------------------------------------
-- 9. 확정
-- -----------------------------------------------------------------------
COMMIT;

-- =======================================================================
-- ROLLBACK 참고
-- =======================================================================
-- 단계 9 직전이면 ROLLBACK; 으로 원복.
-- 단계 9 이후(COMMIT 된 상태) 에는 mysqldump 백업으로 복구해야 한다:
--   DROP TABLE fav_genre;
--   mysql -u user -p monglepick < fav_genre_backup_20260422.sql
-- =======================================================================
