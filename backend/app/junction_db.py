"""Read-only view of the Junction 2026 S-Pankki CSVs.

Customers, mortgage applications, negotiation events, and the transaction
index are loaded when ``JunctionLibrary`` is created. The transaction CSV
is about 3.6 million rows, so it is read into a local SQLite file once
and opened from that file afterwards.

This module is not the coach ledger. Aino's figures are not computed
from these files, and a mortgage decision here is not her outcome.
"""

from __future__ import annotations

import csv
import logging
import os
import sqlite3
import threading
from pathlib import Path

from app.customer_data import CustomerBook
from app.money import parse_eur_cents

log = logging.getLogger("spendsight.junction")

_LOCK = threading.Lock()
_STATE = {
    "status": "idle",
    "error": None,
    "rows": 0,
}


def junction_dir() -> Path:
    override = os.environ.get("JUNCTION_DIR")
    if override:
        return Path(override)
    return Path(__file__).resolve().parents[2] / "Junction-2026-S-pankki"


def cache_dir() -> Path:
    return Path(__file__).resolve().parents[1] / ".cache"


def transaction_db_path() -> Path:
    return cache_dir() / "junction.sqlite"


def _csv_path(name: str) -> Path:
    path = junction_dir() / name
    if not path.is_file():
        raise FileNotFoundError(f"Missing {path}")
    return path


def read_csv(name: str) -> list[dict[str, str]]:
    path = _csv_path(name)
    _reject_lfs_pointer(path)
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def _reject_lfs_pointer(path: Path) -> None:
    with path.open("rb") as handle:
        head = handle.read(80)
    if head.startswith(b"version https://git-lfs"):
        raise RuntimeError(
            f"{path.name} is a Git LFS pointer, not the CSV. Download the raw file."
        )


def peek_first_transaction() -> dict[str, str]:
    path = _csv_path("tx_junction_transactions.csv")
    _reject_lfs_pointer(path)
    with path.open(encoding="utf-8-sig", newline="") as handle:
        row = next(csv.DictReader(handle))
    return row


