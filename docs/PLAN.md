# cineseek 모노레포 전환 계획서

> 작성: 2026-09-23 · 갱신: 2026-09-23 · 상태: **Phase 5 완료 — 전환 본체 종료. Phase 6(서버 배포) 보류 중**
> 결정 사항
> - 접근법 **C**: 임베딩만 Python 컨테이너, 나머지 전부 Kotlin(Spring Boot)
> - **기존 레포 `whsanha55/cineseek` 하나**에 폴더로 나눈다 (python / kotlin / 나중에 ui)
> - Kotlin: **Java 25 + Spring Boot 4.1.1 + JPA**
> - 로컬 개발은 **로컬에서 Docker 이미지를 빌드해 compose로 실행**. 서버 배포는 **profile만 바꿔서** 같은 구조로 올린다
> - **(2026-09-23 변경)** 로컬 PG는 공유 `reins-postgres` 대신 **compose.local.yml의 전용 postgres 서비스**를 쓴다 (계정/비번 `jjong`). compose 프로젝트명·컨테이너·볼륨에 `gonamu` 접두어를 붙여 중복 실행·이름 충돌을 방지한다

---

## 1. 목표

- Python이 낯설어서 **직접 읽고 고칠 코드는 전부 Kotlin**으로 옮긴다.
- 임베딩(bge-m3 dense+sparse)만 **Python 컨테이너에 격리**한다. 한 번 빌드하면 거의 손대지 않는 부품으로 둔다.
- **검색 품질은 현재와 동일하게** 유지한다. 같은 모델과 같은 Qdrant 구조를 쓰므로 결과가 같아야 한다.
- 로컬과 서버가 **같은 이미지, 같은 compose 구조**로 돌아가고, 차이는 profile과 환경변수뿐이게 한다.

### 성공 기준

| # | 기준 | 확인 방법 |
|---|---|---|
| S1 | Kotlin `/api/search` 결과가 현재 Python `/search`와 같다 | 고정 쿼리 10개의 top-5 제목과 순서 비교 |
| S2 | Kotlin 파이프라인으로 PG와 Qdrant를 처음부터 다시 만들 수 있다 | 재색인 후 PG `movie` 건수 = Qdrant 포인트 수, S1 재통과 |
| S3 | 로컬에서 `docker compose up` 한 번으로 전체가 뜬다 (local profile) | api `/actuator/health`, embed `/health` 모두 200 |
| S4 | 서버에서 같은 이미지로 profile만 바꿔 뜬다 (prod profile) | S3과 같은 확인을 서버에서 |
| S5 | p95 검색 지연 300ms 미만 (CONCEPT §9 기준 유지) | 서버에서 쿼리 10개 × N회 측정 |

---

## 2. 레포 구조

기존 GitHub 레포 `whsanha55/cineseek`를 그대로 쓰고, 루트에 있던 Python 파일을 `python/` 폴더로 옮긴다.

```
cineseek/                         ← git 루트 (whsanha55/cineseek)
├── python/                       임베딩 서비스 (FastAPI + FlagEmbedding)
│   ├── embed.py
│   ├── app.py                    → /embed, /health 서버로 교체
│   ├── pyproject.toml, uv.lock
│   └── Dockerfile
├── kotlin/                       API + 수집/색인 파이프라인 (Spring Boot)
│   ├── src/main/kotlin/...
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-local.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/V1__init.sql   ← 현재 db/schema.sql
│   ├── build.gradle.kts
│   └── Dockerfile
├── ui/                           (나중에)
├── docs/
│   ├── PLAN.md                   ← 이 문서
│   ├── CONCEPT.md
│   └── CEO-REVIEW.md
├── compose.yml                   공통 서비스 정의 (qdrant, embed, api)
├── compose.local.yml             로컬 덮어쓰기 (local profile)
├── compose.prod.yml              서버 덮어쓰기 (prod profile)
├── .env.example                  공통 환경변수 예시
└── .gitignore
```

