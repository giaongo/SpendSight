"""Compare one customer with people their age.

The figures come from Statistics Finland table 15jx, the same response as
``GET /statistics/education-assets``: deposits, listed shares, and mutual
funds per person aged 18 and over. It does not count spending or income, and
its unit is a person, not a household.

The Junction data has the customer's age but not their sex or education, so
both stay at "Total". Every euro figure is converted to cents here. The model
and the phone only show them.
"""

from __future__ import annotations

from datetime import date

from app.answer_cards import month_name, usual_out
from app.money import eur, js_round

TOTAL = "SSS"
ALL_AGES = "18-"
DEPOSITS = "F2"
SHARES_AND_FUNDS = "F5"

# (StatFin age code, label)
AGE_GROUPS: tuple[tuple[str, str], ...] = (
    ("18-34", "18–34"),
    ("35-54", "35–54"),
    ("55-64", "55–64"),
    ("65-", "65 and over"),
)

_P25 = "vtutk_h_p25"
_P50 = "vtutk_h_p50"
_P75 = "vtutk_h_p75"
_MEAN_ALL = "vtutk_h_keskiarvo_eiehd"
_OWNER_SHARE = "vtutk_h_omistaja_osuus"

# Dimension ids carry a version suffix, such as ``ikaryhma_10_20180101``.
_DIMENSION_PREFIXES = {
    "asset": "vtutk_varlaji",
    "education": "koulutusaste",
    "sex": "sukupuoli",
    "year": "timeperiod",
    "age": "ikaryhma",
    "info": "contentscode",
}

SOURCE_NAME = "Statistics Finland, households’ assets, table 15jx"


class PeerDataError(Exception):
    """The statistics response is not the table this module reads."""


def age_group(age: int | None) -> str:
    if age is None or age < 18:
        return ALL_AGES
    if age <= 34:
        return "18-34"
    if age <= 54:
        return "35-54"
    if age <= 64:
        return "55-64"
    return "65-"


def age_label(code: str) -> str:
    return dict(AGE_GROUPS).get(code, "18 and over")


class AssetTable:
    """Look up one cell of a json-stat2 response by dimension codes."""

    def __init__(self, data: dict) -> None:
        try:
            ids: list[str] = list(data["id"])
            sizes: list[int] = list(data["size"])
            dimensions = data["dimension"]
            self._values = data["value"]
        except (KeyError, TypeError) as exc:
            raise PeerDataError("The statistics response is not json-stat2") from exc
        self._roles: dict[str, str] = {}
        for role, prefix in _DIMENSION_PREFIXES.items():
            match = next((dim for dim in ids if dim.startswith(prefix)), None)
            if match is None:
                raise PeerDataError(f"The statistics response has no {role} dimension")
            self._roles[role] = match
        self._ids = ids
        self._strides = [1] * len(ids)
        for i in range(len(ids) - 2, -1, -1):
            self._strides[i] = self._strides[i + 1] * sizes[i + 1]
        self._index: dict[str, dict[str, int]] = {}
        for dim in ids:
            index = dimensions[dim]["category"]["index"]
            if isinstance(index, list):
                index = {code: pos for pos, code in enumerate(index)}
            self._index[dim] = index
        years = sorted(self._index[self._roles["year"]])
        self.year = years[-1]
        self.updated = data.get("updated")

    def value(self, *, asset: str, age: str, info: str) -> float | None:
        codes = {
            self._roles["asset"]: asset,
            self._roles["education"]: TOTAL,
            self._roles["sex"]: TOTAL,
            self._roles["year"]: self.year,
            self._roles["age"]: age,
            self._roles["info"]: info,
        }
        offset = 0
        for dim, stride in zip(self._ids, self._strides):
            position = self._index[dim].get(codes[dim])
            if position is None:
                return None
            offset += position * stride
        if isinstance(self._values, dict):
            raw = self._values.get(str(offset))
        else:
            raw = self._values[offset] if offset < len(self._values) else None
        return None if raw is None else float(raw)

    def euros_cents(self, *, asset: str, age: str, info: str) -> int | None:
        raw = self.value(asset=asset, age=age, info=info)
        return None if raw is None else int(round(raw * 100))

    def ages(self) -> list[str]:
        known = self._index[self._roles["age"]]
        return [code for code, _label in AGE_GROUPS if code in known]


def _figure(label: str, cents: int | None) -> dict | None:
    if cents is None:
        return None
    return {"label": label, "amount_cents": cents, "amount": eur(cents)}


