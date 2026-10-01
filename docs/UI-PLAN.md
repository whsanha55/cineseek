# cineseek UI 계획서 — 기능 기획 + 디자인 시스템

> 작성: 2026-09-23 · 갱신: 2026-09-23 (리뷰 피드백 반영 → 1차 구현 완료) · 상태: **v1 구현 완료 (U-4 Playwright 남음)**
> PLAN.md Phase 7(UI)의 상세 계획. 백엔드 전환 계획은 [`PLAN.md`](PLAN.md), 서비스 개념은 [`CONCEPT.md`](CONCEPT.md)
>
> 결정 사항
> - 스택: **React + Vite SPA** (TypeScript). 정적 빌드 → nginx 컨테이너로 compose에 추가
> - v1 범위: **전부** — 시맨틱 검색(U1) + 영화 상세 + 유사 영화(U2) + 하이브리드 필터 전체(U3: 장르·연도·평점·감독·배우) + 탐색
> - 디자인 방향: **미니멀 라이트** — 검색엔진처럼 깨끗한 화이트, 검색창 중심
> - 디자인 시스템: **Tailwind + 자체 토큰(CSS 변수) + shadcn/ui** (복사 후 토큰에 맞춰 커스텀)
> - 계약 원칙: **필터는 전부 ID 기반**(`genreId`·`directorId`·`castId`), **페이지네이션을 URL에 포함**(`limit`·`page`), **카드 DTO 공통화**(`MovieCardItem`)

---

## 1. 목표

- "이런 분위기 영화"를 **문장으로 검색**하고, 결과를 **필터로 좁히고**, 마음에 드는 영화에서 **비슷한 영화로 이어 탐색**하는 흐름을 한 화면 흐름으로 만든다.
- 벡터 검색의 특성(의미 기반, 점수, 하이브리드)을 사용자가 **체감**할 수 있게 한다 — 학습용 프로젝트이므로 "왜 이 결과인가"가 보이는 게 가치다.
- 디자인 토큰과 컴포넌트 규칙을 먼저 정하고 화면은 그 위에 조립한다. 화면마다 스타일을 새로 만들지 않는다.

### 비목표 (v1 제외)

- 로그인·개인화(R1), 찜/시청 기록 — 스키마 자리만 있고 auth가 없다
- 다크 모드 — 토큰 구조만 대비해 두고 v1은 라이트만
- SSR/SEO, i18n (한국어 단일)
- 모바일 앱. 단 **반응형 웹은 v1에 포함** (모바일 폭 360px부터)

### 성공 기준

| # | 기준 | 확인 방법 |
|---|---|---|
| UI1 | 검색 제출 후 즉시(100ms 이내) 스켈레톤 표시. 로컬 compose 기준 **API 응답 p95 1초 이내**. 응답 도착 시 텍스트 결과(제목·메타) 즉시 렌더링. 포스터는 lazy load 후 표시 | 로컬 compose에서 `docs/baseline-eval.txt` 쿼리 10개 수동 확인 |
| UI2 | 결과 → 상세 → 유사 영화 → 상세… 로 뒤로가기 포함 끊김 없이 이어진다 (필터·표시 개수·스크롤 복원 포함) | 수동 시나리오 S-A, S-B (§2) |
| UI3 | 필터(장르·연도·평점·감독·배우)가 URL에 반영되어 새로고침·공유해도 같은 결과 | URL 복사 후 새 탭 |
| UI4 | 로딩·빈 결과·에러 상태가 모든 화면에 있다 | embed 컨테이너를 내린 상태에서 검색 |
| UI5 | 모바일 360px에서 가로 스크롤 없음, 키보드만으로 검색→상세 이동 가능 | 브라우저 반응형 모드 + Tab 순회 |
| UI6 | 화면 코드에 임의 색상·px 값이 없다 (토큰만 사용) | §6.1 검사 명령 통과 |

---

## 2. 사용자 시나리오

