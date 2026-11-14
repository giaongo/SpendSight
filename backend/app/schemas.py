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


class FilingPreviewRequest(BaseModel):
    place: str = Field(min_length=1, max_length=120)
    category: str | None = None


class FactModel(BaseModel):
    kicker: str
    figure: str
    because: str


class CustomerCoachResponse(BaseModel):
    customer_id: str
    answer: str
    model: str
    source: str
    uses_customer_data: bool
    disclaimer: str


class CoachResponse(BaseModel):
    refused: bool
    disclaimer: str
    source: str | None = None
    fact: FactModel | None = None
    next: str | None = None
    screen: str | None = None
    limit: str | None = None