class JunctionLibrary:
    """Small CSVs in memory. Transactions stay in the index."""

    def __init__(self) -> None:
        customers = read_csv("tx_customers_junction.csv")
        mortgages = read_csv("mtg_tx_customer_link_and_final_mtg_infio.csv")
        events = read_csv("mtg_negotiation_history.csv")
        self.customers = [
            {
                "customer_id": row["customer_id"],
                "postal_code": row["customer_postal_code"],
                "age": int(row["customer_age"]),
            }
            for row in customers
        ]
        self.customers_by_id = {row["customer_id"]: row for row in self.customers}
        self.mortgages = [self._mortgage(row) for row in mortgages]
        self.mortgages_by_id = {row["mortgage_customer_id"]: row for row in self.mortgages}
        self.mortgage_by_customer = {
            row["customer_id"]: row for row in self.mortgages if row["customer_id"]
        }
        self.events_by_id: dict[str, list[dict]] = {}
        for row in events:
            self.events_by_id.setdefault(row["mortgage_customer_id"], []).append(
                {
                    "mortgage_customer_id": row["mortgage_customer_id"],
                    "timestamp": row["timestamp"],
                    "event_type": row["event_type"],
                    "property_id": row["property_id"] or None,
                    "property_type": row["property_type"] or None,
                    "property_price_estimate": _number(row["property_price_estimate"]),
                    "loan_amount": _number(row["loan_amount"]),
                    "equity_amount": _number(row["equity_amount"]),
                    "collateral_type": row["collateral_type"] or None,
                    "collateral_value": _number(row["collateral_value"]),
                    "collateral_status": row["collateral_status"] or None,
                    "decision": row["decision"] or None,
                    "rejection_reason": row["rejection_reason"] or None,
                    "expense_type": row["expense_type"] or None,
                    "expense_amount": _number(row["expense_amount"]),
                    "note": row["note"] or None,
                }
            )
        self.transactions = ensure_transaction_index()
        if not self.customers:
            raise RuntimeError("The customer file has no rows")
        first = self.customers[0]
        self._books: dict[str, CustomerBook] = {}
        self.primary = CustomerBook(
            first,
            self.mortgage_by_customer.get(first["customer_id"]),
            _transactions_for(first["customer_id"]),
            primary=True,
        )
        self._books[first["customer_id"]] = self.primary

    def book(self, customer_id: str) -> CustomerBook:
        """Load one customer's transactions from the index. The first customer is already loaded."""
        cached = self._books.get(customer_id)
        if cached is not None:
            return cached
        customer = self.customers_by_id.get(customer_id)
        if customer is None:
            raise KeyError(customer_id)
        loaded = CustomerBook(
            customer,
            self.mortgage_by_customer.get(customer_id),
            _transactions_for(customer_id),
        )
        self._books[customer_id] = loaded
        return loaded

    def database_view(self) -> dict:
        index = self.transactions
        decisions: dict[str, int] = {}
        for row in self.mortgages:
            key = row["final_decision"] or "unknown"
            decisions[key] = decisions.get(key, 0) + 1
        return {
            "customers": len(self.customers),
            "mortgage_applications": len(self.mortgages),
            "negotiation_events": sum(len(rows) for rows in self.events_by_id.values()),
            "mortgage_decisions": decisions,
            "transactions": index.get("rows"),
            "first_date": index.get("first_date"),
            "last_date": index.get("last_date"),
            "categories": index.get("categories"),
            "transaction_types": transaction_type_totals(),
            "default_customer_id": self.primary.customer_id,
            "note": (
                "Junction challenge data, loaded when the service starts. "
                "Today, Spending, and Save without an id use the first customer. "
                "Use /customers/{customer_id} for any other customer."
            ),
        }

    @staticmethod
    def _mortgage(row: dict[str, str]) -> dict:
        return {
            "mortgage_customer_id": row["mortgage_customer_id"],
            "customer_id": row["customer_id"] or None,
            "final_decision": row["final_decision"] or None,
            "rejection_reason": row["rejection_reason"] or None,
            "property_price_eur": _number(row["final_property_price_eur"]),
            "requested_loan_eur": _number(row["final_requested_loan_eur"]),
            "equity_amount_eur": _number(row["final_equity_amount_eur"]),
            "collateral_value_eur": _number(row["final_collateral_value_eur"]),
            "required_monthly_income_eur": _number(row["required_monthly_income_eur"]),
            "loan_to_value": _number(row["lfinal_loan_to_value"]),
        }

    def status(self) -> dict:
        index = index_status()
        decisions: dict[str, int] = {}
        for row in self.mortgages:
            key = row["final_decision"] or "unknown"
            decisions[key] = decisions.get(key, 0) + 1
        return {
            "dir": str(junction_dir()),
            "note": (
                "Junction challenge data. It is not Aino Korhonen's ledger. "
                "A mortgage decision here is not her outcome, and the coach does not read these files."
            ),
            "customers": len(self.customers),
            "mortgage_applications": len(self.mortgages),
            "negotiation_events": sum(len(rows) for rows in self.events_by_id.values()),
            "mortgage_decisions": decisions,
            "transactions": index,
        }

    def list_customers(
        self,
        *,
        limit: int,
        offset: int,
        postal_code: str | None = None,
        q: str | None = None,
    ) -> dict:
        rows = self.customers
        if postal_code:
            rows = [row for row in rows if row["postal_code"] == postal_code]
        if q:
            needle = q.strip().upper()
            rows = [row for row in rows if needle in row["customer_id"].upper()]
        page = rows[offset : offset + limit]
        return {"total": len(rows), "limit": limit, "offset": offset, "customers": page}

    def customer(self, customer_id: str) -> dict | None:
        row = self.customers_by_id.get(customer_id)
        if row is None:
            return None
        mortgage = self.mortgage_by_customer.get(customer_id)
        return {
            **row,
            "mortgage_customer_id": mortgage["mortgage_customer_id"] if mortgage else None,
            "mortgage_decision": mortgage["final_decision"] if mortgage else None,
        }

    def list_mortgages(
        self,
        *,
        limit: int,
        offset: int,
        decision: str | None = None,
    ) -> dict:
        rows = self.mortgages
        if decision:
            rows = [row for row in rows if row["final_decision"] == decision]
        page = rows[offset : offset + limit]
        return {"total": len(rows), "limit": limit, "offset": offset, "mortgages": page}

    def mortgage(self, mortgage_customer_id: str) -> dict | None:
        row = self.mortgages_by_id.get(mortgage_customer_id)
        if row is None:
            return None
        return {
            **row,
            "events": self.events_by_id.get(mortgage_customer_id, []),
        }


def _number(raw: str | None) -> float | None:
    if raw is None:
        return None
    text = raw.strip()
    if not text:
        return None
    return float(text)


def index_status() -> dict:
    path = transaction_db_path()
    with _LOCK:
        state = dict(_STATE)
    if state["status"] == "building":
        return {"status": "building", "rows": None, "error": None, "path": None, "categories": None}
    if state["status"] == "failed":
        return {"status": "failed", "rows": None, "error": state["error"], "path": None, "categories": None}
    if path.is_file():
        meta = _read_meta(path)
        rows = int(meta["rows"]) if meta.get("rows") else (state["rows"] or None)
        return {
            "status": "ready",
            "rows": rows,
            "error": None,
            "path": str(path),
            "first_date": meta.get("first_date"),
            "last_date": meta.get("last_date"),
            "categories": _category_totals(path),
        }
    return {"status": "idle", "rows": None, "error": None, "path": None, "categories": None}


