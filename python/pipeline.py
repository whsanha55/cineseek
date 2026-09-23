"""cineseek 파이프라인 — TMDB 대량 수집 → PG(SoT) → bge-m3 dense+sparse → Qdrant.

PG=진실의 원천, Qdrant=파생 인덱스(재생성 가능). 수집은 discover popularity.desc 기반.
범위: TMDB_PAGES 만큼 discover 페이지(기본 50 → 약 1000건 후보).
"""
import os
import sys
from concurrent.futures import ThreadPoolExecutor

import httpx
import psycopg
from dotenv import load_dotenv
from qdrant_client import QdrantClient
from qdrant_client.http.models import (
    Distance,
    PointStruct,
    SparseVectorParams,
    VectorParams,
)

import embed

load_dotenv()

TMDB_TOKEN = os.environ["TMDB_ACCESS_TOKEN"]
TMDB_BASE = "https://api.themoviedb.org/3"
PG_DSN = (
    f"host={os.environ['PG_HOST']} port={os.environ['PG_PORT']} "
    f"dbname={os.environ['PG_DB']} user={os.environ['PG_USER']} "
    f"password={os.environ['PG_PASSWORD']}"
)
QDRANT_URL = os.environ["QDRANT_URL"]
COLLECTION = os.environ["QDRANT_COLLECTION"]
TARGET_PAGES = int(os.environ.get("TMDB_PAGES", "50"))  # discover 페이지 수 (페이지당 20건)
WORKERS = int(os.environ.get("TMDB_WORKERS", "10"))  # detail 병렬 fetch


def tmdb_get(path: str, **params) -> httpx.Response:
    """TMDB GET (v4 Bearer 인증, ko-KR 고정)."""
    return httpx.get(
        f"{TMDB_BASE}{path}",
        params={"language": "ko-KR", **params},
        headers={"Authorization": f"Bearer {TMDB_TOKEN}"},
        timeout=20,
    )


def fetch_ids() -> list[int]:
    """discover popularity.desc 에서 tmdb_id 수집(페이지당 20건)."""
    ids: list[int] = []
    for page in range(1, TARGET_PAGES + 1):
        r = tmdb_get(
            "/discover/movie",
            page=page,
            sort_by="popularity.desc",
            **{"vote_count.gte": 20},
        )
        r.raise_for_status()
        results = r.json().get("results", [])
        if not results:
            break
        ids += [m["id"] for m in results]
    return ids


def fetch_detail(mid: int) -> dict | None:
    """상세 + credits. overview 없으면 None(SKIP)."""
    detail = tmdb_get(f"/movie/{mid}").json()
    if not detail.get("overview"):
        return None
    detail["_credits"] = tmdb_get(f"/movie/{mid}/credits").json()
    return detail


def fetch_detail_safe(mid: int) -> dict | None:
    """fetch_detail 병렬용 래퍼(단건 실패 스킵)."""
    try:
        return fetch_detail(mid)
    except Exception as e:
        print(f"  tmdb_id={mid} 스킵: {e}")
        return None


