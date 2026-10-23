-- ============================================================
-- V6 — 인물 상세 (생몰일·출생지·소개·외부 링크)
-- 소개와 출생지는 한국어·영어를 따로 둬 언어를 골라 보여줄 수 있게 한다.
-- 한국어 소개가 TMDB에 없으면 영어를 LLM으로 번역해 채우고 biography_ko_translated로 표시한다.
-- 기존 인물은 필모 확인 시각을 만료로 돌려 다음 조회나 일일 증분에서 상세를 받아 오게 한다
-- ============================================================

ALTER TABLE person
    ADD COLUMN birthday                 DATE,
    ADD COLUMN deathday                 DATE,
    ADD COLUMN place_of_birth           TEXT,                           -- TMDB 원문 (영어, 예: 'Syracuse, New York, USA')
    ADD COLUMN place_of_birth_ko        TEXT,                           -- 번역 (예: '미국 뉴욕주 시러큐스')
    ADD COLUMN biography_ko             TEXT,
    ADD COLUMN biography_ko_translated  BOOLEAN NOT NULL DEFAULT FALSE, -- biography_ko가 영어 소개의 기계 번역인지
    ADD COLUMN biography_en             TEXT,
    ADD COLUMN imdb_id                  TEXT,
    ADD COLUMN instagram_id             TEXT,
    ADD COLUMN homepage                 TEXT,
    ADD COLUMN translation_failed_model TEXT;                           -- 번역 영구 실패한 번역 버전 (NULL이면 실패 기록 없음)

UPDATE person SET filmo_checked_at = TIMESTAMPTZ '1970-01-01 00:00:00+00' WHERE filmo_checked_at IS NOT NULL;
