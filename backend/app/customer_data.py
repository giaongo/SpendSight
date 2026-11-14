"""Figures for one customer in the Junction database.

Built the first time that customer is read. The rows are that customer's
own transactions. They are not the design sample.
"""

from __future__ import annotations

from datetime import date

from app.money import eur


class CustomerBook:
    def __init__(self, customer: dict, mortgage: dict | None, rows: list[dict]) -> None:
        if not rows:
            raise LookupError(customer["customer_id"])
        self.customer_id = customer["customer_id"]
        self.postal_code = customer["postal_code"]
        self.age = customer["age"]
        self.mortgage = mortgage
        self.rows = rows
        self.account_id = rows[-1]["bank_account_id"]
        self.first_date = rows[0]["tx_date"]
        self.last_date = rows[-1]["tx_date"]
        self.balance_cents = rows[-1]["balance_cents"]
        self.months: dict[str, list[dict]] = {}
        for row in rows:
            self.months.setdefault(row["tx_date"][:7], []).append(row)
        self.latest_month = self.last_date[:7]
        salaries = [row for row in rows if row["transaction_type"] == "salary"]
        self.latest_salary = salaries[-1] if salaries else None
        self.deposits = [row for row in rows if row["transaction_type"] == "investment_deposit"]

    def identity(self) -> dict:
        mortgage = self.mortgage
        return {
            "customer_id": self.customer_id,
            "postal_code": self.postal_code,
            "age": self.age,
            "account_id": self.account_id,
            "mortgage_customer_id": None if mortgage is None else mortgage["mortgage_customer_id"],
            "mortgage_decision": None if mortgage is None else mortgage["final_decision"],
        }

    def links(self) -> dict:
        customer_id = self.customer_id
        return {
            "today": f"/customers/{customer_id}/today",
            "spending": f"/customers/{customer_id}/spending",
            "save": f"/customers/{customer_id}/save",
        }

    def today_view(self) -> dict:
        month_rows = self.months[self.latest_month]
        salary = self.latest_salary
        return {
            "kind": "read",
            "screen": "today",
            **self.identity(),
            "as_of": self.last_date,
            "balance_cents": self.balance_cents,
            "balance": eur(self.balance_cents),
            "latest_salary_cents": None if salary is None else salary["amount_cents"],
            "latest_salary_date": None if salary is None else salary["tx_date"],
            "month": self.latest_month,
            "month_in_cents": _inflow(month_rows),
            "month_out_cents": _outflow(month_rows),
            "transaction_count": len(self.rows),
            "first_date": self.first_date,
            "note": "The balance is after their latest transaction.",
        }

    def summary_view(self) -> dict:
        return {
            **self.identity(),
            "as_of": self.last_date,
            "first_date": self.first_date,
            "balance_cents": self.balance_cents,
            "balance": eur(self.balance_cents),
            "transaction_count": len(self.rows),
            "latest_month": self.latest_month,
            "reads": self.links(),
        }

    def ai_facts(self) -> dict:
        """Figures the model may use. Computed here, from this customer only."""
        spending = self.spending_view(None)
        today = self.today_view()
        months = self.months_view()["months"]
        return {
            "customer_id": self.customer_id,
            "postal_code": self.postal_code,
            "age": self.age,
            "account_id": self.account_id,
            "as_of": self.last_date,
            "balance_cents": self.balance_cents,
            "balance": today["balance"],
            "latest_salary_cents": today["latest_salary_cents"],
            "latest_salary_date": today["latest_salary_date"],
            "latest_month": self.latest_month,
            "latest_month_in_cents": spending["total_in_cents"],
            "latest_month_out_cents": spending["total_out_cents"],
            "latest_month_categories": [
                {"name": row["name"], "count": row["count"], "amount_cents": row["amount_cents"]}
                for row in spending["categories"]
            ],
            "latest_month_transactions": spending["transactions"],
            "recent_months": months[-6:],
            "all_categories": [
                {"name": row["name"], "count": row["count"], "amount_cents": row["amount_cents"]}
                for row in _categories(self.rows)
            ],
            "mortgage_customer_id": self.identity()["mortgage_customer_id"],
            "mortgage_decision": self.identity()["mortgage_decision"],
            "transaction_count": len(self.rows),
        }

    def months_view(self) -> dict:
        months = []
        for month, rows in self.months.items():
            months.append(
                {
                    "month": month,
                    "count": len(rows),
                    "in_cents": _inflow(rows),
                    "out_cents": _outflow(rows),
                }
            )
        return {
            "customer_id": self.customer_id,
            "account_id": self.account_id,
            "months": months,
        }

    def spending_view(self, month: str | None) -> dict:
        chosen = self.latest_month if month is None else month
        rows = self.months.get(chosen)
        if rows is None:
            raise KeyError(chosen)
        return {
            "kind": "read",
            "screen": "spending",
            "customer_id": self.customer_id,
            "account_id": self.account_id,
            "month": chosen,
            "total_in_cents": _inflow(rows),
            "total_out_cents": _outflow(rows),
            "line_count": len(rows),
            "categories": _categories(rows),
            "transactions": [_transaction(row) for row in rows],
            "note": "Left the account in total_out_cents. Salary is total_in_cents, not part of the spending.",
        }

    def save_view(self, years: int) -> dict:
        start = _window_start(date.fromisoformat(self.last_date), years)
        chosen = [row for row in self.deposits if row["tx_date"] >= start.isoformat()]
        deposited = -sum(row["amount_cents"] for row in chosen)
        latest = chosen[-1] if chosen else None
        return {
            "kind": "read",
            "screen": "save",
            "customer_id": self.customer_id,
            "account_id": self.account_id,
            "years": years,
            "window_start": start.isoformat(),
            "window_end": self.last_date,
            "deposits": len(chosen),
            "deposited_cents": deposited,
            "deposited": eur(deposited),
            "latest_deposit_cents": None if latest is None else -latest["amount_cents"],
            "latest_deposit_date": None if latest is None else latest["tx_date"],
            "balance_cents": self.balance_cents,
            "note": (
                "Recorded investment deposits for this customer. "
                "This is not a forecast, and it does not move money."
            ),
        }


def _inflow(rows: list[dict]) -> int:
    return sum(row["amount_cents"] for row in rows if row["amount_cents"] > 0)


def _outflow(rows: list[dict]) -> int:
    return -sum(row["amount_cents"] for row in rows if row["amount_cents"] < 0)


def _categories(rows: list[dict]) -> list[dict]:
    buckets: dict[str, dict] = {}
    for row in rows:
        bucket = buckets.setdefault(
            row["category"],
            {"name": row["category"], "count": 0, "amount_cents": 0},
        )
        bucket["count"] += 1
        bucket["amount_cents"] += row["amount_cents"]
    items = sorted(buckets.values(), key=lambda item: (item["amount_cents"], item["name"]))
    for item in items:
        item["amount"] = eur(item["amount_cents"])
    return items


def _transaction(row: dict) -> dict:
    return {
        "tx_date": row["tx_date"],
        "tx_timestamp": row["tx_timestamp"],
        "category": row["category"],
        "amount_cents": row["amount_cents"],
        "amount": eur(row["amount_cents"]),
        "balance_cents": row["balance_cents"],
        "transaction_type": row["transaction_type"],
        "merchant_id": row["merchant_id"],
        "mcc_code": row["mcc_code"],
    }


def _window_start(last: date, years: int) -> date:
    months = years * 12
    index = last.year * 12 + (last.month - 1) - (months - 1)
    year, month_index = divmod(index, 12)
    return date(year, month_index + 1, 1)
