FROM python:3.12.13-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY server.py .
COPY pios_mcp/ ./pios_mcp/

# 비루트 사용자로 실행
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin pios \
    && chown -R pios:pios /app
USER 10001:10001

EXPOSE 8001

# 무인증 헬스 엔드포인트 (데이터 없음). Host 검증 대상이 아니므로 localhost로 호출 가능
HEALTHCHECK --interval=15s --timeout=5s --start-period=15s --retries=5 \
    CMD ["python", "-c", "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8001/healthz', timeout=3).status == 200 else 1)"]

CMD ["python", "server.py", "--transport", "streamable-http", "--host", "0.0.0.0", "--port", "8001"]
