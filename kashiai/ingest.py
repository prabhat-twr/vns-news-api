"""Only normalize the existing feed and reviewed editorial knowledge; never scrape articles."""

import hashlib
import html
import json
import re
from datetime import UTC, datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from llama_index.core import Document
from llama_index.core.node_parser import SentenceSplitter

from .schema import Record

IST = timezone(timedelta(hours=5, minutes=30))


def clean(value) -> str:
    return re.sub(r"\s+", " ", re.sub(r"<[^>]*>", " ", html.unescape(str(value or "")))).strip()


def canonical_url(value: str) -> str:
    try:
        p = urlsplit(value.strip())
        if p.scheme not in {"http", "https"} or not p.hostname or p.username or p.password:
            return ""
        query = [
            (k, v)
            for k, v in parse_qsl(p.query)
            if not k.lower().startswith("utm_") and k not in {"fbclid", "gclid"}
        ]
        return urlunsplit((p.scheme, p.netloc.lower(), p.path.rstrip("/"), urlencode(sorted(query)), ""))
    except ValueError:
        return ""


def publication_date(value: str) -> tuple[datetime | None, str]:
    value = clean(value)
    if not value or value in {"आज", "today", "Yesterday", "कल"}:
        return None, "unknown"
    for parser in (
        lambda v: datetime.fromisoformat(v.replace("Z", "+00:00")),
        parsedate_to_datetime,
        lambda v: datetime.strptime(v, "%d %b %Y, %I:%M %p"),
    ):
        try:
            dt = parser(value)
            basis = "publisher_timestamp" if dt.tzinfo else "publisher_date_assumed_Asia_Kolkata"
            return dt.replace(tzinfo=IST).astimezone(UTC) if dt.tzinfo is None else dt.astimezone(UTC), basis
        except (ValueError, TypeError, OverflowError):
            continue
    return None, "unknown"


def normalize_news(items: list, now: datetime | None = None) -> list[Record]:
    now = now or datetime.now(UTC)
    output, seen_urls, seen_titles = [], set(), set()
    for item in items:
        if not isinstance(item, dict) or item.get("cityId") != "varanasi":
            continue
        url = canonical_url(str(item.get("sourceUrl", "")))
        title = clean(item.get("title"))
        publisher = clean(item.get("sourceName")) or "Unknown publisher"
        # Retain independent publishers for comparison, collapse same-publisher syndicated copies.
        title_key = (publisher.casefold(), re.sub(r"\s+", "", title.casefold()))
        if not url or not title or url in seen_urls or title_key in seen_titles:
            continue
        published, basis = publication_date(item.get("date", ""))
        if published and published > now + timedelta(hours=1):
            published, basis = None, "future_timestamp_rejected"
        seen_urls.add(url)
        seen_titles.add(title_key)
        output.append(
            Record(
                id="news-" + hashlib.sha256(url.encode()).hexdigest()[:16],
                title=title,
                text=clean(item.get("summary"))[:800],
                source_url=url,
                publisher=publisher,
                category="news",
                kind="current_news",
                language="hi" if re.search("[\u0900-\u097f]", title) else "en",
                published_at=published,
                date_basis=basis,
                retrieved_at=now,
                rights="Publisher metadata/snippet from vns-news-api; no full article redistribution; verify publisher terms.",
            )
        )
    return output


def load_knowledge(path: Path) -> list[Record]:
    records = [Record.model_validate(row) for row in json.loads(path.read_text(encoding="utf-8"))]
    if len({r.id for r in records}) != len(records):
        raise ValueError("Knowledge IDs must be unique")
    if any(not canonical_url(r.source_url) or r.kind == "current_news" for r in records):
        raise ValueError("Knowledge requires HTTP(S) provenance and a static claim type")
    return records


def make_nodes(records: list[Record]):
    documents = [
        Document(
            id_=r.id,
            text=f"{r.title}\n{r.text}\n{r.text_hi}\n{' '.join(r.aliases)}",
            metadata={
                "record_id": r.id,
                "kind": r.kind,
                "publisher": r.publisher,
                "source_url": r.source_url,
            },
            excluded_embed_metadata_keys=["record_id", "source_url", "publisher", "kind"],
            excluded_llm_metadata_keys=["record_id"],
        )
        for r in records
    ]
    return SentenceSplitter(chunk_size=384, chunk_overlap=48).get_nodes_from_documents(documents)