| 폴더 | 역할 | 스택 | 컨테이너 포트 |
|---|---|---|---|
| `python/` | `POST /embed` 하나만 제공. 텍스트 → dense(1024) + sparse | Python 3.12, FastAPI, FlagEmbedding | 8001 |
| `kotlin/` | 검색 API, TMDB 수집, PG 적재, Qdrant 색인, 평가 | Java 25, Spring Boot 4.x, Kotlin, JPA | 8080 |
| `ui/` | 검색 화면 | 미정 | 미정 |

### 현재 파일의 처리

| 현재 파일 | 전환 후 |
|---|---|
| `embed.py` | `python/`으로 이동. 임베딩 서버 내부에서 사용 |
| `app.py` | `python/`으로 이동 후 `/search` → `/embed` 서버로 교체 |
| `search.py` / `pipeline.py` / `eval.py` | `python/`으로 이동. Kotlin 이식 후 S1·S2 통과하면 **삭제** |
| `db/schema.sql` | `kotlin/.../db/migration/V1__init.sql`로 이동 |
| `docker-compose.yml` | 루트 `compose.yml` 계열로 재작성 |
| `CONCEPT.md` / `CEO-REVIEW.md` | `docs/`로 이동. 전환 결정을 추가로 기록 |

파일 이동은 `git mv`로 해서 이력(`git log --follow`)을 보존한다.

---

## 3. 목표 아키텍처

```
   ui (나중)
     │ HTTP (JSON)
     ▼
┌──────────────────────┐   POST /embed   ┌────────────────────┐
│ api  (kotlin) :8080  │ ──────────────▶ │ embed (python)     │
│  /api/search         │ ◀────────────── │ :8001  bge-m3      │
│  수집·색인 Job        │  dense + sparse │ (FlagEmbedding)    │
└──────┬─────────┬─────┘                 └────────────────────┘
       │ JPA     │ gRPC :6334
       ▼         ▼
 ┌──────────┐ ┌──────────┐
 │PostgreSQL│ │  Qdrant  │
 │  (SoT)   │ │(파생 인덱스)│
 └──────────┘ └──────────┘
```

핵심 불변식은 그대로 유지한다.
- PG = 진실의 원천. Qdrant는 언제든 PG에서 다시 만들 수 있어야 한다.
- 필터 대상(genre/year/rating/director/cast)은 payload에 사본으로 저장한다.
- `movie_id`(PG PK)를 Qdrant 포인트 id로 쓴다.

---

## 4. 폴더 간 계약: 임베딩 API

Kotlin과 Python이 맞물리는 **유일한 지점**이다. 이 계약만 지키면 양쪽을 독립적으로 바꿀 수 있다.

```http
POST /embed
Content-Type: application/json

{ "texts": ["감옥에서 탈출하는 이야기", "..."] }     // 1~64개
```

```json
{
  "model": "BAAI/bge-m3",
  "items": [
    {
      "dense": [0.0123, ...],                          // 1024개, L2 정규화
      "sparse": { "indices": [1234, 88], "values": [0.21, 0.05] }
    }
  ]
}
```

- `GET /health` → 모델 로드가 끝나면 200을 반환한다 (compose healthcheck용).
- 65개 이상은 422로 거절한다. **나눠 보내는 책임은 Kotlin 쪽에 있다.**
- sparse의 `indices`는 정수, `values`는 실수로 고정한다.
- 계약은 `python/README.md`에 적고, Kotlin은 WireMock 스텁으로 계약 테스트를 한다.

---

## 5. Kotlin 설계 (`kotlin/`)

### 스택

