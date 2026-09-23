# cineseek UI 계획서 — 기능 기획 + 디자인 시스템

> 작성: 2026-09-23 · 상태: **초안 (구현 전)**
> PLAN.md Phase 7(UI)의 상세 계획. 백엔드 전환 계획은 [`PLAN.md`](PLAN.md), 서비스 개념은 [`CONCEPT.md`](CONCEPT.md)
>
> 결정 사항
> - 스택: **React + Vite SPA** (TypeScript). 정적 빌드 → nginx 컨테이너로 compose에 추가
> - v1 범위: **전부** — 시맨틱 검색(U1) + 영화 상세 + 유사 영화(U2) + 하이브리드 필터 전체(U3: 장르·연도·평점·감독·배우) + 탐색
> - 디자인 방향: **미니멀 라이트** — 검색엔진처럼 깨끗한 화이트, 검색창 중심
> - 디자인 시스템: **Tailwind + 자체 토큰(CSS 변수) + shadcn/ui** (복사 후 토큰에 맞춰 커스텀)

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
| UI1 | 홈에서 문장을 입력하면 1초 안에 포스터 카드 결과가 뜬다 | 로컬 compose에서 `docs/baseline-eval.txt` 쿼리 10개 수동 확인 |
| UI2 | 결과 → 상세 → 유사 영화 → 상세… 로 뒤로가기 포함 끊김 없이 이어진다 | 수동 시나리오 S-A, S-B (§2) |
| UI3 | 필터(장르·연도·평점·감독·배우)가 URL에 반영되어 새로고침·공유해도 같은 결과 | URL 복사 후 새 탭 |
| UI4 | 로딩·빈 결과·에러 상태가 모든 화면에 있다 | embed 컨테이너를 내린 상태에서 검색 |
| UI5 | 모바일 360px에서 가로 스크롤 없음, 키보드만으로 검색→상세 이동 가능 | 브라우저 반응형 모드 + Tab 순회 |
| UI6 | 화면 코드에 임의 색상·px 값이 없다 (토큰만 사용) | `grep -E '#[0-9a-fA-F]{3,6}\|\[[0-9]+px\]' ui/src` 결과가 토큰 파일뿐 |

---

## 2. 사용자 시나리오

| # | 시나리오 | 흐름 |
|---|---|---|
| S-A | 분위기로 찾기 | 홈 → "우주에서 혼자 살아남는 잔잔한 영화" 입력 → 결과 그리드 → 카드 클릭 → 상세 |
| S-B | 비슷한 영화 따라가기 | 상세 → "이 영화랑 비슷한" 목록 → 다른 영화 상세 → (반복) → 뒤로가기로 복귀 |
| S-C | 조건 좁히기 | 결과 화면 → 장르 "스릴러", 2015년 이후, 평점 7+ → 결과 갱신 |
| S-D | 사람으로 좁히기 | 결과 화면 → 감독 "봉준호" 입력(자동완성) → 해당 감독 영화 중 의미상 가까운 순 |
| S-E | 뭘 찾을지 모를 때 | 홈의 예시 쿼리 칩 클릭 / 탐색 화면에서 장르별로 둘러보기 |

---

## 3. 화면 구성 (IA)

```
/                      홈 — 큰 검색창 + 예시 쿼리 칩 + 장르 바로가기
/search?q=&genre=&yearMin=&yearMax=&ratingMin=&director=&cast=
                       검색 결과 — 필터 바 + 결과 그리드
/movies/:id            영화 상세 — 메타·줄거리·출연진 + 유사 영화
/explore?genre=&sort=  탐색 — 검색어 없이 장르/평점/연도로 둘러보기
*                      404
```

- 상태는 **전부 URL 쿼리**가 원천이다 (필터·검색어). 컴포넌트 상태에 복제하지 않는다 → UI3 충족
- 상단 헤더(로고 + 축소 검색창)는 홈을 제외한 모든 화면에 고정

---

## 4. 기능 명세

### F1. 홈 `/`

