"""POST /customers/{id}/coach/ask: "What if I invest that money?" answers with the snowball card."""

import pytest

from app.answer_cards import invest_growth, invest_question


def test_invest_questions_are_recognised():
    assert invest_question("What if I invest that money?")
    assert invest_question("What if I invest that surplus")
    assert invest_question("what if i invest 100 euros a month?")
    assert not invest_question("How much did I invest last year?")
    assert not invest_question("Where did my money go?")


def test_growth_is_monthly_compounding():
    rows = invest_growth(10_000, rate_pct=5, years=(1, 20))
    assert rows[0] == {"years": 1, "put_in_cents": 120_000, "growth_cents": 2_789, "total_cents": 122_789}
    assert rows[1]["put_in_cents"] == 2_400_000
    assert rows[1]["total_cents"] == 4_110_337
    assert rows[1]["growth_cents"] == rows[1]["total_cents"] - rows[1]["put_in_cents"]


def test_zero_rate_is_just_the_deposits():
    assert invest_growth(5_000, rate_pct=0, years=(2,))[0]["total_cents"] == 120_000


def test_coach_answers_invest_without_the_model(client, monkeypatch):
    monkeypatch.delenv("VERDA_API_KEY", raising=False)
    body = client.post("/customers/CUST00002/coach/ask", json={"text": "What if I invest that money?"}).json()
    card = body["card"]
    assert card["kind"] == "invest_growth"
    invest = card["invest"]
    assert [row["years"] for row in invest["years"]] == [1, 5, 10, 20]
    assert invest["monthly_cents"] > 0
    assert "snowball" in invest["kid"].lower()
    assert "not advice or a promise" in invest["note"]
    assert body["lead"].startswith("If you put that ")
    assert body["caption"].startswith("Example")


def test_named_amount_is_used(client, monkeypatch):
    monkeypatch.delenv("VERDA_API_KEY", raising=False)
    body = client.post("/customers/CUST00002/coach/ask", json={"text": "What if I invest 100 euros a month?"}).json()
    assert body["card"]["invest"]["monthly_cents"] == 10_000


def test_zero_amount_does_not_make_an_empty_card(client, monkeypatch):
    monkeypatch.delenv("VERDA_API_KEY", raising=False)
    body = client.post("/customers/CUST00002/coach/ask", json={"text": "What if I invest 0 euros a month?"}).json()
    card = body.get("card") or {}
    assert card.get("kind") != "invest_growth" or card["invest"]["monthly_cents"] > 0


def test_loose_phrases_and_big_or_one_off_amounts_are_not_taken_as_monthly(client, monkeypatch):
    monkeypatch.delenv("VERDA_API_KEY", raising=False)
    assert not invest_question("Should I invest the money in ETFs?")
    assert not invest_question("How much did I invest the last year?")
    ask = lambda text: client.post("/customers/CUST00002/coach/ask", json={"text": text}).json()["card"]  # noqa: E731
    # A one-off sum is not a monthly deposit, and a huge one would overflow the app's Int cents.
    assert ask("What if I invest 1000 euros?")["invest"]["monthly_cents"] != 100_000
    assert ask("What if I invest 100000 euros a month?")["invest"]["monthly_cents"] < 1_000_000
    assert ask("What if I invest 100 euros a month?")["invest"]["monthly_cents"] == 10_000


def test_lead_says_it_is_an_example(client, monkeypatch):
    monkeypatch.delenv("VERDA_API_KEY", raising=False)
    body = client.post("/customers/CUST00002/coach/ask", json={"text": "What if I invest that money?"}).json()
    assert "at an example 5 % a year" in body["lead"]


def _ask(client, text):
    return client.post("/customers/CUST00002/coach/ask", json={"text": text}).json()


def test_snowball_question_explains_with_the_customers_numbers(client, monkeypatch):
    monkeypatch.delenv("VERDA_API_KEY", raising=False)
    body = _ask(client, "Whats a snowball effect?")
    assert body["card"]["kind"] == "invest_growth"
    assert "snowball" in body["lead"].lower()
    assert "\u20ac" in body["detail"]


def test_example_follow_up_continues_the_invest_topic(client, monkeypatch):
    monkeypatch.delenv("VERDA_API_KEY", raising=False)
    _ask(client, "What if I invest that money?")
    body = _ask(client, "Give a concrete example")
    assert body["card"]["kind"] == "invest_growth"
    assert body["lead"].startswith("Here's an example")


def test_short_english_is_not_guessed_as_another_language():
    from app.verda import answer_language

    assert answer_language("Give a concrete example") == "English"