| 항목 | 선택 | 비고 |
|---|---|---|
| JDK | **Java 25** (Gradle toolchain) | 로컬 Corretto 25로 검증 |
| 프레임워크 | **Spring Boot 4.1.1** | 2026-09-23 확정. 주의: 스타터명이 `web` → `spring-boot-starter-webmvc`, Kotlin JSON은 Jackson 3(`tools.jackson`) 계열 |
| 언어 | **Kotlin 2.3.21** | JVM 25 toolchain 빌드 검증 완료 |
| 빌드 | **Gradle 9.7.1** (Kotlin DSL, wrapper 포함) | Java 25 지원 확인 |
| 영속성 | **Spring Data JPA (Hibernate)** + Flyway | 스키마는 Flyway가 관리, `ddl-auto: validate` |
| Kotlin JPA 플러그인 | `kotlin("plugin.jpa")`, `kotlin("plugin.spring")` | 엔티티에 기본 생성자와 open 클래스를 자동으로 붙여줌 |
| HTTP 클라이언트 | `RestClient` | TMDB, embed 호출 |
| Qdrant | `io.qdrant:client` (gRPC) | ⚠️ Query API(prefetch + RRF) 지원 버전 확인 |
| 병렬 수집 | 가상 스레드 | Python의 `ThreadPoolExecutor(10)` 대체 |
| 테스트 | JUnit 5, Testcontainers(PG, Qdrant), WireMock(TMDB, embed) | |
| API 문서 | springdoc-openapi | 나중에 UI와의 계약 |

### JPA 엔티티 설계

현재 `schema.sql`을 그대로 매핑한다. 스키마는 바꾸지 않는다.

| 엔티티 | 테이블 | PK | 비고 |
|---|---|---|---|
| `Movie` | `movie` | `movieId` (IDENTITY, BIGSERIAL) | `tmdbId` unique. 장르·감독·출연진과 연관 |
| `Genre` | `genre` | `genreId` (**TMDB id를 직접 지정**, 자동 생성 아님) | |
| `MovieGenre` | `movie_genre` | 복합키 (movie_id, genre_id) | `@ManyToMany` + `@JoinTable`로 대체 가능. 추가 컬럼이 없으므로 **`@ManyToMany` 추천** |
| `MovieDirector` | `movie_director` | 복합키 (movie_id, person_id) | `@IdClass`. 부모 참조 없음 — movieId 파생 쿼리로 명시적 조회/삭제 |
| `MovieCast` | `movie_cast` | 복합키 (movie_id, person_id) | `@IdClass`, character·cast_order 포함. 위와 같은 방식 |
| `User`, `WatchHistory` | `"user"`, `watch_history` | | R1 자리. **이번 범위에서는 엔티티를 만들지 않음** (테이블만 Flyway로 유지) |

#### upsert 처리 방식 (Python의 `ON CONFLICT`를 JPA로)

1. `movieRepository.findByTmdbId(tmdbId)`로 조회
2. 있으면 필드를 갱신하고, 없으면 새로 만든다
3. 감독·출연진은 기존 것을 지우고(`deleteAllByMovieId`) 다시 삽입한다 — cascade 없이 명시적으로
4. 장르는 `genreRepository.findById` → 없으면 저장 후 연결
5. 영화 한 건당 트랜잭션 하나. 한 건이 실패해도 나머지는 계속 진행

JPA에서 주의할 점:
- `movie_id`가 IDENTITY라 **Hibernate의 insert 배치가 꺼진다.** 현재 규모(~1000건)에서는 문제없다. 3만 건으로 늘리면 시퀀스 방식으로 바꾸거나 native query로 전환을 검토한다.
- Kotlin 엔티티는 `data class`로 만들지 않는다 (equals/hashCode가 지연 로딩과 충돌하기 때문).
- 검색 경로는 Qdrant payload만 쓰므로 **검색할 때는 JPA를 타지 않는다.** JPA는 수집·색인 쪽에서만 쓴다.

### 패키지 구조 (단일 모듈)