- 화면 중앙 큰 검색창(placeholder: "어떤 영화를 찾고 있나요? 예: 복수극인데 결말이 허무한 느와르"), Enter로 `/search?q=`
- 예시 쿼리 칩 5~6개 — `EvalRunner` 고정 쿼리에서 고른다 (프론트 상수)
- 장르 바로가기 칩 → `/explore?genre=`

### F2. 검색 결과 `/search`

- 결과 그리드: 포스터 카드 (포스터, 제목, 연도, 평점, 장르 최대 2개)
- **관련도 표시**: RRF 점수를 막대/점 3단계(높음·중간·낮음)로. 숫자 그대로는 의미 없으니 v1은 상대 등급만
- 필터 바 (§F4) — 변경 즉시 URL 갱신 → 재조회. 활성 필터는 칩으로 보이고 × 로 해제
- 결과 수 `limit` 기본 20, "더 보기"로 최대 50 (API 상한)
- 상태: 로딩(스켈레톤 카드) / 빈 결과("조건을 줄여보세요" + 활성 필터 해제 버튼) / 에러(재시도)

### F3. 영화 상세 `/movies/:id`

- 상단: 포스터 + 제목/원제, 연도·러닝타임·평점(투표 수), 장르 칩
- 줄거리 전문
- 감독, 주요 출연진(상위 5명, 배역명) — 이름 클릭 시 `/search?q=<현재 검색어>&director=` 또는 `/explore?cast=` 로 이동
- **"이 영화랑 비슷한"**: 유사 영화 카드 8~12개 가로 스크롤(모바일) / 그리드(데스크톱)
- 백드롭 이미지는 미니멀 방향에 맞춰 **쓰지 않는다** (v1)

### F4. 필터 (U3 전체)

| 필터 | UI | 쿼리 파라미터 | 백엔드 |
|---|---|---|---|
| 장르 | 칩 다중 선택 (v1은 단일 → 다중은 백엔드 `should` 필요, §5) | `genre` | 있음 (단일) |
| 연도 | 범위 슬라이더 또는 시작/끝 셀렉트 | `yearMin`, `yearMax` | `yearMin`만 있음 |
| 평점 | 최소값 셀렉트 (6+, 7+, 8+) | `ratingMin` | **없음** |
| 감독 | 자동완성 입력 | `director` | **없음** |
| 배우 | 자동완성 입력 | `cast` | **없음** |

- 모바일: 필터는 "필터" 버튼 → 하단 시트(Sheet)
- 감독·배우 필터는 payload의 상위 3명/상위 5명만 대상이다 (색인 규칙). UI 도움말에 명시

### F5. 탐색 `/explore`

- 검색어 없이 PG에서 조건 목록 조회: 장르 탭 + 정렬(평점순/최신순/인기순)
- 카드·필터 컴포넌트는 F2와 공유. 페이지네이션은 "더 보기"

---

## 5. API 계약 — 현재 vs 필요

현재 Kotlin API는 `GET /api/search`뿐이고, 응답에 UI에 필요한 필드가 빠져 있다. **UI 작업 전 백엔드 Phase(U-B)로 먼저 채운다.**

| # | 엔드포인트 | 상태 | 변경 |
|---|---|---|---|
| A1 | `GET /api/search` | 있음 | 응답에 `movieId`, `posterPath` 추가 (Qdrant payload에 `poster_path` 추가 → 재색인). 파라미터 `yearMax`, `ratingMin`, `director`, `cast` 추가 |
| A2 | `GET /api/movies/{id}` | **없음** | PG 조회: 메타 + 줄거리 + 장르 + 감독 + 출연진(상위 5, 배역) |
| A3 | `GET /api/movies/{id}/similar?limit=` | **없음** | Qdrant 포인트 id = `movie_id`이므로 해당 포인트 벡터로 query (자기 자신 제외). U2 |
| A4 | `GET /api/movies?genre=&sort=&page=` | **없음** | 탐색용 PG 목록 (F5) |
| A5 | `GET /api/genres` | **없음** | 장르 칩 목록 (`name_ko`) |
| A6 | `GET /api/people?q=&role=director\|cast` | **없음** | 감독·배우 자동완성 (PG `movie_director`/`movie_cast` 이름 검색, distinct, 상위 10) |