| # | 시나리오 | 흐름 |
|---|---|---|
| S-A | 분위기로 찾기 | 홈 → "우주에서 혼자 살아남는 잔잔한 영화" 입력 → 결과 그리드 → 카드 클릭 → 상세 |
| S-B | 비슷한 영화 따라가기 | 상세 → "이 영화랑 비슷한" 목록 → 다른 영화 상세 → (반복) → 뒤로가기로 복귀 |
| S-C | 조건 좁히기 | 결과 화면 → 장르 "스릴러", 2015년 이후, 평점 7+ → 결과 갱신 |
| S-D | 사람으로 좁히기 | 결과 화면 → 감독 입력 → 자동완성 목록에서 "봉준호" **선택(personId)** → 해당 감독 영화 중 의미상 가까운 순 |
| S-E | 뭘 찾을지 모를 때 | 홈의 예시 쿼리 칩 클릭 / 탐색 화면에서 장르별로 둘러보기 |

---

## 3. 화면 구성 (IA)

```
/                      홈 — 큰 검색창 + 예시 쿼리 칩 + 장르 바로가기
/search?q=&genreId=&yearMin=&yearMax=&ratingMin=&directorId=&castId=&limit=
                       검색 결과 — 필터 바 + 결과 그리드
/movies/:id            영화 상세 — 메타·줄거리·출연진 + 유사 영화
/explore?genreId=&sort=&page=
                       탐색 — 검색어 없이 장르/평점/연도로 둘러보기
*                      404
```

### 상태 원칙

- **확정 상태는 URL 쿼리가 원천**이다 — 제출된 검색어, 선택한 장르, 적용된 연도·평점, 선택 완료된 감독·배우, 정렬, 표시 개수(`limit`)·페이지(`page`). 컴포넌트 상태에 복제하지 않는다 → UI3. `limit`이 URL에 있어야 "더 보기 후 상세 → 뒤로가기"에서 표시 개수가 복원된다 (UI2)
- **일시적 UI 상태는 컴포넌트 로컬 상태**로 관리한다 — 검색창 입력 중 문자열, 자동완성 검색어, Sheet·Popover 열림 여부, 슬라이더 드래그 중 값. 검색 제출·필터 적용 시점에 `setSearchParams`로 URL에 반영한다. 매 입력마다 URL을 바꾸면 히스토리 오염·불필요한 API 요청·한글 조합 문제가 생긴다
- 내비게이션 정책: **검색 제출 `push` / 필터 변경·더 보기 `replace` / 상세 진입 `push`**
- 필터 파라미터는 **전부 ID 기반** (`genreId`, `directorId`, `castId`). 이름은 API 응답에서 받아 표시용으로만 쓴다 — 동명이인 구분, 한글/영문 표기 차이, 임의 문자열 제출, 공유 URL의 의미 변질을 막는다
- 장르 다중 선택은 **반복 파라미터**: `genreId=28&genreId=53`. `URLSearchParams.getAll("genreId")`로 처리. 쉼표 방식은 쓰지 않는다 (개별 해제가 불명확)
- **검색어 없는 `/search`는 `/explore`로 이동**한다 (조건 유지). `/search`는 의미 검색어 필수, `/explore`는 조건만 — 검색 결과에서 검색어를 지우고 제출하면 자연스럽게 탐색으로 넘어간다

- 상단 헤더(로고 + 축소 검색창)는 홈을 제외한 모든 화면에 고정

---

## 4. 기능 명세

### F1. 홈 `/`

- 화면 중앙 큰 검색창(placeholder: "어떤 영화를 찾고 있나요? 예: 복수극인데 결말이 허무한 느와르"), Enter로 `/search?q=`. 빈 검색어 Enter는 무시
- 예시 쿼리 칩 5~6개 — `EvalRunner` 고정 쿼리에서 고른다 (프론트 상수)
- 장르 바로가기 칩 → `/explore?genreId=`

### F2. 검색 결과 `/search`

- 결과 그리드: 포스터 카드 (포스터, 제목, 연도, 평점, 장르 최대 2개)
- **관련도 표시**: 백엔드는 원본 점수(`score`)만 내려주고, **프론트가 현재 결과 집합 기준 상대 2단계 등급(관련도 높음 / 보통)**을 계산해 표시한다. RRF 점수는 검색마다 분포가 달라 절대값처럼 읽히면 안 되므로 상대 표현만 쓰며, "낮음"은 결과 품질이 나빠 보여 표시하지 않는다. 절대 점수는 개발용 `?debug=1`에서만 숫자로
- 필터 바 (§F4) — 변경 즉시 URL 갱신 → 재조회. 활성 필터는 칩으로 보이고 × 로 해제
- 결과 수 `limit` 기본 20, "더 보기"로 40, 최대 50 (API 상한). **`limit`은 URL에 유지**
- 상태: 로딩(스켈레톤 카드) / 빈 결과(아래 액션 제공) / 에러(재시도)
- 빈 결과 액션 — 단계별 해제:
  1. **[필터 초기화]** — 검색어는 유지하고 필터만 해제 (기본 액션)
  2. **[전체 해제]** — 검색어까지 초기화
  3. **[탐색으로 이동]** — `/explore` 링크

