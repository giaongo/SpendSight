"""Design-sample payments: refiling a line and spotting recurring bills."""


def test_refile_moves_the_line_and_leaves_bonus(client):
    payments = client.get("/payments", params={"month": "oct"}).json()["payments"]
    sokos = next(row for row in payments if row["place"] == "Sokos")
    assert sokos["earns_bonus"] is True
    assert sokos["category"] == "Shopping"
    filed = client.post(f"/payments/{sokos['id']}/filing", json={"category": "Groceries"}).json()
    assert filed["category"] == "Groceries"
    assert filed["basket"] == "everyday"
    assert filed["earns_bonus"] is True
    assert filed["filed_by_customer"] is True
    summary = client.get("/ledger/summary").json()
    assert summary["october_slip_cents"] == 35_600 - 1_500
    assert summary["october_bonus_cents"] == 2_282
    assert summary["october_sgroup_cents"] == 65_200

    unknown = client.post(f"/payments/{sokos['id']}/filing", json={"category": "Mystery"})
    assert unknown.status_code == 400
    assert client.post("/payments/9999/filing", json={"category": "Groceries"}).status_code == 404


def test_recurring_bill_needs_the_same_merchant_and_a_similar_amount(client):
    payments = client.get("/payments").json()["payments"]
    helen = next(row for row in payments if row["place"] == "Helen")
    rent = next(row for row in payments if row["place"] == "Vuokra" and row["month"] == "oct")
    repair = next(row for row in payments if row["place"] == "Kodinkonehuolto")
    assert helen["recurring"] is True
    assert rent["recurring"] is True
    assert repair["recurring"] is False
    assert "about a month" in helen["reason"]
