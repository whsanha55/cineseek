# 운영 가이드

## 서버 구성

서버는 `compose.yml` + `compose.prod.yml` 조합으로 돈다. 로컬과 다른 점은 다음과 같다.

- postgres는 compose로 띄우지 않고, 외부 네트워크 `infra`의 공용 postgres에 붙는다
- UI만 `127.0.0.1:20040`으로 노출한다. 앞단의 Caddy가 외부 요청을 받아 넘긴다
- `prod` profile로 실행한다. TMDB 수집 페이지 수가 크고(기준별 250페이지), CPU 임베딩에 맞춰 배치 크기와 타임아웃을 조정한다
- 상시 수집 스케줄러를 켠다 (`CINESEEK_COLLECT_ENABLED=true`)

## 배포

```bash
./deploy.sh            # 전체 서비스
./deploy.sh api ui     # 지정한 서비스만 — 의존 서비스는 다시 만들지 않는다 (embed 모델 재로딩 방지)
```

빌드·기동 후 UI(nginx)를 거쳐 `/cineseek/genres`가 응답할 때까지 최대 120초 기다린다. 실패하면 최근 로그 30줄을 출력하고 종료 코드 1을 낸다.

## 상시 수집 스케줄러

재색인 배치(`--cineseek.job=reindex`)와 별개로, api 서비스 안에서 수집 스케줄러가 계속 돈다. 기본값은 꺼져 있고 서버에서만 켠다.

### 작업 큐

모든 수집은 PG `collect_task` 테이블의 작업으로 실행한다. `UNIQUE(task_type, payload_key)`라서 같은 작업을 여러 번 넣어도 한 번만 처리된다.

| 작업 | 내용 |
|---|---|
| 전 세계 discover | 3가지 기준으로 TMDB discover |
| 한국 영화 discover | 국가·언어 조건을 합쳐 연도 구간별로 조회. 투표 수 하한이 없다. 페이지 상한에 닿으면 구간을 반으로 나눈다 |
| 영화 상세 | 줄거리가 없어도 저장한다 |
| 배우 필모그래피 | `/person/{id}/movie_credits`. 없는 영화는 한 단계만 더 확장한다 |

### 수집 모드

**초기 집중 수집 (BOOTSTRAP)**

- 30분마다 틱이 돈다. 틱마다 `batch-size`만큼 작업을 가져와(lease 잠금으로 중복 실행 방지) `max-runtime` 안에 처리한다
- 마지막 페이지를 체크포인트로 남기므로, 중간에 멈춰도 다음 틱이 이어서 한다
- 429와 일시적인 5xx는 `Retry-After`와 지수 백오프로 다시 시도한다. 404는 영구 실패로 기록한다

**일일 갱신 (DAILY)**

- 초기 작업이 모두 끝나면 자동으로 넘어간다
- 매일 04:00(Asia/Seoul)에 신작, 필모그래피가 오래된 배우, 줄거리가 빠진 영화, 재시도를 다 쓴 실패 작업을 증분 수집한다

두 모드 모두에서, 사용자가 배우 필모그래피를 조회하면 그 배우를 우선 수집한다. 이 작업은 정기 틱과 별도로 10초마다 도는 사용자 요청 틱(`UserRequestScheduler`)이 처리하고, 받은 인물의 출생지·소개 번역도 바로 시작한다. 분위기 태깅도 틱마다 최대 50편씩 진행하고, 태깅한 영화는 같은 틱에 검색 색인에 반영한다. LLM 콘텐츠 필터에 걸리거나 빈 결과가 나온 영화는 `movie.mood_failed_model`에 태깅 버전을 남기고, 태깅 버전이 바뀔 때까지 다시 시도하지 않는다. 바로 다시 시도하려면 그 값을 `NULL`로 지운다.

