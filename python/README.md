# python/ — cineseek 임베딩 서비스

bge-m3 dense+sparse 임베딩만 제공한다. 수집·검색·평가는 `kotlin/`에 있다.
(`search.py`, `pipeline.py`, `eval.py`는 Kotlin 이식 기준 비교용 — Phase 5에 삭제)

## 실행

```bash
uv run uvicorn app:app --port 8001
# 또는 compose: docker compose -f compose.yml -f compose.local.yml up embed
```

- `EMBED_DEVICE`: `cpu` | `mps` | `cuda` — 컨테이너는 compose가 `cpu`로 고정
- `EMBED_MODEL`: 기본 `BAAI/bge-m3`

## 계약

Kotlin(api)과 맞물리는 유일한 지점. 변경 시 양쪽을 함께 고친다.

### POST /embed

요청:

```json
{ "texts": ["감옥에서 탈출하는 이야기"] }
```

- `texts`는 1~64개. **65개 이상은 422로 거절** — 나눠 보내는 책임은 호출 쪽(Kotlin)

응답:

```json
{
  "model": "BAAI/bge-m3",
  "items": [
    {
      "dense": [0.0123],
      "sparse": { "indices": [1234, 88], "values": [0.21, 0.05] }
    }
  ]
}
```

- `dense`: 1024차원, L2 정규화
- `sparse.indices`: 정수(토큰 id), `sparse.values`: 실수

### GET /health

모델 로드가 끝나면 200. compose healthcheck가 이걸 본다.