def _position(you: int, p25: int, p50: int, p75: int, who: str) -> tuple[str, str]:
    """Where the customer sits among people aged ``who``. Neutral words, no praise or blame."""
    if you < p25:
        return "below_p25", f"Lower than most people aged {who}"
    if you < p50:
        return "p25_to_median", f"Below the middle for people aged {who}"
    if you <= p75:
        return "median_to_p75", f"Above the middle for people aged {who}"
    return "above_p75", f"Higher than most people aged {who}"


def _difference(you: int, median: int, who: str) -> str:
    gap = you - median
    if gap == 0:
        return f"The same as the median for people aged {who}"
    word = "more" if gap > 0 else "less"
    return f"{eur(abs(gap))} {word} than the median for people aged {who}"


def _items(you_label: str, you: int, group: dict) -> list[dict]:
    rows = [
        ("you", you_label, you, True),
        ("p75", f"Aged {group['label']}, 75th percentile", group["p75_cents"], False),
        ("median", f"Aged {group['label']}, median", group["median_cents"], False),
        ("p25", f"Aged {group['label']}, 25th percentile", group["p25_cents"], False),
    ]
    scale = max(max(abs(amount) for _k, _l, amount, _h in rows), 1)
    return [
        {
            "key": key,
            "label": label,
            "amount_cents": amount,
            "amount": eur(amount),
            "detail": None,
            "highlight": highlight,
            "bar_pct": js_round(max(amount, 0) * 100 / scale),
        }
        for key, label, amount, highlight in rows
    ]


def _group(table: AssetTable, code: str) -> dict | None:
    p25 = table.euros_cents(asset=DEPOSITS, age=code, info=_P25)
    p50 = table.euros_cents(asset=DEPOSITS, age=code, info=_P50)
    p75 = table.euros_cents(asset=DEPOSITS, age=code, info=_P75)
    if p25 is None or p50 is None or p75 is None:
        return None
    return {
        "code": code,
        "label": age_label(code),
        "p25_cents": p25,
        "median_cents": p50,
        "p75_cents": p75,
        "mean_cents": table.euros_cents(asset=DEPOSITS, age=code, info=_MEAN_ALL),
        "funds_owner_share_pct": table.value(asset=SHARES_AND_FUNDS, age=code, info=_OWNER_SHARE),
        "funds_median_cents": table.euros_cents(asset=SHARES_AND_FUNDS, age=code, info=_P50),
    }


def _short_date(iso: str) -> str:
    day = date.fromisoformat(iso)
    return f"{day.day} {day.strftime('%b')} {day.year}"


def _customer(book, years: int = 5) -> dict:
    """This customer's own figures for a peer answer, all counted from their Junction rows.

    Only the balance can be compared with the table. The rest is context the model may
    say next to the comparison, never compare with a figure for other people.
    """
    spending = book.spending_view(None)
    month = spending["month"]
    usual, months_used = usual_out(book, month)
    today = book.today_view()
    save = book.save_view(years)
    return {
        "balance_cents": book.balance_cents,
        "balance_date": book.last_date,
        "month": month,
        "month_out_cents": spending["total_out_cents"],
        "month_in_cents": spending["total_in_cents"],
        "usual_out_cents": usual,
        "usual_months": months_used,
        "salary_cents": today["latest_salary_cents"],
        "salary_date": today["latest_salary_date"],
        "years": years,
        "deposits": save["deposits"],
        "deposited_cents": save["deposited_cents"],
    }


def _you_facts(me: dict, group_label: str) -> dict:
    def money(cents: int | None) -> str | None:
        return None if cents is None else eur(cents)

    return {
        "age_group": group_label,
        "s_tili_balance": eur(me["balance_cents"]),
        "s_tili_balance_date": me["balance_date"],
        "latest_month": month_name(me["month"]),
        "money_out_latest_month": eur(me["month_out_cents"]),
        "money_in_latest_month": eur(me["month_in_cents"]),
        "usual_month_money_out": money(me["usual_out_cents"]),
        "latest_salary": money(me["salary_cents"]),
        "latest_salary_date": me["salary_date"],
        f"investment_deposits_paid_in_last_{me['years']}_years": eur(me["deposited_cents"]),
        f"investment_deposit_count_last_{me['years']}_years": me["deposits"],
    }


