FROM docker.io/library/python:3.11-slim-bookworm

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes \
        --no-install-recommends \
        ca-certificates \
        ffmpeg \
        libgomp1 \
        tini \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app \
        --home-dir /home/app --create-home --shell /usr/sbin/nologin app

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1 \
    PORT=8000 \
    HF_HOME=/home/app/.cache/huggingface \
    XDG_CACHE_HOME=/home/app/.cache \
    RDS_CA_BUNDLE=/app/certs/rds-ca-bundle.pem

COPY requirements.txt ./requirements.txt

RUN python -m pip install --requirement requirements.txt

COPY --chown=app:app app ./app
COPY --chown=app:app --chmod=0444 \
    certs/ap-northeast-2-bundle.pem \
    /app/certs/rds-ca-bundle.pem

RUN chmod 0755 /app/certs \
    && mkdir -p /home/app/.cache/huggingface \
    && chown -R app:app /home/app/.cache

USER app

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3).read()"]

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--no-server-header"]
