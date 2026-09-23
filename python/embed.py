"""bge-m3 dense+sparse 임베딩 래퍼 (FlagEmbedding).

pipeline/search/eval 공통 사용. CPU 기반(use_fp16=False).
"""
import os

from dotenv import load_dotenv
from FlagEmbedding import BGEM3FlagModel
from qdrant_client.http.models import SparseVector

load_dotenv()

_model: BGEM3FlagModel | None = None


def get_model() -> BGEM3FlagModel:
    global _model
    if _model is None:
        # EMBED_DEVICE=cpu|mps|cuda — 컨테이너는 compose가 cpu로 고정
        device = os.environ.get("EMBED_DEVICE", "cpu")
        _model = BGEM3FlagModel(os.environ["EMBED_MODEL"], use_fp16=False, devices=device)
    return _model


def encode(texts: list[str]) -> dict:
    """dense(1024, L2 정규화) + sparse(lexical weights) 반환."""
    return get_model().encode(
        list(texts),
        batch_size=16,
        return_dense=True,
        return_sparse=True,
        return_colbert_vecs=False,
    )


def to_sparse(weights: dict) -> SparseVector:
    """lexical_weights dict → Qdrant SparseVector."""
    return SparseVector(
        indices=[int(k) for k in weights],
        values=[float(v) for v in weights.values()],
    )
