"""Rebuild the small Junction fixture used by the tests and CI (OPS-08).

    python tests/fixtures/make_fixtures.py [path/to/Junction-2026-S-pankki]

Keeps every row for a few customers, so their per-customer figures match the
full dataset exactly. Dataset-wide totals do not; those tests are marked
``full_data`` and run only against the real folder.
"""

from __future__ import annotations

import csv
import sys
from pathlib import Path

CUSTOMERS = {"CUST00001", "CUST00002", "CUST04457"}
HERE = Path(__file__).resolve().parent / "junction"


def _copy(src: Path, dest: Path, keep) -> int:
    with src.open(encoding="utf-8-sig", newline="") as fin, dest.open(
        "w", encoding="utf-8-sig", newline=""
    ) as fout:
        reader = csv.DictReader(fin)
        writer = csv.DictWriter(fout, fieldnames=reader.fieldnames, lineterminator="\n")
        writer.writeheader()
        n = 0
        for row in reader:
            if keep(row):
                writer.writerow(row)
                n += 1
    return n


def main() -> None:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[3] / "Junction-2026-S-pankki"
    HERE.mkdir(parents=True, exist_ok=True)

    links = source / "mtg_tx_customer_link_and_final_mtg_infio.csv"
    with links.open(encoding="utf-8-sig", newline="") as handle:
        mortgages = {
            row["mortgage_customer_id"] for row in csv.DictReader(handle) if row["customer_id"] in CUSTOMERS
        }

    counts = {
        "tx_customers_junction.csv": _copy(
            source / "tx_customers_junction.csv", HERE / "tx_customers_junction.csv",
            lambda r: r["customer_id"] in CUSTOMERS,
        ),
        "tx_junction_transactions.csv": _copy(
            source / "tx_junction_transactions.csv", HERE / "tx_junction_transactions.csv",
            lambda r: r["customer_id"] in CUSTOMERS,
        ),
        "mtg_tx_customer_link_and_final_mtg_infio.csv": _copy(
            links, HERE / "mtg_tx_customer_link_and_final_mtg_infio.csv",
            lambda r: r["mortgage_customer_id"] in mortgages,
        ),
    }
    for name, n in counts.items():
        print(f"{n:6d} rows  {name}")


if __name__ == "__main__":
    main()