- 포스터 URL은 **TMDB 이미지 CDN**(`https://image.tmdb.org/t/p/w342{posterPath}`)을 프론트에서 조립한다. 크기: 카드 `w342`, 상세 `w500`. 포스터 없음 → 플레이스홀더 컴포넌트
- 계약 공유: PLAN.md 계획대로 **springdoc-openapi** → `openapi-typescript`로 TS 타입 생성. 수기 타입 금지
- CORS: 로컬은 Vite dev proxy, compose는 nginx가 `/api` → `api:8080` 프록시 → **같은 출처라 CORS 설정 불필요**

---

## 6. 디자인 시스템

### 6.1 원칙

1. **콘텐츠가 주인공** — 색은 포스터가 담당한다. UI 자체는 무채색 + 강조색 1개
2. **검색창이 가장 크다** — 모든 화면에서 검색으로 돌아가는 길이 1클릭 이내
3. **토큰만 쓴다** — 색·간격·반경·그림자·글자 크기는 토큰 이름으로만. 임의값(`[13px]`, `#333`) 금지 (UI6)
4. **여백으로 구분한다** — 테두리·그림자는 최소. 카드 구분은 간격, 필요할 때만 1px 보더

### 6.2 토큰

CSS 변수(`ui/src/styles/tokens.css`)가 원천이고 Tailwind v4 `@theme`에서 참조한다. shadcn 컴포넌트도 같은 변수명(`--background`, `--foreground`, `--primary` …)을 쓰므로 그대로 맞물린다.

**색 (라이트)** — 뉴트럴은 살짝 차가운 회색, 강조색은 하나

| 토큰 | 용도 | 값(초안) |
|---|---|---|
| `--background` | 페이지 배경 | `#FFFFFF` |
| `--surface` | 필터 바, 시트, 입력 배경 | `#F7F7F8` |
| `--foreground` | 본문 텍스트 | `#18181B` |
| `--muted-foreground` | 보조 텍스트(연도, 배역) | `#71717A` |
| `--border` | 구분선, 입력 테두리 | `#E4E4E7` |
| `--primary` | 강조(포커스 링, 활성 칩, 링크) | `#2563EB` 계열 — 확정은 U-D1 |
| `--primary-foreground` | 강조 위 텍스트 | `#FFFFFF` |
| `--rating` | 평점 별 | `#F59E0B` |
| `--destructive` | 에러 | `#DC2626` |

- 대비: 본문 텍스트/배경 4.5:1 이상 (WCAG AA). `--muted-foreground`도 흰 배경에서 AA 충족 확인
- 다크 모드 대비: 값은 `:root`에만 두고 컴포넌트는 변수만 참조 → 나중에 `[data-theme=dark]` 블록 추가로 끝나게

**타이포그래피** — 한글 가독성 우선, 폰트 1종

- 폰트: **Pretendard** (가변, 한글/라틴 모두). 숫자(연도·평점)는 `tabular-nums`
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
| `PosterImage` | 자체 | TMDB URL 조립, lazy load, 없음 → 제목 이니셜 플레이스홀더 |
| `RelevanceMeter` | 자체 | RRF 점수 → 3단계 표시 |
| `FilterBar` / `FilterSheet` | 자체 | 데스크톱 가로 바 / 모바일 하단 시트. 활성 필터 칩 + 전체 해제 |
| `MovieGrid` | 자체 | 반응형 열 수: 2(<640) / 3(≥640) / 4(≥1024) / 5(≥1280) |
| `SimilarRow` | 자체 | 유사 영화 — 모바일 가로 스크롤, 데스크톱 그리드 |
| `EmptyState`, `ErrorState` | 자체 | 아이콘 + 한 줄 설명 + 액션 버튼 |

