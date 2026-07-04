# cineseek

> 영화 도메인 시맨틱 검색·추천 서비스. PostgreSQL(원본/메타) + Qdrant(벡터).

## 1. 한 줄 정의

"이런 분위기 영화" 같은 **자연어·의미 기반**으로 영화를 찾고 추천받는 서비스.
키워드 매칭(기존 검색) 넘어 **임베딩 유사도**로 검색한다.

## 2. 왜 (Problem)

- 기존 영화 검색은 제목·배우·장르 **정확 매칭** 위주. "우주에서 혼자 살아남는 잔잔한 영화" 같은 질의 불가.
- 추천은 협업필터링(평점 기반) 위주 → **콜드스타트**·**설명불가** 문제.
- 벡터 검색으로: 줄거리·리뷰 의미를 이해한 검색 + 유사영화 추천 + 설명가능성(어떤 요소가 유사한지).

## 3. 핵심 유스케이스

| # | 유스케이스 | 쿼리 예시 |
|---|---|---|
| U1 | 시맨틱 검색 | "복수극인데 결말이 허무한 느와르" → 유사 영화 top-k |
| U2 | 유사 영화 추천 | 영화 A 상세 → "이 영화랑 비슷한" |
| U3 | 하이브리드 필터 | 시맨틱 + 메타필터(연도>2010, 평점>7, 장르=SF) |
| U4 | (확장) 개인화 | 시청이력 임베딩 평균 → 추천 |

## 4. 아키텍처

```
Client ──> API (backend)
             ├── PostgreSQL   : 영화 원본/메타(제목,연도,장르,평점,포스터URL,줄거리 원문)
             │                  + 유저/이력, 트랜잭션, 정합성 소스오브트루스
             └── Qdrant       : 임베딩 벡터 + payload(movie_id, 필터용 메타 사본)
                                유사도 검색(cosine), 메타필터(payload index)

Embedding pipeline (배치/이벤트):
   PG 신규/변경 영화 ──> 텍스트 조립(줄거리+태그) ──> Embedding model ──> Qdrant upsert
```

### 역할 분담 (중요)

- **PostgreSQL = 진실의 원천(SoT).** 모든 CRUD·트랜잭션은 여기. movie_id는 PG PK.
- **Qdrant = 파생 인덱스.** 벡터 + 검색필터용 payload만. 언제든 PG에서 재생성 가능해야 함.
- 이중쓰기 정합성: PG 커밋 후 임베딩 파이프라인이 Qdrant 반영(비동기). Qdrant는 eventually consistent 허용.

## 5. 임베딩 대상 (v1 결정 필요)

| 후보 | 신호 강도 | 비용 | v1 |
|---|---|---|---|
| 줄거리(synopsis) | ★★★ | 낮음 | ✅ 채택 |
| 리뷰 집계 | ★★★ | 중(수집필요) | v2 |
| 자막/스크립트 | ★★ | 높음(라이선스) | 보류 |
| 포스터 이미지(CLIP) | ★★ | 중 | v2 |

**v1: 줄거리 텍스트 단일 벡터.** 멀티모달은 v2 이후.

## 6. 데이터 소스

- TMDB API (줄거리·메타·포스터) — 무료, 표준. 우선 후보.
- 라이선스·rate limit 확인 필요.

## 7. 스택 (확정 — 접근법 B: Python + 로컬 임베딩)

- Backend: **Python / FastAPI**
- DB: PostgreSQL 16 (원본/메타/이력)
- Vector: **Qdrant** (Docker)
- Embedding: **로컬 모델** — `bge-m3` 또는 `multilingual-e5` (sentence-transformers). API 비용·의존 0
- Infra: docker-compose(로컬), 운영은 con-jjong(OCI Ampere A1, **ARM CPU / GPU 없음**)
- 결정 근거: 벡터DB 학습 집중 + API 비용/의존 회피 + 인프라 소유. CC/plan-ceo-review 2026-07-04 결정.
- 주의: CPU 임베딩 → 배치 처리량 제한. v1 규모 1~3만건 권장, 그 이상은 배치 병렬/야간 색인.

## 8. v1 스코프 (MVP) — SELECTIVE EXPANSION 확정

