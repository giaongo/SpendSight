"""Impulse spending, runway and subscriptions: counted from the transactions, no model call."""

from datetime import date, timedelta

import pytest

from app.customer_data import CustomerBook
from app.insight_cards import insight_answer, insight_kind

@pytest.fixture(autouse=True)
def no_benchmark(monkeypatch):
    """The own-history wording is tested without the dataset's typical-customer table."""
    monkeypatch.setattr("app.insight_cards._benchmark", lambda month: None)


QUESTIONS = {
    "impulse": ["When do I tend to impulse spend?", "Do I impulse buy a lot?"],
    "runway": [
        "How many days until my money runs out at my current pace?",
        "How long will my money last?",
    ],
    "subscriptions": [
        "What happens if I cancel my top 3 least-used subscriptions?",
        "Which subscriptions do I pay for?",
    ],
}


@pytest.mark.parametrize(("kind", "question"), [(k, q) for k, qs in QUESTIONS.items() for q in qs])
def test_questions_are_recognised(kind, question):
    assert insight_kind(question) == kind


@pytest.mark.parametrize("question", ["Where did my money go?", "What if I invest that money?", "How could I save more?"])
def test_other_questions_are_left_alone(question):
    assert insight_kind(question) is None


def _row(day: date, cents: int, *, category="Lifestyle", merchant="", kind="purchase", hour=21):
    return {
        "bank_account_id": "A", "tx_date": day.isoformat(), "tx_timestamp": f"{day.isoformat()}T{hour:02d}:10:00",
        "category": category, "amount_cents": cents, "balance_cents": 0, "transaction_type": kind,
        "merchant_id": merchant, "mcc_code": "",
    }


def _book(rows, balance=300_000):
    rows[-1]["balance_cents"] = balance
    return CustomerBook({"customer_id": "T1", "postal_code": "00100", "age": 30}, None, rows)


def test_subscription_is_found_and_priced():
    rows = [_row(date(2026, m, 5), -1299, category="Entertainment", merchant="MRC-ENT-1") for m in (6, 7, 8, 9)]
    rows += [_row(date(2026, 9, 28), 300_000, kind="salary", category="Salary")]
    card, lead, _ = insight_answer(_book(rows), "cancel my top 3 subscriptions")
    assert card["kind"] == "subscriptions"
    assert card["items"][0]["amount_cents"] == 1299
    assert "12,99" in lead.replace(".", ",") or "12.99" in lead
    assert "a year" in lead


def test_no_subscriptions_says_so():
    rows = [_row(date(2026, 9, d), -1000 - d * 37, category="Entertainment", merchant=f"M{d}") for d in (1, 8, 15)]
    rows += [_row(date(2026, 9, 28), 300_000, kind="salary", category="Salary")]
    card, lead, _ = insight_answer(_book(rows), "cancel my subscriptions")
    assert card["items"] == []
    assert "couldn't find any subscriptions" in lead


def test_runway_lasts_balance_over_daily_pace():
    start = date(2026, 9, 1)
    rows = [_row(start + timedelta(days=i), -10_000, category="Groceries", hour=12) for i in range(29)]
    rows += [_row(date(2026, 8, 31), 300_000, kind="salary", category="Salary")]
    rows.sort(key=lambda r: r["tx_date"])
    card, lead, _ = insight_answer(_book(rows, balance=290_000), "how long will my money last")
    pace = card["items"][0]["amount_cents"]
    assert 9_000 <= pace <= 10_000  # 29 days of 100 EUR over a 30-day window
    assert f"about {290_000 // pace} days" in lead


def test_impulse_compares_with_the_customers_usual_month():
    rows = [_row(date(2026, 8, 3 + 7 * i), -3000, category="Restaurants", hour=12) for i in range(4)]
    # September: 6 dinners at 120 EUR, four of them on a Friday or Saturday evening.
    for day in (4, 5, 11, 12, 15, 22):
        rows.append(_row(date(2026, 9, day), -12_000, category="Restaurants", hour=20))
    rows.append(_row(date(2026, 9, 28), 300_000, kind="salary", category="Salary"))
    rows.sort(key=lambda r: r["tx_date"])
    card, lead, detail = insight_answer(_book(rows), "when do I impulse spend")
    assert card["kind"] == "impulse_pattern"
    assert lead.startswith("Yes, quite a lot")
    assert "times your usual" in lead
    assert "Friday to Sunday evenings" in detail
    assert len(card["items"]) == 7


def test_impulse_rent_bills_and_groceries_do_not_count():
    rows = [_row(date(2026, 9, d), -9000, category="Groceries", hour=19) for d in range(1, 9)]
    rows += [_row(date(2026, 9, 9), -90_000, category="Housing", hour=9)]
    card, lead, _ = insight_answer(_book(rows), "when do I impulse spend")
    assert "can't see an impulse pattern" in lead
    assert card["items"] == []


def test_impulse_calm_month_is_not_called_a_problem():
    rows = [_row(date(2026, 8, 3 + 7 * i), -12_000, category="Restaurants", hour=12) for i in range(4)]
    rows += [_row(date(2026, 9, 1 + 7 * i), -3000, category="Restaurants", hour=12) for i in range(5)]
    rows.sort(key=lambda r: r["tx_date"])
    _, lead, _ = insight_answer(_book(rows), "when do I impulse spend")
    assert lead.startswith("Not much lately")


@pytest.mark.parametrize("question", [q for qs in QUESTIONS.values() for q in qs])
def test_endpoint_answers_without_the_model(client, monkeypatch, question):
    monkeypatch.delenv("VERDA_API_KEY", raising=False)
    response = client.post("/customers/CUST00002/coach/ask", json={"text": question})
    assert response.status_code == 200
    body = response.json()
    assert body["card"]["kind"] in {"impulse_pattern", "runway", "subscriptions"}
    assert body["caption"] == "Calculated from your transactions"


def test_impulse_names_a_typical_customer_when_a_benchmark_exists(monkeypatch):
    monkeypatch.setattr(
        "app.insight_cards._benchmark",
        lambda month: {"customers": 100, "median_extras_cents": 30_000, "median_share_pct": 40},
    )
    # Every month is as expensive as this one, so the customer is "usual" but still above typical.
    rows = []
    for month in (7, 8, 9):
        for day in (3, 7, 10, 14, 17, 24):
            rows.append(_row(date(2026, month, day), -12_500, category="Restaurants", hour=20))
    rows.append(_row(date(2026, 9, 28), 300_000, kind="salary", category="Salary"))
    rows.sort(key=lambda r: r["tx_date"])
    card, lead, detail = insight_answer(_book(rows), "when do I impulse spend")
    assert lead.startswith("Yes, more than a typical customer")
    assert "against about" in lead
    assert "typical customer" in detail and "outside rent" in detail
    assert "usual level, month after month" in detail
    assert card["reference"] is not None


def test_the_benchmark_file_covers_the_demo_month():
    from app.insight_cards import _load_benchmarks

    bench = _load_benchmarks().get("2026-09")
    assert bench and bench["customers"] > 1000 and bench["median_extras_cents"] > 0