```
com.whsanha55.cineseek
├── config/        설정 프로퍼티 (@ConfigurationProperties), Qdrant 빈, 공용 HTTP
├── domain/        엔티티 (Movie, Genre, MovieDirector, MovieCast) + 복합키
├── repository/    Repository (엔티티별)
├── client/        EmbeddingClient — POST /embed 호출, 64개 단위 분할
│                  TmdbClient      — discover, detail, credits (+ 응답 모델)
├── service/       SearchService, MovieUpsertService, MovieIndexer (+ 입출력 모델)
├── controller/    SearchController + 응답 DTO
└── job/           ReindexJob — 수집 → PG → 임베딩 → Qdrant
                   EvalRunner  — 고정 쿼리 10개 top-5 출력
```

컨벤션은 `kotlin/CLAUDE.md`에서 관리한다. (2026-09-23: 도메인 우선 → 레이어 우선으로 재구성)

### 파이프라인 실행 방식

- 배치 프레임워크 없이 `ApplicationRunner`를 쓴다. 플래그를 줬을 때만 실행하고 끝나면 종료한다.
  - `docker compose run --rm api --cineseek.job=reindex`
  - `docker compose run --rm api --cineseek.job=eval`
- Spring Batch나 스케줄러는 필요해질 때 도입한다.

### API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/search?q=&genre=&yearMin=&limit=` | 현재 `/search`와 같은 응답 구조 (필드명만 camelCase) |
| GET | `/actuator/health` | |
| — | `/api/movies/{id}/similar` | U2. **이번 전환 범위에서는 제외** (전환 후 첫 기능 후보) |

---

## 6. 실행 환경: 로컬 Docker와 서버, profile 분리

### 원칙

- **이미지는 하나.** 로컬에서 빌드한 이미지와 서버에서 쓰는 이미지가 같다.
- 환경 차이는 **Spring profile + 환경변수 + compose 덮어쓰기 파일**로만 표현한다.
- 비밀값(TMDB 토큰, DB 비밀번호)은 `.env`에만 두고 git에 올리지 않는다.

### profile 구성

| 파일 | 내용 |
|---|---|
| `application.yml` | 공통: JPA `ddl-auto: validate`, Flyway, actuator, 배치 크기(64/256), 수집 기본값 |
| `application-local.yml` | 로컬: SQL 로그 켜기, 수집 페이지 수 작게, DB·Qdrant·embed 주소 |
| `application-prod.yml` | 서버: SQL 로그 끄기, 수집 페이지 수 운영값, 서버 DB 주소 |

주소와 비밀값은 yml에 직접 쓰지 않고 환경변수로 받는다 (예: `${PG_HOST}`). profile은 compose에서 `SPRING_PROFILES_ACTIVE`로 지정한다.

### compose 구성

```
compose.yml           qdrant, embed, api 정의 (이미지 빌드 경로, 포트, healthcheck, 의존 순서). 프로젝트명 gonamu-cineseek
compose.local.yml     SPRING_PROFILES_ACTIVE=local, 전용 PG(postgres:17, jjong) + 포트를 호스트에 노출
compose.prod.yml      SPRING_PROFILES_ACTIVE=prod, 서버 PG 연결, 재시작 정책, 필요한 포트만 노출
```

실행 명령:

```bash
# 로컬: 이미지 빌드 + 실행
docker compose -f compose.yml -f compose.local.yml up --build

# 서버: 같은 구조, profile만 다름
docker compose -f compose.yml -f compose.prod.yml up -d
```

의존 순서는 `qdrant` → `embed`(healthcheck가 200이 될 때까지 대기) → `api`로 둔다.

### 로컬 환경에서 주의할 점

