-- ============================================================
-- cineseek PG SoT — TMDB 단일 소스 기반
-- 핵심: movie(메타+줄거리) = 진실의 원천, Qdrant는 파생 인덱스
-- ============================================================

-- 영화 메타 + 줄거리 (임베딩 소스)
CREATE TABLE movie (
    movie_id             BIGSERIAL PRIMARY KEY,
    tmdb_id              BIGINT NOT NULL UNIQUE,   -- TMDB 영화 ID
    title                TEXT NOT NULL,            -- 국문 제목 (language=ko)
    original_title       TEXT,
    overview             TEXT,                     -- 국문 줄거리 → 임베딩 입력
    release_date         DATE,
    release_year         INT,                      -- 필터용 (release_date 추출)
    runtime              INT,                      -- 분
    vote_average         NUMERIC(3,1),             -- 0.0~10.0 → Qdrant payload rating
    vote_count           INT,
    poster_path          TEXT,
    backdrop_path        TEXT,
    original_language    VARCHAR(8),
    overview_updated_at  TIMESTAMPTZ,              -- 재색인 idempotent 기준
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_movie_release_year ON movie(release_year);
CREATE INDEX idx_movie_vote_average ON movie(vote_average);
-- ponytail: overview IS NULL 영화는 파이프라인에서 SKIP (임베딩 불가)

-- 장르 마스터 (TMDB genre_id 그대로 PK)
CREATE TABLE genre (
    genre_id   BIGINT PRIMARY KEY,
    name       TEXT NOT NULL,        -- 영문 기본
    name_ko    TEXT                  -- 국문
);

CREATE TABLE movie_genre (
    movie_id   BIGINT NOT NULL REFERENCES movie(movie_id) ON DELETE CASCADE,
    genre_id   BIGINT NOT NULL REFERENCES genre(genre_id),
    PRIMARY KEY (movie_id, genre_id)
);

-- 감독 (Qdrant payload 필터 대상)
CREATE TABLE movie_director (
    movie_id   BIGINT NOT NULL REFERENCES movie(movie_id) ON DELETE CASCADE,
    person_id  BIGINT NOT NULL,      -- TMDB person id
    name       TEXT NOT NULL,
    PRIMARY KEY (movie_id, person_id)
);

-- 출연진 (Qdrant payload 필터 대상)
CREATE TABLE movie_cast (
    movie_id   BIGINT NOT NULL REFERENCES movie(movie_id) ON DELETE CASCADE,
    person_id  BIGINT NOT NULL,
    name       TEXT NOT NULL,
    character  TEXT,
    cast_order INT,                  -- credits order (주연 우선 정렬)
    PRIMARY KEY (movie_id, person_id)
);
CREATE INDEX idx_movie_cast_order ON movie_cast(movie_id, cast_order);

-- ============================================================
-- R1 자리만 (auth는 v2, v1은 스키마만)
-- ============================================================
CREATE TABLE "user" (
    user_id    BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE watch_history (
    user_id    BIGINT NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    movie_id   BIGINT NOT NULL REFERENCES movie(movie_id) ON DELETE CASCADE,
    watched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, movie_id)
);
