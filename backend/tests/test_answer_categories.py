"""The three answer groups of POST /customers/{id}/coach/ask, and the card under each answer."""

import json

import pytest

from app.answer_cards import (
    FOLLOW_UPS,
    guard_peer_spending,
    personal_kind,
    savings_goal,
    split_answer,
    tidy_answer,
)
from app.money import eur as spending_eur
from app.peers import AssetTable, age_group
from app.statfi import StatisticsApiError, StatisticsCache
from app.verda import question_category

AGES = ["18-", "18-34", "35-54", "55-64", "65-"]
INFO = [
    "vtutk_h_omistaja_osuus",
    "vtutk_h_p50",
    "vtutk_h_keskiarvo_eiehd",
    "vtutk_h_p25",
    "vtutk_h_p75",
]
# Deposits in whole euros by age: (share owning, median, mean, p25, p75)
DEPOSITS = {
    "18-": (99.0, 4000, 20000, 500, 15000),
    "18-34": (99.0, 1500, 7000, 200, 6000),
    "35-54": (99.0, 3100, 15000, 400, 12000),
    "55-64": (99.0, 7200, 29000, 1000, 29600),
    "65-": (99.0, 17500, 40000, 3000, 45000),
}
FUNDS = {age: (40.5, 9000, 12000, 1500, 30000) for age in AGES}


def _dimension(codes: list[str]) -> dict:
    return {"label": "x", "category": {"index": {code: i for i, code in enumerate(codes)}, "label": {}}}


def education_assets() -> dict:
    """A small json-stat2 table with the dimension order Statistics Finland returns."""
    dims = {
        "vtutk_varlaji_2_20211201": ["F2F5", "F2", "F5"],
        "koulutusaste_17_20180101": ["SSS", "7"],
        "sukupuoli_9_20180101": ["SSS", "1", "2"],
        "timeperiod_y": ["2025"],
        "ikaryhma_10_20180101": AGES,
        "contentscode": INFO,
    }
    values = []
    for asset in dims["vtutk_varlaji_2_20211201"]:
        for education in dims["koulutusaste_17_20180101"]:
            for sex in dims["sukupuoli_9_20180101"]:
                for age in AGES:
                    for i, _info in enumerate(INFO):
                        if education != "SSS" or sex != "SSS" or asset == "F2F5":
                            values.append(1)  # a cell this module must not read
                        elif asset == "F2":
                            values.append(DEPOSITS[age][i])
                        else:
                            values.append(FUNDS[age][i])
    return {
        "version": "2.0",
        "class": "dataset",
        "id": list(dims),
        "size": [len(codes) for codes in dims.values()],
        "dimension": {dim: _dimension(codes) for dim, codes in dims.items()},
        "value": values,
        "updated": "2026-09-17T05:00:00Z",
    }


class FakeModel:
    """Stands in for the Verda deployment and keeps every request."""

    def __init__(self, answer: str, classify: str = "personal"):
        self.answer = answer
        self.classify = classify
        self.requests: list[dict] = []

    def __call__(self, request, timeout=0):
        body = json.loads(request.data.decode())
        self.requests.append(body)
        system = body["messages"][0]["content"]
        content = self.classify if "Reply with one word" in system else self.answer
        model = self

        class Response:
            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def read(self):
                return json.dumps(
                    {"model": "test-model", "choices": [{"message": {"content": content}}]}
                ).encode()

        assert model is not None
        return Response()

    def answer_request(self) -> dict:
        return next(r for r in self.requests if "Reply with one word" not in r["messages"][0]["content"])


@pytest.fixture
def verda(monkeypatch):
    monkeypatch.setenv("VERDA_API_KEY", "test-key")
    monkeypatch.setenv("VERDA_BASE_URL", "https://containers.verda.com/demo")

    def install(answer: str, classify: str = "personal") -> FakeModel:
        fake = FakeModel(answer, classify)
        monkeypatch.setattr("app.verda.urlopen", fake)
        return fake

    return install


@pytest.mark.parametrize(
    ("question", "category"),
    [
        ("Where did my money go?", "personal_finance"),
        ("What kind of spender am I?", "personal_finance"),
        ("How could I save more?", "personal_finance"),
        ("What is my balance?", "personal_finance"),
        ("Mikä on saldoni?", "personal_finance"),
        ("What does a typical household spend?", "peer_comparison"),
        ("How do my savings compare with people my age?", "peer_comparison"),
        ("Am I spending more than other people?", "peer_comparison"),
        ("What is the average household spending in Finland?", "peer_comparison"),
        ("Kulutanko enemmän kuin muut ihmiset?", "peer_comparison"),
        ("Paljonko ikäiseni säästävät?", "peer_comparison"),
        ("What is the capital of Finland?", "general"),
        ("What do other people think of me?", "general"),
        ("Tell me a joke", "general"),
    ],
)
def test_questions_fall_into_three_groups(question, category):
    assert question_category(question) == category