| 항목 | 내용 |
|---|---|
| **Docker 안에서는 MPS(Mac GPU)를 쓸 수 없다** | Docker는 Linux VM에서 돌아서 Apple GPU에 접근하지 못한다. 로컬 Docker에서도 임베딩은 **CPU로 돈다.** 서버와 같은 조건이라 오히려 결과 비교에는 유리하다. 대신 로컬 전체 재색인은 지금(MPS)보다 느려진다 |
| 로컬 PG | **compose.local.yml의 전용 postgres**를 띄운다 (2026-09-23 변경, 기존 공유 `reins-postgres` 폐지 — 해당 머신에 컨테이너가 없었음). 계정/비번 `jjong`, 컨테이너 `gonamu-cineseek-postgres`, 데이터 볼륨 `gonamu_pgdata`. api는 서비스명 `postgres`로 접속하고 IDE 직접 실행 시 `localhost:5432` |
| 모델 캐시 | bge-m3(약 2GB)를 매번 받지 않도록 embed 컨테이너의 HuggingFace 캐시를 named volume으로 둔다 |
| CPU 아키텍처 | 로컬 Mac(Apple Silicon)과 서버(OCI A1)가 **둘 다 arm64**다. 로컬에서 빌드한 이미지가 서버에서 그대로 돈다 |

### Dockerfile

| 대상 | 방식 |
|---|---|
| `kotlin/Dockerfile` | 멀티 스테이지: Gradle로 빌드 → JRE 25 이미지에 jar만 복사 |
| `python/Dockerfile` | `uv.lock` 기준으로 고정 설치, CPU 전용 torch, 모델은 이미지에 넣지 않고 볼륨 캐시 |

---

## 7. 단계별 계획

각 단계는 **확인 항목을 통과해야** 다음 단계로 넘어간다.

### Phase 0. 레포 재구성
- [x] 현재 Python 버전으로 **기준 결과 저장**: `eval.py` 출력 → `docs/baseline-eval.txt` (2026-09-23, 재색인 후 CPU로 생성)
- [x] `git mv`로 Python 파일을 `python/`, 문서를 `docs/`로 이동. 이 계획서도 `docs/PLAN.md`로 이동 (2026-09-23 완료 — CONCEPT.md·CEO-REVIEW.md·PLAN.md → `docs/`)
- [x] 루트 `.gitignore` 정리 — 2026-09-23 생성 (`.env` 포함, 나머지는 하위 폴더 .gitignore가 담당)
- 확인: 이동 후에도 `python/`에서 `uv run python eval.py`가 같은 결과를 낸다. `git log --follow python/pipeline.py`로 이력이 보인다.

### Phase 1. Python 임베딩 서버 + Docker 이미지
- [x] `app.py`를 `/embed`, `/health` 서버로 교체 (계약 §4) — 2026-09-23. 계약 문서는 `python/README.md`
- [x] `EMBED_DEVICE` 적용 (기본 cpu, 컨테이너는 compose가 cpu 고정)
- [x] `python/Dockerfile` 작성 (python:3.12-slim + uv 0.12.16, HF 캐시 named volume, 이미지 ~6.2GB)
- [x] 루트 `compose.yml` + `compose.local.yml`에 qdrant, embed 등록. api는 embed healthy까지 대기. 기존 `python/docker-compose.yml`은 삭제
- [x] `search.py`, `eval.py` 유지 (기준 비교용)
- 확인: 컨테이너로 띄운 뒤 `curl /embed` 응답의 dense 길이가 1024이고, 로컬에서 `embed.encode()`를 CPU로 직접 호출한 결과와 값이 같다.
  - 2026-09-23 통과 — dense 1024, sparse nnz 일치, dense[0:3] 완전 일치. S3 재확인도 완료 (api `/actuator/health` UP + embed `/health` 200)

