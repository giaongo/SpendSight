"""Ask Verda's vLLM deployment about one customer's own transactions.

The deployment speaks the OpenAI chat API. See
https://docs.verda.com/containers/tutorials/deploy-with-vllm-indepth/#accessing-the-deployment
"""

from __future__ import annotations

import json
import os
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.customer_data import CustomerBook

DEFAULT_MODEL = "Qwen/Qwen2.5-1.5B-Instruct"
DISCLAIMER = (
    "This answer is generated from that customer's transactions. "
    "It can be wrong. It does not move money."
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
    from_file = file_values.get(name, "").strip()
    if from_file:
        return from_file
    return (os.environ.get(name) or default).strip()


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


def ask_customer(book: CustomerBook, question: str) -> dict:
    key, base, model = settings()
    if not key or not base:
        raise VerdaConfigError("Set VERDA_API_KEY and VERDA_BASE_URL in backend/.env")
    facts = book.ai_facts()
    payload = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": (
                    "You answer questions about one bank customer. Use only the JSON facts. "
                    "Amounts named *_cents are integer cents. Do not invent payments, balances, "
                    "or another customer. If the facts do not contain the answer, say so. "
                    "Do not move money and do not say a loan is approved."
                ),
            },
            {
                "role": "user",
                "content": json.dumps({"question": question, "facts": facts}, ensure_ascii=False),
            },
        ],
        "stream": False,
        "temperature": 0.2,
    }
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
    return {
        "customer_id": book.customer_id,
        "answer": answer,
        "model": used_model,
        "source": "verda",
        "disclaimer": DISCLAIMER,
    }
