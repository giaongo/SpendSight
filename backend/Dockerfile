# SpendSight API image (OPS-02).
# Plain HTTP on 0.0.0.0:8000; Caddy terminates TLS in front (OPS-03).
# One worker only: Aino's demo ledger (effort setting, refiled payments) lives in memory.

FROM python:3.12-slim

COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /bin/uv

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PROJECT_ENVIRONMENT=/opt/venv \
    PATH=/opt/venv/bin:$PATH \
    JUNCTION_DIR=/data/junction \
    SPENDSIGHT_CACHE_DIR=/data/cache

WORKDIR /srv

# Dependencies first, so code changes do not reinstall them.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

COPY app ./app
RUN uv sync --frozen --no-dev --no-editable

# UID 1000 matches the default user on the Ubuntu host, so /data stays writable.
RUN groupadd --system --gid 1000 spendsight \
 && useradd --system --uid 1000 --gid spendsight --no-create-home spendsight \
 && mkdir -p /data/junction /data/cache \
 && chown -R spendsight:spendsight /data
USER spendsight

EXPOSE 8000

# The index build on a cold /data/cache can take minutes, hence the long start period.
HEALTHCHECK --interval=30s --timeout=5s --start-period=600s --retries=3 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/healthz', timeout=4).status == 200 else 1)"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--proxy-headers", "--forwarded-allow-ips", "*"]
