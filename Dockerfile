FROM python:3.11-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 KASHI_PROVIDER=extractive KASHI_EMBEDDINGS=none
COPY pyproject.toml ./
COPY requirements-tested.txt ./
COPY kashiai ./kashiai
ARG INSTALL_EXTRAS=""
RUN if [ -n "$INSTALL_EXTRAS" ]; then pip install --no-cache-dir ".[${INSTALL_EXTRAS}]"; \
    else pip install --no-cache-dir -c requirements-tested.txt .; fi \
    && useradd --create-home --uid 1000 app
COPY data ./data
COPY news.json ./news.json
USER app
ENV PORT=8000
EXPOSE 8000
CMD ["sh", "-c", "uvicorn kashiai.api:app --host 0.0.0.0 --port ${PORT}"]
