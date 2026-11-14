from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class AskRequest(BaseModel):
    """The phone sends the question text. A customer id in the body is ignored."""

    model_config = ConfigDict(extra="ignore")
    text: str = Field(min_length=1, max_length=280)

    @field_validator("text")
    @classmethod
    def strip_text(cls, value: str) -> str:
        cleaned = value.strip()
        if not cleaned:
            raise ValueError("text is empty")
        return cleaned[:280]


class EffortRequest(BaseModel):
    effort: Literal["small", "steady", "big"]


class FilingRequest(BaseModel):
    category: str = Field(min_length=1, max_length=80)


class CustomerCoachResponse(BaseModel):
    """The prose answer, plus the card the phone draws under the five fixed questions."""

    model_config = ConfigDict(extra="ignore")
    customer_id: str
    answer: str
    model: str = ""
    source: str = ""
    uses_customer_data: bool = False
    disclaimer: str
    category: str | None = None
    lead: str | None = None
    detail: str | None = None
    card: dict | None = None
    caption: str | None = None
    follow_ups: list[dict] = Field(default_factory=list)