### Phase 2. Kotlin 뼈대 + profile + Docker 이미지
- [x] Boot 4 + Kotlin + Java 25 + JPA 프로젝트 생성 (2026-09-23: Boot 4.1.1 / Kotlin 2.3.21 / Gradle 9.7.1, start.spring.io 생성 + `kotlin/`에 배치)
- [x] `application.yml` / `-local.yml` / `-prod.yml`
- [x] Flyway `V1__init.sql` = 현재 `schema.sql` (**복사** — 원본 `python/db/schema.sql`은 기준 비교용으로 Phase 5 정리 때 제거). `baselineOnMigrate`는 local에만
- [x] JPA 엔티티 작성, `ddl-auto: validate`로 스키마와 일치하는지 확인 (2026-09-23: `@IdClass` 복합키, 부모 참조·cascade 없음. 컨벤션은 `kotlin/CLAUDE.md`)
- [x] `kotlin/Dockerfile` (멀티 스테이지: temurin 25-jdk 빌드 → 25-jre 실행), compose에 api 추가 (embed는 Phase 1에서)
- [x] Testcontainers 설정 (2026-09-23: `MovieRepositoryTest` — Flyway 스키마 + validate + 저장/조회/명시적 삭제 라운드트립 통과)
- 확인 **S3**: `docker compose -f compose.yml -f compose.local.yml up --build` 후 `/actuator/health`가 UP이다. 빈 PG에서 Flyway가 스키마를 만들고, 엔티티 검증을 통과한다.
  - 2026-09-23: PG·Qdrant·api 기동 + health UP 확인, `./gradlew test` 통과 (embed 미포함 — Phase 1 완료 후 재확인)
  - 2026-09-23: Phase 1 완료 후 embed 포함 전체 스택 재확인 통과 (S3 완료)

### Phase 3. 검색 이식 (먼저 하는 이유: 이미 색인된 데이터로 바로 비교 가능)
- [x] `EmbeddingClient` + WireMock 계약 테스트 (2026-09-23: 64개 분할·응답 파싱 검증)
- [x] `SearchService`: dense와 sparse prefetch(각 20개) → RRF → limit, payload 필터(genre match, release_year ≥) — search.py 이식
- [x] `SearchController` `/api/search` (응답 구조는 python /search와 동일, 필드명 camelCase)
- [x] `EvalRunner` (`--cineseek.job=eval` → 10쿼리 top-5 출력 후 종료)
- 확인 **S1**: Kotlin eval 출력이 `docs/baseline-eval.txt`와 같다.
  - ✅ 2026-09-23 통과 (동점 순서 제외) — 10쿼리 × top-5에서 차이 8개 전부 동점 구간 스왑/반올림. python 자기 재실행도 4개 라인이 뒤집히는 Qdrant tie 비결정성 때문 (쿼리 벡터 자체는 Phase 1에서 비트 단위 동일 확인)
  - 대체 검증: `SearchServiceTest` — 실제 Qdrant 컨테이너 + 임베딩 스텁으로 RRF 순서·payload 필터 확인 통과

### Phase 4. 파이프라인 이식
- [x] `TmdbClient` (ko-KR, Bearer, `vote_count.gte=20`, popularity.desc) + WireMock 테스트 (2026-09-23)
- [x] 가상 스레드로 detail + credits 병렬 수집, 단건 실패는 건너뜀 (`ReindexJob.fetchDetailsParallel`)
- [x] `MovieUpsertService`: JPA로 upsert (§5 처리 방식), 출연진 상위 10명 + Testcontainers 테스트
- [x] `MovieIndexer`: 컬렉션 삭제 후 재생성(dense 1024 cosine + sparse), 64개 단위 임베딩(EmbeddingClient), 256개 단위 upsert + Qdrant 컨테이너 테스트
- [x] `ReindexJob` (`--cineseek.job=reindex`)
- 확인 **S2**: 빈 PG와 빈 Qdrant에서 `docker compose run --rm api --cineseek.job=reindex` → 건수 일치 → S1 재통과.
  - ✅ 2026-09-23 통과 — local profile(2페이지): PG movie 33 = Qdrant points 33, 이후 S1(기준 비교)도 통과

### Phase 5. Python 정리
- [x] `python/`에서 `search.py`, `pipeline.py`, `eval.py`, `db/schema.sql` 삭제 (2026-09-23). 의존성에서 `psycopg`, `httpx`, `qdrant-client` 제거 + `embed.py`의 `to_sparse`(qdrant 전용) 제거, `uv.lock` 재생성
- [x] `python/README.md`에 "임베딩 서비스 전용"과 계약 명시
- 확인: `python/`에는 임베딩 관련 코드만 남고, 이미지가 빌드된다.
  - 2026-09-23 통과 — `app.py`·`embed.py`만 남음, 의존성 정리 후 이미지 빌드 확인