### F3. 영화 상세 `/movies/:id`

- 상단: 포스터 + 제목/원제, 연도·러닝타임·평점(투표 수), 장르 칩
- 줄거리 전문
- 감독, 주요 출연진(상위 5명, 배역명) — 이름 클릭 동작 통일: **감독 → `/explore?directorId=`, 배우 → `/explore?castId=`**. 상세에 직접 들어온 경우에도 성립하며, 이후 검색어를 추가하면 `/search?q=&directorId=`로 좁힌다. 이전 검색 조건 복원은 브라우저 뒤로가기로 충분하다
- **"이 영화랑 비슷한"**: 유사 영화 카드 8~12개 가로 스크롤(모바일) / 그리드(데스크톱)
- 백드롭 이미지는 미니멀 방향에 맞춰 **쓰지 않는다** (v1)

### F4. 필터 (U3 전체)

| 필터 | UI | 쿼리 파라미터 | 백엔드 |
|---|---|---|---|
| 장르 | 칩 다중 선택 (OR) | `genreId` (반복) | 다중 `should` 필요 (U-B1) |
| 연도 | 시작/끝 셀렉트 | `yearMin`, `yearMax` | `yearMin` 있음, `yearMax` 추가 |
| 평점 | 최소값 셀렉트 (6+, 7+, 8+) | `ratingMin` | **없음 → 추가** |
| 감독 | 자동완성 (목록에서 선택) | `directorId` | **없음 → 추가** (ID 필터) |
| 배우 | 자동완성 (목록에서 선택) | `castId` | **없음 → 추가** (ID 필터) |

- 모바일: 필터는 "필터" 버튼 → 하단 시트(Sheet)
- 감독·배우 자동완성은 **임의 문자열 제출 불가** — 목록에서 선택해야 personId가 생긴다
- 감독·배우 필터는 payload의 상위 3명/상위 5명만 대상이다 (색인 규칙, U-D3). UI 도움말에 명시

### F5. 탐색 `/explore`

- 검색어 없이 PG에서 조건 목록 조회: 장르 탭 + 정렬
- **정렬 라벨은 실제 기준을 정확히 표시**: `평점 높은 순` / `최근 개봉 순` / `투표 많은 순` ("인기순"이라 쓰지 않는다 — 기준이 vote_count이므로). 평점 높은 순은 최소 투표 수 임계값(`vote_count >= 100`, 상수)을 적용해 투표 몇 표짜리 영화가 상위에 오지 않게 한다
- 카드·필터 컴포넌트는 F2와 공유. 페이지네이션은 "더 보기", `page`는 URL에 유지

---

## 5. API 계약

현재 Kotlin API는 `GET /cineseek/search`뿐이고, 응답에 UI에 필요한 필드가 빠져 있다. **UI 작업 전 백엔드 Phase(U-B)로 먼저 채운다.**

### 5.0 공통 DTO

카드가 그려지는 모든 응답(A1 검색, A3 유사, A4 탐색)은 같은 아이템 구조를 쓴다 → `MovieCard`, `MovieGrid`, `PosterImage`, 스켈레톤, "더 보기" 로직 전부 공유.

```
MovieCardItem
├─ movieId: number
├─ title: string
├─ originalTitle: string | null
├─ releaseYear: number | null      — 개봉일 없으면 null (0 아님)
├─ rating: number | null           — 평점 없으면 null (0 아님)
├─ voteCount: number
├─ posterPath: string | null
└─ genres: { id: number, name: string, nameKo: string }[]   — 객체 배열 (문자열 배열 아님)

SearchResultItem = MovieCardItem + score: number   — 원본 점수. 등급은 프론트에서 계산
```

페이지네이션 공통 구조:

```
page: { limit: number, offset: number, hasNext: boolean, total: null }
```

