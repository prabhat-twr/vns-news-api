from datetime import UTC, datetime

from kashiai.ingest import canonical_url, normalize_news, publication_date
from kashiai.retrieval import tokens


def test_unicode_tokens_keep_hindi_vowels():
    assert "सारनाथ" in tokens("सारनाथ का इतिहास")
    assert "विश्वनाथ" in tokens("विश्वनाथ मंदिर")


def test_date_has_explicit_timezone():
    dt, basis = publication_date("30 Sep 2026, 12:07 PM")
    assert dt.isoformat() == "2026-09-30T06:37:00+00:00"
    assert "assumed" in basis
    for value in ["आज", "", "not a date"]:
        assert publication_date(value) == (None, "unknown")


def test_news_dedup_city_and_future_dates():
    item = {
        "cityId": "varanasi",
        "title": "<b>News</b>",
        "summary": "<script>bad</script> snippet",
        "sourceUrl": "https://example.org/story?utm_source=test",
        "sourceName": "A",
        "date": "2099-01-01",
    }
    items = [
        item,
        dict(item, sourceUrl="https://example.org/story"),
        dict(item, cityId="jaunpur"),
        dict(item, sourceUrl="javascript:alert(1)"),
        dict(item, sourceUrl="https://other.org/story", sourceName="B"),
    ]
    records = normalize_news(items, datetime(2026, 1, 1, tzinfo=UTC))
    assert len(records) == 2
    assert records[0].title == "News"
    assert records[0].date_basis == "future_timestamp_rejected"
    assert records[0].published_at is None


def test_canonical_url_security():
    assert canonical_url("javascript:alert(1)") == ""
    assert canonical_url("https://user:pass@example.com") == ""
    assert canonical_url("https://EXAMPLE.org/A/?utm_source=x&b=2#hash") == "https://example.org/A?b=2"
