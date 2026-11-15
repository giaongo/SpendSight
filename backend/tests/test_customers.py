import pytest


@pytest.mark.full_data
def test_database_overview_matches_the_index(client):
    body = client.get("/database").json()
    assert body["customers"] == 5_100
    assert body["mortgage_applications"] == 200
    assert body["transactions"] == 3_626_755
    assert body["first_date"] == "2024-10-01"
    assert body["last_date"] == "2026-09-30"
    assert body["default_customer_id"] == "CUST00001"
    types = {row["transaction_type"]: row["count"] for row in body["transaction_types"]}
    assert types["purchase"] == 3_000_711
    assert types["salary"] == 121_725
    categories = {row["category"]: row["n"] for row in body["categories"]}
    assert categories["Groceries"] == 868_519


def test_any_customer_can_be_read(client):
    summary = client.get("/customers/CUST00002").json()
    assert summary["customer_id"] == "CUST00002"
    assert summary["account_id"] == "ACC000002"
    assert summary["balance_cents"] == 153_916
    assert summary["reads"]["today"] == "/customers/CUST00002/today"

    today = client.get("/customers/CUST00002/today").json()
    assert today["as_of"] == "2026-09-30"
    assert today["latest_salary_cents"] == 157_242
    assert today["month_out_cents"] == 68_450
    assert today["month_in_cents"] == 157_242

    spending = client.get("/customers/CUST00002/spending", params={"month": "2026-09"}).json()
    assert spending["line_count"] == 18
    assert spending["total_out_cents"] == 68_450

    save = client.get("/customers/CUST00002/save", params={"years": 1}).json()
    assert save["deposits"] == 0
    assert save["deposited_cents"] == 0

    months = client.get("/customers/CUST00002/months").json()
    assert months["months"][0]["month"] == "2024-10"
    assert months["months"][-1]["month"] == "2026-09"
    assert len(months["months"]) == 24

    assert client.get("/customers/CUST99999").status_code == 404
    assert client.get("/customers/CUST00002/spending", params={"month": "1999-01"}).status_code == 400


def test_the_default_reads_stay_on_the_first_customer(client):
    assert client.get("/today").json()["customer_id"] == "CUST00001"
    assert client.get("/customers/CUST00001/today").json()["balance_cents"] == 472_358


def test_spending_highlights_are_the_biggest_rise_and_its_friday_evenings(client):
    body = client.get("/customers/CUST00001/spending").json()
    change = body["highlights"]["spending_change"]
    assert change["month"] == body["month"]
    assert change["delta_cents"] == change["amount_cents"] - change["prior_cents"] > 0
    friday = body["highlights"]["friday_evenings"]
    if friday is not None:
        assert friday["category"] == change["category"]
        assert 0 < friday["amount_cents"] <= change["amount_cents"]


def test_safe_to_spend_is_the_weekly_budget_less_this_weeks_spending(client):
    safe = client.get("/customers/CUST00001/today").json()["safe_to_spend"]
    assert safe["today"] == "2026-09-30"
    assert safe["week_start"] == "2026-09-28"
    assert safe["days_left"] == 5
    assert safe["free_month_cents"] == safe["income_cents"] - safe["essentials_cents"]
    assert abs(safe["weekly_cents"] - safe["free_month_cents"] * 12 / 52) <= 0.5
    assert safe["left_cents"] == safe["weekly_cents"] - safe["spent_week_cents"]
    assert safe["status"] in {"on_track", "tight", "overdraft_risk"}


def test_demo_savings_accounts_only_for_the_demo_customer(client, monkeypatch):
    from app import demo

    assert client.get("/customers/CUST00001/save").json()["savings_accounts"] == []
    monkeypatch.setitem(demo.DEMO_SAVINGS, "CUST00001", demo.DEMO_SAVINGS["CUST00007"])
    body = client.get("/customers/CUST00001/save").json()
    asp, tuotto = body["savings_accounts"]
    assert body["savings_total_cents"] == 139_000
    assert (asp["name"], asp["balance_cents"], asp["goal_cents"], asp["goal_percent"]) == ("ASP account", 124_000, 2_000_000, 6)
    assert asp["goal_share"] == 0.062
    assert (tuotto["name"], tuotto["balance_cents"], tuotto["goal_cents"]) == ("S-Tuottotili", 15_000, None)


def test_baskets_split_money_out_and_count_taxis_as_everyday(client):
    from app.customer_data import baskets

    rows = [
        {"category": "Transportation", "mcc_code": "4121", "amount_cents": -3359, "transaction_type": "purchase"},
        {"category": "Transportation", "mcc_code": "5542", "amount_cents": -5000, "transaction_type": "purchase"},
        {"category": "Housing", "mcc_code": None, "amount_cents": -98031, "transaction_type": "housing_payment"},
        {"category": "Restaurants", "mcc_code": "5812", "amount_cents": -2000, "transaction_type": "purchase"},
        {"category": "Salary", "mcc_code": None, "amount_cents": 300000, "transaction_type": "salary"},
    ]
    split = baskets(rows)
    assert (split["essentials_cents"], split["everyday_cents"]) == (103031, 5359)

    body = client.get("/customers/CUST00001/spending").json()
    split = body["baskets"]
    assert split["essentials_cents"] + split["everyday_cents"] <= body["total_out_cents"]
