"""cineseek 임베딩 서버 — bge-m3 dense+sparse. 계약은 README.md 참고.

실행: uv run uvicorn app:app --port 8001
  GET  /health  — 모델 로드 완료 후 200 (compose healthcheck용)
  POST /embed   — 텍스트 1~64개 → dense(1024) + sparse
"""
import os
from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI
from pydantic import BaseModel, Field

import embed

load_dotenv()

EMBED_BATCH_LIMIT = 64  # 계약: 1회 최대 64개. 나눠 보내는 책임은 호출 쪽(Kotlin)


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=EMBED_BATCH_LIMIT)


class SparseDto(BaseModel):
    indices: list[int]
    values: list[float]


class EmbedItem(BaseModel):
    dense: list[float]
    sparse: SparseDto


class EmbedResponse(BaseModel):
    model: str
    items: list[EmbedItem]


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 시작 시 모델 미리 로드 — /health가 200이면 준비 완료
    embed.get_model()
    yield


app = FastAPI(title="cineseek-embed", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/embed")
def embed_texts(req: EmbedRequest) -> EmbedResponse:
    out = embed.encode(req.texts)
    items = [
        EmbedItem(
            dense=d.tolist(),
            sparse=SparseDto(
                indices=[int(k) for k in w],
                values=[float(v) for v in w.values()],
            ),
        )
        for d, w in zip(out["dense_vecs"], out["lexical_weights"])
    ]
    return EmbedResponse(model=os.environ["EMBED_MODEL"], items=items)
