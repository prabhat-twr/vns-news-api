import json
import logging
import threading
import time
from datetime import UTC, datetime, timedelta

import httpx

from .config import Settings
from .events import MAX_EVENTS, EventStore, load_seed, new_event, to_record
from .ingest import load_knowledge, normalize_news
from .retrieval import HybridRetriever

log = logging.getLogger(__name__)


class Corpus:
    def __init__(self, settings: Settings, embed_model=None):
        self.settings = settings
        self.warnings = []
        if embed_model is None and settings.embeddings == "huggingface":
            from llama_index.embeddings.huggingface import HuggingFaceEmbedding

            embed_model = HuggingFaceEmbedding(
                model_name=settings.embedding_model, query_instruction="query: ", text_instruction="passage: "
            )
        elif settings.embeddings not in {"none", "huggingface"}:
            raise ValueError("KASHI_EMBEDDINGS must be none or huggingface")
        self.embed_model = embed_model
        self.reranker = None
        if settings.rerank:
            from sentence_transformers import CrossEncoder

            self.reranker = CrossEncoder("cross-encoder/mmarco-mMiniLMv2-L12-H384-v1")
        self.knowledge = load_knowledge(settings.knowledge_path)
        self.news = normalize_news(json.loads(settings.news_path.read_text(encoding="utf-8")))
        self.lock = threading.Lock()
        self.event_lock = threading.Lock()
        self.event_store = EventStore(settings)
        self.seed_events = load_seed(settings.events_seed_path)
        self.user_events = []
        self.events_error = None
        try:
            self.user_events = self.event_store.load()
        except (httpx.HTTPError, ValueError) as exc:
            log.warning("Event store load failed: %s", type(exc).__name__)
            self.events_error = "Saved events could not be loaded; showing the built-in list."
        self._rebuild_events()
        self.refresh_lock = threading.Lock()
        self.last_attempt = None
        self.fetched_at = None
        self.last_error = None
        self.origin = "repository_snapshot"

    def refresh(self):
        if not self.settings.news_url or (
            self.last_attempt is not None
            and time.monotonic() - self.last_attempt < self.settings.refresh_seconds
        ):
            return
        if not self.refresh_lock.acquire(blocking=False):
            return
        try:
            self.last_attempt = time.monotonic()
            # The URL is operator configuration, never supplied by a question/API caller.
            with httpx.Client(timeout=15, follow_redirects=False) as client:
                with client.stream(
                    "GET", self.settings.news_url, headers={"User-Agent": "KashiAI/0.1"}
                ) as response:
                    response.raise_for_status()
                    payload = bytearray()
                    for chunk in response.iter_bytes():
                        payload.extend(chunk)
                        if len(payload) > 5_000_000:
                            raise ValueError("Feed exceeds 5 MB limit")
            items = json.loads(payload)
            if not isinstance(items, list) or len(items) > 10000:
                raise ValueError("Feed must be a bounded JSON array")
            news = normalize_news(items)
            if not news:
                raise ValueError("Feed contains no usable Varanasi records")
            index = HybridRetriever(news + self.event_records, self.embed_model, self.reranker)
            with self.lock:
                self.news, self.news_index = news, index
                self.fetched_at = datetime.now(UTC)
                self.last_error, self.origin = None, "live_feed"
        except (httpx.HTTPError, ValueError, TypeError, OSError, RuntimeError) as exc:
            log.warning("News refresh failed: %s", type(exc).__name__)
            self.last_error = "Feed refresh failed; using the last available snapshot."
        finally:
            self.refresh_lock.release()

    def _event_records(self):
        records = []
        for event in self.seed_events + self.user_events:
            try:
                records.append(to_record(event))
            except (KeyError, ValueError, TypeError):
                log.warning("Skipping malformed event %s", event.get("id"))
        return records

    def _rebuild_events(self):
        records = self._event_records()
        static = HybridRetriever(self.knowledge + records, self.embed_model, self.reranker)
        news_index = HybridRetriever(self.news + records, self.embed_model, self.reranker)
        with self.lock:
            self.event_records = records
            self.static_index, self.news_index = static, news_index

    def list_events(self):
        events = [dict(e, editable=e.get("origin") == "admin") for e in self.seed_events + self.user_events]
        return sorted(events, key=lambda e: e["start"])

    def add_event(self, data):
        with self.event_lock:
            current = self.event_store.load()  # re-read so concurrent edits are not lost
            if len(current) >= MAX_EVENTS:
                raise ValueError(f"The calendar is full ({MAX_EVENTS} events). Delete old events first.")
            event = new_event(data)
            current.append(event)
            self.event_store.save(current)
            self.user_events = current
            self._rebuild_events()
            return event

    def delete_event(self, event_id):
        with self.event_lock:
            current = self.event_store.load()
            remaining = [e for e in current if e.get("id") != event_id]
            if len(remaining) == len(current):
                return False
            self.event_store.save(remaining)
            self.user_events = remaining
            self._rebuild_events()
            return True

    def status(self):
        with self.lock:
            latest = max((r.published_at for r in self.news if r.published_at), default=None)
            return {
                "origin": self.origin,
                "records": len(self.news),
                "fetched_at": self.fetched_at.isoformat() if self.fetched_at else None,
                "latest_publication": latest.isoformat() if latest else None,
                "stale": not latest or datetime.now(UTC) - latest > timedelta(hours=48),
                "error": self.last_error,
            }