- 아이콘: **lucide-react** (shadcn 기본)
- 컴포넌트 카탈로그: 별도 Storybook 없이 개발용 `/_ds` 라우트 한 장에 토큰·컴포넌트를 모아 본다 (빌드에서 제외)

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
├── index.html
├── vite.config.ts      dev proxy: /api → localhost:8080
├── Dockerfile          node 빌드 → nginx:alpine 정적 서빙 + /api 프록시
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
| 테스트 | Vitest + Testing Library (훅·컴포넌트), 시나리오는 수동 → 이후 Playwright | |

compose: `ui` 서비스 추가 (compose.local.yml에서 `5173:80` 또는 `3000:80` 노출), `depends_on: api`.

---

## 8. 단계별 계획

각 단계는 확인 항목을 통과해야 다음으로 넘어간다.

### Phase U-B. 백엔드 API 보강 (kotlin/)
- [ ] springdoc-openapi 추가, `/v3/api-docs` 노출
- [ ] Qdrant payload에 `poster_path` 추가 → 재색인. A1 응답에 `movieId`, `posterPath` 추가
- [ ] A1 필터 확장: `yearMax`, `ratingMin`, `director`, `cast`
- [ ] A2 상세, A3 유사, A4 목록, A5 장르, A6 사람 자동완성
- 확인: 각 엔드포인트 테스트(기존 컨벤션: WireMock/Testcontainers). EvalRunner 결과가 `baseline-eval.txt`와 동일 (필터 추가가 기본 검색을 바꾸지 않음)

### Phase U-0. 디자인 시스템 기반 (ui/)
- [ ] Vite + React + TS 스캐폴드, Tailwind v4, shadcn init
- [ ] `tokens.css` 작성 (§6.2), Pretendard 적용
- [ ] shadcn 컴포넌트 가져와 토큰 연결, 자체 컴포넌트 골격
- [ ] `/_ds` 카탈로그 페이지
- 확인: `/_ds`에서 모든 토큰·컴포넌트·상태(로딩/빈/에러)가 보인다. UI6 grep 통과

### Phase U-1. 검색 흐름 (F1, F2, F4)
- [ ] API 타입 생성 파이프라인 (`pnpm gen:api`)
- [ ] 홈, 검색 결과, 필터 바/시트, URL 상태 훅
- 확인: UI1, UI3, UI4, 시나리오 S-A·S-C·S-D

### Phase U-2. 상세 + 유사 영화 (F3)
- 확인: UI2, 시나리오 S-B

### Phase U-3. 탐색 (F5) + 배포 구성
- [ ] 탐색 화면
- [ ] `ui/Dockerfile` + nginx.conf, compose에 `ui` 서비스
- 확인: `docker compose ... up --build` 한 번으로 UI까지 뜬다. UI5, 시나리오 S-E

---

## 9. 열린 질문

| # | 질문 | 기본안 |
|---|---|---|
| U-D1 | 강조색 | 블루(`#2563EB` 계열). 영화 느낌을 원하면 딥 레드/앰버도 후보 — `/_ds`에서 비교 후 확정 |
| U-D2 | 장르 다중 선택 시 AND/OR | OR(`should`) — "스릴러 또는 범죄" 쪽이 탐색에 자연스러움 |
| U-D3 | 감독/배우 필터가 상위 3명/5명만 대상인 한계 | v1은 도움말로 명시. 필요하면 payload를 전체 출연진으로 확장(재색인) |
| U-D4 | 관련도 표시 방식 | RRF 점수 상대 3단계. 절대 점수는 개발용 `?debug=1`에서만 숫자로 |
| U-D5 | 탐색 "인기순" 정렬 기준 | PG에 popularity 컬럼이 없음 → `vote_count` 대체, 또는 V2 마이그레이션으로 추가 |
| U-D6 | 데이터 규모 | 로컬 ~40건·운영 ~1000건이면 탐색/필터 결과가 자주 비게 된다. UI 확인 전 로컬 `TMDB_PAGES`를 늘릴지 |
