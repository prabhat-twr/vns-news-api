#!/usr/bin/env python3
"""
Build events.json for the VNSNews / Banaras Buzz app by asking Claude to
research upcoming Varanasi events, mirroring Anthropic's "varanasi-events"
skill (curated sources + targeted search) using the API's server-side
web_fetch and web_search tools in a single Messages call.

Usage:
    python build_events_feed.py --output events.json --days 30

Requires:
    pip install anthropic
    ANTHROPIC_API_KEY environment variable set (e.g. a GitHub Actions secret)

Exits non-zero (without writing a partial/invalid file) on any failure, so a
calling CI workflow can skip the commit step.
"""

import argparse
import json
import os
import re
import sys
from datetime import datetime, timedelta, timezone

try:
    import anthropic
except ImportError:
    print("FAIL: the 'anthropic' package is not installed (pip install anthropic)", file=sys.stderr)
    sys.exit(1)

IST = timezone(timedelta(hours=5, minutes=30))

# Change this if Anthropic retires the alias — see
# https://platform.claude.com/docs/en/about-claude/models/overview
MODEL = os.environ.get("EVENTS_MODEL", "claude-sonnet-5")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

CURATED_SOURCES = """\
Religious / spiritual:
- Shri Kashi Vishwanath Official Web Portal — https://www.shrikashivishwanath.org/general/aarti
  (the temple's own aarti schedule and any special puja/festival announcements — the most
  authoritative source for anything tied to the main temple)
- UP Tourism's Varanasi page — https://uptourism.gov.in/en/page/varanasi-sarnath
  (the state tourism department occasionally lists major religious/cultural observances)

Cultural (concerts, classical performances, festivals):
- AllEvents.in Varanasi — https://allevents.in/varanasi/all
  (also try https://allevents.in/varanasi/concerts and https://allevents.in/varanasi/festivals)
- 10times.com Varanasi — https://10times.com/varanasi-in
- eventseeker.com/varanasi — https://eventseeker.com/varanasi

General / civic / tourism:
- happeningnext.com/varanasi — https://happeningnext.com/varanasi
- UP Tourism's Varanasi page (above) also doubles as a general source
"""


def build_prompt(window_start: str, window_end: str, locations_json: str) -> str:
    return f"""You are compiling a structured events feed for a Varanasi (Banaras/Kashi) city
app. Research upcoming events using your web_fetch and web_search tools, then output ONLY
a JSON object matching the exact schema below.

## Date window
Cover events from {window_start} to {window_end} (inclusive), today being {window_start}.

## Step 1: Check curated sources first
Use web_fetch on each of these URLs and look for events falling inside the date window.
These are known-reliable sources for Varanasi specifically — check them before general
search, since generic "Varanasi events" queries often surface stale listicles.

{CURATED_SOURCES}

Discard anything clearly outdated (dated before {window_start}) or duplicated across sources.

## Step 2: Fill gaps with targeted search
Curated sources rarely catch everything, especially major recurring festivals that get their
own news coverage rather than event-listing placement — things like Dev Deepawali,
Mahashivratri, Ganga Mahotsav, Bharat Milap, Nag Nathaiya, or Kashi Tamil Sangamam. Run a
couple of targeted web_search queries such as "Varanasi festival [current month/year]" and
"Varanasi [known upcoming festival name] date" to catch these and confirm exact dates, since
festival dates (especially Hindu lunisolar ones) shift year to year and must never be assumed
from memory or training data.

Since daily Ganga Aarti happens every evening regardless of season, don't list it as a
discrete event unless there's something special about a particular date (a grand/special
aarti, an anniversary, a festival aarti).

## Step 3: Output schema
Convert every event you found into this exact JSON schema (no markdown, no commentary,
just the object):

{{
  "generated_at": "<ISO 8601 datetime this file was produced, IST i.e. +05:30>",
  "location": "Varanasi, India",
  "window": {{ "start": "{window_start}", "end": "{window_end}" }},
  "events": [
    {{
      "id": "<kebab-case slug of name + start date, e.g. 'nag-panchami-2026-08-17'>",
      "name": "<event name>",
      "category": "religious | cultural | tourism",
      "start": "<ISO 8601 datetime, +05:30. If only a date is known, use T00:00:00+05:30 and set all_day true>",
      "end": "<ISO 8601 datetime, or null if unknown/not meaningfully time-boxed>",
      "all_day": false,
      "date_confidence": "confirmed | approximate | conflicting",
      "date_note": "<explain if approximate/conflicting, e.g. which sources disagreed and why you picked this date — null if confirmed and uncontested>",
      "location": {{
        "name": "<venue/ghat/temple name>",
        "lat": null,
        "lng": null,
        "coordinate_precision": null
      }},
      "description": "<one-line description>",
      "source_url": "<link>",
      "image_url": null
    }}
  ]
}}

Notes on filling this in:
- `category` must be exactly one of religious, cultural, tourism.
- For `location.lat`/`lng`, check this reference table first — it has verified or
  reasonable-approximation coordinates for venues that come up repeatedly:

{locations_json}

  Copy both the coordinates and that table's "precision" value into `coordinate_precision`.
  For a venue not in that table, do a quick web_search for its coordinates rather than
  guessing. A venue named only vaguely (e.g. "Venue TBA") should get lat/lng: null with a
  date_note-style caveat — don't fabricate a location.
- `date_confidence` should reflect what you actually found: "conflicting" when sources
  disagreed on the date (explain the disagreement in date_note), "approximate" when you're
  inferring a likely date without full confirmation, "confirmed" otherwise.
- `image_url` should be null — these sources rarely provide a usable direct image URL.
- If a source gives a vague or conflicting date, say so in date_note rather than picking one
  arbitrarily, and prefer the most official-looking source (temple portal or government
  tourism site) over aggregators when they disagree.
- If genuinely nothing is found for a category, that's fine — just don't include events for it.

## Output format — IMPORTANT
After you finish researching, output the final JSON object wrapped EXACTLY like this, with
nothing else after the closing marker:

<<<EVENTS_JSON>>>
{{ ...the JSON object... }}
<<<END_EVENTS_JSON>>>
"""


