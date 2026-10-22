# cineseek

"감옥에서 탈출하는 이야기", "비 오는 날 보기 좋은 잔잔한 영화"처럼 자연어로 영화를 찾는 시맨틱 검색 서비스.

## 동작 방식

```
TMDB ──수집──▶ PostgreSQL ──임베딩(bge-m3)──▶ Qdrant
  (원본·메타, SoT)        dense + sparse

검색어 ──임베딩──▶ Qdrant 하이브리드 검색(RRF) ──▶ PG에서 카드 조립 ──▶ UI
```

- TMDB에서 영화를 수집해 PostgreSQL에 저장한다. 한국 영화와 배우 필모그래피는 상시 수집 스케줄러가 채운다
- 줄거리에 LLM으로 분위기 태그를 붙여 임베딩 입력에 넣는다 (색인할 때만 사용하고, 검색할 때는 LLM을 부르지 않는다)
- bge-m3의 dense(의미)와 sparse(키워드) 벡터를 RRF로 합치고, 투표 수가 많은 영화를 조금 올려 순위를 매긴다. 투표 수 10 미만 무명작은 투표 수 조건을 따로 주지 않으면 빠진다
- 장르·연도·평점·감독·배우 필터를 함께 걸 수 있다

## 구성

| 디렉터리 | 서비스 | 역할 | 스택 |
|---|---|---|---|
| `kotlin/` | `api` | 검색 API, 수집·색인 파이프라인, 평가 | Spring Boot 4, Java 25 |
| `python/` | `embed` | 텍스트 → dense(1024) + sparse 임베딩 | FastAPI, FlagEmbedding |
| `ui/` | `ui` | 검색 화면. nginx가 정적 파일을 서빙하고 `/cineseek`를 api로 프록시 | React, Vite |
| — | `qdrant` | 벡터 검색 | Qdrant |
| — | `postgres` | 영화 원본 데이터 (로컬 compose만. 서버는 공용 PG 사용) | PostgreSQL 17 |

## 빠른 시작

Docker와 [TMDB API](https://www.themoviedb.org/settings/api) v4 Read Access Token이 필요하다.

```bash
# 1. TMDB 토큰 입력
cp kotlin/.env.example kotlin/.env

# 2. 전체 기동 — 첫 기동은 bge-m3 모델(~2GB)을 받느라 몇 분 걸린다
docker compose -f compose.yml -f compose.local.yml up --build

# 3. 색인 — 로컬은 약 40편. -e TMDB_PAGES=25로 늘리면 약 500편
docker compose -f compose.yml -f compose.local.yml run --rm api --cineseek.job=reindex
```

브라우저에서 http://localhost:5173 을 열거나 API를 직접 호출한다.

```bash
curl 'http://localhost:8080/cineseek/search?q=감옥에서 탈출하는 이야기&limit=5'
```

API 문서는 http://localhost:8080/swagger-ui.html 에 있다.

## 설계 원칙

- PostgreSQL이 원본(SoT)이다. Qdrant는 파생 인덱스라 언제든 PG에서 다시 만들 수 있다 (재색인은 멱등)
- Qdrant 포인트 id는 `movie_id`(PG PK)다. payload에는 필터용 값만 두고, 화면에 보일 필드는 PG에서 조립한다
- 검색 경로에는 외부 API 의존이 없다. 임베딩은 로컬 bge-m3로 한다

## 문서

- [개발 가이드](docs/development.md) — 환경변수, IDE 실행, 테스트, 프론트엔드, 검색 API, 평가
- [E2E 실행](docs/development.md#e2e) — `scripts/e2e/server.sh start` 후 `scripts/e2e/run-all.sh`
- [운영 가이드](docs/operations.md) — 상시 수집 스케줄러, 수집 보고, 서버 배포
- [임베딩 서비스 계약](python/README.md) — `POST /embed`
- [Kotlin 코드 규칙](kotlin/CLAUDE.md)
- 할 일과 아이디어는 [GitHub Issues](https://github.com/whsanha55/cineseek/issues)에서 관리한다 (우선순위 `P0`~`P3`, 보류 `backlog`)

<!-- convention:start -->
## Convention

코드 컨벤션은 [`docs/convention/`](docs/convention/)에 있고, 이 프로젝트만의 예외는 [`LOCAL.md`](docs/convention/LOCAL.md)에 적는다.
<!-- convention:end -->
