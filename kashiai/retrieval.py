import re
import unicodedata

from llama_index.core import VectorStoreIndex
from rank_bm25 import BM25Okapi

from .ingest import IST, make_nodes

STOP = set(
    "a an the is are of in on to for what how tell me about and varanasi kashi banaras latest news today compare timeline sources reports क्या है हैं के की का में और बताओ वाराणसी काशी बनारस समाचार खबर आज ताजा ताज़ा तुलना समयरेखा".split()
)
ALIASES = {
    "temples": "temple मंदिर",
    "temple": "मंदिर",
    "ghats": "ghat घाट",
    "ghat": "घाट",
    "flood": "बाढ़",
    "traffic": "यातायात",
    "education": "शिक्षा",
    "food": "भोजन",
    "history": "इतिहास",
    "sarnath": "सारनाथ",
    "bhu": "बीएचयू",
    "music": "संगीत",
    "festival": "त्योहार",
    "police": "पुलिस",
}


def tokens(text: str) -> list[str]:
    # Keep Devanagari combining marks; Python \w alone loses Hindi vowel signs.
    parts = re.findall(r"[a-z0-9]+|[\u0900-\u097f]+", unicodedata.normalize("NFC", text.lower()))
    return [p for p in parts if p not in STOP]


def query_tokens(text):
    parts = tokens(text)
    return parts + tokens(" ".join(ALIASES.get(p, "") for p in parts))


class HybridRetriever:
    def __init__(self, records, embed_model=None, reranker=None):
        self.records = {r.id: r for r in records}
        self.nodes = make_nodes(records)
        self.lexical = BM25Okapi([tokens(n.text) or ["_"] for n in self.nodes]) if self.nodes else None
        self.index = (
            VectorStoreIndex(self.nodes, embed_model=embed_model) if embed_model and self.nodes else None
        )
        self.reranker = reranker
        self.mode = "dense+bm25+rrf" if self.index else "bm25"

    def search(self, question, *, news=False, since=None, until=None, k=5):
        eligible = {
            r.id
            for r in self.records.values()
            if (r.kind == "current_news") == news
            and (
                (not since and not until)
                or (
                    r.published_at is not None
                    and (not since or r.published_at.astimezone(IST).date() >= since)
                    and (not until or r.published_at.astimezone(IST).date() <= until)
                )
            )
        }
        if not eligible or not self.lexical:
            return []
        scores = self.lexical.get_scores(query_tokens(question))
        lexical = sorted(
            [
                (n.node_id, float(scores[i]))
                for i, n in enumerate(self.nodes)
                if n.metadata["record_id"] in eligible and scores[i] > 0
            ],
            key=lambda x: -x[1],
        )
        node_map = {n.node_id: n for n in self.nodes}
        ranks = [lexical]
        if self.index:
            # Filter before top-k by retrieving this bounded corpus. No post-filter recall loss.
            dense = self.index.as_retriever(similarity_top_k=len(self.nodes)).retrieve(question)
            ranks.append(
                [
                    (n.node.node_id, n.score)
                    for n in dense
                    if n.node.metadata["record_id"] in eligible and (n.score or 0) >= 0.72
                ]
            )
        fused = {}
        for ranked in ranks:
            seen_records = set()
            for rank, (nid, _) in enumerate(ranked[:100], 1):
                rid = node_map[nid].metadata["record_id"]
                if rid not in seen_records:
                    fused[rid] = fused.get(rid, 0) + 1 / (60 + rank)
                    seen_records.add(rid)
        candidates = sorted(fused, key=fused.get, reverse=True)[: max(k * 4, 20)]
        if self.reranker and candidates:
            values = self.reranker.predict(
                [(question, self.records[r].title + " " + self.records[r].text) for r in candidates]
            )
            candidates = [r for _, r in sorted(zip(values, candidates), reverse=True)]
        return [(self.records[r], fused[r]) for r in candidates[:k]]
