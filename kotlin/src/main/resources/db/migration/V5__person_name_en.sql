-- ============================================================
-- V5 — 인물 영어 이름 (인물 페이지 URL slug의 원천)
-- 기존 인물은 필모 확인 시각을 만료로 돌려, 다음 조회나 일일 증분의
-- 만료 필모 재수집에서 영어 이름을 받아 오게 한다
-- ============================================================

ALTER TABLE person ADD COLUMN name_en TEXT;  -- TMDB 영어 번역 이름 (번역이 없으면 NULL)

UPDATE person SET filmo_checked_at = TIMESTAMPTZ '1970-01-01 00:00:00+00' WHERE filmo_checked_at IS NOT NULL;