- 전체 결과 수(`total`)는 **미제공** — 하이브리드 검색에서 전체 count 비용이 크다. "더 보기" 표시는 `hasNext`로 판단

### 5.1 엔드포인트

| # | 엔드포인트 | 상태 | 변경 |
|---|---|---|---|
| A1 | `GET /cineseek/search` | 있음 | 응답을 `SearchResultItem[]` + `page`로 확정 — 표시 필드는 PG 조립이라 payload는 필터용 ID(`genre_ids`·`director_ids`·`cast_ids`)만 담는다(재색인). 파라미터 `genreId`(반복, OR), `yearMax`, `ratingMin`, `directorId`, `castId`, `limit`, `offset` 추가. `q` 필수 — 없는 요청은 프론트가 `/explore`로 보내므로 도달하지 않음 |
| A2 | `GET /cineseek/movies/{id}` | **없음** | PG 조회: 메타 + 줄거리 + 장르 + 감독 + 출연진(상위 5, 배역, personId 포함) |
| A3 | `GET /cineseek/movies/{id}/similar?limit=` | **없음** | U2. 세부는 §5.2 |
| A4 | `GET /cineseek/movies?genreId=&sort=&page=&limit=` | **없음** | 탐색용 PG 목록 (F5). `sort`: `rating` \| `release` \| `vote_count` |
| A5 | `GET /cineseek/genres` | **없음** | 장르 칩 목록 (`id`, `name`, `nameKo`) |
| A6 | `GET /cineseek/people?q=&role=director\|cast` · `GET /cineseek/people/{id}` | **없음** | 감독·배우 자동완성 + 새로고침 시 칩 라벨용 ID 조회 (PG `movie_director`/`movie_cast`, distinct, 상위 10) |

### 5.2 응답 예시

**A1 검색:**

```json
{
  "items": [
    {
      "movieId": 123,
      "title": "마션",
      "originalTitle": "The Martian",
      "releaseYear": 2015,
      "rating": 8.0,
      "voteCount": 21000,
      "posterPath": "/example.jpg",
      "genres": [{ "id": 878, "name": "Science Fiction", "nameKo": "SF" }],
      "score": 0.032
    }
  ],
  "page": { "limit": 20, "offset": 0, "hasNext": true, "total": null }
}
```

**A2 상세:**

```json
{
  "movieId": 123,
  "title": "마션",
  "originalTitle": "The Martian",
  "releaseYear": 2015,
  "runtime": 144,
  "rating": 8.0,
  "voteCount": 21000,
  "posterPath": "/example.jpg",
  "overview": "...",
  "genres": [{ "id": 878, "name": "Science Fiction", "nameKo": "SF" }],
  "directors": [{ "personId": 21684, "name": "리들리 스콧" }],
  "cast": [{ "personId": 5678, "name": "맷 데이먼", "character": "마크 와트니" }]
}
```

**A3 유사 영화 — 구현 세부:**

- 검색 방식: 하이브리드가 아니라 **해당 영화의 저장 벡터로 vector-to-vector 검색** (코사인 유사도). Qdrant 조회 시 `with_vector=true`로 원본 벡터를 가져와 query로 쓴다
- **전제 확인: Qdrant point id = `movie_id`** — 재색인 후에도 포인트 id를 movie_id로 upsert해 유지한다 (U-B2에서 테스트로 검증)
- 자기 자신 제외. 응답 조립은 PG로 하므로 PG에 없는 포인트(삭제됨)는 제외된다. "PG에는 있지만 Qdrant에 없는 영화"는 상세는 정상 응답, `similar`만 빈 배열
- 장르 필터는 v1 없음. `limit` 기본 8, 상한 20
- 응답: `SearchResultItem[]` + `page` (score = 코사인 유사도). UI는 유사 영화 카드에는 관련도 등급을 표시하지 않는다

**A6 사람 자동완성:**

```json
{
  "items": [
    {
      "personId": 21684,
      "name": "봉준호",
      "role": "DIRECTOR",
      "knownFor": ["기생충", "살인의 추억"]
    }
  ]
}
```

### 5.3 포스터·TMDB 정책

