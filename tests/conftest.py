import json
from dataclasses import replace

import pytest

from kashiai.config import Settings
from kashiai.service import Corpus


@pytest.fixture(scope="session")
def corpus(tmp_path_factory):
    path = tmp_path_factory.mktemp("feed") / "news.json"
    stories = [
        {
            "cityId": "varanasi",
            "title": "Ganga flood warning",
            "summary": "River flood levels rose.",
            "sourceName": "Publisher A",
            "sourceUrl": "https://example.org/flood-a",
            "date": "2026-01-02T10:00:00+05:30",
        },
        {
            "cityId": "varanasi",
            "title": "Ganga flood response",
            "summary": "Flood shelters opened.",
            "sourceName": "Publisher B",
            "sourceUrl": "https://example.net/flood-b",
            "date": "2026-01-03T10:00:00+05:30",
        },
        {
            "cityId": "varanasi",
            "title": "Ganga flood notice",
            "summary": "Unknown publication date.",
            "sourceName": "Publisher C",
            "sourceUrl": "https://example.com/flood-c",
            "date": "आज",
        },
    ]
    for i, topic in enumerate(["traffic", "school", "cricket", "music", "museum", "festival"]):
        stories.append(
            {
                "cityId": "varanasi",
                "title": topic,
                "summary": topic + " update",
                "sourceName": "Publisher A",
                "sourceUrl": f"https://example.org/{topic}",
                "date": "2026-01-01T12:00:00Z",
            }
        )
    path.write_text(json.dumps(stories), encoding="utf-8")
    return Corpus(replace(Settings(), news_url="", news_path=path, provider="extractive", embeddings="none"))