def test_every_ask_next_chip_lands_in_its_own_group():
    for chips in FOLLOW_UPS.values():
        for text, category in chips:
            assert question_category(text) == category, text


def test_personal_questions_pick_a_card():
    assert personal_kind("Where did my money go?") == "spending_breakdown"
    assert personal_kind("How could I save more?") == "spending_breakdown"
    assert personal_kind("How did my spending change over the last months?") == "month_trend"
    assert personal_kind("What is my balance?") == "balance"
    assert personal_kind("How much have I saved?") == "savings"


def test_a_named_category_becomes_the_main_figure(client, verda):
    verda("You spent **€38.98** on restaurants in September.\n\nThat was 4 payments.")
    body = client.post(
        "/customers/CUST00002/coach/ask", json={"text": "How much did I spend on restaurants?"}
    ).json()
    card = body["card"]
    assert card["headline"] == {"label": "Restaurants, September 2026", "amount_cents": 3898, "amount": "€38.98"}
    assert [item["key"] for item in card["items"] if item["highlight"]] == ["Restaurants"]


def test_a_peer_answer_never_claims_a_spending_comparison():
    q = "Am I spending more than other people?"
    raw = (
        "No, you are not spending more than other people your age.\n\n"
        "Your S-Tili balance of €4,723.58 is below the median. You spent €2,114.38 in September."
    )
    assert guard_peer_spending(q, raw, "English") == (
        "I can compare your savings with people your age, not your spending.\n\n"
        "Your S-Tili balance of €4,723.58 is below the median. You spent €2,114.38 in September."
    )
    honest = "I can compare savings with people your age, not spending.\n\nYour balance is below the median."
    assert guard_peer_spending(q, honest, "English") == honest
    # A savings question is left alone.
    assert guard_peer_spending("How do my savings compare with people my age?", raw, "English") == raw


def test_the_answer_is_held_to_the_layout():
    raw = "**Headline:** Balance: €1,539.16\n\nParagraph 2: You spent $684.50.\n\nSome advice.\n\nMore."
    assert tidy_answer(raw, paragraphs=2) == "Balance: €1,539.16\n\nYou spent €684.50."
    assert tidy_answer("Helsinki.\n\nAnything else?", paragraphs=1) == "Helsinki."


def test_the_answer_splits_into_a_lead_and_a_detail():
    assert split_answer("You spent **€684.50**.\n\nMostly groceries.") == ("You spent **€684.50**.", "Mostly groceries.")
    assert split_answer("You spent **€684.50**. Mostly groceries.") == ("You spent **€684.50**.", "Mostly groceries.")
    assert split_answer("Helsinki.") == ("Helsinki.", None)


def test_a_personal_question_gets_a_card_built_from_the_transactions(client, verda):
    model = verda("You spent **€684.50** in September.\n\nGroceries were the largest part, €467.86.")
    response = client.post("/customers/CUST00002/coach/ask", json={"text": "Where did my money go?"})
    assert response.status_code == 200
    body = response.json()
    assert body["category"] == "personal_finance"
    assert body["lead"] == "You spent **€684.50** in September."
    assert body["detail"] == "Groceries were the largest part, €467.86."
    assert body["uses_customer_data"] is True
    assert body["caption"] == "AI answer · numbers come from your transactions"

    card = body["card"]
    spending = client.get("/customers/CUST00002/spending").json()
    assert card["kind"] == "spending_breakdown"
    assert card["context"] == "September 2026"
    assert card["headline"]["amount_cents"] == spending["total_out_cents"] == 68450
    assert card["items"][0] == {
        "key": "Groceries",
        "label": "Groceries",
        "amount_cents": 46786,
        "amount": "€467.86",
        "detail": "68% · 6 payments",
        "highlight": False,
        "bar_pct": 100,
    }
    assert sum(item["amount_cents"] for item in card["items"]) == spending["total_out_cents"]
    assert card["table"]["columns"] == ["Category", "Spent", "Share"]
    assert card["screen"] == "spending"
    assert [chip["category"] for chip in body["follow_ups"]] == ["personal_finance", "peer_comparison"]

    sent = model.answer_request()
    payload = json.loads(sent["messages"][1]["content"])
    assert payload["card"]["main_figure"] == "Money out, September 2026: €684.50"
    assert "facts" in payload and "peers" not in payload
    assert "_cents" not in sent["messages"][1]["content"]
    assert "Write exactly two short paragraphs" in sent["messages"][0]["content"]


