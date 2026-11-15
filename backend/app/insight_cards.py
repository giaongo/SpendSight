"""Cards for three questions every customer should be able to ask.

- "When do I tend to impulse spend?"
- "How many days until my money runs out at my current pace?"
- "What happens if I cancel my top 3 subscriptions?"

Every figure is counted from the customer's own transactions, and the sentences are fixed
text, so these answers need no model call. When the data does not support an answer (no
subscriptions, too few small purchases) the card says so instead of inventing one.
"""

from __future__ import annotations

import re
import json
from collections import defaultdict
from functools import lru_cache
from pathlib import Path
from datetime import date, timedelta
from statistics import median

from app.answer_cards import _figure, _item, _with_bars, month_name
from app.customer_data import is_essential
from app.demo import demo_today
from app.money import eur, js_round

_IMPULSE = re.compile(r"impulse|splurge|spontaneous|when do i (?:tend to )?(?:spend|buy|shop)", re.IGNORECASE)
_RUNWAY = re.compile(
    r"run out|runs out|ran out|days until|days will my|how long (?:will|does|do) my (?:money|balance)|"
    r"money (?:last|lasts)|runway",
    re.IGNORECASE,
)
_SUBS = re.compile(r"subscription|recurring (?:payment|charge|cost)|cancel .{0,30}(?:top|least)", re.IGNORECASE)

EVENING_HOUR = 17
MIN_IMPULSE = 5
_NOT_IMPULSE = {"Groceries", "Salary", "Invest Deposit"}
_OUTSIDE = ("Housing", "Salary", "Invest Deposit")  # not counted in "what you spend outside rent"
_BENCHMARKS = Path(__file__).with_name("benchmarks.json")
WEEKDAYS = ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
_HOUR = re.compile(r"[T :]([0-9]{2}):[0-9]{2}")
NOTE = "Calculated from your transactions by SpendSight's tools, not guessed by AI."


def insight_kind(question: str) -> str | None:
    if _SUBS.search(question):
        return "subscriptions"
    if _RUNWAY.search(question):
        return "runway"
    if _IMPULSE.search(question):
        return "impulse"
    return None


def insight_answer(book, question: str) -> tuple[dict, str, str] | None:
    """(card, lead, detail) for one of the three questions, or None when it is not one of them."""
    kind = insight_kind(question)
    if kind is None:
        return None
    return {"impulse": impulse, "runway": runway, "subscriptions": subscriptions}[kind](book)


def _hour(row: dict) -> int | None:
    found = _HOUR.search(row.get("tx_timestamp") or "")
    return int(found.group(1)) if found else None


def _pct(part: int, whole: int) -> int:
    return js_round(part * 100 / whole) if whole else 0