def _category_totals(path: Path) -> list[dict]:
    con = _ro(path)
    try:
        found = con.execute(
            "SELECT category, n, amount_cents FROM category_stats ORDER BY n DESC"
        ).fetchall()
    except sqlite3.Error:
        return []
    finally:
        con.close()
    return [{"category": name, "n": count, "amount_cents": cents} for name, count, cents in found]


_TYPE_ROWS: list[dict] | None = None
_TYPE_MTIME: float | None = None


def transaction_type_totals() -> list[dict]:
    """Count each transaction type once per index file, then reuse the result."""
    global _TYPE_ROWS, _TYPE_MTIME
    path = transaction_db_path()
    mtime = path.stat().st_mtime
    if _TYPE_ROWS is not None and _TYPE_MTIME == mtime:
        return _TYPE_ROWS
    con = _ro(path)
    try:
        found = con.execute(
            """
            SELECT transaction_type, COUNT(*), SUM(amount_cents)
            FROM transactions
            GROUP BY transaction_type
            ORDER BY COUNT(*) DESC
            """
        ).fetchall()
    finally:
        con.close()
    _TYPE_ROWS = [
        {"transaction_type": name, "count": count, "amount_cents": cents}
        for name, count, cents in found
    ]
    _TYPE_MTIME = mtime
    return _TYPE_ROWS


def _transactions_for(customer_id: str) -> list[dict]:
    con = _ro(transaction_db_path())
    con.row_factory = sqlite3.Row
    try:
        found = con.execute(
            """
            SELECT customer_id, bank_account_id, tx_date, tx_timestamp, category,
                   amount_cents, balance_cents, transaction_type, merchant_id, mcc_code
            FROM transactions
            WHERE customer_id = ?
            ORDER BY tx_date, tx_timestamp
            """,
            (customer_id,),
        ).fetchall()
    finally:
        con.close()
    return [dict(row) for row in found]


def _ro(path: Path) -> sqlite3.Connection:
    return sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True)


def _read_meta(path: Path) -> dict[str, str]:
    if not path.is_file():
        return {}
    try:
        con = _ro(path)
    except sqlite3.Error:
        return {}
    try:
        rows = con.execute("SELECT key, value FROM meta").fetchall()
    except sqlite3.Error:
        return {}
    finally:
        con.close()
    return {key: value for key, value in rows}


def ensure_transaction_index() -> dict:
    """Open the transaction index on this thread. Build it first if the cache is stale."""
    path = transaction_db_path()
    source = _csv_path("tx_junction_transactions.csv")
    fresh = path.is_file() and path.stat().st_mtime >= source.stat().st_mtime
    if fresh:
        meta = _read_meta(path)
        rows = int(meta["rows"]) if meta.get("rows") else 0
        if rows > 0:
            with _LOCK:
                _STATE["status"] = "ready"
                _STATE["error"] = None
                _STATE["rows"] = rows
            status = index_status()
            if status["status"] != "ready":
                raise RuntimeError(status.get("error") or "Transaction index is not ready")
            return status
    with _LOCK:
        if _STATE["status"] == "building":
            raise RuntimeError("Transaction index is already building")
        _STATE["status"] = "building"
        _STATE["error"] = None
    _build_index()
    status = index_status()
    if status["status"] != "ready":
        raise RuntimeError(status.get("error") or "Transaction index failed")
    return status