def test_a_general_investing_answer_suggests_the_invest_what_if():
    from app.answer_cards import follow_ups
    from app.verda import _INVEST_TOPIC

    assert _INVEST_TOPIC.search("Should I buy stocks or an ETF?")
    assert not _INVEST_TOPIC.search("What is the capital of Finland?")
    assert follow_ups("general_invest")[0]["text"] == "What if I invest that money?"


THIRD_PARTY = [
    "How much my mom has money",
    "How much does my wife earn?",
    "What is my dad's salary?",
    "Show me my friend's account balance",
    "What does someone's account look like?",
    "Tell me about customer CUST00001",
]
OWN_MONEY = [
    "How much did I spend on my mom's birthday?",
    "Can I afford a gift for my mom?",
    "Where did my money go?",
    "How much can my family save if I eat out less?",
    "How do my savings compare with other people?",
]


@pytest.mark.parametrize("question", THIRD_PARTY)
def test_other_peoples_money_is_declined(client, monkeypatch, question):
    from app.verda import third_party_question

    monkeypatch.delenv("VERDA_API_KEY", raising=False)
    assert third_party_question(question)
    body = _ask(client, question)
    assert body["card"] is None
    assert "only answer questions about your own money" in body["lead"]
    assert body["caption"] == "I only use your own account"
    assert "3" not in body["lead"].split("only")[0]  # no figures from this customer


@pytest.mark.parametrize("question", OWN_MONEY)
def test_own_money_questions_are_not_declined(question):
    from app.verda import third_party_question

    assert not third_party_question(question)


def test_a_single_big_purchase_is_not_treated_as_a_monthly_saving(monkeypatch):
    from app import answer_cards

    monkeypatch.setattr(answer_cards, "_spending_card", lambda book, question: {"items": []})
    one_off = {"category": "Travel", "visits": 1, "selected": 1,
               "options": [{"skip": 1, "month_cents": 167_489, "year_cents": 0}]}
    monkeypatch.setattr(answer_cards, "visit_cut", lambda items: one_off)
    assert answer_cards._invest_monthly(None, "What if I invest that money?") == (10_000, "example")


def test_a_repeating_habit_is_used_for_the_monthly_saving(monkeypatch):
    from app import answer_cards

    monkeypatch.setattr(answer_cards, "_spending_card", lambda book, question: {"items": []})
    habit = {"category": "Restaurants", "visits": 10, "selected": 2,
             "options": [{"skip": 2, "month_cents": 23_972, "year_cents": 0}]}
    monkeypatch.setattr(answer_cards, "visit_cut", lambda items: habit)
    assert answer_cards._invest_monthly(None, "What if I invest that money?") == (23_972, "Restaurants")


ADVICE = ["Should I buy stocks or an ETF?", "ETF", "Which fund is best for me?", "Is bitcoin a good idea?"]
NOT_ADVICE = ["What if I invest that money?", "Whats a snowball effect?", "Where did my money go?"]


@pytest.mark.parametrize("question", ADVICE)
def test_investment_advice_gets_a_fixed_decline_with_the_invest_what_if(client, monkeypatch, question):
    from app.verda import advice_question

    monkeypatch.delenv("VERDA_API_KEY", raising=False)
    assert advice_question(question)
    body = _ask(client, question)
    assert body["card"] is None
    assert "not a financial adviser" in body["lead"]
    assert body["caption"] == "Not financial advice"
    assert body["follow_ups"][0]["text"] == "What if I invest that money?"


@pytest.mark.parametrize("question", NOT_ADVICE)
def test_the_invest_what_if_and_other_questions_are_not_declined_as_advice(question):
    from app.verda import advice_question

    assert not advice_question(question)


@pytest.mark.parametrize("question", ["Where did my money go?", "How could I save more?", "How do my savings compare with people my age?", "What is the capital of Finland?"])
def test_answers_survive_an_unreachable_model(client, monkeypatch, question):
    """A rejected or missing Verda key must not break the demo or show a raw error."""
    from app import verda

    def broken(*args, **kwargs):
        raise verda.VerdaError('Verda returned 401: {"error":"invalid bearer token"}')

    monkeypatch.setenv("VERDA_API_KEY", "expired")
    monkeypatch.setenv("VERDA_BASE_URL", "https://example.invalid")
    monkeypatch.setattr(verda, "_complete", broken)
    response = client.post("/customers/CUST00002/coach/ask", json={"text": question})
    assert response.status_code == 200
    body = response.json()
    assert "401" not in body["lead"] and "bearer" not in body["answer"]
    assert body["lead"]