포함:
- [ ] TMDB 영화 1~3만건 적재 → PG
- [ ] 줄거리 로컬 임베딩(bge-m3/e5) → Qdrant upsert (배치)
- [ ] U1 시맨틱 검색 API
- [ ] U2 유사영화 API
- [ ] U3 **하이브리드 필터** (시맨틱 + genre/year/rating **+ director/cast**) ← 체리픽 추가
- [ ] **평가 하니스** — 쿼리 10종 고정, 실행 시 top-5 출력 (품질 회귀 감지) ← 체리픽 추가

제외(→ 로드맵/v2+):
- **개인화 추천** (본거/안본거) → 로드맵. v1은 PG 이력 테이블 자리만 비워둠
- 리뷰/멀티모달(포스터 CLIP) 임베딩
- UI(cineseek-ui 별도 repo), 유저/auth 시스템, 실시간 재색인

## 8-bis. 로드맵 (defer 항목 — 아키텍처가 막지 않게 v1 설계)

| 순위 | 항목 | v1이 비워둬야 할 자리 |
|---|---|---|
| R1 | 개인화 추천 (취향벡터 = 본 영화 임베딩 평균 → 안 본 것 유사검색) | PG `user`, `watch_history` 테이블 스키마 자리. auth는 v2 |
| R2 | 리뷰 임베딩 (줄거리+리뷰 결합 신호) | Qdrant 컬렉션 재색인 idempotent 유지 |
| R3 | 포스터 이미지 검색 (CLIP 멀티모달) | 별도 컬렉션/벡터 분리 가능한 구조 |

## 8-ter. v1 아키텍처 스케치

```
                    ┌──────────────┐
   Client ─────────▶│  FastAPI     │
                    │  /search     │──┐
                    │  /similar/:id│  │  1) 쿼리 텍스트 임베딩(로컬 모델)
                    └──────────────┘  │  2) Qdrant 유사도검색 + payload 필터
                          │           │     (genre/year/rating/director/cast)
                          │           │  3) hit movie_id 로 PG 메타 조회
              ┌───────────┴───────────┐
              ▼                       ▼
      ┌──────────────┐        ┌──────────────┐
      │ PostgreSQL   │        │   Qdrant     │
      │ (SoT)        │        │ (파생 인덱스) │
      │ movie 원본/  │        │ 벡터 + payload│
      │ 메타, 이력   │        │ (필터용 메타) │
      └──────────────┘        └──────────────┘
              ▲                       ▲
              │                       │
      ┌───────┴───────────────────────┴───────┐
      │  배치 임베딩 파이프라인 (Python)         │
      │  PG movie ─▶ 텍스트조립 ─▶ 로컬임베딩    │
      │           ─▶ Qdrant upsert (idempotent)│
      └────────────────────────────────────────┘
```

핵심 불변식:
- PG = 진실의 원천. Qdrant는 **언제든 PG에서 재생성 가능**해야 함 (파이프라인 idempotent).
- payload에 필터 대상(genre/year/rating/director/cast) 사본 저장 → Qdrant 단독 필터.
- 개인화(R1) 대비: movie_id는 안정적 PK 유지, PG 이력 테이블 자리 확보.

## 9. 성공 기준

- 시맨틱 쿼리 10종 수동 평가 → top-5 관련성 체감 "쓸만함".
- p95 검색 지연 < 300ms (벡터검색+PG조인 포함).
- Qdrant 컬렉션 PG에서 **처음부터 재생성 가능**(idempotent 파이프라인).

## 10. 열린 질문

1. ~~임베딩 모델: API vs 로컬~~ → **로컬(bge-m3/e5) 확정**
2. ~~백엔드 언어: Java vs Python~~ → **Python/FastAPI 확정**
3. 규모: 영화 몇 건? (CPU 임베딩이라 1~3만건 권장. 10만↑는 배치 전략 필요)
4. 하이브리드 검색에 PG full-text(BM25) 결합할지, Qdrant sparse vector 쓸지?
5. bge-m3 vs multilingual-e5 중 택1 (한국어 줄거리 비중에 따라 — bge-m3가 다국어 강함)