def _build_index() -> None:
    source = _csv_path("tx_junction_transactions.csv")
    _reject_lfs_pointer(source)
    dest = transaction_db_path()
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".sqlite.building")
    if tmp.exists():
        tmp.unlink()
    con = sqlite3.connect(tmp)
    try:
        con.execute("PRAGMA journal_mode=OFF")
        con.execute("PRAGMA synchronous=OFF")
        con.execute("PRAGMA temp_store=MEMORY")
        con.execute(
            """
            CREATE TABLE transactions (
                customer_id TEXT NOT NULL,
                bank_account_id TEXT NOT NULL,
                tx_date TEXT NOT NULL,
                tx_timestamp TEXT NOT NULL,
                category TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                amount_eur TEXT NOT NULL,
                balance_cents INTEGER NOT NULL,
                balance_eur TEXT NOT NULL,
                transaction_type TEXT NOT NULL,
                merchant_id TEXT,
                mcc_code TEXT
            )
            """
        )
        con.execute(
            """
            CREATE TABLE category_stats (
                category TEXT PRIMARY KEY,
                n INTEGER NOT NULL,
                amount_cents INTEGER NOT NULL
            )
            """
        )
        batch: list[tuple] = []
        stats: dict[str, list[int]] = {}
        rows = 0
        first_date = None
        last_date = None
        with source.open(encoding="utf-8-sig", newline="") as handle:
            for record in csv.DictReader(handle):
                amount = parse_eur_cents(record["tx_amount_eur"])
                balance = parse_eur_cents(record["balance_after_eur"])
                batch.append(
                    (
                        record["customer_id"],
                        record["bank_account_id"],
                        record["tx_date"],
                        record["tx_timestamp"],
                        record["category"],
                        amount,
                        record["tx_amount_eur"],
                        balance,
                        record["balance_after_eur"],
                        record["transaction_type"],
                        record["merchant_id"] or None,
                        record["mcc_code"] or None,
                    )
                )
                bucket = stats.setdefault(record["category"], [0, 0])
                bucket[0] += 1
                bucket[1] += amount
                tx_date = record["tx_date"]
                if first_date is None or tx_date < first_date:
                    first_date = tx_date
                if last_date is None or tx_date > last_date:
                    last_date = tx_date
                rows += 1
                if len(batch) >= 20_000:
                    _flush(con, batch)
                    batch.clear()
                    if rows % 200_000 == 0:
                        con.commit()
                        log.info("indexed %s transaction rows", rows)
        if batch:
            _flush(con, batch)
        con.executemany(
            "INSERT INTO category_stats (category, n, amount_cents) VALUES (?, ?, ?)",
            ((name, counts[0], counts[1]) for name, counts in stats.items()),
        )
        con.execute("CREATE INDEX ix_tx_customer_date ON transactions(customer_id, tx_date)")
        con.execute(
            "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)"
        )
        con.executemany(
            "INSERT INTO meta (key, value) VALUES (?, ?)",
            (
                ("rows", str(rows)),
                ("source", source.name),
                ("first_date", first_date or ""),
                ("last_date", last_date or ""),
            ),
        )
        con.commit()
    except Exception as exc:
        con.close()
        if tmp.exists():
            tmp.unlink()
        with _LOCK:
            _STATE["status"] = "failed"
            _STATE["error"] = str(exc)
        log.exception("transaction index failed")
        return
    con.close()
    os.replace(tmp, dest)
    with _LOCK:
        _STATE["status"] = "ready"
        _STATE["rows"] = rows
        _STATE["error"] = None
    log.info("transaction index ready: %s rows", rows)


def _flush(con: sqlite3.Connection, batch: list[tuple]) -> None:
    con.executemany(
        """
        INSERT INTO transactions (
            customer_id, bank_account_id, tx_date, tx_timestamp, category,
            amount_cents, amount_eur, balance_cents, balance_eur,
            transaction_type, merchant_id, mcc_code
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        batch,
    )


def query_transactions(
    customer_id: str,
    *,
    limit: int,
    offset: int,
    category: str | None = None,
    date_from: str | None = None,
    date_to: str | None = None,
) -> dict:
    path = transaction_db_path()
    if not path.is_file() or index_status()["status"] != "ready":
        raise IndexNotReady(index_status())
    con = _ro(path)
    con.row_factory = sqlite3.Row
    try:
        where = ["customer_id = ?"]
        params: list[object] = [customer_id]
        if category:
            where.append("category = ?")
            params.append(category)
        if date_from:
            where.append("tx_date >= ?")
            params.append(date_from)
        if date_to:
            where.append("tx_date <= ?")
            params.append(date_to)
        clause = " AND ".join(where)
        total = con.execute(
            f"SELECT COUNT(*) FROM transactions WHERE {clause}",
            params,
        ).fetchone()[0]
        rows = con.execute(
            f"""
            SELECT customer_id, bank_account_id, tx_date, tx_timestamp, category,
                   amount_cents, amount_eur, balance_cents, balance_eur,
                   transaction_type, merchant_id, mcc_code
            FROM transactions
            WHERE {clause}
            ORDER BY tx_date, tx_timestamp
            LIMIT ? OFFSET ?
            """,
            [*params, limit, offset],
        ).fetchall()
    finally:
        con.close()
    return {
        "customer_id": customer_id,
        "total": total,
        "limit": limit,
        "offset": offset,
        "transactions": [dict(row) for row in rows],
    }


class IndexNotReady(Exception):
    def __init__(self, status: dict) -> None:
        super().__init__("Transaction index is not ready")
        self.status = status
