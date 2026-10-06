"""bge-m3 dense+sparse 임베딩 래퍼 (FlagEmbedding).

임베딩 서버(app.py) 전용. CPU 기반(use_fp16=False).
"""
import os

from FlagEmbedding import BGEM3FlagModel

# ponytail-audit #15: load_dotenv() 제거 — app.py가 이미 호출하고, 환경변수는 get_model() 호출 시점에 읽는다

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