### Phase 6. 서버 배포 (con-jjong, OCI A1 arm64)
- [ ] `compose.prod.yml` 작성 (prod profile, 서버 PG 주소, `restart: unless-stopped`)
- [ ] 이미지를 서버로 옮기는 방법 결정 (D2)
- [ ] 서버에서 재색인 1회 실행
- 확인 **S4, S5**.

### Phase 7. UI (별도 계획)
- `ui/` 폴더 추가. springdoc OpenAPI로 계약을 공유하고, Kotlin 쪽에서 CORS를 설정한다.
- compose에 ui 서비스 추가.
- 이번 계획 범위 밖이다. 스택은 그때 정한다.

---

## 8. 결정이 필요한 것

| # | 질문 | 추천 | 이유 |
|---|---|---|---|
| D1 | 서버의 PG | 확인 필요 | 로컬은 공유 인프라(reins-postgres). 서버에도 공유 PG가 있는지, compose에 새로 띄울지 |
| D2 | 이미지를 서버로 옮기는 방법 | 서버에서 `git pull` 후 직접 빌드 | 둘 다 arm64라 어디서 빌드해도 같음. 레지스트리(ghcr.io) 설정이 필요 없어 가장 단순. 빌드가 느리면 ghcr.io로 전환 |
| D3 | Kotlin 패키지 루트 | `com.whsanha55.cineseek` | |
| D4 | 로컬 개발 시 api도 항상 컨테이너로 돌릴지 | 기본은 컨테이너. 디버깅이 필요할 때만 IDE에서 `local` profile로 실행 | 같은 profile을 쓰므로 둘 다 가능. IDE 실행 시에는 DB 주소가 `localhost`가 되니 환경변수만 바꾸면 됨 |

---

## 9. 리스크

| 리스크 | 영향 | 대응 |
|---|---|---|
| FlagEmbedding/torch를 arm64 Linux 이미지로 빌드할 때 실패 | Phase 1 지연 | `uv.lock` 고정 설치, CPU 전용 torch 휠. Phase 1에서 가장 먼저 확인 |
| Python 이미지 수 GB | 빌드와 배포가 느림 | 모델은 이미지가 아닌 볼륨에 캐시 |
| 로컬 Docker에서 MPS를 쓸 수 없음 | 로컬 재색인이 느려짐 | 로컬은 수집 페이지 수를 작게(`application-local.yml`). 전체 재색인은 서버에서 |
| MPS와 CPU의 벡터 값 미세 차이 | S1 비교가 어긋날 수 있음 | CPU 기준 결과를 따로 만들어 비교 (Phase 3 주의 참고) |
| Kotlin과 Python의 sparse 변환 차이 | S1 실패 | 계약에 타입 명시. Phase 3에서 Python `to_sparse` 결과와 직접 비교 |
| JPA IDENTITY로 insert 배치 불가 | 대량 적재 시 느림 | 현재 규모는 문제없음. 3만 건 확대 시 재검토 |
| Kotlin, Gradle, Qdrant 클라이언트의 Java 25 / Boot 4 호환 | Phase 2 지연 | 착수 시 버전 확인 (§5의 ⚠️ 항목) |
| embed 서버가 시작 직후 느림 (모델 로드) | api가 먼저 떠서 실패 | embed healthcheck가 모델 로드 후에만 200, api는 `depends_on: condition: service_healthy` |

---

## 10. 범위 밖 (이번 전환에서 하지 않음)

- U2 유사영화 API, 개인화(R1), 리뷰/포스터 임베딩(R2, R3)
- 정량 평가(NDCG/MRR)
- 데이터를 3만 건으로 확대
- UI 구현
- CI/CD 자동화
