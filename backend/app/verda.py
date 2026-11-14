"""Ask Verda's vLLM deployment a question.

Spending questions include that customer's transactions. Any other question
is sent on its own, without those figures.

The deployment speaks the OpenAI chat API. See
https://docs.verda.com/containers/tutorials/deploy-with-vllm-indepth/#accessing-the-deployment
"""

from __future__ import annotations

import json
import os
import re
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.customer_data import CustomerBook

DEFAULT_MODEL = "Qwen/Qwen2.5-1.5B-Instruct"
SPENDING_DISCLAIMER = (
    "This answer uses that customer's transactions. "
    "It can be wrong. It does not move money."
)
OTHER_DISCLAIMER = "This answer is not based on the customer's transactions. It can be wrong."

_PERSONAL = re.compile(r"\b(i|i'm|i've|i’ve|my|mine|our)\b", re.IGNORECASE)
_MONEY = re.compile(
    r"\b(balance|salary|salaries|spend|spending|spent|transaction|transactions|"
    r"payment|payments|paid|account|save|saving|saved|savings|deposit|deposits|"
    r"mortgage|bill|bills|rent|income|wage|paycheck|bonus|grocery|groceries|"
    r"coffee|restaurant|dining|taxi|subscription|budget|afford|euro|euros|"
    r"money|cost|costs|withdraw|withdrawal|transfer|loan)\b",
    re.IGNORECASE,
)
_GENERAL = re.compile(
    r"^(what is|what's|who is|who's|where is|where are|when is|explain|define|"
    r"how do|how does|tell me|write |translate|capital of|weather)\b",
    re.IGNORECASE,
)


class VerdaConfigError(Exception):
    """The deployment URL or inference key is not set."""


class VerdaError(Exception):
    """The deployment did not return an answer."""


def env_path() -> Path:
    return Path(__file__).resolve().parents[1] / ".env"


def _parse_env(path: Path) -> dict[str, str]:
    if not path.is_file():
        return {}
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        text = line.strip()
        if not text or text.startswith("#") or "=" not in text:
            continue
        key, value = text.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def _setting(name: str, file_values: dict[str, str], default: str = "") -> str:
    if name in os.environ:
        return os.environ.get(name, "").strip()
    from_file = file_values.get(name, "").strip()
    if from_file:
        return from_file
    return default


def settings() -> tuple[str, str, str]:
    file_values = _parse_env(env_path())
    key = _setting("VERDA_API_KEY", file_values)
    base = _setting("VERDA_BASE_URL", file_values).rstrip("/")
    model = _setting("VERDA_MODEL", file_values, DEFAULT_MODEL)
    return key, base, model


def chat_url(base: str) -> str:
    if base.endswith("/v1"):
        return base + "/chat/completions"
    return base + "/v1/chat/completions"


def spending_relevance(question: str) -> str | None:
    """Return ``spending``, ``other``, or None when the wording is ambiguous."""
    text = question.strip()
    personal = bool(_PERSONAL.search(text))
    money = bool(_MONEY.search(text))
    general = bool(_GENERAL.search(text))
    if personal and money:
        return "spending"
    if general and not personal:
        return "other"
    if not money:
        return "other"
    return None


def _messages(book: CustomerBook, question: str, relevance: str) -> tuple[list[dict], bool]:
    if relevance == "spending":
        facts = book.ai_facts()
        return (
            [
                {
                    "role": "system",
                    "content": (
                        "The question is about this customer's own spending. "
                        "Answer it from the JSON facts only. Amounts named *_cents are integer cents. "
                        "Use the euro strings when you name a figure. "
                        "Do not invent payments, balances, or another customer. "
                        "If the facts do not contain the answer, say so. "
                        "Do not move money and do not say a loan is approved."
                    ),
                },
                {
                    "role": "user",
                    "content": json.dumps(
                        {"question": question, "facts": facts},
                        ensure_ascii=False,
                    ),
                },
            ],
            True,
        )
    return (
        [
            {
                "role": "system",
                "content": (
                    "Answer the question directly. It is not about this customer's spending, "
                    "so do not mention their balance, salary, payments, or transactions."
                ),
            },
            {"role": "user", "content": question},
        ],
        False,
    )


def _complete(key: str, base: str, model: str, messages: list[dict], *, temperature: float, max_tokens: int | None) -> tuple[str, str]:
    payload: dict = {
        "model": model,
        "messages": messages,
        "stream": False,
        "temperature": temperature,
    }
    if max_tokens is not None:
        payload["max_tokens"] = max_tokens
    request = Request(
        chat_url(base),
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {key}",
        },
        method="POST",
    )
    try:
        with urlopen(request, timeout=60) as response:
            raw = response.read().decode("utf-8")
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:300]
        raise VerdaError(f"Verda returned {exc.code}: {detail}") from exc
    except URLError as exc:
        raise VerdaError(f"Could not reach Verda: {exc.reason}") from exc
    try:
        body = json.loads(raw)
        answer = body["choices"][0]["message"]["content"].strip()
        used_model = body.get("model") or model
    except (KeyError, IndexError, TypeError, json.JSONDecodeError, AttributeError) as exc:
        raise VerdaError("Verda returned an answer the service could not read") from exc
    if not answer:
        raise VerdaError("Verda returned an empty answer")
    return answer, used_model


def _classify(key: str, base: str, model: str, question: str) -> str:
    answer, _model = _complete(
        key,
        base,
        model,
        [
            {
                "role": "system",
                "content": (
                    "Decide if the question is about the user's own spending, balance, "
                    "salary, bills, savings, transactions, or mortgage. "
                    "Reply with one word: spending or other."
                ),
            },
            {"role": "user", "content": question},
        ],
        temperature=0,
        max_tokens=8,
    )
    word = answer.lower().split()[0].strip(".,:;")
    if word == "spending":
        return "spending"
    return "other"


def ask_customer(book: CustomerBook, question: str) -> dict:
    key, base, model = settings()
    if not key or not base:
        raise VerdaConfigError("Set VERDA_API_KEY and VERDA_BASE_URL in backend/.env")
    relevance = spending_relevance(question)
    if relevance is None:
        relevance = _classify(key, base, model, question)
    messages, uses_data = _messages(book, question, relevance)
    answer, used_model = _complete(key, base, model, messages, temperature=0.2, max_tokens=None)
    return {
        "customer_id": book.customer_id,
        "answer": answer,
        "model": used_model,
        "source": "verda",
        "uses_customer_data": uses_data,
        "disclaimer": SPENDING_DISCLAIMER if uses_data else OTHER_DISCLAIMER,
    }
