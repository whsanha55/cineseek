# cineseek

"이런 분위기 영화" 같은 자연어로 영화를 찾는 시맨틱 검색 서비스.
TMDB에서 영화를 수집해 PostgreSQL(원본)에 적재하고, bge-m3 dense+sparse 임베딩을 Qdrant에 색인해 하이브리드(RRF) 검색한다.

## 구성

```
cineseek/
├── kotlin/             API + 수집/색인 파이프라인 + 평가 (Spring Boot 4, Java 25)
├── python/             임베딩 서비스 — POST /embed 하나만 (FastAPI + FlagEmbedding, bge-m3)
├── docs/               기획·계획 문서
├── compose.yml         공통 서비스 정의 (qdrant, embed, api)
└── compose.local.yml   로컬 덮어쓰기 (전용 postgres, 포트 노출, local profile)
```

| 서비스 | 역할 | 포트 |
|---|---|---|
| `api` (kotlin/) | `/api/search`, TMDB 수집 → PG → 임베딩 → Qdrant | 8080 |
| `embed` (python/) | 텍스트 → dense(1024) + sparse | 8001 |
| `qdrant` | 벡터 검색 | 6333(REST), 6334(gRPC) |
| `postgres` (로컬만) | 영화 원본/메타 (SoT) | 5432 |

## 빠른 시작

```bash
# 1. TMDB 토큰 설정 (https://www.themoviedb.org/settings/api 의 v4 Read Access Token)
cp kotlin/.env.example kotlin/.env

# 2. 전체 기동 — 첫 기동은 bge-m3 모델(~2GB) 다운로드로 수 분 걸린다
docker compose -f compose.yml -f compose.local.yml up --build

# 3. 색인 (로컬 profile은 TMDB 2페이지 ≈ 40건)
docker compose -f compose.yml -f compose.local.yml run --rm api --cineseek.job=reindex

# 4. 검색
curl 'http://localhost:8080/api/search?q=감옥에서 탈출하는 이야기&limit=5'
```

- 헬스체크: `GET :8080/actuator/health`, `GET :8001/health`
- 검색 파라미터: `q`(필수), `genre`, `yearMin`, `limit`(1~50, 기본 5)
- 평가(고정 쿼리 10개 top-5): `... run --rm api --cineseek.job=eval` — 기준 결과는 `docs/baseline-eval.txt`

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

## 문서

- [`docs/CONCEPT.md`](docs/CONCEPT.md) — 서비스 개념·아키텍처
- [`docs/PLAN.md`](docs/PLAN.md) — Python → Kotlin 전환 계획과 진행 상황
- [`docs/UI-PLAN.md`](docs/UI-PLAN.md) — UI 기능 기획 + 디자인 시스템 계획
- [`kotlin/CLAUDE.md`](kotlin/CLAUDE.md) — Kotlin 코드 컨벤션
- [`python/README.md`](python/README.md) — 임베딩 서비스 계약(`/embed`)
