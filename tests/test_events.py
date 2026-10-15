from dataclasses import replace

import httpx
import pytest
from fastapi.testclient import TestClient

from kashiai.agent import Assistant
from kashiai.api import create_app
from kashiai.events import EventStore, load_seed
from kashiai.schema import AskRequest, EventIn
from kashiai.service import Corpus

EVENT = {
    "name": "Ghats Kite Festival",
    "category": "cultural",
    "start": "2026-11-20T16:00",
    "venue": "Assi Ghat",
    "description": "Kite flying contest and music on the ghats.",
    "source_url": "https://example.org/kites",
}


@pytest.fixture
def events_corpus(corpus):
    # A fresh corpus per test so added events do not leak into the shared fixture.
    settings = replace(corpus.settings, admin_key="secret-key")
    return Corpus(settings)


def client_for(c):
    return TestClient(create_app(c.settings, c))


def test_seed_events_are_searchable(events_corpus):
    assert events_corpus.seed_events, "events.json should load"
    assert any(r.kind == "event" for r in events_corpus.static_index.records.values())
    answer = Assistant(events_corpus).ask(AskRequest(question="Krishna Janmotsav event"))
    assert any(c.record.kind == "event" for c in answer.citations)


def test_admin_can_add_and_delete_event_and_it_becomes_searchable(events_corpus):
    with client_for(events_corpus) as client:
        assert client.post("/api/events", json=EVENT).status_code == 401
        assert client.post("/api/events", json=EVENT, headers={"X-Admin-Key": "nope"}).status_code == 401
        created = client.post("/api/events", json=EVENT, headers={"X-Admin-Key": "secret-key"})
        assert created.status_code == 201
        event_id = created.json()["id"]

        listed = client.get("/api/events").json()
        mine = [e for e in listed["events"] if e["id"] == event_id]
        assert mine and mine[0]["editable"] and listed["editing_enabled"]

        answer = client.post("/api/ask", json={"question": "kite festival"}).json()
        assert any(c["record"]["title"] == "Ghats Kite Festival" for c in answer["citations"])

        assert client.delete(f"/api/events/{event_id}").status_code == 401
        assert client.delete(f"/api/events/{event_id}", headers={"X-Admin-Key": "secret-key"}).status_code == 204
        assert client.delete(f"/api/events/{event_id}", headers={"X-Admin-Key": "secret-key"}).status_code == 404
        answer = client.post("/api/ask", json={"question": "kite festival"}).json()
        assert not any(c["record"]["title"] == "Ghats Kite Festival" for c in answer["citations"])


def test_dated_news_search_includes_events(events_corpus):
    events_corpus.add_event(EventIn(**EVENT))
    answer = Assistant(events_corpus).ask(
        AskRequest(question="kite festival news", since="2026-11-19", until="2026-11-21")
    )
    assert answer.route == "news"
    assert any(c.record.title == "Ghats Kite Festival" for c in answer.citations)


def test_editing_disabled_without_admin_key(corpus):
    with client_for(corpus) as client:
        assert client.post("/api/events", json=EVENT, headers={"X-Admin-Key": "x"}).status_code == 503
        assert client.get("/api/events").json()["editing_enabled"] is False


@pytest.mark.parametrize(
    "bad",
    [
        {"start": "next friday"},
        {"start": "2026-11-20", "end": "2026-11-19"},
        {"source_url": "javascript:alert(1)"},
        {"name": "  "},
    ],
)
def test_event_validation(events_corpus, bad):
    with client_for(events_corpus) as client:
        response = client.post("/api/events", json={**EVENT, **bad}, headers={"X-Admin-Key": "secret-key"})
        assert response.status_code == 422


def test_kv_store_round_trip(monkeypatch, corpus):
    settings = replace(corpus.settings, cf_account_id="acc", cf_kv_namespace_id="ns", cf_kv_token="tok")
    saved = {}

    def handler(request: httpx.Request):
        assert request.headers["Authorization"] == "Bearer tok"
        assert request.url.path == "/client/v4/accounts/acc/storage/kv/namespaces/ns/values/events"
        if request.method == "PUT":
            saved["body"] = request.content
            return httpx.Response(200, json={"success": True})
        if "body" not in saved:
            return httpx.Response(404)
        return httpx.Response(200, content=saved["body"])

    real_client = httpx.Client
    monkeypatch.setattr(
        "kashiai.events.httpx.Client", lambda **kw: real_client(transport=httpx.MockTransport(handler), **kw)
    )
    store = EventStore(settings)
    assert store.backend == "cloudflare_kv"
    assert store.load() == []
    store.save([{"id": "a"}])
    assert store.load() == [{"id": "a"}]


def test_bad_seed_file_is_ignored(tmp_path):
    path = tmp_path / "events.json"
    path.write_text("not json", encoding="utf-8")
    assert load_seed(path) == []
