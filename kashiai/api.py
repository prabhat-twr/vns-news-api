import asyncio
import hmac
import os
import threading
from contextlib import asynccontextmanager
from pathlib import Path

import httpx
from fastapi import FastAPI, Header, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from .agent import Assistant
from .config import Settings
from .schema import Answer, AskRequest, EventIn
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
        allow_methods=["GET", "POST", "DELETE"],
        allow_headers=["Content-Type", "X-Admin-Key"],
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

    def require_admin(key):
        if not settings.admin_key:
            raise HTTPException(503, "Adding events is not enabled on this server.")
        if not key or not hmac.compare_digest(key.encode(), settings.admin_key.encode()):
            raise HTTPException(401, "Wrong admin key.")

    @app.get("/api/events")
    def list_events():
        corpus = app.state.corpus
        return {
            "events": corpus.list_events(),
            "storage": corpus.event_store.backend,
            "editing_enabled": bool(settings.admin_key),
            "error": corpus.events_error,
        }

    @app.get("/api/admin/check", status_code=204)
    def admin_check(x_admin_key: str | None = Header(default=None)):
        require_admin(x_admin_key)
        return Response(status_code=204)

    @app.post("/api/events", status_code=201)
    def add_event(event: EventIn, x_admin_key: str | None = Header(default=None)):
        require_admin(x_admin_key)
        try:
            return app.state.corpus.add_event(event)
        except ValueError as exc:
            raise HTTPException(409, str(exc)) from exc
        except httpx.HTTPError as exc:
            raise HTTPException(502, "Could not save to the event store. Please try again.") from exc

    @app.delete("/api/events/{event_id}", status_code=204)
    def delete_event(event_id: str, x_admin_key: str | None = Header(default=None)):
        require_admin(x_admin_key)
        try:
            found = app.state.corpus.delete_event(event_id)
        except httpx.HTTPError as exc:
            raise HTTPException(502, "Could not update the event store. Please try again.") from exc
        if not found:
            raise HTTPException(404, "Event not found (built-in events cannot be deleted).")
        return Response(status_code=204)

    # Serve the built frontend from the same origin when present (single-service deploys).
    web_dir = Path(__file__).resolve().parents[1] / "frontend/dist"
    if web_dir.is_dir():
        app.mount("/", StaticFiles(directory=web_dir, html=True), name="web")

    return app


app = create_app()
