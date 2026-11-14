from app.junction_db import peek_first_transaction
from app.money import parse_eur_cents


def test_amount_parser_keeps_cents():
    assert parse_eur_cents("-27.32") == -2_732
    assert parse_eur_cents("3080.7") == 308_070
    assert parse_eur_cents("-511.9") == -51_190


def test_transaction_file_is_real_csv_not_an_lfs_pointer():
    row = peek_first_transaction()
    assert row["customer_id"] == "CUST00001"
    assert row["category"] == "Utilities"
    assert row["tx_date"] == "2024-10-03"
    assert not row["customer_id"].startswith("version")
    assert parse_eur_cents(row["tx_amount_eur"]) == -2_732

