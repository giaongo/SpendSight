"""Figures for the first customer in the Junction database.

Built once, while the library is created. The rows are that customer's
own transactions. They are not the design sample.
"""

from __future__ import annotations

from datetime import date, timedelta

from app.demo import demo_savings, demo_today
from app.money import eur, js_round


class CustomerBook:
    def __init__(self, customer: dict, mortgage: dict | None, rows: list[dict], *, primary: bool = False) -> None:
        if not rows:
            raise LookupError(customer["customer_id"])
        self.primary = primary
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

    def session_view(self) -> dict:
        return {
            **self.identity(),
            "as_of": self.last_date,
            "today": demo_today().isoformat(),
            "balance_cents": self.balance_cents,
            "balance": eur(self.balance_cents),
            "transaction_count": len(self.rows),
            "coach": "POST /coach/ask",
            "reads": self.links(),
            "note": (
                "This session is the first customer in the database. "
                "Pass a customer id to /customers/{customer_id} for anyone else."
            ),
        }

    def links(self) -> dict:
        customer_id = self.customer_id
        return {
            "summary": f"/customers/{customer_id}",
            "today": f"/customers/{customer_id}/today",
            "spending": f"/customers/{customer_id}/spending",
            "save": f"/customers/{customer_id}/save",
            "months": f"/customers/{customer_id}/months",
            "transactions": f"/junction/customers/{customer_id}/transactions",
        }

    def today_view(self) -> dict:
        month_rows = self.months[self.latest_month]
        salary = self.latest_salary
        return {
            "kind": "read",
            "screen": "today",
            **self.identity(),
            "as_of": self.last_date,
            "today": demo_today().isoformat(),
            "balance_cents": self.balance_cents,
            "balance": eur(self.balance_cents),
            "latest_salary_cents": None if salary is None else salary["amount_cents"],
            "latest_salary_date": None if salary is None else salary["tx_date"],
            "month": self.latest_month,
            "month_in_cents": _inflow(month_rows),
            "month_out_cents": _outflow(month_rows),
            "transaction_count": len(self.rows),
            "first_date": self.first_date,
            "safe_to_spend": self.safe_to_spend(demo_today()),
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
            "highlights": self._highlights(chosen, rows),
            "baskets": baskets(rows),
            "note": "Left the account in total_out_cents. Salary is total_in_cents, not part of the spending.",
        }

    def safe_to_spend(self, today: date) -> dict | None:
        """What is left of this week's budget.

        The usual income (average salary over the last six months) minus this
        month's essentials paid before this week is free to spend a month. As a
        weekly budget that is x 12 / 52, and what left the account since Monday
        comes off it.
        """
        week_start = today - timedelta(days=today.weekday())
        first_month = _month_back(today, SAFE_INCOME_MONTHS - 1)
        salaries = [
            row["amount_cents"]
            for row in self.rows
            if row["transaction_type"] == "salary" and first_month <= row["tx_date"][:7] <= today.isoformat()[:7]
        ]
        if not salaries:
            return None
        income = js_round(sum(salaries) / len(salaries))
        month_start = today.replace(day=1).isoformat()
        essentials = -sum(
            row["amount_cents"]
            for row in self.rows
            if row["amount_cents"] < 0
            and is_essential(row)
            and month_start <= row["tx_date"] < week_start.isoformat()
        )
        free = income - essentials
        weekly = js_round(free * 12 / 52)
        spent_rows = [
            row
            for row in self.rows
            if row["amount_cents"] < 0 and week_start.isoformat() <= row["tx_date"] <= today.isoformat()
        ]
        spent = -sum(row["amount_cents"] for row in spent_rows)
        left = weekly - spent
        days_left = 7 - today.weekday()
        if left < 0:
            status = "overdraft_risk"
        elif left * 4 < weekly:
            status = "tight"
        else:
            status = "on_track"
        return {
            "today": today.isoformat(),
            "week_start": week_start.isoformat(),
            "income_months": SAFE_INCOME_MONTHS,
            "income_cents": income,
            "essentials_cents": essentials,
            "essential_categories": list(ESSENTIAL_CATEGORIES),
            "free_month_cents": free,
            "weekly_cents": weekly,
            "spent_week_cents": spent,
            "spent_week_count": len(spent_rows),
            "left_cents": left,
            "left": eur(left),
            "days_left": days_left,
            "per_day_cents": js_round(max(left, 0) / days_left),
            "status": status,
        }

    def _highlights(self, month: str, rows: list[dict]) -> dict:
        """The Home carousel figures: the biggest rise on last month, and its Friday evenings."""
        earlier = sorted(key for key in self.months if key < month)
        prior_month = earlier[-1] if earlier else None
        now = _spent_by_category(rows)
        before = _spent_by_category(self.months[prior_month]) if prior_month else {}
        change = None
        if before:
            rises = [(now[name][1] - before.get(name, (0, 0))[1], name) for name in now]
            rises = [(delta, name) for delta, name in rises if delta > 0]
            if rises:
                delta, name = max(rises, key=lambda item: (item[0], item[1]))
                prior_count, prior_cents = before.get(name, (0, 0))
                change = {
                    "category": name,
                    "month": month,
                    "prior_month": prior_month,
                    "count": now[name][0],
                    "amount_cents": now[name][1],
                    "amount": eur(now[name][1]),
                    "prior_count": prior_count,
                    "prior_cents": prior_cents,
                    "prior": eur(prior_cents),
                    "delta_cents": delta,
                    "delta": eur(delta),
                }
        friday = None
        if change is not None:
            picked = [
                row
                for row in rows
                if row["category"] == change["category"] and row["amount_cents"] < 0 and _friday_evening(row)
            ]
            if picked:
                cents = -sum(row["amount_cents"] for row in picked)
                friday = {
                    "category": change["category"],
                    "month": month,
                    "count": len(picked),
                    "amount_cents": cents,
                    "amount": eur(cents),
                    "rule": f"Fridays from {FRIDAY_EVENING_FROM}:00",
                }
        return {"spending_change": change, "friday_evenings": friday}

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
            **_savings_accounts(self.customer_id),
            "note": (
                "Recorded investment deposits for this customer. "
                "This is not a forecast, and it does not move money."
            ),
        }