def test_a_peer_question_compares_with_statistics_finland(client, verda):
    client.app.state.statistics = StatisticsCache(fetch=education_assets)
    model = verda("I can compare savings with people your age, not spending.\n\nYour balance is below the median.")
    response = client.post("/customers/CUST00002/coach/ask", json={"text": "What does a typical household spend?"})
    assert response.status_code == 200
    body = response.json()
    assert body["category"] == "peer_comparison"
    assert body["uses_customer_data"] is True
    assert "Statistics Finland" in body["caption"]

    card = body["card"]
    assert card["kind"] == "peer_compare"
    # CUST00002 is 47, so the group is 35–54. The balance is €1,539.16.
    assert card["context"] == "Bank deposits · aged 35–54 · Finland 2025"
    assert card["headline"]["amount_cents"] == 153916
    assert card["reference"] == {"label": "Median deposits, aged 35–54", "amount_cents": 310000, "amount": "€3,100.00"}
    assert card["verdict"] == "Below the middle for people aged 35–54"
    assert card["verdict_detail"] == "€1,560.84 less than the median for people aged 35–54"
    assert [item["key"] for item in card["items"]] == ["you", "p75", "median", "p25"]
    assert card["items"][0]["highlight"] is True
    assert card["options"]["selected"] == "35-54"
    assert card["options"]["note"] == "Set from your details: you are 47."
    choices = {choice["key"]: choice for choice in card["options"]["choices"]}
    assert list(choices) == ["18-34", "35-54", "55-64", "65-"]
    assert choices["18-34"]["verdict"] == "Above the middle for people aged 18–34"
    assert choices["65-"]["verdict"] == "Lower than most people aged 65 and over"
    assert ["Own listed shares or funds, aged 35–54", "40.5%"] in card["table"]["rows"]

    sent = model.answer_request()
    payload = json.loads(sent["messages"][1]["content"])
    peers = payload["peers"]
    assert peers["people_your_age"]["deposits_median"] == "€3,100.00"
    assert peers["comparison"]["position"] == "Below the middle for people aged 35–54"
    # The customer's own figures go with the statistics, counted by the service.
    spending = client.get("/customers/CUST00002/spending").json()
    assert peers["you"]["s_tili_balance"] == "€1,539.16"
    assert peers["you"]["age_group"] == "35–54"
    assert peers["you"]["latest_month"] == "September 2026"
    assert peers["you"]["money_out_latest_month"] == spending_eur(spending["total_out_cents"]) == "€684.50"
    assert peers["you"]["usual_month_money_out"] == "€1,813.53"
    assert peers["you"]["latest_salary"] == "€1,572.42"
    assert peers["you"]["investment_deposits_paid_in_last_5_years"] == "€0.00"
    assert ["Your money out, September 2026", "€684.50"] in card["table"]["rows"]
    # Only the comparison leaves the service: no transactions, ids, or postal code.
    assert "facts" not in payload
    text = sent["messages"][1]["content"]
    assert "CUST00002" not in text and "00530" not in text and "_cents" not in text
    assert "not spending" in sent["messages"][0]["content"]


def test_a_peer_question_without_statistics_does_not_call_the_model(client, verda):
    def offline():
        raise StatisticsApiError("Could not reach Statistics Finland API")

    client.app.state.statistics = StatisticsCache(fetch=offline)
    model = verda("should not be used")
    response = client.post("/customers/CUST00002/coach/ask", json={"text": "How do my savings compare with people my age?"})
    assert response.status_code == 200
    body = response.json()
    assert body["category"] == "peer_comparison"
    assert body["card"] is None
    assert body["model"] == ""
    assert body["lead"].startswith("I can’t reach Statistics Finland")
    assert model.requests == []


def test_a_general_question_has_no_card_and_no_figures(client, verda):
    model = verda("Helsinki.")
    response = client.post("/customers/CUST00002/coach/ask", json={"text": "What is the capital of Finland?"})
    body = response.json()
    assert body["category"] == "general"
    assert body["card"] is None
    assert body["lead"] == "Helsinki."
    assert body["detail"] is None
    assert body["uses_customer_data"] is False
    assert body["caption"] == "AI answer · not based on your transactions"
    payload = json.loads(model.answer_request()["messages"][1]["content"])
    assert set(payload) == {"question", "language"}