- 포스터 URL은 **TMDB 이미지 CDN**(`https://image.tmdb.org/t/p/w342{posterPath}`)을 프론트에서 조립한다. 크기: 카드 `w342`, 상세 `w500`
- `PosterImage`: lazy load. `posterPath` 없음 → 제목 이니셜 플레이스홀더. **로드 실패(CDN 에러·404·잘못된 경로) → `onError` 1회 처리 후 플레이스홀더로 전환, 무한 재시도 금지**
- TMDB 데이터·이미지 사용 표기: 푸터에 출처 문구("This product uses the TMDB API but is not endorsed or certified by TMDB.")를 둔다

### 5.4 계약 공유·CORS

- PLAN.md 계획대로 **springdoc-openapi** → `openapi-typescript`로 TS 타입 생성. 수기 타입 금지
- CORS: 로컬은 Vite dev proxy, compose는 nginx가 `/cineseek` → `api:8080` 프록시 → **같은 출처라 CORS 설정 불필요**

---

## 6. 디자인 시스템

### 6.1 원칙

1. **콘텐츠가 주인공** — 색은 포스터가 담당한다. UI 자체는 무채색 + 강조색 1개
2. **검색창이 가장 크다** — 모든 화면에서 검색으로 돌아가는 길이 1클릭 이내
3. **토큰만 쓴다** — 원시값(`#333`, `[13px]`)은 `tokens.css` 안에서만 존재한다. 컴포넌트는 의미 토큰만 참조한다 (`bg-background`, `bg-surface`, `text-foreground`, `text-muted-foreground`, `border-border`, `bg-primary`, `text-destructive`). shadcn 복사본에 들어오는 Tailwind 기본색·임의값 클래스(`bg-blue-*`, `text-zinc-*`, `border-gray-*`, `rounded-[...]`, `shadow-[...]`)도 금지 (UI6)
4. **여백으로 구분한다** — 테두리·그림자는 최소. 카드 구분은 간격, 필요할 때만 1px 보더

UI6 검사 명령 (tokens.css 제외 + 임의값 클래스):

```bash
grep -R -E '#[0-9a-fA-F]{3,8}|\[[0-9]+px\]|bg-(red|blue|green|yellow|purple|pink|orange|zinc|gray|slate|stone|neutral)-|rounded-\[|shadow-\[' \
  ui/src --exclude='tokens.css'   # 결과 0건이어야 함
```

### 6.2 토큰

CSS 변수(`ui/src/styles/tokens.css`)가 원천이고 Tailwind v4 `@theme`에서 참조한다. shadcn 컴포넌트도 같은 변수명을 쓰므로 그대로 맞물린다.

**색 (라이트)** — 뉴트럴은 살짝 차가운 회색, 강조색은 하나

| 토큰 | 용도 | 값(초안) |
|---|---|---|
| `--background` | 페이지 배경 | `#FFFFFF` |
| `--surface` | 필터 바, 시트, 입력 배경 | `#F7F7F8` |
| `--foreground` | 본문 텍스트 | `#18181B` |
| `--muted-foreground` | 보조 텍스트(연도, 배역) | `#71717A` |
| `--border` | 구분선, 입력 테두리 | `#E4E4E7` |
| `--primary` | 강조(포커스 링, 활성 칩, 링크) | `#2563EB` |
| `--primary-foreground` | 강조 위 텍스트 | `#FFFFFF` |
| `--rating` | 평점 별 | `#F59E0B` |
| `--destructive` | 에러 | `#DC2626` |

**shadcn 호환 alias** — shadcn 컴포넌트가 기대하는 변수를 기본 토큰에 연결해 복사 시 수정량을 최소화한다:

```css
:root {
  /* ... 기본 토큰 (위 표) ... */

  --card: var(--background);
  --card-foreground: var(--foreground);
  --popover: var(--background);
  --popover-foreground: var(--foreground);
  --secondary: var(--surface);
  --secondary-foreground: var(--foreground);
  --muted: var(--surface);
  --accent: var(--surface);
  --accent-foreground: var(--foreground);
  --input: var(--border);
  --ring: var(--primary);
}
```

- 대비: 본문 텍스트/배경 4.5:1 이상 (WCAG AA). `--muted-foreground`도 흰 배경에서 AA 충족 확인
- 다크 모드 대비: 값은 `:root`에만 두고 컴포넌트는 변수만 참조 → 나중에 `[data-theme=dark]` 블록 추가로 끝나게

**타이포그래피** — 한글 가독성 우선, 폰트 1종

