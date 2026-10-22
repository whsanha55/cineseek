# 개발 가이드

처음 실행하는 방법은 [README의 빠른 시작](../README.md#빠른-시작)을 본다. 이 문서는 그 다음 단계를 다룬다.

## 저장소 구조

```
cineseek/
├── kotlin/             api — 검색 API, 수집·색인 파이프라인, 평가
├── python/             embed — 임베딩 서비스 (POST /embed)
├── ui/                 프론트엔드 SPA
├── docs/               개발·운영 문서, 코드 컨벤션
├── openapi.json        API 스펙 스냅샷 (프론트 타입 생성의 원본)
├── compose.yml         공통 서비스 정의
├── compose.local.yml   로컬용 덮어쓰기 — 전용 postgres, 포트 노출, local profile
├── compose.prod.yml    서버용 덮어쓰기 — 공용 postgres, 수집 스케줄러 켬, prod profile
└── deploy.sh           서버 배포 스크립트
```

## 로컬 포트

| 서비스 | 포트 | 확인 |
|---|---|---|
| `ui` | 5173 | http://localhost:5173 |
| `api` | 8080 | `GET /actuator/health` |
| `embed` | 8001 | `GET /health` |
| `qdrant` | 6333(REST), 6334(gRPC) | http://localhost:6333/dashboard |
| `postgres` | 5432 | `jjong` / `jjong`, DB `cineseek` |

## 환경변수

비밀값은 `.env` 파일에 둔다. `.env`는 git에 올라가지 않는다.

| 파일 | 변수 | 설명 |
|---|---|---|
| `kotlin/.env` | `TMDB_ACCESS_TOKEN` | 필수. TMDB v4 Read Access Token |
| `kotlin/.env` | `LLM_API_KEY` | 선택. 분위기 태깅용 OpenAI 호환 API 키. 비어 있으면 태깅을 건너뛴다 |
| `kotlin/.env` | `LLM_BASE_URL`, `LLM_MODEL` | 선택. 기본값은 `https://api.openai.com/v1`, `gpt-4o-mini` |
| `python/.env` | `EMBED_DEVICE`, `EMBED_MODEL` | compose 없이 embed를 직접 띄울 때만 쓴다 |

- compose는 `kotlin/.env`를 `env_file`로 읽고, IDE·`bootRun`은 `spring.config.import`로 읽는다
- 접속 주소(`PG_*`, `QDRANT_*`, `EMBED_URL`)는 `application.yml` 기본값이 localhost 기준이고, compose에서는 서비스 이름으로 덮어쓴다
- compose는 PG 계정을 `PG_USER`·`PG_PASSWORD`로 보간한다. 루트 `.env`에 이 값을 따로 두면 로컬 postgres 컨테이너 계정(`jjong`)과 어긋나니 주의한다

## IDE에서 api 실행

인프라만 compose로 띄우고 api는 IDE(작업 디렉터리 `kotlin/`)나 Gradle로 실행한다.

```bash
docker compose -f compose.yml -f compose.local.yml up postgres qdrant embed
cd kotlin && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## 테스트

```bash
cd kotlin && ./gradlew test   # Testcontainers 사용 — Docker만 떠 있으면 된다
cd ui && pnpm test            # Vitest
```

## 프론트엔드

```bash
cd ui
pnpm install
pnpm dev        # Vite 개발 서버 — /cineseek 요청은 localhost:8080으로 프록시
pnpm build      # 타입 검사 + 프로덕션 빌드
pnpm lint
```

- 검색어와 필터 상태는 모두 URL 쿼리에 담는다
- 찜·봤어요·관심없음은 로그인이 없어 localStorage `cineseek.marks`에 종류별 영화 id 배열로만 둔다. 찜이 3개 이상이면 홈에 "내 취향 추천"이 나온다. localStorage를 쓸 수 없는 브라우저(Safari 사생활 보호 모드 등)에서는 이 기능을 숨긴다
- 디자인 카탈로그는 개발 서버의 http://localhost:5173/_ds 에 있다 (운영 빌드에서는 빠진다)
- API 타입은 루트 `openapi.json`에서 생성한다. API를 바꾸면 `http://localhost:8080/v3/api-docs` 결과로 `openapi.json`을 갱신하고 `pnpm gen:api`를 실행한다

## 검색 API

`GET /cineseek/search`

| 파라미터 | 설명 |
|---|---|
| `q` | 필수. 검색어 |
| `genreId` | 장르. 여러 번 주면 OR |
| `excludeGenreId` | 제외 장르. 여러 번 주면 하나라도 겹치는 영화를 뺀다 |
| `yearMin`, `yearMax` | 개봉 연도 범위 |
| `ratingMin` | 최소 평점 |
| `directorId`, `castId` | 감독·배우 TMDB id |
| `limit`, `offset` | 페이지. `limit`은 1~50 |

- 응답은 `items`(영화 카드 + `score`)와 `page{limit, offset, hasNext, total}`이다. `total`은 항상 `null`이다
- `items[].matchedTerms`는 검색 근거다. 쿼리 sparse와 결과의 저장 sparse에 함께 있는 토큰을 쿼리 원문 단어로 되돌려 기여도 순 최대 3개를 준다. 비어 있으면 의미(dense)로만 찾은 결과다
- 잘못된 값은 400 ProblemDetail(`code`, `requestId`, `errors`)로 응답한다
- 전체 엔드포인트는 Swagger(`/swagger-ui.html`)에서 본다

## 취향 추천 API

`GET /cineseek/recommendations`

| 파라미터 | 설명 |
|---|---|
| `likedId` | 필수. 찜한 영화 id. 반복 파라미터, 1~50개 |
| `dislikedId` | 관심없음 영화 id. 최대 50개 |
| `watchedId` | 봤어요 영화 id. 최대 50개. 결과에서만 뺀다 |
| `limit` | 1~50. 기본 20 |

- Qdrant recommend API에 찜을 positive, 관심없음을 negative 포인트 id(= `movieId`)로 넘기고 `dense` 벡터로 찾는다
- 찜·관심없음·봤어요 영화는 결과에 나오지 않는다. 아직 색인되지 않은 id는 무시하고, 남는 찜이 없으면 빈 목록이다
- 응답 형태는 유사 영화와 같다 (`items[].score`는 코사인 유사도, `page.hasNext`는 항상 `false`)

## 배치 작업

api 이미지에 `--cineseek.job=<이름>`을 주면 해당 작업만 돌고 종료한다.

```bash
docker compose -f compose.yml -f compose.local.yml run --rm api --cineseek.job=<이름>
```

| 이름 | 하는 일 |
|---|---|
| `reindex` | TMDB 수집 → PG 저장 → 임베딩 → Qdrant 색인. 분위기 태깅은 하지 않는다 (상주 api의 수집 스케줄러가 틱마다 태깅하고 색인에 반영한다) |
| `eval` | 라벨셋으로 검색 품질(nDCG@10, MRR)을 재고 기준값과 비교한다. 떨어지면 종료 코드 1 |
| `collect-report` | 수집 현황 보고 ([운영 가이드](operations.md#수집-보고)) |

## 검색 품질 평가

- 라벨: `kotlin/src/main/resources/eval/queries.json` (쿼리별 정답 tmdbId와 등급 1~2)
- 기준값: 같은 폴더 `baseline.json`. 서버에서 `eval`을 돌린 뒤 로그 마지막에 찍힌 기준값 JSON으로 덮어써 갱신한다
- 라벨을 고친 쿼리는 해시가 바뀌어 비교에서 자동으로 빠진다

## 코드 규칙

- 공통 컨벤션: [`docs/convention/`](convention/) — 프로젝트 예외는 [`LOCAL.md`](convention/LOCAL.md)
- Kotlin: [`kotlin/CLAUDE.md`](../kotlin/CLAUDE.md)
