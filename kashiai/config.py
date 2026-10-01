import os
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Settings:
    knowledge_path: Path = field(default_factory=lambda: ROOT / "data/knowledge.json")
    news_path: Path = field(default_factory=lambda: ROOT / "news.json")
    events_seed_path: Path = field(default_factory=lambda: ROOT / "events.json")
    # Admin-added events persist in Cloudflare Workers KV; editing is disabled without an admin key.
    cf_account_id: str = field(default_factory=lambda: os.getenv("CF_ACCOUNT_ID", ""))
    cf_kv_namespace_id: str = field(default_factory=lambda: os.getenv("CF_KV_NAMESPACE_ID", ""))
    cf_kv_token: str = field(default_factory=lambda: os.getenv("CF_KV_TOKEN", ""))
    admin_key: str = field(default_factory=lambda: os.getenv("KASHI_ADMIN_KEY", ""))
    news_url: str = field(
        default_factory=lambda: os.getenv(
            "KASHI_NEWS_URL", "https://raw.githubusercontent.com/prabhat-twr/vns-news-api/main/news.json"
        )
    )
    refresh_seconds: int = field(
        default_factory=lambda: max(60, int(os.getenv("KASHI_REFRESH_SECONDS", "900")))
    )
    embeddings: str = field(default_factory=lambda: os.getenv("KASHI_EMBEDDINGS", "none"))
    embedding_model: str = "intfloat/multilingual-e5-small"
    provider: str = field(default_factory=lambda: os.getenv("KASHI_PROVIDER", "ollama"))
    model: str = field(default_factory=lambda: os.getenv("KASHI_MODEL", "qwen2.5:1.5b"))
    ollama_url: str = field(default_factory=lambda: os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"))
    rerank: bool = field(default_factory=lambda: os.getenv("KASHI_RERANK", "false").lower() == "true")
    origins: list[str] = field(
        default_factory=lambda: os.getenv(
            "KASHI_CORS_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173"
        ).split(",")
    )