- 폰트: **Pretendard (가변, 한글/라틴 모두), 자체 호스팅** — `ui/public/fonts/PretendardVariable.woff2`. CDN을 쓰지 않는다 (외부 네트워크 의존·폰트 깜빡임·CSP 회피). fallback 스택:

```
Pretendard Variable, Pretendard, -apple-system, BlinkMacSystemFont, system-ui, sans-serif
```

- 숫자(연도·평점)는 `tabular-nums`
- 스케일 (rem, 기준 16px):

| 토큰 | 크기/행간 | 용도 |
|---|---|---|
| `text-xs` | 12/16 | 칩, 메타 보조 |
| `text-sm` | 14/20 | 카드 메타, 필터 라벨 |
| `text-base` | 16/24 | 본문, 줄거리 |
| `text-lg` | 18/28 | 카드 제목 |
| `text-2xl` | 24/32 | 섹션 제목 |
| `text-4xl` | 36/40 | 상세 제목 (모바일 `text-2xl`) |

- 굵기: 400(본문) / 500(라벨·칩) / 700(제목) 세 가지만

**간격** — 4px 그리드. Tailwind 기본 스케일(1=4px)을 그대로 쓰고, 레이아웃 규칙만 고정

- 페이지 좌우 여백: 모바일 16px(`4`), 데스크톱 32px(`8`), 콘텐츠 최대폭 1200px
- 카드 그리드 간격: 모바일 12px(`3`), 데스크톱 24px(`6`)
- 섹션 간격: 48px(`12`)

**기타**

| 토큰 | 값 | 용도 |
|---|---|---|
| `--radius` | 8px | 입력, 버튼, 카드 포스터 |
| `--radius-full` | 9999px | 칩, 검색창 |
| `--shadow-sm` | 아주 옅게 1단계만 | 검색창 포커스, 드롭다운 |
| 모션 | 150ms ease-out | 호버·포커스 전환만. `prefers-reduced-motion` 존중 |

### 6.3 컴포넌트

shadcn/ui에서 가져와 토큰에 맞추는 것과 직접 만드는 것을 나눈다. 모든 컴포넌트는 로딩/빈/에러 상태를 가진다.

| 컴포넌트 | 출처 | 설명 |
|---|---|---|
| Button, Input, Badge, Skeleton, Select, Slider, Sheet, Popover, Command, Tooltip | shadcn | Command = 감독/배우 자동완성 |
| `SearchBar` | 자체 | `size="hero"`(홈) / `"compact"`(헤더). Enter 검색, `/` 단축키로 포커스 |
| `MovieCard` | 자체 | 포스터(2:3) + 제목(2줄 말줄임) + 연도·평점 + 장르 칩. 호버 시 포스터만 살짝 확대 |
| `PosterImage` | 자체 | TMDB URL 조립, lazy load, 없음·로드 실패 → 제목 이니셜 플레이스홀더 (§5.3) |
| `RelevanceMeter` | 자체 | 원본 점수 → 현재 결과 집합 기준 상대 2단계 (§F2) |
| `FilterBar` / `FilterSheet` | 자체 | 데스크톱 가로 바 / 모바일 하단 시트. 활성 필터 칩 + 전체 해제 |
| `MovieGrid` | 자체 | 반응형 열 수: 2(<640) / 3(≥640) / 4(≥1024) / 5(≥1280) |
| `SimilarRow` | 자체 | 유사 영화 — 모바일 가로 스크롤, 데스크톱 그리드 |
| `EmptyState`, `ErrorState` | 자체 | 아이콘 + 한 줄 설명 + 액션 버튼 (F2의 3종 액션 포함) |

- 아이콘: **lucide-react** (shadcn 기본)
- 컴포넌트 카탈로그: 별도 Storybook 없이 개발용 `/_ds` 라우트 한 장에 토큰·컴포넌트를 모아 본다. **`import.meta.env.DEV` 조건부 라우트 + dynamic import로 등록해 운영 번들에서 제외**한다:

```tsx
{import.meta.env.DEV && (
  <Route path="/_ds" lazy={() => import("./pages/_DesignSystem")} />
)}
```

### 6.4 접근성·반응형