def _savings_accounts(customer_id: str) -> dict:
    """The demo customer's savings accounts, with goal progress. Empty for everyone else."""
    accounts = demo_savings(customer_id)
    for account in accounts:
        account["balance"] = eur(account["balance_cents"])
        goal = account["goal_cents"]
        account["goal"] = None if goal is None else eur(goal)
        # Progress as a share of the goal; the whole percent is rounded down, as in "6 % there".
        account["goal_share"] = None if not goal else round(account["balance_cents"] / goal, 4)
        account["goal_percent"] = None if not goal else account["balance_cents"] * 100 // goal
    total = sum(account["balance_cents"] for account in accounts)
    return {
        "savings_accounts": accounts,
        "savings_total_cents": total,
        "savings_total": eur(total),
        "savings_note": "Demo savings balances. The Junction data has no savings accounts." if accounts else None,
    }


def _inflow(rows: list[dict]) -> int:
    return sum(row["amount_cents"] for row in rows if row["amount_cents"] > 0)


SAFE_INCOME_MONTHS = 6  # the usual income is the average salary over this many months
ESSENTIAL_CATEGORIES = ("Housing", "Utilities", "Health", "Transportation")
EVERYDAY_MCC = ("4121",)  # taxis: in Transportation, but a choice, so everyday spending
OUTSIDE_BASKETS = ("Salary", "Invest Deposit")  # not spending
OUTSIDE_TYPES = ("salary", "investment_deposit")


def is_essential(row: dict) -> bool:
    """Must-pay costs that repeat: housing, utilities, health and transport, but not taxis."""
    return row["category"] in ESSENTIAL_CATEGORIES and (row.get("mcc_code") or "") not in EVERYDAY_MCC


def baskets(rows: list[dict]) -> dict:
    """Outgoing payments split into the design's three baskets, in positive cents."""
    essentials = everyday = 0
    for row in rows:
        if row["amount_cents"] >= 0:
            continue
        if row["category"] in OUTSIDE_BASKETS or row["transaction_type"] in OUTSIDE_TYPES:
            continue
        if is_essential(row):
            essentials -= row["amount_cents"]
        else:
            everyday -= row["amount_cents"]
    return {
        "essentials_cents": essentials,
        # Must-pays that do not repeat, such as a dentist bill. The data has no such label yet.
        "unexpected_cents": 0,
        "everyday_cents": everyday,
        "rule": "Essentials are housing, utilities, health and transport, except taxis.",
    }


def _month_back(today: date, months: int) -> str:
    """The ISO month (YYYY-MM) this many months before today's month."""
    index = today.year * 12 + today.month - 1 - months
    return f"{index // 12:04d}-{index % 12 + 1:02d}"


FRIDAY_EVENING_FROM = 17  # hour; a payment from 17:00 on a Friday is a Friday evening


def _spent_by_category(rows: list[dict]) -> dict[str, tuple[int, int]]:
    """Outgoing payments per category: (count, cents as a positive number)."""
    spent: dict[str, tuple[int, int]] = {}
    for row in rows:
        if row["amount_cents"] < 0 and row["category"]:
            count, cents = spent.get(row["category"], (0, 0))
            spent[row["category"]] = (count + 1, cents - row["amount_cents"])
    return spent


def _friday_evening(row: dict) -> bool:
    if date.fromisoformat(row["tx_date"]).weekday() != 4:
        return False
    stamp = row["tx_timestamp"] or ""
    try:
        hour = int(stamp.split(":")[1])
    except (IndexError, ValueError):
        return False
    return hour >= FRIDAY_EVENING_FROM


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
