# Validation record

Checked on 2026-09-30 with Python 3.11 and Node 22 on Windows. `requirements-tested.txt` records the resolved lightweight Python environment; CI and the default Docker build use it as a constraints file. The optional dense/paid extras resolve separately. `frontend/package-lock.json` pins frontend dependencies.

- Backend: 29 tests cover source normalization, Hindi tokenization, real LangGraph/LangChain routing, LlamaIndex dense wiring with mock embeddings, timestamp filters, source comparisons, model citation failure, API validation, refresh failure and the original generator contract. CI exposed a machine-uptime-dependent initial refresh delay; the fix uses an explicit unattempted state and regression cases at 0, 1 and 10,000 seconds uptime.
- Frontend: all 6 Playwright checks passed across desktop and mobile configurations; production build succeeded. Browser tests use deterministic API fixtures. Separate real-app screenshots exercise the actual FastAPI backend and a Hindi query.
- Static checks: Ruff passed; Python compilation passed; no modifications to original data/feed generation/workflow files.
- Live news adapter: successfully refreshed the public upstream feed, normalized 194 Varanasi records, and reported `live_feed`, non-stale publication data and no refresh error. This confirms connectivity and schema handling, not publisher accuracy.
- Development retrieval evaluation: 24 cases, BM25 mode, Recall@5 **0.9630**, MRR@5 **0.9722**, nDCG@5 **0.9387**, routing **1.0**, abstention on two unanswerable cases **1.0**. See the full per-case `evaluation/results.json`. Timing is local and not a service guarantee.
- Citation ID validity is only structural. These results do not measure factual entailment, Hindi generation quality, dense-model quality, production scale or adversarial robustness.

## Environment limitations

The local machine has no running Docker engine, so a local image build could not be executed. GitHub Actions successfully built the image and passed its `/health` smoke test, and the Linux frontend build/browser tests passed. No local Ollama executable/model or multilingual E5/cross-encoder weights were available during the lightweight validation run. Dense plumbing was tested with LlamaIndex MockEmbedding, and model success/failure branches with a deterministic fake model; a real LangChain Ollama client also correctly fell back when its local endpoint was unavailable. Downloaded-model inference and reranking still need hardware-backed evaluation. Neither paid API inference nor public hosting was activated.

## Deployment checks

Before presenting a public generative demo, run full local AI mode and repeat evaluation with the downloaded embedding model. Verify Hindi fluency, source entailment, response time and memory on the target machine. Check CORS from the deployed frontend, cold starts, a successful live feed refresh and stale-feed behavior. Record the model tag, runtime version and corpus commit beside any reported results.
