FROM node:22-slim AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/index.html ./
COPY frontend/src ./src
# "/" makes the frontend call the API on its own origin.
RUN VITE_API_URL=/ npm run build

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
COPY --from=web /web/dist ./frontend/dist
USER app
ENV PORT=8000
EXPOSE 8000
CMD ["sh", "-c", "uvicorn kashiai.api:app --host 0.0.0.0 --port ${PORT}"]
