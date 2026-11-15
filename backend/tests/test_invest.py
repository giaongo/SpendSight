"""POST /customers/{id}/coach/ask: "What if I invest that money?" answers with the snowball card."""

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
    assert "not a promise" in invest["note"]
    assert body["lead"].startswith("Invest ")
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
