"""cineseek 검색 — hybrid(dense+sparse RRF) + payload 필터.

CLI: uv run python search.py "복수" --genre 범죄 --year-min 2000
search_movies()는 app.py(API)와 공유.
"""
import argparse
import os

from dotenv import load_dotenv
from qdrant_client import QdrantClient
from qdrant_client.http.models import (
    FieldCondition,
    Filter,
    Fusion,
    FusionQuery,
    MatchValue,
    Prefetch,
    Range,
)

import embed

load_dotenv()
COLLECTION = os.environ["QDRANT_COLLECTION"]


def build_filter(genre: str | None, year_min: int | None) -> Filter | None:
    must = []
    if genre:
        must.append(FieldCondition(key="genres", match=MatchValue(value=genre)))
    if year_min:
        must.append(FieldCondition(key="release_year", range=Range(gte=year_min)))
    return Filter(must=must) if must else None


def search_movies(
    query: str, genre: str | None = None, year_min: int | None = None, limit: int = 5
) -> list[dict]:
    """hybrid 검색 → 결과 dict 리스트."""
    qc = QdrantClient(url=os.environ["QDRANT_URL"])
    flt = build_filter(genre, year_min)

    out = embed.encode([query])
    dense = out["dense_vecs"][0].tolist()
    sparse = embed.to_sparse(out["lexical_weights"][0])

    res = qc.query_points(
        COLLECTION,
        prefetch=[
            Prefetch(query=dense, using="dense", limit=20, filter=flt),
            Prefetch(query=sparse, using="sparse", limit=20, filter=flt),
        ],
        query=FusionQuery(fusion=Fusion.RRF),
        limit=limit,
        with_payload=True,
    )

    return [
        {
            "title": (p or {}).get("title"),
            "release_year": (p or {}).get("release_year"),
            "rating": (p or {}).get("rating"),
            "score": h.score,
            "genres": (p or {}).get("genres"),
            "directors": (p or {}).get("directors"),
        }
        for h in res.points
        if (p := h.payload)
    ]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("query", nargs="*", default=["복수와 구원의 이야기"])
    ap.add_argument("--genre", help="장르 필터(예: 범죄)")
    ap.add_argument("--year-min", type=int, help="개봉연도 하한(예: 2000)")
    ap.add_argument("--limit", type=int, default=5)
    args = ap.parse_args()

    query = " ".join(args.query)
    flt = build_filter(args.genre, args.year_min)
    print(f"쿼리: {query}", end="")
    if flt:
        print(f"  | 필터: genre={args.genre} year>={args.year_min}", end="")
    print("\n")

    for i, r in enumerate(search_movies(query, args.genre, args.year_min, args.limit), 1):
        print(f"{i}. {r['title']} ({r['release_year']})  score={r['score']:.3f}  rating={r['rating']}")
        print(f"   genres={r['genres']}  directors={r['directors']}")


if __name__ == "__main__":
    main()
