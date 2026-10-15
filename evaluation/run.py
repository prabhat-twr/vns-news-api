"""Deterministic retrieval metrics; no paid judge or invented faithfulness score."""

import argparse
import json
import math
import statistics
import time
from dataclasses import replace
from pathlib import Path

from kashiai.agent import Assistant, route_question
from kashiai.config import ROOT, Settings
from kashiai.schema import AskRequest
from kashiai.service import Corpus


def evaluate(corpus):
    cases = json.loads((ROOT / "evaluation/questions.json").read_text(encoding="utf-8"))
    assistant = Assistant(corpus)
    rows = []
    for case in cases:
        request = AskRequest(question=case["question"])
        row = {"question": request.question, "routing_correct": route_question(request) == case["route"]}
        if not case.get("routing_only"):
            start = time.perf_counter()
            answer = assistant.ask(request)
            ids = [c.record.id for c in answer.citations]
            relevant = set(case["relevant"])
            row.update(
                {
                    "retrieved": ids,
                    "latency_ms": (time.perf_counter() - start) * 1000,
                    "citation_id_validity": float(
                        all(c.number in range(1, len(answer.citations) + 1) for c in answer.citations)
                    ),
                }
            )
            if relevant:
                row["recall_at_5"] = len(relevant.intersection(ids)) / len(relevant)
                row["mrr_at_5"] = next((1 / (i + 1) for i, rid in enumerate(ids) if rid in relevant), 0)
                dcg = sum(1 / math.log2(i + 2) for i, rid in enumerate(ids) if rid in relevant)
                ideal = sum(1 / math.log2(i + 2) for i in range(min(5, len(relevant))))
                row["ndcg_at_5"] = dcg / ideal
            else:
                row["abstention_correct"] = not ids
        rows.append(row)
    keys = [
        "routing_correct",
        "recall_at_5",
        "mrr_at_5",
        "ndcg_at_5",
        "abstention_correct",
        "citation_id_validity",
        "latency_ms",
    ]
    return {
        "mode": corpus.static_index.mode,
        "cases": len(rows),
        "metrics": {key: round(statistics.mean(row[key] for row in rows if key in row), 4) for key in keys},
        "limitations": "Small development set, not held-out. Citation validity is structural, not semantic faithfulness. News correctness is covered by dated fixtures, not live relevance labels.",
        "details": rows,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="evaluation/results.json")
    args = parser.parse_args()
    corpus = Corpus(replace(Settings(), news_url="", provider="extractive"))
    report = evaluate(corpus)
    Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report["metrics"], indent=2))
