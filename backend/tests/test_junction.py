from app.junction_db import peek_first_transaction
from app.money import parse_eur_cents


def test_amount_parser_keeps_cents():
    assert parse_eur_cents("-27.32") == -2_732
    assert parse_eur_cents("3080.7") == 308_070
    assert parse_eur_cents("-511.9") == -51_190


def test_customers_and_mortgages_come_from_the_csv_folder(client):
    status = client.get("/junction/status").json()
    assert status["customers"] == 5_100
    assert status["mortgage_applications"] == 200
    assert status["negotiation_events"] > 0
    assert "not Aino" in status["note"]
    tx = status["transactions"]
    assert tx["status"] == "ready"
    assert tx["rows"] == 3_626_755

    page = client.get("/junction/customers", params={"limit": 1}).json()
    assert page["total"] == 5_100
    assert page["customers"][0]["customer_id"] == "CUST00001"
    assert page["customers"][0]["postal_code"] == "00990"
    assert page["customers"][0]["age"] == 63

    missing = client.get("/junction/customers", params={"q": "AINO"}).json()
    assert missing["total"] == 0

    mortgage = client.get("/junction/mortgages/C0004").json()
    assert mortgage["customer_id"] == "CUST04457"
    assert mortgage["final_decision"] == "approved"
    assert mortgage["events"]
    assert mortgage["events"][0]["event_type"] == "application_created"


def test_transaction_file_is_real_csv_not_an_lfs_pointer():
    row = peek_first_transaction()
    assert row["customer_id"] == "CUST00001"
    assert row["category"] == "Utilities"
    assert row["tx_date"] == "2024-10-03"
    assert not row["customer_id"].startswith("version")
    assert parse_eur_cents(row["tx_amount_eur"]) == -2_732


def test_transactions_are_a_separate_read_from_her_ledger(client):
    response = client.get(
        "/junction/customers/CUST00001/transactions",
        params={"limit": 1},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["customer_id"] == "CUST00001"
    assert body["total"] == 581
    first = body["transactions"][0]
    assert first["category"] == "Utilities"
    assert first["amount_cents"] == -2_732
    assert first["tx_date"] == "2024-10-03"
    # The screen reads use this same first customer. The design sample stays on the summary.
    today = client.get("/today").json()
    assert today["customer_id"] == "CUST00001"
    assert today["balance_cents"] == 472_358
    assert client.get("/ledger/summary").json()["safe_to_spend_cents"] == 102_830
