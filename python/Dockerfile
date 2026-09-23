# cineseek 임베딩 서버 — bge-m3, CPU 고정 (Docker는 MPS 사용 불가)
FROM python:3.12-slim

COPY --from=ghcr.io/astral-sh/uv:0.12.16 /uv /uvx /bin/

WORKDIR /app

# 의존성만 먼저 설치해 레이어 캐시 (소스 변경에도 재설치 안 함)
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-install-project

COPY app.py embed.py ./

# HuggingFace 모델 캐시는 named volume으로 (이미지에 넣지 않는다)
ENV HF_HOME=/models \
    EMBED_DEVICE=cpu

EXPOSE 8001
CMD ["uv", "run", "--no-sync", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]
