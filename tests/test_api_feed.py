import importlib.util
import sys
from dataclasses import replace

import httpx
import pytest
from fastapi.testclient import TestClient

from kashiai.api import create_app
from kashiai.config import ROOT


def test_api_validation_and_citations(corpus):
    with TestClient(create_app(corpus.settings, corpus)) as client:
        assert client.get("/health").json()["knowledge_records"] >= 40
        response = client.post("/api/ask", json={"question": "सारनाथ क्या है?"})
        assert response.status_code == 200
        answer = response.json()
        assert answer["language"] == "hi" and answer["citations"]
        assert answer["citations"][0]["record"]["source_url"].startswith("https://")
        for payload in [
            {"question": "  "},
            {"question": "ok", "top_k": 99},
            {"question": "ok", "since": "2026-02-01", "until": "2026-01-01"},
        ]:
            assert client.post("/api/ask", json=payload).status_code == 422
        assert len(client.get("/api/sources").json()["knowledge"]) >= 40


@pytest.mark.parametrize("uptime", [0.0, 1.0, 10000.0])
def test_feed_failure_preserves_index(corpus, monkeypatch, uptime):
    old_index = corpus.news_index
    monkeypatch.setattr(corpus, "settings", replace(corpus.settings, news_url="https://example.org/feed"))
    monkeypatch.setattr(corpus, "last_attempt", None)
    monkeypatch.setattr(corpus, "last_error", None)
    monkeypatch.setattr("kashiai.service.time.monotonic", lambda: uptime)
    calls = []

    def fail(*args, **kwargs):
        calls.append(True)
        raise httpx.ConnectError("offline")

    monkeypatch.setattr(httpx.Client, "stream", fail)
    corpus.refresh()
    assert corpus.news_index is old_index
    assert corpus.status()["error"]
    corpus.refresh()
    assert len(calls) == 1  # First refresh is immediate, retries still respect the interval.


def test_original_generator_contract():
    path = ROOT / "scripts/build_local_news_feed.py"
    spec = importlib.util.spec_from_file_location("legacy_feed", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    story = module.app_article(
        city=module.CITIES[0],
        title="Title",
        summary="Summary",
        date="Today",
        image_url="",
        source_url="https://example.org/story",
        source_name="Hindustan",
    )
    assert story["cityId"] == "varanasi" and story["content"] == "Summary"
    merged, new = module.merge_articles([], [story], 300, "2026-01-01T00:00:00Z")
    again, added = module.merge_articles(merged, [story], 300, "2026-01-02T00:00:00Z")
    assert len(new) == 1 and not added and len(again) == 1
    assert again[0]["id"] == merged[0]["id"]
    assert again[0]["firstSeenAt"] == "2026-01-01T00:00:00Z"
