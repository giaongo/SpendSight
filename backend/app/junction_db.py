"""Read-only view of the Junction 2026 S-Pankki CSVs.

Customers, mortgage applications, and the transaction index are loaded
when ``JunctionLibrary`` is created. The transaction CSV
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
    override = os.environ.get("SPENDSIGHT_CACHE_DIR")
    if override:
        return Path(override)
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
        self.customers = [
            {
                "customer_id": row["customer_id"],
                "postal_code": row["customer_postal_code"],
                "age": int(row["customer_age"]),
            }
            for row in customers
        ]
        self.customers_by_id = {row["customer_id"]: row for row in self.customers}
        self.mortgage_by_customer = {
            row["customer_id"]: row
            for row in (self._mortgage(raw) for raw in mortgages)
            if row["customer_id"]
        }
        self.transactions = ensure_transaction_index()
        if not self.customers:
            raise RuntimeError("The customer file has no rows")
        self._books: dict[str, CustomerBook] = {}

    def book(self, customer_id: str) -> CustomerBook:
        """Load one customer's transactions from the index once, then reuse them."""
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

    @staticmethod
    def _mortgage(row: dict[str, str]) -> dict:
        return {
            "mortgage_customer_id": row["mortgage_customer_id"],
            "customer_id": row["customer_id"] or None,
            "final_decision": row["final_decision"] or None,
        }


def index_status() -> dict:
    path = transaction_db_path()
    with _LOCK:
        state = dict(_STATE)
    if state["status"] == "building":
        return {"status": "building", "rows": None, "error": None, "path": None}
    if state["status"] == "failed":
        return {"status": "failed", "rows": None, "error": state["error"], "path": None}
    if path.is_file():
        meta = _read_meta(path)
        rows = int(meta["rows"]) if meta.get("rows") else (state["rows"] or None)
        return {
            "status": "ready",
            "rows": rows,
            "error": None,
            "path": str(path),
        }
    return {"status": "idle", "rows": None, "error": None, "path": None}


def index_ready() -> dict:
    """Cheap readiness check for /healthz. Does not open the index."""
    path = transaction_db_path()
    with _LOCK:
        state = dict(_STATE)
    ready = state["status"] == "ready" and path.is_file()
    return {
        "status": "ready" if ready else state["status"],
        "rows": state["rows"] if ready else None,
        "error": state["error"],
    }


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
        batch: list[tuple] = []
        rows = 0
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
                rows += 1
                if len(batch) >= 20_000:
                    _flush(con, batch)
                    batch.clear()
                    if rows % 200_000 == 0:
                        con.commit()
                        log.info("indexed %s transaction rows", rows)
        if batch:
            _flush(con, batch)
        con.execute("CREATE INDEX ix_tx_customer_date ON transactions(customer_id, tx_date)")
        con.execute(
            "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)"
        )
        con.executemany(
            "INSERT INTO meta (key, value) VALUES (?, ?)",
            (
                ("rows", str(rows)),
                ("source", source.name),
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