- 모든 인터랙션 요소 키보드 도달 가능, 포커스 링은 `--primary` 2px
- 포스터 `alt` = 영화 제목. 평점은 별 아이콘 + 숫자 텍스트(색만으로 전달 금지)
- 브레이크포인트: Tailwind 기본(`sm 640`, `md 768`, `lg 1024`, `xl 1280`). 모바일 우선 작성

---

## 7. 기술 구성

```
ui/
├── src/
│   ├── api/            openapi-typescript 생성 타입 + fetch 래퍼
│   ├── components/
│   │   ├── ui/         shadcn 복사본 (토큰만 수정)
│   │   └── ...         자체 컴포넌트 (§6.3)
│   ├── pages/          Home, Search, MovieDetail, Explore, NotFound, _DesignSystem
│   ├── hooks/          useSearchParamsState 등 URL 상태 훅
│   ├── styles/tokens.css
│   └── main.tsx
├── public/fonts/PretendardVariable.woff2
├── index.html
├── vite.config.ts      dev proxy: /cineseek → localhost:8080
├── Dockerfile          node 빌드 → nginx:alpine 정적 서빙 + /cineseek 프록시
└── nginx.conf
```

| 항목 | 선택 | 이유 |
|---|---|---|
| 언어 | TypeScript | API 타입 생성과 맞물림 |
| 라우팅 | React Router | URL 쿼리 = 상태 원천 |
| 서버 상태 | TanStack Query | 캐시 덕분에 상세 ↔ 결과 뒤로가기 시 재요청 없음 |
| 스타일 | Tailwind v4 + tokens.css | §6.2 |
| 컴포넌트 | shadcn/ui + lucide-react | §6.3 |
| 패키지 매니저 | pnpm | |
| 테스트 | Vitest + Testing Library (훅·컴포넌트), 시나리오는 Playwright (§8) | |

**nginx.conf 정책:**

```nginx
location /assets/ {
    expires 1y;
    add_header Cache-Control "public, immutable";   # 해시 자산은 장기 캐시
}
location = /index.html {
    add_header Cache-Control "no-cache";            # index.html은 항상 재검증
}
location /cineseek/ {
    proxy_pass http://api:8080;   # trailing slash 주의 — /cineseek 경로가 유지되는지 실제 백엔드 경로로 확인
}
location / {
    try_files $uri $uri/ /index.html;               # SPA fallback
}
```

- gzip 활성화 (brotli는 필요 시 후속)
- 보안 헤더 최소: `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`

**Health check:**

- API에 `/actuator/health` 노출 (U-B1, springdoc과 함께)
- compose `api` 서비스에 healthcheck 정의. 단순 `depends_on`은 시작 순서만 보장한다
- **UI를 API 준비까지 지연시키지 않는다** — nginx는 정적 응답이 가능하고, API 요청이 실패하는 동안엔 ErrorState(재시도)를 보여주면 충분하다

compose: `ui` 서비스 추가 (compose.local.yml에서 `5173:80` 또는 `3000:80` 노출), `depends_on: api`.

---

## 8. 단계별 계획

각 단계는 확인 항목을 통과해야 다음으로 넘어간다. **U-0은 U-B와 병렬 진행 가능** — `/_ds`는 §5.0의 Mock DTO로 만들 수 있어 백엔드 완료를 기다리지 않는다.

### Phase U-B1. 계약 + 핵심 검색 API (kotlin/)
- [x] springdoc-openapi 추가, `/v3/api-docs` 노출. `/actuator/health` 노출
- [x] 필터용 payload 재구성(`genre_ids`·`director_ids`·`cast_ids`) → 재색인. A1 응답을 `SearchResultItem` + `page` 구조로 확정 — 표시 필드는 PG 조립이라 `poster_path` payload 추가는 불필요해짐
- [x] A1 필터 확장: `genreId` 다중(OR/should), `yearMax`, `ratingMin`, `directorId`, `castId`, `limit`/`offset`/`hasNext`
- [x] A5 장르, A6 사람 자동완성 + `GET /cineseek/people/{id}`
- 확인: 기존 컨벤션 테스트(WireMock/Testcontainers) 통과(35건). EvalRunner 결과 `baseline-eval.txt`와 상위권 일치 (하위권 차이는 TMDB discover 시점 차이 — 새 영화 유입)

