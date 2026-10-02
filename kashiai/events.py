"""Varanasi events for retrieval: the reviewed events.json seed plus admin-added events in Cloudflare KV."""

import hashlib
import json
import logging
import re
import threading
from datetime import UTC, datetime
from pathlib import Path

import httpx

from .ingest import IST, canonical_url, clean
from .schema import EventIn, Record

log = logging.getLogger(__name__)
KV_KEY = "events"
MAX_EVENTS = 500


def parse_when(value: str) -> tuple[datetime, bool]:
    """ISO date or datetime -> aware UTC datetime and whether it is an all-day event."""
    value = value.strip()
    dt = datetime.fromisoformat(value)
    all_day = len(value) == 10
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=IST)
    return dt.astimezone(UTC), all_day


def describe_when(event: dict) -> str:
    start, all_day = parse_when(event["start"])
    fmt = "%a %d %b %Y" if all_day else "%a %d %b %Y, %I:%M %p IST"
    text = start.astimezone(IST).strftime(fmt)
    if event.get("end"):
        end, end_all_day = parse_when(event["end"])
        text += " to " + end.astimezone(IST).strftime("%a %d %b %Y" if end_all_day else fmt)
    return text


def to_record(event: dict) -> Record:
    start, _ = parse_when(event["start"])
    venue = event.get("venue") or "Varanasi"
    text = (
        f"Event in Varanasi: {event['name']}. {event.get('description', '')} "
        f"When: {describe_when(event)}. Where: {venue}. Category: {event.get('category', 'other')}."
    )
    return Record(
        id="event-" + event["id"],
        title=event["name"],
        text=clean(text),
        source_url=event.get("source_url") or "",
        publisher="KashiAI events calendar" if event.get("origin") == "admin" else "Varanasi events listing",
        category="events",
        kind="event",
        language="en",
        aliases=["event", "events", "happening", "utsav", "कार्यक्रम", "आयोजन", event.get("category", "")],
        published_at=start,
        date_basis="event_start",
    )


def new_event(data: EventIn) -> dict:
    start, all_day = parse_when(data.start)
    digest = hashlib.sha1(f"{data.name}|{data.start}|{datetime.now(UTC).isoformat()}".encode()).hexdigest()[:8]
    slug = re.sub(r"[^a-z0-9]+", "-", data.name.lower()).strip("-")[:40] or "event"
    return {
        "id": f"{slug}-{start.astimezone(IST).date().isoformat()}-{digest}",
        "name": clean(data.name),
        "category": data.category,
        "start": data.start.strip(),
        "end": (data.end or "").strip() or None,
        "all_day": all_day,
        "venue": clean(data.venue),
        "description": clean(data.description),
        "source_url": canonical_url(data.source_url) if data.source_url else "",
        "origin": "admin",
        "added_at": datetime.now(UTC).isoformat(),
    }


def load_seed(path: Path) -> list[dict]:
    """Normalise the repository's events.json (generated listing) into stored-event shape."""
    try:
        rows = json.loads(path.read_text(encoding="utf-8")).get("events", [])
    except (OSError, ValueError, AttributeError):
        return []
    events = []
    for row in rows:
        try:
            parse_when(row["start"])
            events.append(
                {
                    "id": "seed-" + str(row["id"]),
                    "name": clean(row["name"]),
                    "category": row.get("category") or "other",
                    "start": row["start"],
                    "end": row.get("end"),
                    "all_day": bool(row.get("all_day")),
                    "venue": clean((row.get("location") or {}).get("name", "")),
                    "description": clean(row.get("description", "")),
                    "source_url": canonical_url(row.get("source_url") or ""),
                    "origin": "seed",
                }
            )
        except (KeyError, ValueError, TypeError):
            continue
    return events


class EventStore:
    """Cloudflare Workers KV when configured, otherwise process memory (lost on restart)."""

    def __init__(self, settings):
        self.account = settings.cf_account_id
        self.namespace = settings.cf_kv_namespace_id
        self.token = settings.cf_kv_token
        self.memory: list[dict] = []
        self.lock = threading.Lock()

    @property
    def backend(self) -> str:
        return "cloudflare_kv" if self.account and self.namespace and self.token else "memory"

    def _url(self):
        return (
            f"https://api.cloudflare.com/client/v4/accounts/{self.account}"
            f"/storage/kv/namespaces/{self.namespace}/values/{KV_KEY}"
        )

    def load(self) -> list[dict]:
        if self.backend == "memory":
            return list(self.memory)
        with httpx.Client(timeout=15) as client:
            response = client.get(self._url(), headers={"Authorization": f"Bearer {self.token}"})
        if response.status_code == 404:
            return []
        response.raise_for_status()
        data = response.json()
        if not isinstance(data, list):
            raise ValueError("Stored events must be a JSON array")
        return data

    def save(self, events: list[dict]) -> None:
        if self.backend == "memory":
            self.memory = list(events)
            return
        with httpx.Client(timeout=15) as client:
            response = client.put(
                self._url(),
                headers={"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"},
                content=json.dumps(events, ensure_ascii=False).encode(),
            )
        response.raise_for_status()