인물 번역도 틱마다 최대 20명씩 진행한다. 출생지는 항상, 소개는 TMDB에 한국어 소개가 없을 때만 영어 소개를 번역한다. 영어 원문은 그대로 두고 번역문은 `person.place_of_birth_ko`·`biography_ko`(`biography_ko_translated = true`)에 따로 저장한다. 영구 실패는 `person.translation_failed_model`에 번역 버전을 남기는 방식으로 분위기 태깅과 같게 다룬다.

### 수집 보고

```bash
docker compose -f compose.yml -f compose.prod.yml run --rm api --cineseek.job=collect-report
```

현재 모드, 작업 유형별 상태, 연도별 한국 영화 수, 줄거리 누락 수, 시드 배우 10명의 필모그래피 확보율, PG 대비 색인이 안 된 영화 수를 보여준다. 같은 지표가 매 틱 로그에도 남는다.

### payload 동기화

검색은 Qdrant payload의 `mood_tags`로 분위기 태그 후보 목록을 만들고, `release_date`로 최근 개봉작을 투표 수 하한에서 뺀다. 이 필드가 생기기 전에 색인된 포인트는 값이 없으므로, 그 코드를 배포한 뒤 한 번 돌린다. 임베딩 없이 두 필드만 덮어쓰고 다른 payload 키는 그대로 둔다. 다시 돌려도 된다.

```bash
docker compose -f compose.yml -f compose.prod.yml run --rm --no-deps api --cineseek.job=payload-sync
```

로그 마지막 줄에 PG 영화 수, 태그가 있는 영화 수, 개봉일이 있는 영화 수, 갱신한 포인트 수가 찍힌다. 이후 새로 수집·태깅·색인되는 영화는 색인할 때 두 필드가 함께 들어간다.

### 인물 상세 백필

정기 수집은 영화의 출연진(`movie_cast`)만 채우고 인물 정보(`person`)는 인물 페이지를 열 때 만든다. 이 작업은 출연작이 많은 배우의 생몰일·출생지·소개·외부 링크를 미리 받아 두고, 출생지와 영어 소개를 한국어로 번역한다.

```bash
docker compose -f compose.yml -f compose.prod.yml run -d --rm api --cineseek.job=person-details   # --min-works=3 처럼 기준을 바꿀 수 있다
docker logs -f <컨테이너>   # "인물 상세 진행", "인물 번역 진행" 로그로 진행률을 본다
```

- 대상은 출연 N편(기본 5) 이상이면서 아직 `person` 행이 없는 배우다. 이미 있는 배우는 건너뛰므로 중단돼도 다시 돌리면 이어서 한다
- 번역은 상세 수집이 끝난 뒤 번역이 남은 인물 전체를 200명씩 처리한다. LLM 호출이라 수천 명이면 몇 시간 걸린다. 실패한 인물은 수집 틱이 다시 번역한다
- 필모그래피는 받지 않는다(`filmo_checked_at`은 비워 둔다). 인물 페이지를 처음 열 때 기존처럼 필모 수집이 돈다

### 설정

`kotlin/src/main/resources/application.yml`의 `cineseek.collect.*`에서 조정한다. 추가 비밀값은 없고 TMDB 토큰만 쓴다.

| 키 | 기본값 | 설명 |
|---|---|---|
| `enabled` | `false` | 스케줄러 켜기 (`CINESEEK_COLLECT_ENABLED`) |
| `interval` | `30m` | 초기 수집 틱 주기 |
| `batch-size` | `100` | 틱당 최대 작업 수 |
| `max-runtime` | `25m` | 틱 하나의 최대 실행 시간 |
| `concurrency` | `4` | 영화 상세 수집 동시성 |
| `kr.years-back` | `30` | 한국 영화 수집 연도 범위 |
| `seed-person-ids` | 배우 10명 | 우선 수집할 배우의 TMDB id (`CINESEEK_COLLECT_SEED_PERSON_IDS`, 빈 값이면 끈다) |
| `daily-cron` | `0 0 4 * * *` | 일일 갱신 시각 |
