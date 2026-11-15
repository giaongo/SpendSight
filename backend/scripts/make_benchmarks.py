"""Build app/benchmarks.json: what a typical customer spends on extras, month by month.

Run from backend/ after the Junction index exists (start the service once):

    .venv/Scripts/python scripts/make_benchmarks.py

"Extras" are purchases that are a choice: not rent, utilities, health, transport (taxis count as a
choice), groceries, salary or investment deposits. The share is extras divided by everything spent
outside rent. The medians run over every customer who spent anything that month.
"""

from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path
from statistics import median

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / ".cache" / "junction.sqlite"
OUT = ROOT / "app" / "benchmarks.json"
MONTHS = 12

QUERY = """
select substr(tx_date, 1, 7) as month, customer_id,
  sum(case
        when category in ('Housing', 'Salary', 'Invest Deposit') then 0
        when category in ('Utilities', 'Health', 'Groceries') then 0
        when category = 'Transportation' and coalesce(mcc_code, '') != '4121' then 0
        else -amount_cents end) as extras,
  sum(case when category in ('Housing', 'Salary', 'Invest Deposit') then 0 else -amount_cents end) as base
from transactions
where amount_cents < 0
group by month, customer_id
having base > 0
"""


def main() -> int:
    if not DB.exists():
        print(f"No index at {DB}. Start the service once to build it.", file=sys.stderr)
        return 1
    by_month: dict[str, list[tuple[int, int]]] = {}
    for month, _customer, extras, base in sqlite3.connect(DB).execute(QUERY):
        by_month.setdefault(month, []).append((extras, base))
    out = {}
    for month in sorted(by_month)[-MONTHS:]:
        rows = by_month[month]
        out[month] = {
            "customers": len(rows),
            "median_extras_cents": int(median(e for e, _ in rows)),
            "median_share_pct": round(median(100 * e / b for e, b in rows)),
        }
    OUT.write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} for {len(out)} months")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