def upsert_pg(conn, m: dict) -> int:
    """movie + genre/director/cast upsert(idempotent). movie_id 반환."""
    rd = m.get("release_date") or ""
    params = dict(
        tmdb_id=m["id"],
        title=m.get("title") or "",
        original_title=m.get("original_title"),
        overview=m["overview"],
        release_date=rd or None,
        release_year=int(rd[:4]) if len(rd) >= 4 else None,
        runtime=m.get("runtime"),
        vote_average=m.get("vote_average"),
        vote_count=m.get("vote_count"),
        poster_path=m.get("poster_path"),
        backdrop_path=m.get("backdrop_path"),
        original_language=m.get("original_language"),
    )
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO movie (tmdb_id, title, original_title, overview, release_date, release_year,
                               runtime, vote_average, vote_count, poster_path, backdrop_path,
                               original_language, overview_updated_at)
            VALUES (%(tmdb_id)s, %(title)s, %(original_title)s, %(overview)s, %(release_date)s,
                    %(release_year)s, %(runtime)s, %(vote_average)s, %(vote_count)s,
                    %(poster_path)s, %(backdrop_path)s, %(original_language)s, now())
            ON CONFLICT (tmdb_id) DO UPDATE SET
                title=EXCLUDED.title, overview=EXCLUDED.overview,
                release_date=EXCLUDED.release_date, release_year=EXCLUDED.release_year,
                runtime=EXCLUDED.runtime, vote_average=EXCLUDED.vote_average,
                vote_count=EXCLUDED.vote_count, poster_path=EXCLUDED.poster_path,
                overview_updated_at=now()
            RETURNING movie_id
            """,
            params,
        )
        movie_id = cur.fetchone()[0]

        for g in m.get("genres", []):
            cur.execute(
                "INSERT INTO genre(genre_id, name) VALUES (%s,%s) ON CONFLICT DO NOTHING",
                (g["id"], g["name"]),
            )
            cur.execute(
                "INSERT INTO movie_genre(movie_id, genre_id) VALUES (%s,%s) ON CONFLICT DO NOTHING",
                (movie_id, g["id"]),
            )

        for c in m["_credits"].get("crew", []):
            if c.get("job") == "Director":
                cur.execute(
                    "INSERT INTO movie_director(movie_id, person_id, name) VALUES (%s,%s,%s) ON CONFLICT DO NOTHING",
                    (movie_id, c["id"], c["name"]),
                )

        for c in m["_credits"].get("cast", [])[:10]:
            cur.execute(
                """INSERT INTO movie_cast(movie_id, person_id, name, character, cast_order)
                   VALUES (%s,%s,%s,%s,%s) ON CONFLICT DO NOTHING""",
                (movie_id, c["id"], c["name"], c.get("character"), c.get("order")),
            )
    return movie_id


def build_payload(m: dict) -> dict:
    """Qdrant payload(하이브리드 필터 대상 사본)."""
    crew = m["_credits"].get("crew", [])
    cast = m["_credits"].get("cast", [])
    return {
        "title": m.get("title"),
        "release_year": int(m["release_date"][:4]) if m.get("release_date") else None,
        "rating": m.get("vote_average"),
        "genres": [g["name"] for g in m.get("genres", [])],
        "directors": [c["name"] for c in crew if c.get("job") == "Director"][:3],
        "cast": [c["name"] for c in cast[:5]],
    }


def ensure_collection(qc: QdrantClient) -> None:
    if qc.collection_exists(COLLECTION):
        qc.delete_collection(COLLECTION)  # PG=SoT → 재색인 시 재생성
    qc.create_collection(
        COLLECTION,
        vectors_config={"dense": VectorParams(size=1024, distance=Distance.COSINE)},
        sparse_vectors_config={"sparse": SparseVectorParams()},
    )
    print(f"컬렉션 재생성(dense+sparse): {COLLECTION}")


def main() -> None:
    ids = fetch_ids()
    print(f"TMDB 후보: {len(ids)}개 id (discover {TARGET_PAGES}페이지)")

    print(f"상세 수집(병렬 {WORKERS})...")
    with ThreadPoolExecutor(max_workers=WORKERS) as ex:
        movies = [m for m in ex.map(fetch_detail_safe, ids) if m]
    print(f"overview 있는 영화: {len(movies)}건")

    conn = psycopg.connect(PG_DSN)
    qc = QdrantClient(url=QDRANT_URL)
    ensure_collection(qc)

    stored: list[tuple[int, str, dict]] = []
    for m in movies:
        movie_id = upsert_pg(conn, m)
        stored.append((movie_id, m["overview"], build_payload(m)))
    conn.commit()
    print(f"PG 적재: {len(stored)}건")

    print("임베딩(bge-m3 dense+sparse)...")
    overviews = [s[1] for s in stored]
    out = embed.encode(overviews)
    dense = out["dense_vecs"]
    sparse = out["lexical_weights"]

    points = [
        PointStruct(
            id=s[0],
            vector={"dense": dense[i].tolist(), "sparse": embed.to_sparse(sparse[i])},
            payload=s[2],
        )
        for i, s in enumerate(stored)
    ]
    # ponytail: 대량 upsert은 배치 분할(Qdrant 단일 요청 한계 방지)
    BATCH = 256
    for i in range(0, len(points), BATCH):
        qc.upsert(collection_name=COLLECTION, points=points[i : i + BATCH])
    print(f"Qdrant upsert: {len(points)}건(dense+sparse) → 컬렉션 {COLLECTION}")


if __name__ == "__main__":
    sys.exit(main())