@lru_cache(maxsize=1)
def _load_benchmarks() -> dict:
    """Typical-customer figures per month, built by scripts/make_benchmarks.py."""
    try:
        return json.loads(_BENCHMARKS.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def _benchmark(month: str) -> dict | None:
    return _load_benchmarks().get(month)


def _discretionary(row: dict) -> bool:
    """Spending you choose: not rent, bills, health, transport or groceries."""
    return row["amount_cents"] < 0 and not is_essential(row) and row["category"] not in _NOT_IMPULSE


def impulse(book) -> tuple[dict, str, str]:
    """Discretionary spending this month against the customer's own usual month, and when it happens.

    Any size counts: a 120 EUR dinner is the kind of purchase that makes a month jump, so a small-purchase
    filter would miss it.
    """
    month = book.latest_month
    rows = [row for row in book.months.get(month, []) if _discretionary(row)]
    prior = sorted(m for m in book.months if m < month)[-6:]
    totals = [t for m in prior if (t := sum(-r["amount_cents"] for r in book.months[m] if _discretionary(r))) > 0]
    usual = js_round(median(totals)) if totals else None
    card = {
        "kind": "impulse_pattern",
        "title": "Your impulse spending",
        "context": f"{month_name(month)}: eating out, shopping and fun",
        "options": None,
        "how": (
            f"Every purchase in {month_name(month)} outside rent, bills, health, transport and groceries. "
            "Your usual month is the median of your earlier months. Friday to Sunday evenings are purchases "
            f"from {EVENING_HOUR}:00. {NOTE}"
        ),
        "screen": "spending",
    }
    if len(rows) < MIN_IMPULSE:
        card.update(items=[], headline=None, verdict="Not enough purchases to see a pattern.")
        return card, "I can't see an impulse pattern yet.", f"You made {len(rows)} everyday purchases in {month_name(month)}."
    by_day = [0] * 7
    count_day = [0] * 7
    by_category: dict[str, int] = defaultdict(int)
    evening = []
    for row in rows:
        day = date.fromisoformat(row["tx_date"]).weekday()
        by_day[day] += -row["amount_cents"]
        count_day[day] += 1
        by_category[row["category"]] += -row["amount_cents"]
        hour = _hour(row)
        if day >= 4 and hour is not None and hour >= EVENING_HOUR:
            evening.append(row)
    total = sum(by_day)
    top = max(by_category, key=by_category.get)
    evening_total = -sum(row["amount_cents"] for row in evening)
    peak = max(range(7), key=lambda d: by_day[d])
    shown = month_name(month)
    bench = _benchmark(month)
    own = total / usual if usual else None
    if bench:
        typical = bench["median_extras_cents"]
        ratio = total / typical
        if ratio >= 1.3:
            lead = (
                f"Yes, more than a typical customer. In {shown} you spent {eur(total)} on {top.lower()} and "
                f"other extras, against about {eur(typical)} for a typical customer."
            )
        elif ratio <= 0.75:
            lead = (
                f"Less than a typical customer. In {shown} you spent {eur(total)} on extras, against about "
                f"{eur(typical)} for a typical customer."
            )
        else:
            lead = (
                f"About the same as a typical customer. In {shown} you spent {eur(total)} on extras, "
                f"and a typical customer spends about {eur(typical)}."
            )
    elif own is not None:
        times = f"{own:.0f}" if own >= 10 else f"{own:.1f}".rstrip("0").rstrip(".")
        if own >= 1.5:
            lead = (
                f"Yes, quite a lot. In {shown} you spent {eur(total)} on {top.lower()} and other extras, "
                f"about {times} times your usual {eur(usual)}."
            )
        elif own <= 0.75:
            lead = f"Not much lately. In {shown} you spent {eur(total)} on extras, less than your usual {eur(usual)}."
        else:
            lead = f"About as usual. In {shown} you spent {eur(total)} on extras, close to your usual {eur(usual)}."
    else:
        lead = f"In {shown} you spent {eur(total)} on {top.lower()} and other extras."
    parts = []
    base = -sum(r["amount_cents"] for r in book.months.get(month, []) if r["amount_cents"] < 0 and r["category"] not in _OUTSIDE)
    if bench and base > 0:
        parts.append(
            f"That is {_pct(total, base)} % of what you spend outside rent, against {bench['median_share_pct']} % "
            "for a typical customer."
        )
    if own is not None and 0.75 < own < 1.3 and bench and total / bench["median_extras_cents"] >= 1.3:
        parts.append("It is your usual level, month after month.")
    elif own is not None and own >= 1.5:
        parts.append(f"It is also well above your own usual month of {eur(usual)}.")
    if evening:
        parts.append(
            f"{len(evening)} of your {len(rows)} purchases were on Friday to Sunday evenings, together {eur(evening_total)}."
        )
    if len(evening) >= 3 or _pct(len(evening), len(rows)) >= 40:
        parts.append("A small budget for weekend evenings, set before you go out, could help.")
    detail = " ".join(parts) or "Nothing stands out."
    items = [
        _item(
            WEEKDAYS[d],
            WEEKDAYS[d],
            by_day[d],
            detail=f"{count_day[d]} purchase{'s' if count_day[d] != 1 else ''}",
            highlight=d == peak,
        )
        for d in range(7)
    ]
    card.update(
        headline=_figure(f"Extras in {shown} ({len(rows)} purchases)", total),
        reference=_figure("Your usual month", usual) if usual else None,
        verdict=f"Biggest day: {WEEKDAYS[peak]}",
        verdict_detail=detail,
        items=_with_bars(items),
    )
    return card, lead, detail


def _next_payday(book, today: date) -> date | None:
    salaries = [date.fromisoformat(row["tx_date"]) for row in book.rows if row["transaction_type"] == "salary"]
    if not salaries:
        return None
    gaps = [(b - a).days for a, b in zip(salaries, salaries[1:])][-3:]
    gap = js_round(median(gaps)) if gaps else 30
    payday = salaries[-1] + timedelta(days=max(gap, 7))
    while payday < today:
        payday += timedelta(days=max(gap, 7))
    return payday


def runway(book) -> tuple[dict, str, str]:
    """How long the balance lasts at the pace of the last 30 days, against the next payday."""
    today = demo_today()
    last = date.fromisoformat(book.last_date)
    since = (last - timedelta(days=30)).isoformat()
    spent = -sum(row["amount_cents"] for row in book.rows if row["amount_cents"] < 0 and row["tx_date"] > since)
    daily = js_round(spent / 30)
    balance = book.balance_cents
    payday = _next_payday(book, today)
    days_to_payday = (payday - today).days if payday else None
    card = {
        "kind": "runway",
        "title": "How long your money lasts",
        "context": "At the pace of the last 30 days",
        "options": None,
        "how": (
            f"Your balance divided by what left your account per day over the last 30 days "
            f"({eur(daily)} a day, rent and bills included). Payday is estimated from the gaps between "
            f"your last salaries. {NOTE}"
        ),
        "screen": "today",
    }
    items = [_item("pace", "You spend about, per day", daily)]
    if daily <= 0:
        card.update(headline=_figure("Balance", balance), verdict="You spent nothing recently.", items=items)
        return card, f"Your balance is {eur(balance)} and nothing has left your account in 30 days.", ""
    days = balance // daily if balance > 0 else 0
    parts = [f"At the pace you've spent lately, your money would last about {days} days."]
    detail = ""
    if days_to_payday is not None:
        parts.append(f"Your next payday is only about {days_to_payday} days away.")
        needed = js_round(balance / max(days_to_payday, 1))
        items.append(_item("room", "You could spend up to, per day, and still reach payday", needed, highlight=True))
        if days < days_to_payday:
            cut = daily - needed
            detail = f"To make it to payday, spend about {eur(cut)} less per day."
        else:
            detail = "You make it to payday with room to spare."
    card.update(
        headline=_figure("Balance", balance),
        verdict=f"Lasts about {days} days",
        verdict_detail=detail or None,
        items=_with_bars(items),
    )
    return card, " ".join(parts), detail


def subscriptions(book) -> tuple[dict, str, str]:
    """Charges that repeat for about the same amount in at least three months in a row."""
    groups: dict[tuple[str, str], list[dict]] = defaultdict(list)
    for row in book.rows:
        key = (row.get("merchant_id") or "").strip()
        if row["amount_cents"] < 0 and key and not is_essential(row) and row["category"] not in ("Groceries", "Restaurants"):
            groups[(key, row["category"])].append(row)
    found = []
    for (merchant, category), rows in groups.items():
        months = sorted({row["tx_date"][:7] for row in rows})
        amounts = [-row["amount_cents"] for row in rows]
        mean = sum(amounts) / len(amounts)
        steady = (max(amounts) - min(amounts)) <= 0.05 * mean
        once_a_month = len(rows) <= len(months) * 1.3
        if len(months) >= 3 and steady and once_a_month:
            found.append((js_round(mean), merchant, category, len(months)))
    found.sort(reverse=True)
    card = {
        "kind": "subscriptions",
        "title": "Your subscriptions",
        "context": "Payments that repeat monthly for the same amount",
        "options": None,
        "how": (
            "A subscription here is a payment to the same merchant, for about the same amount, in at least "
            f"3 months. Rent, bills, groceries and restaurants are not counted. {NOTE}"
        ),
        "screen": "spending",
    }
    if not found:
        card.update(
            headline=None,
            verdict="No subscriptions found",
            verdict_detail="Apart from your regular bills, none of your payments repeat for the same amount each month.",
            items=[],
        )
        return card, "Good news: I couldn't find any subscriptions in your transactions.", "So there's nothing to cancel here."
    top = found[:3]
    month_total = sum(amount for amount, *_ in top)
    items = [
        _item(merchant, f"{category} · {merchant}", amount, detail=f"{months} months in a row")
        for amount, merchant, category, months in top
    ]
    card.update(
        headline=_figure(f"Top {len(top)} together, per month", month_total),
        verdict=f"Cancelling them saves {eur(month_total * 12)} a year",
        verdict_detail=None,
        items=_with_bars(items),
    )
    lead = f"Cancelling your top {len(top)} subscriptions would free up {eur(month_total)} a month, about {eur(month_total * 12)} a year."
    return card, lead, "Check which ones you actually use before you cancel."
