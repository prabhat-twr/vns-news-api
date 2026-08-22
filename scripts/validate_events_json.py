#!/usr/bin/env python3
"""
Validate an events.json file produced by the varanasi-events skill before
handing it to the user. Catches the mistakes that would actually break a
consumer app: missing required fields, malformed dates, bad enum values,
non-URL source links.

Usage:
    python validate_events_json.py path/to/events.json

Exits 0 and prints "OK" if the file is valid. Exits 1 and lists every
problem found (with the event's index and name where possible) otherwise —
fix those before delivering the file.
"""

import json
import sys
from datetime import datetime

REQUIRED_TOP_LEVEL = ["generated_at", "location", "window", "events"]
REQUIRED_WINDOW = ["start", "end"]
REQUIRED_EVENT_FIELDS = [
    "id", "name", "category", "start", "date_confidence",
    "location", "description", "source_url",
]
VALID_CATEGORIES = {"religious", "cultural", "tourism"}
VALID_CONFIDENCE = {"confirmed", "approximate", "conflicting"}


def is_iso_datetime(value):
    if not isinstance(value, str):
        return False
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
        return True
    except ValueError:
        return False


def is_iso_date(value):
    if not isinstance(value, str):
        return False
    try:
        datetime.strptime(value, "%Y-%m-%d")
        return True
    except ValueError:
        return False


def validate(data):
    errors = []

    for field in REQUIRED_TOP_LEVEL:
        if field not in data:
            errors.append(f"Missing top-level field: '{field}'")

    if "window" in data and isinstance(data["window"], dict):
        for field in REQUIRED_WINDOW:
            if field not in data["window"]:
                errors.append(f"Missing window.{field}")
            elif not is_iso_date(data["window"][field]):
                errors.append(f"window.{field} is not a YYYY-MM-DD date: {data['window'][field]!r}")

    events = data.get("events")
    if not isinstance(events, list):
        errors.append("'events' must be a list")
        return errors
    if len(events) == 0:
        errors.append("'events' is empty — if genuinely nothing was found, that's suspicious enough to double check rather than ship")

    seen_ids = set()
    for i, ev in enumerate(events):
        label = f"event[{i}]" + (f" ({ev.get('name')})" if isinstance(ev, dict) and ev.get("name") else "")

        if not isinstance(ev, dict):
            errors.append(f"{label}: not an object")
            continue

        for field in REQUIRED_EVENT_FIELDS:
            if field not in ev:
                errors.append(f"{label}: missing field '{field}'")

        eid = ev.get("id")
        if eid is not None:
            if eid in seen_ids:
                errors.append(f"{label}: duplicate id '{eid}'")
            seen_ids.add(eid)

        cat = ev.get("category")
        if cat is not None and cat not in VALID_CATEGORIES:
            errors.append(f"{label}: category '{cat}' not in {sorted(VALID_CATEGORIES)}")

        conf = ev.get("date_confidence")
        if conf is not None and conf not in VALID_CONFIDENCE:
            errors.append(f"{label}: date_confidence '{conf}' not in {sorted(VALID_CONFIDENCE)}")

        start = ev.get("start")
        if start is not None and not is_iso_datetime(start):
            errors.append(f"{label}: start '{start}' is not a valid ISO 8601 datetime")

        end = ev.get("end")
        if end is not None and not is_iso_datetime(end):
            errors.append(f"{label}: end '{end}' is not a valid ISO 8601 datetime")

        loc = ev.get("location")
        if loc is not None:
            if not isinstance(loc, dict):
                errors.append(f"{label}: location must be an object")
            else:
                if "name" not in loc:
                    errors.append(f"{label}: location.name missing")
                lat, lng = loc.get("lat"), loc.get("lng")
                if (lat is None) != (lng is None):
                    errors.append(f"{label}: location.lat/lng should both be null or both be set, got lat={lat!r} lng={lng!r}")
                if lat is not None and not (-90 <= lat <= 90):
                    errors.append(f"{label}: location.lat {lat} out of range")
                if lng is not None and not (-180 <= lng <= 180):
                    errors.append(f"{label}: location.lng {lng} out of range")

        src = ev.get("source_url")
        if src is not None and not (isinstance(src, str) and src.startswith("http")):
            errors.append(f"{label}: source_url doesn't look like a URL: {src!r}")

    return errors


def main():
    if len(sys.argv) != 2:
        print("Usage: python validate_events_json.py path/to/events.json")
        sys.exit(2)

    path = sys.argv[1]
    try:
        with open(path) as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        print(f"FAIL: not valid JSON — {e}")
        sys.exit(1)
    except OSError as e:
        print(f"FAIL: couldn't read file — {e}")
        sys.exit(1)

    errors = validate(data)
    if errors:
        print(f"FAIL: {len(errors)} problem(s) found in {path}\n")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)

    n = len(data.get("events", []))
    print(f"OK: {path} is valid ({n} events)")
    sys.exit(0)


if __name__ == "__main__":
    main()
