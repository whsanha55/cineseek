-- ============================================================
-- V2 — LLM 분위기(mood) 태그 컬럼
-- TMDB 밖에서 생성되는 값이라 재수집(updateFrom) 갱신 대상이 아니다
-- ============================================================

ALTER TABLE movie ADD COLUMN mood_tags      TEXT;         -- LLM 생성 분위기 태그 (쉼표 구분, 한국어)
ALTER TABLE movie ADD COLUMN mood_desc      TEXT;         -- 분위기 한줄 설명
ALTER TABLE movie ADD COLUMN mood_model     TEXT;         -- 태그 생성 모델·프롬프트 버전 (예: gpt-4o-mini/abc12345)
ALTER TABLE movie ADD COLUMN mood_tagged_at TIMESTAMPTZ;  -- 태그 시각
