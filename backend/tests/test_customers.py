import pytest


@pytest.mark.full_data
def test_health_counts_the_whole_dataset(client):
    body = client.get("/health").json()
    assert body["ok"] is True
    assert body["customers"] == 5_100
    assert body["transactions_rows"] == 3_626_755


def test_any_customer_can_be_read(client):
    summary = client.get("/customers/CUST00002").json()
    assert summary["customer_id"] == "CUST00002"
    assert summary["account_id"] == "ACC000002"
    assert summary["balance_cents"] == 153_916
    assert summary["reads"] == {
        "today": "/customers/CUST00002/today",
        "spending": "/customers/CUST00002/spending",
        "save": "/customers/CUST00002/save",
    }

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

    assert client.get("/customers/CUST99999").status_code == 404
    assert client.get("/customers/CUST00002/spending", params={"month": "1999-01"}).status_code == 400



def test_a_mortgage_link_comes_from_the_csv_folder(client):
    borrower = client.get("/customers/CUST04457").json()
    assert borrower["mortgage_customer_id"] == "C0004"
    assert borrower["mortgage_decision"] == "approved"
    assert client.get("/customers/CUST00001").json()["mortgage_customer_id"] is None
