"""Aino Korhonen's demo ledger.

October is one line per place. November is each payment from 1 to 13 Nov.
Today in the demo is 13 Nov. Salary lands on Thursday 15 Nov.

These rows are the sample in the design. They are not read from
Junction-2026-S-pankki. That dataset is a different door.
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass

from app.money import eur, js_round

BALANCE_CENTS = 118_420
SALARY_CENTS = 284_000
BUFFER_CENTS = 8_000
JACKET_CENTS = 8_900
GROCERY_PLAN_CENTS = 28_000
COFFEE_PLAN_CENTS = 1_600
WEEK_SHOP_CENTS = 7_000
ASP_MIN_CENTS = 5_000
ASP_MAX_CENTS = 150_000
HOME_EUR = 220_000

# Under €50 the rate is 0. Then 1% at €50, up to 5% at €900.
BONUS_STEPS = (
    (5_000, 1.0),
    (20_000, 1.5),
    (30_000, 2.0),
    (40_000, 2.5),
    (50_000, 3.0),
    (60_000, 3.5),
    (70_000, 4.0),
    (80_000, 4.5),
    (90_000, 5.0),
)

BASKET_NAME = {
    "regular": "Must, every month",
    "irregular": "Must, when it happens",
    "everyday": "Everyday",
}
BASKET_LINE = {
    "regular": "These leave even when you buy nothing else.",
    "irregular": "You cannot skip these. You also cannot pick the day.",
    "everyday": (
        "Chosen one payment at a time. Food for the house is in here, "
        "and so is the money that slips through."
    ),
}
FOOD_CATEGORIES = ("Groceries", "Coffee & drinks", "Dining out")

# A known category maps to one basket. Groceries stay everyday, outside the slip.
FILING: tuple[tuple[str, str], ...] = (
    ("Rent", "regular"),
    ("Bills", "regular"),
    ("Transport pass", "regular"),
    ("Repair", "irregular"),
    ("Health", "irregular"),
    ("Groceries", "everyday"),
    ("Dining out", "everyday"),
    ("Coffee & drinks", "everyday"),
    ("Taxis", "everyday"),
    ("Pharmacy", "everyday"),
    ("Shopping", "everyday"),
)
FILING_BASKET = dict(FILING)

SLIP_CATEGORIES = frozenset(
    {"Dining out", "Coffee & drinks", "Taxis", "Pharmacy", "Shopping"}
)
IMPULSE_CATEGORIES = frozenset(
    {"Dining out", "Coffee & drinks", "Taxis", "Shopping"}
)

# slip is None for Small: that line is October's weekly pace, not a stored euro.
EFFORTS = {
    "small": {"name": "Small", "save": 5_000, "slip": None, "salary_percent": 2},
    "steady": {"name": "Steady", "save": 40_000, "slip": 6_400, "salary_percent": 14},
    "big": {"name": "Big", "save": 80_000, "slip": 4_000, "salary_percent": 28},
}

# Holds that repeat a posted merchant. The cushion and the food plan are not bills.
HOLD_KEY = {
    "December rent": "rent",
    "Elisa": "elisa",
    "Helen": "helen",
    "HSL season ticket": "hsl",
    "If insurance": "if",
    "Netflix": "netflix",
    "Fressi": "gym",
}

_VISIT = re.compile(r"(\d+)\s+(visits|orders|rides)")
_DAY = re.compile(r"(\d+)")


def rate_pct(cents: int) -> float:
    pct = 0.0
    for at, step in BONUS_STEPS:
        if cents >= at:
            pct = step
    return pct


def bonus_cents(cents: int) -> int:
    return js_round(cents * rate_pct(cents) / 100)


def next_bonus_step(cents: int) -> tuple[int, float] | None:
    for at, step in BONUS_STEPS:
        if at > cents:
            return at, step
    return None


def monthly_payment_cents(principal_cents: int, annual_pct: float, years: int) -> int:
    principal = principal_cents / 100
    months = years * 12
    rate = annual_pct / 100 / 12
    if rate == 0:
        return js_round(principal / months * 100)
    grown = (1 + rate) ** months
    return js_round(principal * (rate * grown) / (grown - 1) * 100)


def loan_sketch(
    price_eur: int = HOME_EUR,
    rate_percent: float = 4.0,
    years: int = 25,
    down_percent: float = 10.0,
) -> dict:
    price_cents = price_eur * 100
    down = js_round(price_cents * down_percent / 100)
    loan = price_cents - down
    pay = monthly_payment_cents(loan, rate_percent, years)
    stress = monthly_payment_cents(loan, 6.0, years)
    return {
        "price_eur": price_eur,
        "down_cents": down,
        "loan_cents": loan,
        "years": years,
        "rate_percent": rate_percent,
        "monthly_cents": pay,
        "monthly": eur(pay),
        "stress_6pct_cents": stress,
        "stress_6pct": eur(stress),
        "offer": None,
        "decision": None,
        "note": (
            "Arithmetic on the price and rate you set. It does not say approved or rejected. "
            "The mortgage files in Junction-2026-S-pankki are not this outcome."
        ),
    }


@dataclass
class Hold:
    name: str
    detail: str
    amount_cents: int
    before_payday: bool = False
    buffer: bool = False


@dataclass
class Payment:
    id: int
    month: str
    place: str
    amount_cents: int
    basket: str
    category: str
    earns_bonus: bool
    when_label: str
    key: str
    detail: str
    filed_by_customer: bool = False

    def visit_count(self) -> int:
        match = _VISIT.search(self.detail)
        return int(match.group(1)) if match else 1


def _payments() -> list[Payment]:
    rows = [
        ("oct", "Vuokra", 82_000, "regular", "Rent", False, "2 Oct", "rent", "Rent"),
        ("oct", "Elisa", 3_000, "regular", "Bills", False, "28 Oct", "elisa", "Phone"),
        ("oct", "Helen", 4_600, "regular", "Bills", False, "14 Oct", "helen", "Electricity"),
        ("oct", "If", 1_800, "regular", "Bills", False, "20 Oct", "if", "Insurance"),
        ("oct", "Spotify and Netflix", 2_000, "regular", "Bills", False, "5 Oct", "stream", "Streaming"),
        ("oct", "Fressi", 2_990, "regular", "Bills", False, "2 Oct", "gym", "Gym"),
        ("oct", "HSL", 7_400, "regular", "Transport pass", False, "15 Oct", "hsl", "Season ticket"),
        ("oct", "Kodinkonehuolto", 16_500, "irregular", "Repair", False, "8 Oct", "repair", "Washing machine"),
        ("oct", "Hammaslääkäri", 4_500, "irregular", "Health", False, "26 Oct", "dentist", "Dentist"),
        ("oct", "Prisma Herttoniemi", 40_000, "everyday", "Groceries", True, "October", "prisma", "9 visits"),
        ("oct", "S-market", 12_900, "everyday", "Groceries", True, "October", "smarket", "6 visits"),
        ("oct", "K-Market Kamppi", 6_400, "everyday", "Groceries", False, "October", "kmarket", "4 visits"),
        ("oct", "Lidl", 4_700, "everyday", "Groceries", False, "October", "lidl", "3 visits"),
        ("oct", "Wolt", 11_800, "everyday", "Dining out", False, "October", "wolt", "7 orders"),
        ("oct", "Rosso", 6_800, "everyday", "Dining out", True, "October", "rosso", "2 visits"),
        ("oct", "Robert's Coffee", 4_600, "everyday", "Coffee & drinks", False, "October", "roberts", "11 visits"),
        ("oct", "Picnic", 2_400, "everyday", "Coffee & drinks", True, "October", "picnic", "5 visits"),
        ("oct", "ABC", 1_600, "everyday", "Coffee & drinks", True, "October", "abc", "2 visits"),
        ("oct", "Bolt", 4_500, "everyday", "Taxis", False, "October", "bolt", "2 rides"),
        ("oct", "Yliopiston Apteekki", 2_400, "everyday", "Pharmacy", False, "27 Oct", "pharmacy", "1 visit"),
        ("oct", "Sokos", 1_500, "everyday", "Shopping", True, "27 Oct", "sokos", "1 visit"),
        ("nov", "Vuokra", 82_000, "regular", "Rent", False, "3 Nov", "rent", "Rent"),
        ("nov", "K-Market Kamppi", 3_800, "everyday", "Groceries", False, "4 Nov", "kmarket", "Groceries"),
        ("nov", "Bolt", 1_420, "everyday", "Taxis", False, "5 Nov", "bolt", "Taxi"),
        ("nov", "Spotify", 1_200, "regular", "Bills", False, "5 Nov", "stream", "Streaming"),
        ("nov", "Prisma Herttoniemi", 7_200, "everyday", "Groceries", True, "6 Nov", "prisma", "Groceries"),
        ("nov", "Wolt", 3_240, "everyday", "Dining out", False, "7 Nov", "wolt", "Dinner"),
        ("nov", "S-market", 4_600, "everyday", "Groceries", True, "8 Nov", "smarket", "Groceries"),
        ("nov", "Robert's Coffee", 1_240, "everyday", "Coffee & drinks", False, "8 Nov", "roberts", "Coffee"),
        ("nov", "Lidl", 2_200, "everyday", "Groceries", False, "9 Nov", "lidl", "Groceries"),
        ("nov", "Rosso", 2_800, "everyday", "Dining out", True, "10 Nov", "rosso", "Dinner"),
        ("nov", "Prisma Herttoniemi", 5_600, "everyday", "Groceries", True, "11 Nov", "prisma", "Groceries"),
        ("nov", "Picnic", 640, "everyday", "Coffee & drinks", True, "11 Nov", "picnic", "Coffee"),
        ("nov", "Wolt", 2_800, "everyday", "Dining out", False, "12 Nov", "wolt", "Dinner"),
        ("nov", "Bolt", 1_180, "everyday", "Taxis", False, "12 Nov", "bolt", "Taxi"),
        ("nov", "Robert's Coffee", 1_160, "everyday", "Coffee & drinks", False, "12 Nov", "roberts", "Coffee"),
        ("nov", "Picnic", 560, "everyday", "Coffee & drinks", True, "13 Nov", "picnic", "Coffee"),
    ]
    return [Payment(i, *row) for i, row in enumerate(rows)]


def _holds() -> list[Hold]:
    return [
        Hold("December rent", "Leaves 2 Dec. The next salary is 15 Dec.", 82_000),
        Hold("Elisa", "18 Nov, same as October", 3_000),
        Hold("Helen", "14 Nov, the same day it left in October. Due before payday.", 4_600, before_payday=True),
        Hold("HSL season ticket", "20 Nov, after payday", 7_400),
        Hold("If insurance", "Monthly, not posted yet. October left on 20 Oct.", 1_800),
        Hold("Netflix", "Spotify already left on 5 Nov. No date before payday.", 800),
        Hold("Fressi", "October left on 2 Oct. November has not left, so it is already due.", 2_990, before_payday=True),
        Hold("Groceries still to buy", "Food for the rest of November. A plan, not a bill.", GROCERY_PLAN_CENTS),
        Hold("Cushion", "The buffer for a repair. October’s repair was €165.", BUFFER_CENTS, buffer=True),
    ]


def _similar(left: int, right: int) -> bool:
    if left <= 0 or right <= 0:
        return False
    return abs(left - right) / max(left, right) <= 0.10


def _day(label: str) -> int | None:
    match = _DAY.match(label or "")
    return int(match.group(1)) if match else None


def _about_a_month(left: str, right: str) -> bool:
    day_left = _day(left)
    day_right = _day(right)
    if day_left is None or day_right is None:
        return False
    # October is stored as 30 days, the same way This week counts it.
    gap = abs((30 - day_left) + day_right) if "Oct" in left and "Nov" in right else abs(day_left - day_right)
    if "Oct" in right and "Nov" in left:
        gap = (30 - day_right) + day_left
    return 20 <= gap <= 40


class Ledger:
    def __init__(self) -> None:
        self.payments = _payments()
        self.holds = _holds()
        self.effort = "steady"
        self.balance_cents = BALANCE_CENTS
        self.salary_cents = SALARY_CENTS
        self.today = "13 Nov"
        self.payday = "Thursday 15 Nov"
        self.customer_name = "Aino Korhonen"
        self.account = "s-tili"
        # Kept out of every spending total. Marking them does not move S-Tili.
        self.watch = [
            {
                "id": "bolt",
                "name": "Second Bolt",
                "amount_cents": 1_180,
                "when": "12 Nov",
                "detail": "Four minutes after the 12 Nov ride. Not in the spending total.",
            },
            {
                "id": "refund",
                "name": "Prisma refund",
                "amount_cents": 420,
                "when": "11 Nov",
                "detail": "Not on S-Tili.",
            },
        ]

    def effort_spec(self) -> dict:
        return EFFORTS[self.effort]

    def set_effort(self, effort: str) -> dict:
        if effort not in EFFORTS:
            raise KeyError(effort)
        self.effort = effort
        return self.effort_view()

    def month(self, month: str) -> list[Payment]:
        return [row for row in self.payments if row.month == month]

    def sum_month(self, month: str, pred=lambda _row: True) -> int:
        return sum(row.amount_cents for row in self.month(month) if pred(row))

    def payment(self, payment_id: int) -> Payment:
        for row in self.payments:
            if row.id == payment_id:
                return row
        raise LookupError(payment_id)

    def refile(self, payment_id: int, category: str) -> Payment:
        """Refile one line. October is one line per place, so the whole line moves.

        Bonus stays on the place. A filing does not turn it on or off.
        """
        basket = FILING_BASKET.get(category)
        if basket is None:
            raise KeyError(category)
        row = self.payment(payment_id)
        row.category = category
        row.basket = basket
        row.filed_by_customer = True
        return row

    def is_recurring(self, row: Payment) -> bool:
        """Same merchant, a similar amount, and about a month between charges."""
        for other in self.payments:
            if other.id == row.id or other.key != row.key:
                continue
            if _similar(row.amount_cents, other.amount_cents) and _about_a_month(row.when_label, other.when_label):
                return True
        for hold in self.holds:
            if HOLD_KEY.get(hold.name) == row.key and _similar(row.amount_cents, hold.amount_cents):
                return True
        return False

    def filing_reason(self, row: Payment) -> str:
        if row.filed_by_customer:
            return f"You filed this as {row.category}. SpendSight counts it that way now."
        if self.is_recurring(row):
            return (
                f"Filed as {row.category}. The same merchant, a similar amount, "
                "and about a month between charges."
            )
        return f"Filed as {row.category}."

    def payment_view(self, row: Payment) -> dict:
        return {
            "id": row.id,
            "month": row.month,
            "place": row.place,
            "amount_cents": row.amount_cents,
            "amount": eur(row.amount_cents),
            "category": row.category,
            "basket": row.basket,
            "basket_name": BASKET_NAME[row.basket],
            "earns_bonus": row.earns_bonus,
            "when": row.when_label,
            "detail": row.detail,
            "key": row.key,
            "filed_by_customer": row.filed_by_customer,
            "recurring": self.is_recurring(row),
            "in_slip": row.category in SLIP_CATEGORIES,
            "reason": self.filing_reason(row),
        }

    def slip_cents(self) -> int:
        spec = self.effort_spec()
        if spec["slip"] is None:
            october_slip = self.sum_month("oct", lambda row: row.category in SLIP_CATEGORIES)
            return js_round(october_slip * 7 / 30)
        return spec["slip"]

    def slipped_since_friday(self) -> int:
        return sum(
            row.amount_cents
            for row in self.month("nov")
            if row.when_label == "13 Nov" and row.category in SLIP_CATEGORIES
        )

    def safe_week(self) -> dict:
        bills = [hold for hold in self.holds if hold.before_payday]
        bill_sum = sum(hold.amount_cents for hold in bills)
        buffer = next(hold.amount_cents for hold in self.holds if hold.buffer)
        return {
            "bills": [
                {"name": hold.name, "amount_cents": hold.amount_cents, "detail": hold.detail}
                for hold in bills
            ],
            "bills_cents": bill_sum,
            "buffer_cents": buffer,
            "spend_cents": self.balance_cents - bill_sum - buffer,
        }

    def runway(self) -> dict:
        days = 13
        rent = sum(row.amount_cents for row in self.month("nov") if row.key == "rent")
        variable = self.sum_month("nov") - rent
        count = sum(1 for row in self.month("nov") if row.key != "rent")
        per_day = js_round(variable / days)
        left = math.floor(self.balance_cents / per_day) if per_day else 0
        return {
            "days": days,
            "rent_cents": rent,
            "variable_cents": variable,
            "count": count,
            "per_day_cents": per_day,
            "left_days": left,
        }

    def impulse(self) -> dict:
        rows = [row for row in self.month("nov") if row.category in IMPULSE_CATEGORIES]
        by_day: dict[str, dict] = {}
        for row in rows:
            bucket = by_day.setdefault(
                row.when_label,
                {"when": row.when_label, "amount_cents": 0, "count": 0, "places": []},
            )
            bucket["amount_cents"] += row.amount_cents
            bucket["count"] += 1
            bucket["places"].append(row.place)
        days = sorted(by_day.values(), key=lambda item: _day(item["when"]) or 0)
        peak = sorted(days, key=lambda item: (-item["count"], -item["amount_cents"]))[0]
        coffee = [row for row in self.month("oct") if row.category == "Coffee & drinks"]
        wolt = next(row for row in self.month("oct") if row.key == "wolt")
        return {
            "count": len(rows),
            "days": days,
            "peak": peak,
            "coffee_visits": sum(row.visit_count() for row in coffee),
            "wolt_orders": wolt.visit_count(),
        }

    def dining(self) -> dict:
        rows = [row for row in self.month("oct") if row.category == "Dining out"]
        total = sum(row.amount_cents for row in rows)
        orders = sum(row.visit_count() for row in rows)
        meal = js_round(total / orders) if orders else 0
        fifth = js_round(total * 0.2)
        wolt = sum(row.amount_cents for row in self.month("oct") if row.key == "wolt")
        rosso = sum(row.amount_cents for row in self.month("oct") if row.key == "rosso")
        return {
            "dining_cents": total,
            "orders": orders,
            "meal_cents": meal,
            "fifth_cents": fifth,
            "once_cents": meal * 4,
            "twice_cents": meal * 8,
            "wolt_cents": wolt,
            "rosso_cents": rosso,
        }

    def subscription_cut(self) -> dict:
        fressi = sum(row.amount_cents for row in self.month("oct") if row.key == "gym")
        spotify = sum(row.amount_cents for row in self.month("nov") if row.key == "stream")
        netflix = next(hold.amount_cents for hold in self.holds if hold.name == "Netflix")
        items = [
            {
                "id": "gym",
                "name": "Fressi",
                "amount_cents": fressi,
                "use": "Charged on 2 Oct. No visit is in the ledger.",
            },
            {
                "id": "netflix",
                "name": "Netflix",
                "amount_cents": netflix,
                "use": "No play count. The November charge has not left.",
            },
            {
                "id": "spotify",
                "name": "Spotify",
                "amount_cents": spotify,
                "use": "Charged on 5 Nov. No play count is in the ledger.",
            },
        ]
        month = sum(item["amount_cents"] for item in items)
        return {"items": items, "month_cents": month, "year_cents": month * 12}

    def basket_rows(self, month: str) -> list[dict]:
        rows = []
        for key, name in BASKET_NAME.items():
            amount = self.sum_month(month, lambda row, basket=key: row.basket == basket)
            rows.append(
                {
                    "id": key,
                    "name": name,
                    "line": BASKET_LINE[key],
                    "amount_cents": amount,
                    "amount": eur(amount),
                }
            )
        return rows

    def food_rows(self, month: str) -> list[dict]:
        rows = []
        for name in FOOD_CATEGORIES:
            amount = self.sum_month(month, lambda row, label=name: row.category == label)
            rows.append({"name": name, "amount_cents": amount, "amount": eur(amount)})
        return rows

    def charts(self) -> list[dict]:
        labels = ("Groceries", "Coffee & drinks", "Dining out", "Taxis")
        rows = []
        for label in labels:
            october = self.sum_month("oct", lambda row, name=label: row.category == name)
            november = self.sum_month("nov", lambda row, name=label: row.category == name)
            rows.append(
                {
                    "label": label,
                    "october_pace_cents": js_round(october * 13 / 30),
                    "november_cents": november,
                }
            )
        return rows

    def sgroup(self, month: str) -> int:
        return self.sum_month(month, lambda row: row.earns_bonus)

    def jacket(self) -> dict:
        now = self.sgroup("nov")
        with_jacket = now + JACKET_CENTS
        gain = bonus_cents(with_jacket) - bonus_cents(now)
        spend = self.safe_week()["spend_cents"]
        slip = self.slip_cents()
        return {
            "price_cents": JACKET_CENTS,
            "sgroup_now_cents": now,
            "sgroup_with_jacket_cents": with_jacket,
            "gain_cents": gain,
            "loss_cents": JACKET_CENTS - gain,
            "over_slip_cents": JACKET_CENTS - slip,
            "left_in_week_cents": spend - JACKET_CENTS,
        }

    def bonus_view(self) -> dict:
        now = self.sgroup("nov")
        october = self.sgroup("oct")
        nxt = next_bonus_step(now)
        now_bonus = bonus_cents(now)
        story: dict = {
            "november_sgroup_cents": now,
            "november_bonus_cents": now_bonus,
            "november_rate_pct": rate_pct(now),
            "october_sgroup_cents": october,
            "october_bonus_cents": bonus_cents(october),
            "october_rate_pct": rate_pct(october),
        }
        if nxt:
            at, _step = nxt
            then_bonus = bonus_cents(at)
            gap = at - now
            gain = then_bonus - now_bonus
            story.update(
                {
                    "next_step_cents": at,
                    "next_rate_pct": _step,
                    "gap_cents": gap,
                    "bonus_at_next_cents": then_bonus,
                    "gain_cents": gain,
                    "loss_cents": gap - gain,
                    "visa_on_gap_cents": js_round(gap * 5 / 1000),
                }
            )
        week_at = now + WEEK_SHOP_CENTS + COFFEE_PLAN_CENTS
        month_at = now + GROCERY_PLAN_CENTS + COFFEE_PLAN_CENTS
        food_only = now + GROCERY_PLAN_CENTS
        story.update(
            {
                "week_at_cents": week_at,
                "month_at_cents": month_at,
                "month_bonus_cents": bonus_cents(month_at),
                "food_only_cents": food_only,
                "food_only_bonus_cents": bonus_cents(food_only),
                "steps": [{"at_cents": at, "pct": pct} for at, pct in BONUS_STEPS],
                "note": "The Visa 0.5% is a separate line on the moved amount. It is not folded into the step.",
            }
        )
        return story

    def effort_view(self) -> dict:
        rows = []
        october_slip = self.sum_month("oct", lambda row: row.category in SLIP_CATEGORIES)
        usual = js_round(october_slip * 7 / 30)
        for key, spec in EFFORTS.items():
            slip = usual if spec["slip"] is None else spec["slip"]
            rows.append(
                {
                    "id": key,
                    "name": spec["name"],
                    "save_cents": spec["save"],
                    "save": eur(spec["save"]),
                    "slip_cents": slip,
                    "slip": eur(slip),
                    "salary_percent": spec["salary_percent"],
                    "selected": key == self.effort,
                    "piles_cents": {str(years): spec["save"] * 12 * years for years in (1, 3, 5)},
                }
            )
        return {
            "selected": self.effort,
            "efforts": rows,
            "asp_min_cents": ASP_MIN_CENTS,
            "asp_max_cents": ASP_MAX_CENTS,
            "note": (
                "€1,500 is the most ASP allows in a month. It is not this month’s number. "
                "The pile is the monthly amount times twelve times the years, with no interest. "
                "Rent, the food plan, and the buffer do not move. Safe to spend does not move."
            ),
        }

    def today_view(self) -> dict:
        week = self.safe_week()
        spec = self.effort_spec()
        slip = self.slip_cents()
        slipped = self.slipped_since_friday()
        bonus = self.bonus_view()
        subs = {item["id"]: item["amount_cents"] for item in self.subscription_cut()["items"]}
        holds = sum(hold.amount_cents for hold in self.holds)
        return {
            "kind": "read",
            "screen": "today",
            "customer_name": self.customer_name,
            "today": self.today,
            "payday": self.payday,
            "account": self.account,
            "effort": self.effort,
            "effort_name": spec["name"],
            "balance_cents": self.balance_cents,
            "balance": eur(self.balance_cents),
            "salary_cents": self.salary_cents,
            "salary": eur(self.salary_cents),
            "safe_to_spend_cents": week["spend_cents"],
            "safe_to_spend": eur(week["spend_cents"]),
            "save_cents": spec["save"],
            "save": eur(spec["save"]),
            "slip_cents": slip,
            "slip": eur(slip),
            "slipped_since_friday_cents": slipped,
            "slipped_since_friday": eur(slipped),
            "buffer_cents": week["buffer_cents"],
            "bills_before_payday_cents": week["bills_cents"],
            "left_after_salary_cents": self.balance_cents + self.salary_cents - holds,
            "food": {"oct": self.food_rows("oct"), "nov": self.food_rows("nov")},
            "october_baskets": self.basket_rows("oct"),
            "october_slip_cents": self.sum_month("oct", lambda row: row.category in SLIP_CATEGORIES),
            "bonus_gap_cents": bonus.get("gap_cents"),
            "november_bonus_cents": bonus["november_bonus_cents"],
            "bonus_at_next_cents": bonus.get("bonus_at_next_cents"),
            "jacket_cents": JACKET_CENTS,
            "fressi_cents": subs["gym"],
            "netflix_cents": subs["netflix"],
            "card_blocked": False,
            "note": (
                "Passing the slip line sends one note. The card is not blocked. "
                "Groceries stay outside the slip. The save setting does not change safe to spend."
            ),
        }

    def summary(self) -> dict:
        """The oracle figures. Steady names stay on Steady even if the setting changes."""
        week = self.safe_week()
        runway = self.runway()
        impulse = self.impulse()
        dining = self.dining()
        subs = self.subscription_cut()
        jacket = self.jacket()
        holds = sum(hold.amount_cents for hold in self.holds)
        peak = impulse["peak"]
        steady_save = EFFORTS["steady"]["save"]
        loan = loan_sketch()
        return {
            "october_out_cents": self.sum_month("oct"),
            "must_every_month_cents": self.sum_month("oct", lambda row: row.basket == "regular"),
            "must_when_it_happens_cents": self.sum_month("oct", lambda row: row.basket == "irregular"),
            "everyday_cents": self.sum_month("oct", lambda row: row.basket == "everyday"),
            "october_slip_cents": self.sum_month("oct", lambda row: row.category in SLIP_CATEGORIES),
            "october_sgroup_cents": self.sgroup("oct"),
            "october_bonus_cents": bonus_cents(self.sgroup("oct")),
            "november_out_cents": self.sum_month("nov"),
            "november_sgroup_cents": self.sgroup("nov"),
            "november_bonus_cents": bonus_cents(self.sgroup("nov")),
            "holds_cents": holds,
            "left_after_salary_cents": self.balance_cents + self.salary_cents - holds,
            "safe_to_spend_cents": week["spend_cents"],
            "bills_before_payday_cents": week["bills_cents"],
            "steady_slip_cents": EFFORTS["steady"]["slip"],
            "runway_days": runway["left_days"],
            "runway_per_day_cents": runway["per_day_cents"],
            "runway_payments": runway["count"],
            "impulse_day": peak["when"],
            "impulse_payments": peak["count"],
            "impulse_cents": peak["amount_cents"],
            "dining_fifth_cents": dining["fifth_cents"],
            "dining_one_meal_cents": dining["once_cents"],
            "dining_two_meals_cents": dining["twice_cents"],
            "subscriptions_month_cents": subs["month_cents"],
            "subscriptions_year_cents": subs["year_cents"],
            "jacket_bonus_gain_cents": jacket["gain_cents"],
            "jacket_net_cost_cents": jacket["loss_cents"],
            "steady_pile_5y_cents": steady_save * 12 * 5,
            "loan_default_month_cents": loan["monthly_cents"],
            "loan_stress_6pct_cents": loan["stress_6pct_cents"],
        }
