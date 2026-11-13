"""One signed-in demo session.

The body of a request does not choose the customer. A customer id sent
by the phone is ignored. The session is Aino, on S-Tili.

The audit row is the intent class, the tool name, the time, and the
account. It does not keep the question text. The thread she sees can
be exported or deleted on its own.
"""

from __future__ import annotations

from datetime import datetime, timezone

from app.coach import answer, public_answer
from app.ledger import Ledger


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class Session:
    def __init__(self) -> None:
        self.ledger = Ledger()
        self.thread: list[dict] = []
        self.audit: list[dict] = []

    def ask(self, text: str) -> dict:
        full = answer(self.ledger, text)
        at = _now()
        self.audit.append(
            {
                "at": at,
                "intent": full["intent"],
                "tool": full["tool"],
                "account": self.ledger.account,
            }
        )
        shown = public_answer(full)
        self.thread.append({"role": "user", "text": text, "at": at})
        self.thread.append({"role": "coach", "at": at, "answer": shown})
        return shown

    def export_thread(self) -> dict:
        return {
            "customer_name": self.ledger.customer_name,
            "account": self.ledger.account,
            "messages": self.thread,
        }

    def delete_thread(self) -> None:
        self.thread.clear()
