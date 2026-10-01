import asyncio
import os
import threading
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from .agent import Assistant
from .config import Settings
from .schema import Answer, AskRequest
from .service import Corpus


def create_app(settings=None, corpus=None):
    settings = settings or Settings()

    @asynccontextmanager
    async def lifespan(app):
        app.state.corpus = corpus or Corpus(settings)
        app.state.assistant = Assistant(app.state.corpus)
        app.state.slots = threading.BoundedSemaphore(2)

        async def refresh_loop():
            while True:
                await asyncio.to_thread(app.state.corpus.refresh)
                await asyncio.sleep(settings.refresh_seconds)

        task = asyncio.create_task(refresh_loop())
        yield
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            pass

    app = FastAPI(title="KashiAI", version="0.1.0", lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.origins,
        allow_methods=["GET", "POST"],
        allow_headers=["Content-Type"],
        allow_credentials=False,
    )

    @app.get("/health")
    def health():
        return {
            "status": "ok",
            # Render sets this, so a deploy can be confirmed from outside.
            "commit": os.getenv("RENDER_GIT_COMMIT", "")[:7],
            "retrieval_mode": app.state.corpus.static_index.mode,
            "model_provider": settings.provider,
            "knowledge_records": len(app.state.corpus.knowledge),
            "news": app.state.corpus.status(),
        }

    @app.get("/api/sources")
    def sources():
        return {
            "knowledge": [r.model_dump(mode="json") for r in app.state.corpus.knowledge],
            "news": app.state.corpus.status(),
        }

    @app.post("/api/ask", response_model=Answer)
    def ask(request: AskRequest):
        if not app.state.slots.acquire(blocking=False):
            raise HTTPException(429, "The assistant is busy. Please retry shortly.")
        try:
            return app.state.assistant.ask(request)
        finally:
            app.state.slots.release()

    # Serve the built frontend from the same origin when present (single-service deploys).
    web_dir = Path(__file__).resolve().parents[1] / "frontend/dist"
    if web_dir.is_dir():
        app.mount("/", StaticFiles(directory=web_dir, html=True), name="web")

    return app


app = create_app()