def test_the_model_sorts_a_question_the_patterns_cannot(client, verda):
    client.app.state.statistics = StatisticsCache(fetch=education_assets)
    model = verda("Antwort.", classify="peer")
    response = client.post("/customers/CUST00001/coach/ask", json={"text": "Wie viel sparen Leute in meinem Alter?"})
    body = response.json()
    assert body["category"] == "peer_comparison"
    assert body["card"]["options"]["selected"] == "55-64"
    assert any("personal, peer, or general" in r["messages"][0]["content"] for r in model.requests)


def test_the_asset_table_reads_cells_by_code():
    table = AssetTable(education_assets())
    assert table.year == "2025"
    assert table.value(asset="F2", age="55-64", info="vtutk_h_p50") == 7200
    assert table.value(asset="F5", age="18-34", info="vtutk_h_omistaja_osuus") == 40.5
    assert table.value(asset="F2", age="99-", info="vtutk_h_p50") is None
    assert [age_group(age) for age in (17, 18, 34, 35, 54, 55, 64, 65, None)] == [
        "18-", "18-34", "18-34", "35-54", "35-54", "55-64", "55-64", "65-", "18-",
    ]


def test_the_statistics_cache_keeps_a_response_and_retries_a_failure():
    calls = []
    now = [0.0]

    def fetch():
        calls.append(now[0])
        if len(calls) == 1:
            raise StatisticsApiError("offline")
        return {"ok": len(calls)}

    cache = StatisticsCache(fetch=fetch, ttl_seconds=10, clock=lambda: now[0])
    with pytest.raises(StatisticsApiError):
        cache.education_assets()
    assert cache.education_assets() == {"ok": 2}
    now[0] = 5
    assert cache.education_assets() == {"ok": 2}
    now[0] = 11
    assert cache.education_assets() == {"ok": 3}


@pytest.mark.parametrize(
    ("question", "goal"),
    [
        ("I want to save up 500e in next 3 months", (50000, 3)),
        ("How can I save €1,500 in six months?", (150000, 6)),
        ("Can I put aside 250 euros in a year?", (25000, 12)),
        ("Haluan säästää 500 € kolmessa kuukaudessa", (50000, 3)),
        ("Jag vill spara 900 € på 3 månader", (90000, 3)),
    ],
)
def test_a_savings_target_is_read_from_the_question(question, goal):
    assert savings_goal(question) == goal
    assert personal_kind(question) == "savings_goal"
    assert question_category(question) == "personal_finance"


def test_a_question_without_a_target_keeps_its_card():
    assert savings_goal("How could I save more?") is None
    assert savings_goal("How much have I saved?") is None
    assert savings_goal("I want to save 500e") is None


def test_a_savings_goal_gets_a_monthly_plan(client, verda):
    model = verda("Put aside **€166.67** a month.\n\nThat fits what your usual month leaves.")
    response = client.post(
        "/customers/CUST00002/coach/ask", json={"text": "I want to save up 500e in next 3 months"}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["category"] == "personal_finance"
    card = body["card"]
    assert card["kind"] == "savings_goal"
    assert card["title"] == "Saving €500.00 in 3 months"
    assert card["headline"]["amount_cents"] == 16667
    assert [row[1] for row in card["table"]["rows"]] == ["€166.67", "€166.67", "€166.66"]
    assert card["table"]["rows"][-1][2] == "€500.00"
    assert card["verdict"] is not None
    # The model is handed the monthly figure, so it never has to divide.
    assert "€166.67" in model.requests[-1]["messages"][1]["content"]


WHAT_IF = [
    "What if I ate out less?",
    "What if I skip 2 restaurant visits?",
    "What if I eat out less often?",
    "Mitä jos söisin vähemmän ulkona?",
    "Tänk om jag åt ute mindre?",
]


@pytest.mark.parametrize("question", WHAT_IF)
def test_eating_out_what_if_opens_the_visit_cut(question):
    from app.answer_cards import _SAVE_Q

    assert question_category(question) == "personal_finance"
    assert personal_kind(question) == "spending_breakdown"
    assert _SAVE_Q.search(question)


def test_other_what_if_questions_do_not_get_the_visit_cut():
    from app.answer_cards import _SAVE_Q

    assert not _SAVE_Q.search("What if I invest 100 euros a month?")
    assert not _SAVE_Q.search("What if interest rates rise?")
