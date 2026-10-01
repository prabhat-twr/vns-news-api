from datetime import UTC, date, datetime

import pytest
from langchain_core.messages import AIMessage
from llama_index.core.embeddings import MockEmbedding

from kashiai.agent import Assistant, route_question, time_window
from kashiai.retrieval import HybridRetriever
from kashiai.schema import AskRequest


@pytest.mark.parametrize(
    "question,route",
    [
        ("सारनाथ का महत्व", "knowledge"),
        ("आज की खबर", "news"),
        ("compare flood sources", "compare"),
        ("flood timeline", "timeline"),
    ],
)
def test_routes(question, route):
    assert route_question(AskRequest(question=question)) == route


def test_relative_dates_use_india_midnight():
    request = AskRequest(question="today news")
    assert time_window(request, datetime(2026, 1, 1, 20, tzinfo=UTC)) == (date(2026, 1, 2), date(2026, 1, 2))


@pytest.mark.parametrize(
    "question,expected",
    [
        ("What is Sarnath?", "sarnath"),
        ("सारनाथ का महत्व", "sarnath"),
        ("Banarasi silk sarees", "silk"),
        ("बिस्मिल्लाह खान", "musicians"),
    ],
)
def test_bilingual_retrieval(corpus, question, expected):
    assert expected in [r.id for r, _ in corpus.static_index.search(question)]


def test_news_date_filter_excludes_unknown(corpus):
    hits = corpus.news_index.search("flood", news=True, since=date(2026, 1, 3), until=date(2026, 1, 3))
    assert len(hits) == 1
    assert hits[0][0].publisher == "Publisher B"


def test_real_llamaindex_dense_pipeline(corpus):
    # Mock embeddings test wiring, not semantic quality or downloaded model performance.
    index = HybridRetriever(corpus.knowledge[:5], embed_model=MockEmbedding(embed_dim=8))
    hits = index.search("Kashi names")
    assert index.mode == "dense+bm25+rrf"
    assert hits and len({r.id for r, _ in hits}) == len(hits)


def test_timeline_and_comparison(corpus):
    assistant = Assistant(corpus)
    timeline = assistant.ask(
        AskRequest(question="flood timeline", since=date(2026, 1, 1), until=date(2026, 1, 4))
    )
    dates = [c.record.published_at for c in timeline.citations]
    assert dates == sorted(dates) and len(dates) == 2
    compared = assistant.ask(AskRequest(question="compare flood sources"))
    assert len({c.record.publisher for c in compared.citations}) >= 2
    assert compared.trace == ["route:compare", "tool:search_compare", "synthesize:extractive"]


def test_unknown_abstention(corpus):
    answer = Assistant(corpus).ask(AskRequest(question="quantum spacetime superconductors"))
    assert answer.generation_mode == "abstained" and not answer.citations


def test_empty_date_range_does_not_show_old_news(corpus):
    answer = Assistant(corpus).ask(AskRequest(question="flood news", since=date(2040, 1, 1)))
    assert not answer.citations


def test_traditions_labelled(corpus):
    answer = Assistant(corpus).ask(AskRequest(question="Kal Bhairav Kotwal"))
    assert any(c.record.kind == "religious_tradition" for c in answer.citations)
    assert "religious tradition" in answer.answer


class FakeModel:
    def __init__(self, content):
        self.content = content

    def invoke(self, messages):
        assert "untrusted" in messages[0].content
        return AIMessage(content=self.content)


@pytest.mark.parametrize(
    "content",
    ["Invented [999]", "URL https://bad.test [1]", ""],
)
def test_citation_guard_falls_back(corpus, content):
    answer = Assistant(corpus, FakeModel(content)).ask(AskRequest(question="Sarnath"))
    assert answer.generation_mode == "extractive"
    assert any("citation checks" in w for w in answer.warnings)


def test_valid_model_response(corpus):
    answer = Assistant(corpus, FakeModel("Sarnath is associated with the Buddha's first teaching. [1]")).ask(
        AskRequest(question="Sarnath")
    )
    assert answer.generation_mode == "llm"


def test_uncited_background_paragraph_allowed(corpus):
    content = "Sarnath lies just outside Varanasi.\n\nIt is associated with the Buddha's first teaching. [1]"
    answer = Assistant(corpus, FakeModel(content)).ask(AskRequest(question="Sarnath"))
    assert answer.generation_mode == "llm"


def test_uncited_answer_is_flagged_as_general_knowledge(corpus):
    answer = Assistant(corpus, FakeModel("General background about the ghats.")).ask(AskRequest(question="Sarnath"))
    assert answer.generation_mode == "llm"
    assert any("general background" in w for w in answer.warnings)


def test_grouped_citations_normalised(corpus):
    answer = Assistant(corpus, FakeModel("Sarnath matters [1, 2]. More [n].")).ask(AskRequest(question="Sarnath"))
    assert answer.generation_mode == "llm"
    assert "[1][2]" in answer.answer and "[n]" not in answer.answer
