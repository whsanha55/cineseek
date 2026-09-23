# kotlin/ 컨벤션

확정된 컨벤션. 새 코드에 적용하고, 변경이 필요하면 이 문서를 먼저 고친다.

## JPA 엔티티

- 일반 `class`를 쓴다 (`data class` ❌ — equals/hashCode가 지연 로딩과 충돌). PK는 `val`, 가변 필드는 `var`
- 설명 주석은 KDoc(`/** ... */`). `//` 한 줄 주석은 쓰지 않는다
- `@Table(name)`은 항상 명시
- 컬럼명은 naming strategy 자동 변환(camelCase → snake_case)에 맡긴다. `@Column(name=...)`은 자동 변환이 안 되는 특이사항(예약어 등)만
- NOT NULL 컬럼에만 `@Column(nullable = false)`
- UNIQUE 제약도 `@Column(unique = true)`로 표시
- nullable 컬럼 → `T? = null` 생성자 기본값
- 자동 생성 PK: `@GeneratedValue(strategy = GenerationType.IDENTITY)` + `val id: Long = 0`
- 타입 매핑: TIMESTAMPTZ → `Instant`, DATE → `LocalDate`, NUMERIC → `BigDecimal`
- 생성/수정 시각: `@CreationTimestamp`/`@UpdateTimestamp` + `var createdAt: Instant? = null` (Kotlin 타입은 nullable, Hibernate가 flush 때 채움. Spring Data auditing 안 씀)
- 들여쓰기는 탭, 주석·문서는 한국어

### 연관관계

- 컬렉션은 `MutableSet`
- **`cascade`·`orphanRemoval` 일절 안 씀** — 저장·삭제는 서비스에서 명시적으로 한다 (암시 동작은 헷갈린다). movie 삭제 시 자식은 DB의 `ON DELETE CASCADE`가 처리
- **자식 엔티티는 부모 참조를 갖지 않는다** — 조회·삭제는 Repository의 movieId 파생 쿼리(`findAllByMovieId` 등)로 명시적으로. 부모 참조를 만들면 movie_id 이중 매핑(Hibernate `DuplicateMappingException`)이나 은밀한 동기화 문제가 생긴다
- 복합키는 `@IdClass` + `data class`(Serializable, 기본값 0)를 해당 엔티티 파일 안에 배치. `@EmbeddedId`·`@MapsId`는 안 씀 (부모 참조가 필요해져서)
- `@JoinTable`의 조인 컬럼(`@JoinColumn`)은 자동 변환 규칙의 예외로 항상 명시 (조인 컬럼 추론 오류는 조용히 잘못된 컬럼을 만든다)

## 테스트

- 테스트 클래스명은 대상 + `Test` (`MovieRepositoryTest`). 테스트 메서드는 백틱 한국어 문장 (`fun \`영화 저장 후 tmdbId로 조회\``)
- DB가 필요한 테스트는 Testcontainers PG + `@ServiceConnection` — 로컬 PG 의존 없음
- `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` → Flyway가 스키마 만들고 `ddl-auto: validate`가 엔티티와 대조
- assertion은 kotlin.test (별도 assertion 라이브러리 안 씀)

## 패키지 구조

- 도메인 우선 + 도메인 안 계층 분리: `movie/domain`(엔티티), `movie/repository`(리포지토리), `movie/service`(서비스)
- 최상위 도메인: `config`, `embedding`, `tmdb`, `movie`, `index`, `search`, `job`, `eval` (PLAN §5)
- 외부 HTTP는 `config/HttpClients.kt`의 `http1RestClient()`로 구성 (HTTP/1.1 고정)

## Repository

- `JpaRepository` 상속, 엔티티별 파일 (파일명 = 클래스명)
- 파생 쿼리 우선. `@Query`는 파생으로 못 쓸 때만
- import는 명시적으로 (와일드카드 ❌)

## 일반 Kotlin

- DTO는 `data class` (엔티티만 일반 class 예외). 와이어 DTO는 사용 파일 안에서 `private`
- 외부 HTTP 클라이언트는 `RestClient.builder()` 직접 구성 — Boot 4는 `RestClient.Builder` 자동구성 빈이 없다. 대상이 uvicorn(HTTP/1.1)이면 HTTP/1.1로 고정한다 (h2c 시도가 RST_STREAM을 유발)
- 배치 실행(`--cineseek.job=eval` 등)은 `@ConditionalOnProperty(prefix="cineseek", name=["job"])` + `ApplicationRunner` + `exitProcess(0)`
- Qdrant는 gRPC(`io.qdrant:client`)만 사용. 주의: 클라이언트 POM이 grpc 의존성을 runtime scope로 선언하므로 `grpc-protobuf`·`grpc-stub`을 implementation으로 직접 추가해야 컴파일된다
