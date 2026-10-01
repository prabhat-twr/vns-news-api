from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, Field, model_validator

Kind = Literal["historical_fact", "official_information", "religious_tradition", "current_news"]
Route = Literal["auto", "knowledge", "news", "timeline", "compare"]


class Record(BaseModel):
    id: str
    title: str
    text: str
    text_hi: str = ""
    source_url: str
    publisher: str
    category: str
    kind: Kind
    language: str = "en"
    aliases: list[str] = Field(default_factory=list)
    published_at: datetime | None = None
    date_basis: str = "unknown"
    retrieved_at: datetime | None = None
    reviewed_on: date | None = None
    rights: str = "Original editorial summary; source content retains its own rights."


class Turn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(max_length=4000)


class AskRequest(BaseModel):
    question: str = Field(min_length=2, max_length=1000)
    # Earlier chat turns, oldest first, so follow-up questions keep their context.
    history: list[Turn] = Field(default_factory=list, max_length=12)
    language: Literal["auto", "hi", "en"] = "auto"
    route: Route = "auto"
    since: date | None = None
    until: date | None = None
    top_k: int = Field(default=5, ge=1, le=10)

    @model_validator(mode="after")
    def validate_request(self):
        self.question = self.question.strip()
        if len(self.question) < 2:
            raise ValueError("Please enter a question.")
        if self.since and self.until and self.since > self.until:
            raise ValueError("since must be on or before until")
        return self


class Citation(BaseModel):
    number: int
    record: Record
    score: float


class Answer(BaseModel):
    answer: str
    language: str
    route: str
    citations: list[Citation]
    warnings: list[str]
    trace: list[str]
    retrieval_mode: str
    generation_mode: str
    elapsed_ms: float
    feed_status: dict
