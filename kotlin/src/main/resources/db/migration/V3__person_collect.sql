-- ============================================================
-- V3 — 카탈로그 확장: person 마스터 + 수집(collect) 운영 테이블
-- movie.origin_country(KR 발굴 필터), person(필모그래피 상태),
-- collect_task(작업 큐), collect_state(수집 모드 단일 행)
-- ============================================================

-- 제작국가 (TMDB production_countries[0] ISO 코드, 예: 'KR')
ALTER TABLE movie ADD COLUMN origin_country VARCHAR(8);

-- 인물 마스터 — person_id는 TMDB person id 그대로 (자연 PK)
CREATE TABLE person (
    person_id            BIGINT PRIMARY KEY,
    name                 TEXT NOT NULL,
    profile_path         TEXT,
    filmo_checked_at     TIMESTAMPTZ,                          -- 필모그래피 마지막 갱신 확인 시각
    filmo_external_count INT,                                  -- TMDB 외부 출연작 수
    filmo_stored_count   INT,                                  -- DB에 상세 저장된 출연작 수
    filmo_state          VARCHAR(16) NOT NULL DEFAULT 'NONE',  -- NONE / COLLECTING / COMPLETE
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 수집 작업 큐 — (task_type, payload_key)로 멱등
CREATE TABLE collect_task (
    task_id       BIGSERIAL PRIMARY KEY,
    task_type     VARCHAR(32) NOT NULL,                        -- GLOBAL_DISCOVER / KR_DISCOVER / MOVIE_DETAIL / PERSON_FILMO
    payload_key   TEXT NOT NULL,                               -- 예: 'kr:origin:2023-01-01..2023-12-31', 'movie:tmdb:123', 'person:456'
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',      -- PENDING / RUNNING / DONE / FAILED
    attempts      INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,                                 -- 재시도 예약 시각 (NULL이면 즉시 가능)
    last_error    TEXT,
    priority      INT NOT NULL DEFAULT 0,
    checkpoint    TEXT,                                        -- JSON (예: {"page":12}) — 마지막 완료 페이지
    locked_at     TIMESTAMPTZ,                                 -- 작업 임대(lease)
    locked_by     VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (task_type, payload_key)
);
CREATE INDEX idx_collect_task_status_priority ON collect_task(status, priority);

-- 수집 모드 — 단일 행(id=1)만 허용
CREATE TABLE collect_state (
    id              INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    mode            VARCHAR(16) NOT NULL,                      -- BOOTSTRAP / DAILY
    mode_changed_at TIMESTAMPTZ,
    bootstrapped_at TIMESTAMPTZ,                               -- 초기 enqueue 완료 표시(1회 가드)
    last_run_at     TIMESTAMPTZ,
    last_summary    TEXT,                                      -- 마지막 틱 요약 JSON
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO collect_state(id, mode) VALUES (1, 'BOOTSTRAP');