### Phase U-0. 디자인 시스템 기반 (ui/) — U-B1과 병렬
- [x] Vite + React + TS 스캐폴드, Tailwind v4 (shadcn CLI 대신 필요한 프리미티브를 토큰에 맞춰 직접 작성)
- [x] `tokens.css` 작성 (§6.2 + shadcn alias), Pretendard 자체 호스팅(dynamic-subset woff2 번들)
- [x] 자체 컴포넌트 골격 — 실제 생성 타입(openapi-typescript) 연결
- [x] `/_ds` 카탈로그 페이지 (DEV 전용 라우팅, 운영 번들 제외 확인)
- 확인: UI6 grep 통과 (px 임의값·기본색 클래스 0건 — `var()` 참조만 허용)

### Phase U-1. 검색 흐름 (F1, F2, F4)
- [x] API 타입 생성 파이프라인 (`pnpm gen:api` — 루트 `openapi.json` 스냅샷 → `schema.d.ts`)
- [x] 홈, 검색 결과, 필터 바/시트, URL 상태 훅 (§3 상태 원칙)
- [x] Vitest: 쿼리 파라미터 파싱·정규화, 장르 다중, 잘못된 값 처리, 관련도 등급 계산, TMDB URL (21건 통과)
- [x] Testing Library: SearchBar Enter 제출·빈 값, PosterImage 오류 fallback
- 확인: UI1, UI3, UI4, 시나리오 S-A·S-C·S-D — 헤드리스 브라우저로 실증 (필터 → URL `genreId=878`, 자동완성 선택 → `directorId=525` + 칩 라벨)

### Phase U-B2 + U-2. 상세 + 유사 영화 (F3)
- [x] A2 상세, A3 유사 영화 (§5.2 세부: vector-to-vector, point id = movie_id — 재색인 upsert로 유지)
- [x] 상세 UI, `SimilarRow`
- 확인: UI2, 시나리오 S-B — 상세 → 유사 → 상세 → 뒤로가기 복귀 실증

### Phase U-B3 + U-3. 탐색 + 배포 구성 (F5)
- [x] A4 목록 (`sort`: rating/release/vote_count, 평점순 `vote_count >= 100`). 상세 인물 클릭용 `directorId`·`castId` 파라미터 추가(F3)
- [x] 탐색 화면, 검색어 없는 `/search` → `/explore` 이동
- [x] `ui/Dockerfile` + nginx.conf (§7 정책), compose에 `ui` 서비스 + `api` healthcheck
- 확인: `docker compose up --build` 한 번으로 UI까지 뜸(5173). UI5(375px 무결), 시나리오 S-E

### Phase U-4. Playwright (U-3 이후)
- 최소 시나리오 1개 자동화: 홈 검색 → 결과 필터 → 상세 진입 → 유사 영화 진입 → 뒤로가기 → **기존 필터·표시 개수·스크롤 복원 확인**
- 스크롤 복원은 TanStack Query 캐시만으로 해결되지 않는다 — React Router `ScrollRestoration` 정책을 함께 적용·확인

---

## 9. 결정 사항 (구 열린 질문)

| # | 항목 | 결정 |
|---|---|---|
| U-D1 | 강조색 | **블루(`#2563EB`) 유지** — 미니멀 검색 서비스에 적합. `/_ds`에서 최종 확인만 |
| U-D2 | 장르 다중 선택 | **OR(`should`)를 v1에 채택** (반복 파라미터 `genreId`). `match=all`(AND)은 필요 시 확장 |
| U-D3 | 감독/배우 필터 상위 3명/5명 한계 | **가능하면 인물 ID 전체를 payload에 색인** (재색인 1회, U-B1에서 검토). 여의치 않으면 UI 도움말로 명시 |
| U-D4 | 관련도 표시 | **프론트가 현재 결과 집합 기준 상대 2단계(높음/보통)**. "낮음" 미표시. 절대 점수는 `?debug=1` 전용 |
| U-D5 | 탐색 정렬 | `vote_count` 기준, UI 라벨은 **"투표 많은 순"** 으로 정확히 표시. 평점 높은 순은 최소 투표 수 임계값(`vote_count >= 100`) 적용. popularity 컬럼 추가는 V2 |
| U-D6 | 데이터 규모 | **UI 확인 전 로컬 `TMDB_PAGES`를 늘려 500~1000건 확보** — 탐색/필터 결과가 자주 비지 않게 |
