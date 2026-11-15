"""The demo's fixed customer and day.

The Junction demo is told as CUST00007 on Wednesday 30 Sep 2026, the day after
their last transaction. Both can be changed for a run:

- ``SPENDSIGHT_CUSTOMER``: the signed-in customer. If that id is not in the
  customer file (as in the test fixture), the first customer is used.
- ``SPENDSIGHT_TODAY``: an ISO date, or ``real`` for the machine's date.
"""

from __future__ import annotations

import os
from datetime import date

DEFAULT_CUSTOMER = "CUST00007"
DEFAULT_TODAY = date(2026, 9, 30)


def demo_customer() -> str:
    return os.environ.get("SPENDSIGHT_CUSTOMER", "").strip() or DEFAULT_CUSTOMER


def demo_today() -> date:
    raw = os.environ.get("SPENDSIGHT_TODAY", "").strip()
    if not raw:
        return DEFAULT_TODAY
    if raw.lower() == "real":
        return date.today()
    return date.fromisoformat(raw)


# The design's savings accounts for the demo customer. The Junction data has no
# savings balances, so these are demo figures, not computed from transactions.
DEMO_SAVINGS: dict[str, tuple[dict, ...]] = {
    "CUST00007": (
        {
            "account_id": "ASP-9930",
            "kind": "asp",
            "name": "ASP account",
            "subtitle": "First home savings",
            "last4": "9930",
            "balance_cents": 124_000,
            "goal_cents": 2_000_000,
            "goal_for": "your first home",
        },
        {
            "account_id": "STT-5512",
            "kind": "tuottotili",
            "name": "S-Tuottotili",
            "subtitle": "Savings account",
            "last4": "5512",
            "balance_cents": 15_000,
            "goal_cents": None,
            "goal_for": None,
        },
    ),
}


def demo_savings(customer_id: str) -> list[dict]:
    return [dict(account) for account in DEMO_SAVINGS.get(customer_id, ())]