def extract_json(text: str) -> dict:
    match = re.search(r"<<<EVENTS_JSON>>>\s*(\{.*\})\s*<<<END_EVENTS_JSON>>>", text, re.DOTALL)
    if not match:
        raise ValueError(
            "Could not find <<<EVENTS_JSON>>> ... <<<END_EVENTS_JSON>>> markers in the "
            "model's response. Last 2000 chars of response:\n" + text[-2000:]
        )
    return json.loads(match.group(1))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="events.json")
    parser.add_argument("--days", type=int, default=30)
    args = parser.parse_args()

    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        print("FAIL: ANTHROPIC_API_KEY is not set", file=sys.stderr)
        sys.exit(1)

    now_ist = datetime.now(IST)
    window_start = now_ist.strftime("%Y-%m-%d")
    window_end = (now_ist + timedelta(days=args.days)).strftime("%Y-%m-%d")

    locations_path = os.path.join(SCRIPT_DIR, "varanasi_locations.json")
    with open(locations_path, "r", encoding="utf-8") as f:
        locations_json = f.read()

    prompt = build_prompt(window_start, window_end, locations_json)

    client = anthropic.Anthropic(api_key=api_key)

    print(f"Requesting events for {window_start}..{window_end} from {MODEL}...")
    try:
        response = client.messages.create(
            model=MODEL,
            max_tokens=12000,
            tools=[
                {"type": "web_fetch_20250910", "name": "web_fetch", "max_uses": 10},
                {"type": "web_search_20250305", "name": "web_search", "max_uses": 8},
            ],
            extra_headers={"anthropic-beta": "web-fetch-2025-09-10"},
            messages=[{"role": "user", "content": prompt}],
        )
    except Exception as e:  # noqa: BLE001 - surface any API error plainly
        print(f"FAIL: Anthropic API call failed — {e}", file=sys.stderr)
        sys.exit(1)

    full_text = "".join(
        block.text for block in response.content if getattr(block, "type", None) == "text"
    )

    try:
        data = extract_json(full_text)
    except (ValueError, json.JSONDecodeError) as e:
        print(f"FAIL: could not parse model output — {e}", file=sys.stderr)
        sys.exit(1)

    data.setdefault("generated_at", now_ist.isoformat())
    data.setdefault("location", "Varanasi, India")
    data.setdefault("window", {"start": window_start, "end": window_end})

    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

    n = len(data.get("events", []))
    searches = getattr(response.usage, "server_tool_use", None)
    print(f"Wrote {args.output} with {n} events.")
    if searches:
        print(f"Server tool usage: {searches}")


if __name__ == "__main__":
    main()
