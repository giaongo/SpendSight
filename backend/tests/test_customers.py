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
