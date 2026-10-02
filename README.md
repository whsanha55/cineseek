# cineseek

"이런 분위기 영화" 같은 자연어로 영화를 찾는 시맨틱 검색 서비스.
TMDB에서 영화를 수집해 PostgreSQL(원본)에 적재하고, bge-m3 dense+sparse 임베딩을 Qdrant에 색인해 하이브리드(RRF) 검색한다.

## 구성

```
cineseek/
├── kotlin/             API + 수집/색인 파이프라인 + 평가 (Spring Boot 4, Java 25)
├── python/             임베딩 서비스 — POST /embed 하나만 (FastAPI + FlagEmbedding, bge-m3)
├── ui/                 프론트엔드 — React + Vite SPA, nginx 정적 서빙 + /cineseek 프록시
├── docs/               eval 기준 결과, 코드 컨벤션
├── compose.yml         공통 서비스 정의 (qdrant, embed, api, ui)
└── compose.local.yml   로컬 덮어쓰기 (전용 postgres, 포트 노출, local profile)
```

| 서비스 | 역할 | 포트 |
|---|---|---|
| `ui` (ui/) | 정적 SPA + `/cineseek` 프록시(같은 출처, CORS 불필요) | 5173 |
| `api` (kotlin/) | `/cineseek/search` 등 조회 API + TMDB 수집 → PG → 임베딩 → Qdrant | 8080 |
| `embed` (python/) | 텍스트 → dense(1024) + sparse | 8001 |
| `qdrant` | 벡터 검색 | 6333(REST), 6334(gRPC) |
| `postgres` (로컬만) | 영화 원본/메타 (SoT) | 5432 |

## 빠른 시작

```bash
# 1. TMDB 토큰 설정 (https://www.themoviedb.org/settings/api 의 v4 Read Access Token)
cp kotlin/.env.example kotlin/.env

# 2. 전체 기동 — 첫 기동은 bge-m3 모델(~2GB) 다운로드로 수 분 걸린다
#    루트 .env의 PG_USER/PG_PASSWORD가 compose 보간을 덮어쓰면 컨테이너 PG 기본값(jjong)을 함께 넘긴다
docker compose -f compose.yml -f compose.local.yml up --build

# 3. 색인 (로컬 profile은 TMDB 2페이지 ≈ 40건. 늘리려면 -e TMDB_PAGES=25 → 약 500건)
docker compose -f compose.yml -f compose.local.yml run --rm api --cineseek.job=reindex

# 4. 브라우저에서 http://localhost:5173 — 검색 API는 curl로도
curl 'http://localhost:8080/cineseek/search?q=감옥에서 탈출하는 이야기&limit=5'
```

- 헬스체크: `GET :8080/actuator/health`, `GET :8001/health`
- API 문서: `:8080/swagger-ui.html` (OpenAPI JSON `:8080/v3/api-docs`). 프론트 타입은 `cd ui && pnpm gen:api`로 재생성 — 원본은 루트 `openapi.json` 스냅샷
- 검색 파라미터: `q`(필수), `genreId`(반복·OR), `yearMin`, `yearMax`, `ratingMin`, `directorId`, `castId`, `limit`(1~50), `offset`. 응답은 `items`(카드+`score`) + `page{limit,offset,hasNext,total:null}`. 잘못된 값은 400 ProblemDetail(`code`, `requestId`, `errors`)로 응답
- 평가(고정 쿼리 10개 top-5): `... run --rm api --cineseek.job=eval` — 기준 결과는 `docs/baseline-eval.txt`

## 프론트엔드 (ui/)

```bash
cd ui
pnpm install
pnpm dev        # Vite dev 서버 — /cineseek는 localhost:8080으로 프록시
pnpm test       # Vitest (URL 상태·관련도·TMDB URL·컴포넌트)
pnpm build      # tsc + 프로덕션 빌드 (/_ds는 운영 번들에서 제외)
```

- 확정 검색·필터 상태는 전부 URL 쿼리가 원천이다
- 개발용 디자인 카탈로그: `http://localhost:5173/_ds`

## 환경변수

| 위치 | 용도 |
|---|---|
| `kotlin/.env` | api 비밀값(`TMDB_ACCESS_TOKEN`). compose는 `env_file`로, IDE/bootRun은 `spring.config.import`로 읽는다 |
| `python/.env` | embed를 compose 없이 직접 띄울 때(`EMBED_DEVICE`, `EMBED_MODEL`) |

주소(`PG_*`, `QDRANT_*`, `EMBED_URL`)는 `application.yml` 기본값이 로컬 호스트 기준이고, compose에서는 서비스명으로 덮어쓴다.

## IDE에서 api 직접 실행

인프라만 compose로 띄우고 api는 IDE(작업 디렉터리 `kotlin/`)에서 돌린다.

```bash
docker compose -f compose.yml -f compose.local.yml up postgres qdrant embed
cd kotlin && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

테스트는 Testcontainers를 쓰므로 Docker만 떠 있으면 된다: `cd kotlin && ./gradlew test`

## 설계 불변식

- PostgreSQL이 진실의 원천(SoT)이다. Qdrant는 파생 인덱스라 언제든 PG에서 다시 만들 수 있어야 한다 (재색인 idempotent)
- Qdrant 포인트 id = `movie_id`(PG PK). payload에는 필터용 값만 두고, 표시 필드는 PG에서 조립한다
- 검색 경로에는 외부 API 의존이 없다 — 임베딩은 로컬 bge-m3

## 문서

- 기획·할 일은 [GitHub Issues](https://github.com/whsanha55/cineseek/issues)에서 관리한다 (우선순위 라벨 `P0`~`P3`, 보류 아이디어는 `backlog`)
- [`kotlin/CLAUDE.md`](kotlin/CLAUDE.md) — Kotlin 코드 컨벤션
- [`python/README.md`](python/README.md) — 임베딩 서비스 계약(`/embed`)

<!-- convention:start -->
## Convention

이 프로젝트는 개인 컨벤션 [whsanha55/claude-code-skills/conventions](https://github.com/whsanha55/claude-code-skills/tree/main/conventions)를 따른다. 문서는 `docs/convention/`에 있고, 프로젝트 예외는 `docs/convention/LOCAL.md`에 적는다.
<!-- convention:end -->
