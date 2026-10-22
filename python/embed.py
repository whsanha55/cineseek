"""bge-m3 dense+sparse 임베딩 래퍼 (FlagEmbedding).

임베딩 서버(app.py) 전용. CPU 기반(use_fp16=False).
"""
import os

from FlagEmbedding import BGEM3FlagModel

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


def tokens(ids: list[int]) -> list[str]:
    """sparse 토큰 id → 토큰 문자열 (sentencepiece 원형, 단어 시작은 "▁" 접두)."""
    return get_model().tokenizer.convert_ids_to_tokens(ids)
