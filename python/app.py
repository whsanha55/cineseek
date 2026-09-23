"""cineseek FastAPI — 시맨틱 검색 API.

실행: uv run uvicorn app:app --port 8001 --reload
  GET /health
  GET /search?q=복수&genre=범죄&year_min=2000&limit=5
포트 8000은 reins가 사용 → cineseek는 8001.
"""
from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI, Query

import embed
from search import search_movies

load_dotenv()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 시작 시 임베딩 모델 미리 로드(첫 요청 지연 방지)
    embed.get_model()
    yield


app = FastAPI(title="cineseek", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/search")
def search(
    q: str = Query(..., description="검색 쿼리"),
    genre: str | None = Query(None, description="장르 필터(예: 범죄)"),
    year_min: int | None = Query(None, description="개봉연도 하한"),
    limit: int = Query(5, ge=1, le=50),
):
    results = search_movies(q, genre, year_min, limit)
    return {"query": q, "filter": {"genre": genre, "year_min": year_min}, "count": len(results), "results": results}
