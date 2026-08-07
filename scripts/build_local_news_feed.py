#!/usr/bin/env python3
"""
Incrementally maintain one app-ready JSON feed for personal local news.

Cities:
- Varanasi
- Mirzapur
- Jaunpur

Sources:
- Dainik Jagran
- Hindustan
- Amar Ujala

This script uses publisher listing/RSS metadata only: headline, summary, date,
image, and source link. It does not copy full article bodies.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Any


JAGRAN_IMAGE_BASE_URL = "https://www.jagranimages.com/images/"
JAGRAN_ARTICLE_BASE_URL = "https://www.jagran.com/uttar-pradesh/"
SOURCE_ORDER = ("Dainik Jagran", "Hindustan", "Amar Ujala")


@dataclass(frozen=True)
class CityConfig:
    city_id: str
    city_name: str
    jagran_listing_url: str
    jagran_city_slug: str
    hindustan_rss_url: str
    amar_ujala_rss_url: str
    terms: tuple[str, ...]


CITIES = (
    CityConfig(
        city_id="varanasi",
        city_name="वाराणसी",
        jagran_listing_url="https://www.jagran.com/uttar-pradesh/varanasi-city",
        jagran_city_slug="varanasi-city",
        hindustan_rss_url="https://api.livehindustan.com/feeds/rss/uttar-pradesh/varanasi/rssfeed.xml",
        amar_ujala_rss_url="https://www.amarujala.com/rss/varanasi.xml",
        terms=("वाराणसी", "varanasi", "बनारस", "banaras", "काशी", "kashi"),
    ),
    CityConfig(
        city_id="mirzapur",
        city_name="मिर्जापुर",
        jagran_listing_url="https://www.jagran.com/uttar-pradesh/mirzapur",
        jagran_city_slug="mirzapur",
        hindustan_rss_url="https://api.livehindustan.com/feeds/rss/uttar-pradesh/mirzapur/rssfeed.xml",
        amar_ujala_rss_url="https://www.amarujala.com/rss/mirzapur.xml",
        terms=("मिर्जापुर", "mirzapur", "vindhyachal", "विंध्याचल"),
    ),
    CityConfig(
        city_id="jaunpur",
        city_name="जौनपुर",
        jagran_listing_url="https://www.jagran.com/uttar-pradesh/jaunpur",
        jagran_city_slug="jaunpur",
        hindustan_rss_url="https://api.livehindustan.com/feeds/rss/uttar-pradesh/jaunpur/rssfeed.xml",
        amar_ujala_rss_url="https://www.amarujala.com/rss/jaunpur.xml",
        terms=("जौनपुर", "jaunpur"),
    ),
)


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def clean_text(value: str | None) -> str:
    if not value:
        return ""
    value = html.unescape(value)
    value = re.sub(r"<[^>]+>", " ", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def fetch_text(url: str, accept: str = "text/html,application/xhtml+xml,application/xml") -> str:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept": accept,
            "Accept-Language": "hi-IN,hi;q=0.9,en;q=0.8",
        },
    )
    with urllib.request.urlopen(request, timeout=25) as response:
        return response.read().decode("utf-8", "replace")


def parse_date(value: str) -> str:
    value = clean_text(value)
    if not value:
        return "आज"
    try:
        parsed = parsedate_to_datetime(value)
        return parsed.strftime("%d %b %Y, %I:%M %p")
    except Exception:
        return value


def is_city_story(city: CityConfig, title: str, summary: str, link: str) -> bool:
    haystack = f"{title} {summary} {link}".lower()
    return any(term.lower() in haystack for term in city.terms)


def normalize_for_key(value: str) -> str:
    return re.sub(r"\W+", "", clean_text(value).lower())


def article_key(article: dict[str, Any]) -> str:
    source_url = clean_text(article.get("sourceUrl"))
    if source_url:
        return "url:" + source_url.lower()
    return "title:" + normalize_for_key(
        f"{article.get('cityId', '')} {article.get('sourceName', '')} {article.get('title', '')}"
    )


def stable_article_id(article: dict[str, Any]) -> str:
    city_id = clean_text(article.get("cityId")) or "local"
    digest = hashlib.sha1(article_key(article).encode("utf-8")).hexdigest()[:12]
    return f"{city_id}-{digest}"


def enrich_article(article: dict[str, Any], timestamp: str) -> dict[str, Any]:
    article = dict(article)
    article.setdefault("id", stable_article_id(article))
    article.setdefault("content", article.get("summary", ""))
    article.setdefault("location", article.get("cityName", ""))
    article.setdefault("firstSeenAt", timestamp)
    article["updatedAt"] = timestamp
    return article


def extract_jagran_next_data(page_html: str) -> dict[str, Any]:
    match = re.search(
        r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>',
        page_html,
        flags=re.DOTALL,
    )
    if not match:
        raise RuntimeError("Could not find Jagran __NEXT_DATA__ JSON.")
    return json.loads(match.group(1))


def walk_article_objects(node: Any) -> list[dict[str, Any]]:
    articles: list[dict[str, Any]] = []
    if isinstance(node, dict):
        if "headline" in node and ("summary" in node or "webTitleUrl" in node):
            articles.append(node)
        for value in node.values():
            articles.extend(walk_article_objects(value))
    elif isinstance(node, list):
        for value in node:
            articles.extend(walk_article_objects(value))
    return articles


def jagran_article_url(city: CityConfig, item: dict[str, Any]) -> str:
    slug = clean_text(item.get("webTitleUrl"))
    article_id = clean_text(str(item.get("id", "")))
    if slug and article_id:
        return f"{JAGRAN_ARTICLE_BASE_URL}{city.jagran_city_slug}-{slug}-{article_id}.html"
    return city.jagran_listing_url


def jagran_image_url(item: dict[str, Any]) -> str:
    image = clean_text(item.get("imgName")) or clean_text(item.get("articleVideoThumbnail"))
    if not image:
        return ""
    if image.startswith(("http://", "https://")):
        return image
    return JAGRAN_IMAGE_BASE_URL + image.lstrip("/")


def app_article(
    *,
    city: CityConfig,
    title: str,
    summary: str,
    date: str,
    image_url: str,
    source_url: str,
    source_name: str,
) -> dict[str, Any]:
    return {
        "cityId": city.city_id,
        "cityName": city.city_name,
        "title": title,
        "summary": summary,
        "content": summary,
        "location": city.city_name,
        "date": date,
        "imageUrl": image_url,
        "sourceUrl": source_url,
        "sourceName": source_name,
        "isBreaking": False,
    }


def get_jagran_articles(city: CityConfig, limit: int) -> list[dict[str, Any]]:
    page = fetch_text(city.jagran_listing_url)
    next_data = extract_jagran_next_data(page)
    items = walk_article_objects(next_data)
    output: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in items:
        item_city = clean_text(item.get("city"))
        item_city_hn = clean_text(item.get("cityHn"))
        title = clean_text(item.get("headline"))
        summary = clean_text(item.get("summary"))
        link = jagran_article_url(city, item)
        city_matches = item_city == city.jagran_city_slug or item_city_hn == city.city_name
        if not city_matches and not is_city_story(city, title, summary, link):
            continue
        if not title or not summary or title in seen:
            continue
        seen.add(title)
        output.append(
            app_article(
                city=city,
                title=title,
                summary=summary,
                date=parse_date(clean_text(item.get("modDate"))),
                image_url=jagran_image_url(item),
                source_url=link,
                source_name="Dainik Jagran",
            )
        )
        if len(output) >= limit:
            break
    return output


def rss_child_text(item: ET.Element, tag_name: str) -> str:
    child = item.find(tag_name)
    return clean_text(child.text if child is not None else "")


def rss_media_url(item: ET.Element) -> str:
    namespaces = {"media": "http://search.yahoo.com/mrss/"}
    media = item.find("media:content", namespaces)
    if media is not None:
        return clean_text(media.attrib.get("url"))
    enclosure = item.find("enclosure")
    if enclosure is not None:
        return clean_text(enclosure.attrib.get("url"))
    return ""


def get_rss_articles(city: CityConfig, url: str, source_name: str, limit: int) -> list[dict[str, Any]]:
    xml_text = fetch_text(url, accept="application/rss+xml,application/xml,text/xml")
    root = ET.fromstring(xml_text)
    output: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in root.findall("./channel/item"):
        title = rss_child_text(item, "title")
        summary = rss_child_text(item, "description")
        link = rss_child_text(item, "link") or rss_child_text(item, "guid")
        if not title or not summary or title in seen:
            continue
        if not is_city_story(city, title, summary, link):
            continue
        seen.add(title)
        output.append(
            app_article(
                city=city,
                title=title,
                summary=summary,
                date=parse_date(rss_child_text(item, "pubDate")),
                image_url=rss_media_url(item),
                source_url=link,
                source_name=source_name,
            )
        )
        if len(output) >= limit:
            break
    return output


def fetch_latest_articles(per_source: int) -> tuple[list[dict[str, Any]], list[str]]:
    collected: list[dict[str, Any]] = []
    failures: list[str] = []
    for city in CITIES:
        for source_name, loader in (
            ("Dainik Jagran", lambda c=city: get_jagran_articles(c, per_source)),
            ("Hindustan", lambda c=city: get_rss_articles(c, c.hindustan_rss_url, "Hindustan", per_source)),
            ("Amar Ujala", lambda c=city: get_rss_articles(c, c.amar_ujala_rss_url, "Amar Ujala", per_source)),
        ):
            try:
                stories = loader()
                print(f"{city.city_name} / {source_name}: {len(stories)} stories")
                collected.extend(stories)
            except Exception as exc:
                failures.append(f"{city.city_name} / {source_name}: {exc}")
    return dedupe_articles(collected), failures


def dedupe_articles(articles: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen: set[str] = set()
    output: list[dict[str, Any]] = []
    for article in articles:
        key = article_key(article)
        if key in seen:
            continue
        seen.add(key)
        output.append(article)
    return output


def balanced_limit(articles: list[dict[str, Any]], limit: int) -> list[dict[str, Any]]:
    by_city_source: dict[str, dict[str, list[dict[str, Any]]]] = {
        city.city_id: {source: [] for source in SOURCE_ORDER} for city in CITIES
    }
    other: list[dict[str, Any]] = []
    for article in articles:
        city_id = article.get("cityId", "")
        source_name = article.get("sourceName", "")
        if city_id in by_city_source and source_name in by_city_source[city_id]:
            by_city_source[city_id][source_name].append(article)
        else:
            other.append(article)

    output: list[dict[str, Any]] = []
    while len(output) < limit:
        added = False
        for city in CITIES:
            for source in SOURCE_ORDER:
                bucket = by_city_source.get(city.city_id, {}).get(source, [])
                if bucket and len(output) < limit:
                    output.append(bucket.pop(0))
                    added = True
        if not added:
            break

    for article in other:
        if len(output) >= limit:
            break
        output.append(article)
    return output


def load_existing(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, list):
        raise RuntimeError(f"{path} must contain a JSON array.")
    return [item for item in data if isinstance(item, dict)]


def merge_articles(
    existing: list[dict[str, Any]],
    latest: list[dict[str, Any]],
    max_stories: int,
    timestamp: str,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    existing_by_key = {article_key(article): enrich_article(article, timestamp) for article in existing}
    new_articles: list[dict[str, Any]] = []

    for article in latest:
        key = article_key(article)
        if key in existing_by_key:
            continue
        enriched = enrich_article(article, timestamp)
        new_articles.append(enriched)
        existing_by_key[key] = enriched

    merged = new_articles + [existing_by_key[article_key(article)] for article in existing if article_key(article) in existing_by_key]
    deduped = dedupe_articles(merged)
    limited = deduped[:max_stories]
    for article in limited:
        article["isBreaking"] = False
    if limited:
        limited[0]["isBreaking"] = True
    return limited, new_articles


def write_json(path: Path, articles: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(articles, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def update_feed(output: Path, per_source: int, initial_limit: int, max_stories: int) -> int:
    timestamp = now_iso()
    existing = load_existing(output)
    latest, failures = fetch_latest_articles(per_source)
    if not latest and not existing:
        raise RuntimeError("No articles collected. " + "; ".join(failures))

    if existing:
        articles, new_articles = merge_articles(existing, latest, max_stories, timestamp)
    else:
        initial = balanced_limit(latest, initial_limit)
        articles = [enrich_article(article, timestamp) for article in initial]
        for article in articles:
            article["isBreaking"] = False
        if articles:
            articles[0]["isBreaking"] = True
        new_articles = articles

    write_json(output, articles)
    print(f"Wrote {len(articles)} stories to {output}")
    print(f"New unique stories added: {len(new_articles)}")
    if new_articles:
        for article in new_articles[:10]:
            print(f"  + [{article.get('cityName')}] {article.get('sourceName')}: {article.get('title')}")
    if failures:
        print("Warnings:")
        for failure in failures:
            print(f"  - {failure}")
    return len(new_articles)


def main() -> int:
    parser = argparse.ArgumentParser(description="Incrementally update VNS local news JSON.")
    parser.add_argument("--per-source", type=int, default=8, help="Maximum latest stories to fetch per city/source.")
    parser.add_argument("--initial-limit", type=int, default=45, help="Stories to seed when output JSON does not exist.")
    parser.add_argument("--max-stories", type=int, default=300, help="Maximum stories to keep in output JSON.")
    parser.add_argument("--output", default="news-api-sample/news.json", help="Output JSON file path.")
    parser.add_argument("--watch", action="store_true", help="Keep running and refresh repeatedly.")
    parser.add_argument("--interval-minutes", type=int, default=60, help="Refresh interval when --watch is used.")
    args = parser.parse_args()

    output = Path(args.output)
    if not args.watch:
        update_feed(output, args.per_source, args.initial_limit, args.max_stories)
        return 0

    interval_seconds = max(1, args.interval_minutes) * 60
    print(f"Watching every {args.interval_minutes} minute(s). Press Ctrl+C to stop.")
    while True:
        try:
            update_feed(output, args.per_source, args.initial_limit, args.max_stories)
        except Exception as exc:
            print(f"ERROR: {exc}", file=sys.stderr)
        time.sleep(interval_seconds)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("Stopped.")
        raise SystemExit(0)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