def peer_card(book, data: dict) -> tuple[dict, dict]:
    """The card the phone renders and the facts the model may use.

    The facts hold three parts: ``you`` (this customer's own figures), ``people_your_age``
    (Statistics Finland), and ``comparison`` (the one like-for-like comparison, already
    worked out here). Raises PeerDataError when the customer's age group has no deposit figures.
    """
    table = AssetTable(data)
    chosen = age_group(book.age)
    groups = {code: g for code in [*table.ages(), ALL_AGES] if (g := _group(table, code)) is not None}
    group = groups.get(chosen) or groups.get(ALL_AGES)
    if group is None:
        raise PeerDataError("No deposit figures for this age group")
    you = book.balance_cents
    you_label = f"You, S-Tili on {_short_date(book.last_date)}"

    def choice(g: dict) -> dict:
        key, text = _position(you, g["p25_cents"], g["median_cents"], g["p75_cents"], g["label"])
        return {
            "key": g["code"],
            "label": g["label"],
            "verdict": text,
            "verdict_key": key,
            "verdict_detail": _difference(you, g["median_cents"], g["label"]),
            "reference": _figure(f"Median deposits, aged {g['label']}", g["median_cents"]),
            "items": _items(you_label, you, g),
        }

    selected = choice(group)
    me = _customer(book)
    funds_share = group["funds_owner_share_pct"]
    label = group["label"]
    table_rows = [
        ["Your S-Tili balance", eur(you)],
        [f"Deposits, aged {label}, 25th percentile", eur(group["p25_cents"])],
        [f"Deposits, aged {label}, median", eur(group["median_cents"])],
        [f"Deposits, aged {label}, 75th percentile", eur(group["p75_cents"])],
    ]
    if group["mean_cents"] is not None:
        table_rows.append([f"Deposits, aged {label}, mean", eur(group["mean_cents"])])
    table_rows.append([f"Your investment deposits, last {me['years']} years, paid in", eur(me["deposited_cents"])])
    if funds_share is not None:
        table_rows.append([f"Own listed shares or funds, aged {label}", f"{funds_share:g}%"])
    if group["funds_median_cents"] is not None:
        table_rows.append([f"Shares and funds of those owners, median, aged {label}", eur(group["funds_median_cents"])])
    table_rows.append([f"Your money out, {month_name(me['month'])}", eur(me["month_out_cents"])])
    if me["usual_out_cents"] is not None:
        table_rows.append(["Your money out, usual month", eur(me["usual_out_cents"])])

    card = {
        "kind": "peer_compare",
        "title": "You and people your age",
        "context": f"Bank deposits · aged {group['label']} · Finland {table.year}",
        "headline": _figure("Your S-Tili balance", you),
        "reference": selected["reference"],
        "verdict": selected["verdict"],
        "verdict_detail": selected["verdict_detail"],
        "items": selected["items"],
        "options": {
            "label": "Compare with an age group",
            "selected": group["code"],
            "note": f"Set from your details: you are {book.age}." if book.age is not None else None,
            "choices": [choice(g) for code, g in groups.items() if code != ALL_AGES],
        },
        "how": (
            f"People your age: {SOURCE_NAME}, {table.year}. Bank deposits per person aged "
            f"{group['label']}, sex and education not set. Half of them have more than the median. "
            "Your figure is the balance of your S-Tili account after your latest payment, so other "
            "accounts are not in it. Your investment deposits are what you paid in, not what they are worth. "
            "Statistics Finland has no spending figures, so your spending is shown on its own. "
            "The AI only wrote the sentence."
        ),
        "table": {
            "caption": f"You and people aged {label}",
            "columns": ["", "Amount"],
            "rows": table_rows,
        },
        "screen": "today",
    }
    facts = {
        "you": _you_facts(me, label),
        "people_your_age": {
            "age_group": label,
            "deposits_25th_percentile": eur(group["p25_cents"]),
            "deposits_median": eur(group["median_cents"]),
            "deposits_75th_percentile": eur(group["p75_cents"]),
            "deposits_mean": None if group["mean_cents"] is None else eur(group["mean_cents"]),
            "share_owning_listed_shares_or_funds": None if funds_share is None else f"{funds_share:g}%",
            "listed_shares_and_funds_median_of_owners": (
                None if group["funds_median_cents"] is None else eur(group["funds_median_cents"])
            ),
        },
        "statistics": {
            "source": SOURCE_NAME,
            "year": table.year,
            "covers": "bank deposits, listed shares, and mutual funds per person aged 18 and over",
            "does_not_cover": "spending, income, salary, or households",
        },
        "comparison": {
            "compares": "your S-Tili balance with the bank deposits of people your age",
            "position": selected["verdict"],
            "difference_from_median": selected["verdict_detail"],
        },
    }
    return card, facts
