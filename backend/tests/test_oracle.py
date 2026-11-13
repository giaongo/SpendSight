"""The design's selfCheck figures, returned by the service."""

ORACLE = {
    "october_out_cents": 224_390,
    "must_every_month_cents": 103_790,
    "must_when_it_happens_cents": 21_000,
    "everyday_cents": 99_600,
    "october_slip_cents": 35_600,
    "october_sgroup_cents": 65_200,
    "october_bonus_cents": 2_282,
    "november_out_cents": 121_640,
    "november_sgroup_cents": 21_400,
    "november_bonus_cents": 321,
    "holds_cents": 138_590,
    "left_after_salary_cents": 263_830,
    "safe_to_spend_cents": 102_830,
    "bills_before_payday_cents": 7_590,
    "steady_slip_cents": 6_400,
    "runway_days": 38,
    "runway_per_day_cents": 3_049,
    "runway_payments": 15,
    "impulse_day": "12 Nov",
    "impulse_payments": 3,
    "impulse_cents": 5_140,
    "dining_fifth_cents": 3_720,
    "dining_one_meal_cents": 8_268,
    "dining_two_meals_cents": 16_536,
    "subscriptions_month_cents": 4_990,
    "subscriptions_year_cents": 59_880,
    "jacket_bonus_gain_cents": 285,
    "jacket_net_cost_cents": 8_615,
    "steady_pile_5y_cents": 2_400_000,
    "loan_default_month_cents": 104_512,
    "loan_stress_6pct_cents": 127_572,
}


def test_summary_matches_the_oracle(client):
    got = client.get("/ledger/summary").json()
    for key, want in ORACLE.items():
        assert got[key] == want, key


def test_charts_match_the_thirteen_day_pace(client):
    rows = {row["label"]: row for row in client.get("/charts").json()["rows"]}
    assert rows["Groceries"] == {"label": "Groceries", "october_pace_cents": 27_733, "november_cents": 23_400}
    assert rows["Coffee & drinks"]["october_pace_cents"] == 3_727
    assert rows["Coffee & drinks"]["november_cents"] == 3_600
    assert rows["Dining out"] == {"label": "Dining out", "october_pace_cents": 8_060, "november_cents": 8_840}
    assert rows["Taxis"] == {"label": "Taxis", "october_pace_cents": 1_950, "november_cents": 2_600}


def test_today_is_the_first_customer(client):
    session = client.get("/session").json()
    today = client.get("/today").json()
    assert session["customer_id"] == "CUST00001"
    assert session["postal_code"] == "00990"
    assert session["age"] == 63
    assert session["account_id"] == "ACC000001"
    assert today["kind"] == "read"
    assert today["customer_id"] == session["customer_id"]
    assert today["as_of"] == "2026-09-30"
    assert today["balance_cents"] == 472_358
    assert today["latest_salary_cents"] == 244_781
    assert today["latest_salary_date"] == "2026-09-15"
    assert today["month"] == "2026-09"
    assert today["month_out_cents"] == 211_438
    assert today["month_in_cents"] == 244_781
    assert today["transaction_count"] == 581
    assert today["mortgage_customer_id"] is None


def test_spending_and_save_are_the_first_customer(client):
    spending = client.get("/spending").json()
    assert spending["kind"] == "read"
    assert spending["customer_id"] == "CUST00001"
    assert spending["month"] == "2026-09"
    assert spending["total_out_cents"] == 211_438
    assert spending["total_in_cents"] == 244_781
    assert spending["line_count"] == 32
    categories = {row["name"]: row["amount_cents"] for row in spending["categories"]}
    assert categories["Housing"] == -53_934
    assert categories["Groceries"] == -37_979
    assert categories["Salary"] == 244_781
    september = client.get("/spending", params={"month": "2026-09"}).json()
    assert september["line_count"] == 32
    assert client.get("/spending", params={"month": "oct"}).status_code == 400

    save = client.get("/save", params={"years": 5}).json()
    assert save["kind"] == "read"
    assert save["customer_id"] == "CUST00001"
    assert save["deposits"] == 24
    assert save["deposited_cents"] == 553_323
    assert save["latest_deposit_cents"] == 23_572
    assert save["latest_deposit_date"] == "2026-09-18"
    assert "does not move money" in save["note"]
    one_year = client.get("/save", params={"years": 1}).json()
    assert one_year["deposits"] == 12
    assert one_year["deposited_cents"] == 277_585
    assert client.get("/save", params={"years": 2}).status_code == 400

    asked = client.post(
        "/coach/ask",
        json={"text": "What is safe for me to spend this week?"},
    ).json()
    assert asked["refused"] is False
    assert asked["fact"]["figure"] == "€1,028.30"
    assert "kind" not in asked


def test_effort_changes_the_slip_and_not_safe_to_spend(client):
    big = client.put("/settings/effort", json={"effort": "big"}).json()
    assert big["today"]["save_cents"] == 80_000
    assert big["today"]["slip_cents"] == 4_000
    assert big["today"]["safe_to_spend_cents"] == 102_830
    small = client.put("/settings/effort", json={"effort": "small"}).json()
    assert small["today"]["save_cents"] == 5_000
    assert small["today"]["slip_cents"] == 8_307
    assert client.get("/ledger/summary").json()["safe_to_spend_cents"] == 102_830
    assert client.get("/ledger/summary").json()["steady_slip_cents"] == 6_400
    piles = {row["id"]: row["piles_cents"] for row in small["efforts"]}
    assert piles["steady"]["5"] == 2_400_000


def test_loan_sketch_is_arithmetic_not_an_offer(client):
    body = client.get("/loan/sketch").json()
    assert body["monthly_cents"] == 104_512
    assert body["stress_6pct_cents"] == 127_572
    assert body["offer"] is None
    assert body["decision"] is None
    assert "not this outcome" in body["note"]
