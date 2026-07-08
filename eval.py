"""cineseek 평가 하니스 — 고정 쿼리 10종, hybrid(dense+sparse RRF).

회귀감지 기준점. 모델/임베딩/검색 방식 변경 전후에 같은 쿼리로 비교.
현재: dense+sparse hybrid (RRF).
"""
import os

from dotenv import load_dotenv
from qdrant_client import QdrantClient
from qdrant_client.http.models import Fusion, FusionQuery, Prefetch

import embed

load_dotenv()
COLLECTION = os.environ["QDRANT_COLLECTION"]

QUERIES = [
    "감옥에서 탈출하는 이야기",
    "가족을 지키려는 아버지의 사투",
    "피의 복수극",
    "우주 정거장에서 벌어지는 재난",
    "사랑과 배신의 로맨스",
    "연쇄살인범을 쫓는 형사",
    "전쟁 속 전우애와 희생",
    "밀실에서 일어난 미스터리",
    "초능력 히어로들의 세계를 지키는 전투",
    "시간을 거슬러 가는 모험",
]


def main() -> None:
    qc = QdrantClient(url=os.environ["QDRANT_URL"])
    out = embed.encode(QUERIES)
    dense_all = out["dense_vecs"]
    sparse_all = out["lexical_weights"]

    for i, q in enumerate(QUERIES):
        print(f"\n▶ {q}")
        dense = dense_all[i].tolist()
        sparse = embed.to_sparse(sparse_all[i])
        res = qc.query_points(
            COLLECTION,
            prefetch=[
                Prefetch(query=dense, using="dense", limit=20),
                Prefetch(query=sparse, using="sparse", limit=20),
            ],
            query=FusionQuery(fusion=Fusion.RRF),
            limit=5,
        )
        for j, h in enumerate(res.points, 1):
            p = h.payload or {}
            print(
                f"  {j}. {p.get('title')} ({p.get('release_year')})  "
                f"score={h.score:.3f}  genres={p.get('genres')}"
            )


if __name__ == "__main__":
    main()
