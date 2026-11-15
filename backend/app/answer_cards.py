"""The card under a coach answer, and the parts of that answer.

The phone renders a coach answer the way the design does: a lead sentence,
a short detail line, then one card with figures it can expand into a table,
"How I got this", and "Ask next" chips. The service builds the card from the
customer's own Junction rows. The model only writes the lead and the detail,
so no figure on the card comes from the model.

Every card has the same keys, so one component can draw any of them:
``kind``, ``title``, ``context``, ``headline``, ``reference``, ``verdict``,
``verdict_detail``, ``items``, ``options``, ``how``, ``table``, ``screen``.
"""

from __future__ import annotations

import calendar
import re
from datetime import date

from app.money import eur, js_round

PERSONAL = "personal_finance"
PEER = "peer_comparison"
GENERAL = "general"

_SAVINGS = re.compile(
    r"\bsaved\b|\bsavings\b|put aside|invest|deposit|säästin|säästöt|säästöni|säästänyt|sijoit|talletu|talletin|sparat",
    re.IGNORECASE,
)
_BALANCE = re.compile(
    r"balance|salary|paycheck|wage|how much (do i have|is left|have i got)|left on|saldo|palkka|jäljellä",
    re.IGNORECASE,
)
_MONTHS = re.compile(
    r"months|monthly|trend|over time|usual|normal month|last year|change|changed|compare|"
    r"kuukaudet|kuukausittain|tavallinen kuukausi|muuttu|vertaa|månad|förändr",
    re.IGNORECASE,
)

FOLLOW_UPS: dict[str, list[tuple[str, str]]] = {
    "spending_breakdown": [
        ("How did my spending change over the last months?", PERSONAL),
        ("How do my savings compare with people my age?", PEER),
    ],
    "month_trend": [
        ("Where did my money go?", PERSONAL),
        ("How do my savings compare with people my age?", PEER),
    ],
    "balance": [
        ("Where did my money go?", PERSONAL),
        ("How much have I saved?", PERSONAL),
    ],
    "savings": [
        ("How do my savings compare with people my age?", PEER),
        ("Where did my money go?", PERSONAL),
    ],
    "savings_goal": [
        ("Where did my money go?", PERSONAL),
        ("How much have I saved?", PERSONAL),
    ],
    "peer_compare": [
        ("Where did my money go?", PERSONAL),
        ("How much have I saved?", PERSONAL),
    ],
    GENERAL: [
        ("Where did my money go?", PERSONAL),
        ("How do my savings compare with people my age?", PEER),
    ],
}

CAPTIONS = {
    PERSONAL: "AI answer · numbers come from your transactions",
    PEER: "AI answer · numbers come from your transactions and Statistics Finland",
    GENERAL: "AI answer · not based on your transactions",
}


def follow_ups(key: str) -> list[dict]:
    return [{"text": text, "category": category} for text, category in FOLLOW_UPS.get(key, FOLLOW_UPS[GENERAL])]


def personal_kind(question: str) -> str:
    """Which card answers a question about the customer's own money.

    "How could I save more?" is about where the money goes, so only past savings
    ("saved", "savings", deposits) open the savings card. A target with an amount
    and a time ("save €500 in 3 months") opens the savings goal card.
    """
    if savings_goal(question) is not None:
        return "savings_goal"
    if _SAVINGS.search(question):
        return "savings"
    if _BALANCE.search(question):
        return "balance"
    if _MONTHS.search(question):
        return "month_trend"
    return "spending_breakdown"


def personal_card(book, question: str) -> dict:
    kind = personal_kind(question)
    if kind == "savings_goal":
        goal = savings_goal(question)
        assert goal is not None
        return _savings_goal_card(book, *goal)
    if kind == "savings":
        return _savings_card(book)
    if kind == "balance":
        return _balance_card(book)
    if kind == "month_trend":
        return _month_trend_card(book)
    return _spending_card(book, question)


# Words that name a Junction category, so "restaurants" makes that row the main figure.
_CATEGORY_WORDS: dict[str, str] = {
    "Restaurants": r"restaurant|dining|eat(ing)? out|takeaway|wolt|ravintola",
    "Groceries": r"grocer|food|supermarket|ruoka|ruokaostok|kauppa",
    "Transportation": r"transport|taxi|bus|train|fuel|petrol|car\b|liikenne|bensa|taksi",
    "Entertainment": r"entertainment|movie|cinema|concert|game|viihde|elokuv",
    "Housing": r"housing|rent\b|mortgage payment|asuminen|vuokra",
    "Utilities": r"utilit|electric|water bill|internet|phone bill|sähkö",
    "Health": r"health|doctor|dentist|pharmacy|medicine|terveys|apteekki|lääkäri",
    "Lifestyle": r"lifestyle|clothes|clothing|shopping|beauty|vaatte",
    "Travel": r"travel|flight|hotel|holiday|vacation|matka|loma",
    "Home & Tech": r"home & tech|electronics|furniture|gadget|tech|kodinkone",
}


def named_category(question: str, names: list[str]) -> str | None:
    """The category the question names, if the customer has it this month."""
    text = question.lower()
    for name in names:
        if name.lower() in text:
            return name
    for name, words in _CATEGORY_WORDS.items():
        if name in names and re.search(words, text):
            return name
    return None


def card_facts(card: dict) -> dict:
    """What the card shows, as euro strings, so the lead matches the card."""

    def figure(value: dict | None) -> str | None:
        return None if value is None else f"{value['label']}: {value['amount']}"

    return {
        "title": card["title"],
        "context": card["context"],
        "main_figure": figure(card.get("headline")),
        "compared_with": figure(card.get("reference")),
        "verdict": card.get("verdict"),
        "verdict_detail": card.get("verdict_detail"),
        "rows": [
            f"{item['label']}: {item['amount']}" + (f" ({item['detail']})" if item.get("detail") else "")
            for item in card["items"]
        ],
    }


_PARAGRAPHS = re.compile(r"\n\s*\n")
_SENTENCE_END = re.compile(r"(?<=[.!?])\s+(?=[\"“*A-ZÅÄÖ0-9€])")
# A small model sometimes copies a payload key or a layout word as a label: "**Headline:** ...".
_LABEL = re.compile(
    r"^\s*\**\s*(main figure|headline|card|lead|answer|detail|paragraph\s*[12]|kappale\s*[12])\s*\**\s*:\s*\**\s*",
    re.IGNORECASE,
)
_DOLLAR = re.compile(r"\$\s?(?=\d)")


def tidy_answer(text: str, paragraphs: int) -> str:
    """Hold the model to the layout: at most ``paragraphs`` paragraphs, no copied labels, euros only.

    Nothing here changes a figure. A ``$`` before an amount becomes ``€``, because every
    amount the model was given is in euros.
    """
    parts = [part.strip() for part in _PARAGRAPHS.split(text.strip()) if part.strip()]
    parts = [_DOLLAR.sub("€", _LABEL.sub("", part)).strip() for part in parts[:paragraphs]]
    return "\n\n".join(part for part in parts if part) or text.strip()


_ASKS_SPENDING = re.compile(
    r"spend|spent|spender|income|earn|salary|household|kulut|kulua|tulot|palkka|ansai|kotitalou",
    re.IGNORECASE,
)
_SPEND_WORD = re.compile(r"spend|spent|spender|income|earn|salar|kulut|kulua|tulo|palka|ansai", re.IGNORECASE)
_OTHERS_WORD = re.compile(
    r"\b(other|others|people|everyone|average|typical|most|peers?|households?|finns)\b|muut|muita|muihin|keskiverto|ikäis",
    re.IGNORECASE,
)
_SAYS_NO_DATA = re.compile(
    r"compare[^.!?]*\bnot (your )?spending\b|can(not|'t|’t) compare|no (figure|data)|en kulutustasi|ei kulutust",
    re.IGNORECASE,
)
NOT_SPENDING = {
    "English": "I can compare your savings with people your age, not your spending.",
    "Finnish": "Voin verrata säästöjäsi ikäisiisi, en kulutustasi.",
}


def guard_peer_spending(question: str, answer: str, language: str) -> str:
    """A peer answer about spending must not claim a spending comparison.

    Statistics Finland's table has no spending or income, so a sentence that sets the
    customer's spending or income against other people is removed, and the answer opens
    with the fixed sentence that says what can be compared. Only for languages with that
    sentence; other languages rely on the prompt.
    """
    opener = NOT_SPENDING.get(language)
    if opener is None or not _ASKS_SPENDING.search(question):
        return answer
    kept_paragraphs = []
    for paragraph in _PARAGRAPHS.split(answer.strip()):
        sentences = [
            sentence
            for sentence in _SENTENCE_END.split(paragraph.strip())
            if _SAYS_NO_DATA.search(sentence)
            or not (_SPEND_WORD.search(sentence) and _OTHERS_WORD.search(sentence))
        ]
        if sentences:
            kept_paragraphs.append(" ".join(sentences))
    if not any(_SAYS_NO_DATA.search(paragraph) for paragraph in kept_paragraphs[:1]):
        kept_paragraphs.insert(0, opener)
    return "\n\n".join(kept_paragraphs[:2])


def split_answer(text: str) -> tuple[str, str | None]:
    """The lead is the first paragraph. Without a blank line, it is the first sentence."""
    text = text.strip()
    parts = [part.strip() for part in _PARAGRAPHS.split(text) if part.strip()]
    if len(parts) > 1:
        return parts[0], "\n\n".join(parts[1:])
    pieces = _SENTENCE_END.split(text, maxsplit=1)
    if len(pieces) == 2 and pieces[1].strip():
        return pieces[0].strip(), pieces[1].strip()
    return text, None


# Cards ---------------------------------------------------------------------


def month_name(month: str) -> str:
    year, number = month.split("-")
    return f"{calendar.month_name[int(number)]} {year}"


def _month_short(month: str) -> str:
    year, number = month.split("-")
    return f"{calendar.month_abbr[int(number)]} {year}"


def _short_date(iso: str) -> str:
    day = date.fromisoformat(iso)
    return f"{day.day} {calendar.month_abbr[day.month]} {day.year}"


def _month_context(book, month: str) -> str:
    """Say so when the latest month is not over yet."""
    last = date.fromisoformat(book.last_date)
    if month == book.latest_month and last.day < calendar.monthrange(last.year, last.month)[1]:
        return f"{month_name(month)}, up to {_short_date(book.last_date)}"
    return month_name(month)


def _figure(label: str, cents: int | None) -> dict | None:
    if cents is None:
        return None
    return {"label": label, "amount_cents": cents, "amount": eur(cents)}


def _item(key: str, label: str, cents: int, *, detail: str | None = None, highlight: bool = False) -> dict:
    return {
        "key": key,
        "label": label,
        "amount_cents": cents,
        "amount": eur(cents),
        "detail": detail,
        "highlight": highlight,
        "bar_pct": None,
    }


def _with_bars(items: list[dict]) -> list[dict]:
    scale = max((abs(item["amount_cents"]) for item in items), default=0)
    for item in items:
        item["bar_pct"] = 0 if scale == 0 else js_round(max(item["amount_cents"], 0) * 100 / scale)
    return items


def _median(values: list[int]) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[middle]
    return js_round((ordered[middle - 1] + ordered[middle]) / 2)


def usual_out(book, before: str, window: int = 6) -> tuple[int | None, int]:
    """Median money out of up to ``window`` months before ``before``."""
    months = [row for row in book.months_view()["months"] if row["month"] < before][-window:]
    return _median([row["out_cents"] for row in months]), len(months)


def _against_usual(amount: int, usual: int | None, what: str) -> tuple[str | None, str | None]:
    if usual is None:
        return None, None
    gap = amount - usual
    if gap == 0:
        return "The same as your usual month", f"{what} matches your usual month, {eur(usual)}"
    word = "More" if gap > 0 else "Less"
    return (
        f"{word} than your usual month",
        f"{eur(abs(gap))} {word.lower()} than your usual month, {eur(usual)}",
    )


def _table(caption: str, columns: list[str], rows: list[list[str]]) -> dict:
    return {"caption": caption, "columns": columns, "rows": rows}


def _spending_card(book, question: str = "") -> dict:
    view = book.spending_view(None)
    month = view["month"]
    total = view["total_out_cents"]
    out = [row for row in view["categories"] if row["amount_cents"] < 0]
    out.sort(key=lambda row: (row["amount_cents"], row["name"]))
    items = []
    for row in out:
        cents = -row["amount_cents"]
        share = 0 if total == 0 else js_round(cents * 100 / total)
        word = "payment" if row["count"] == 1 else "payments"
        items.append(_item(row["name"], row["name"], cents, detail=f"{share}% · {row['count']} {word}"))
    usual, months_used = usual_out(book, month)
    verdict, verdict_detail = _against_usual(total, usual, "Money out")
    headline = _figure(f"Money out, {month_name(month)}", total)
    named = named_category(question, [item["key"] for item in items])
    if named is not None:
        chosen = next(item for item in items if item["key"] == named)
        chosen["highlight"] = True
        headline = _figure(f"{named}, {month_name(month)}", chosen["amount_cents"])
    return {
        "kind": "spending_breakdown",
        "title": "Where did your money go?",
        "context": _month_context(book, month),
        "headline": headline,
        "reference": _figure(f"Your usual month, median of {months_used}", usual),
        "verdict": verdict,
        "verdict_detail": verdict_detail,
        "items": _with_bars(items),
        "baskets": view["baskets"],
        "options": None,
        "how": (
            f"From your S-Bank transactions in {month_name(month)}, grouped by category. "
            "Money out counts every payment that left the account, including investment deposits. "
            f"Your usual month is the median of the {months_used} months before. "
            "The AI only wrote the sentence."
        ),
        "table": _table(
            f"{month_name(month)} by category",
            ["Category", "Spent", "Share"],
            [[item["label"], item["amount"], item["detail"].split(" · ")[0]] for item in items],
        ),
        "screen": "spending",
    }


def _month_trend_card(book, window: int = 6) -> dict:
    months = book.months_view()["months"][-window:]
    latest = months[-1]
    usual, months_used = usual_out(book, latest["month"])
    items = [
        _item(
            row["month"],
            _month_short(row["month"]),
            row["out_cents"],
            detail=f"In {eur(row['in_cents'])}",
            highlight=row is latest,
        )
        for row in months
    ]
    verdict, verdict_detail = _against_usual(latest["out_cents"], usual, f"{month_name(latest['month'])}")
    first, last = months[0]["month"], latest["month"]
    return {
        "kind": "month_trend",
        "title": "Your last months",
        "context": f"{_month_short(first)} – {_month_short(last)}",
        "headline": _figure(f"Money out, {month_name(last)}", latest["out_cents"]),
        "reference": _figure(f"Your usual month, median of {months_used}", usual),
        "verdict": verdict,
        "verdict_detail": verdict_detail,
        "items": _with_bars(items),
        "options": None,
        "how": (
            "From your S-Bank transactions, added up by calendar month. Money in is every payment into the "
            "account, money out every payment that left it. "
            f"Your usual month is the median of the {months_used} months before {month_name(last)}. "
            "The AI only wrote the sentence."
        ),
        "table": _table(
            "Money in and out by month",
            ["Month", "In", "Out"],
            [[month_name(row["month"]), eur(row["in_cents"]), eur(row["out_cents"])] for row in months],
        ),
        "screen": "spending",
    }


def _balance_card(book) -> dict:
    today = book.today_view()
    month = today["month"]
    items = [_item("balance", f"Balance, {_short_date(book.last_date)}", today["balance_cents"], highlight=True)]
    if today["latest_salary_cents"] is not None:
        items.append(
            _item(
                "salary",
                f"Latest salary, {_short_date(today['latest_salary_date'])}",
                today["latest_salary_cents"],
            )
        )
    items.append(_item("month_in", f"Money in, {month_name(month)}", today["month_in_cents"]))
    items.append(_item("month_out", f"Money out, {month_name(month)}", today["month_out_cents"]))
    return {
        "kind": "balance",
        "title": "Your balance",
        "context": f"S-Tili · {_short_date(book.last_date)}",
        "headline": _figure("Balance", today["balance_cents"]),
        "reference": _figure("Latest salary", today["latest_salary_cents"]),
        "verdict": None,
        "verdict_detail": None,
        "items": _with_bars(items),
        "options": None,
        "how": (
            "The balance is your S-Tili account after your latest transaction. "
            "The salary is the latest payment marked as salary. The AI only wrote the sentence."
        ),
        "table": _table("Your account", ["", "Amount"], [[item["label"], item["amount"]] for item in items]),
        "screen": "today",
    }


def _savings_card(book, years: int = 5) -> dict:
    view = book.save_view(years)
    by_year: dict[str, int] = {}
    for row in book.deposits:
        if row["tx_date"] >= view["window_start"]:
            year = row["tx_date"][:4]
            by_year[year] = by_year.get(year, 0) - row["amount_cents"]
    items = [_item(year, year, cents) for year, cents in sorted(by_year.items())]
    if items:
        items[-1]["highlight"] = True
    count = view["deposits"]
    if count:
        verdict = f"{count} investment deposit{'s' if count != 1 else ''} in {years} years"
        verdict_detail = f"Latest {eur(view['latest_deposit_cents'])} on {_short_date(view['latest_deposit_date'])}"
    else:
        verdict = f"No investment deposits in {years} years"
        verdict_detail = None
    return {
        "kind": "savings",
        "title": "What you have put aside",
        "context": f"{_short_date(view['window_start'])} – {_short_date(view['window_end'])}",
        "headline": _figure(f"Investment deposits, {years} years", view["deposited_cents"]),
        "reference": _figure("Latest deposit", view["latest_deposit_cents"]),
        "verdict": verdict,
        "verdict_detail": verdict_detail,
        "items": _with_bars(items),
        "options": None,
        "how": (
            "From your S-Bank transactions marked as investment deposits, added up by year. "
            "It is what you paid in, not what it is worth now. Nothing here moves money. "
            "The AI only wrote the sentence."
        ),
        "table": _table(
            "Investment deposits by year",
            ["Year", "Deposited"],
            [[item["label"], item["amount"]] for item in items],
        ),
        "screen": "save",
    }


# Savings goal ----------------------------------------------------------------

_GOAL_VERB = re.compile(
    r"\bsave\b|\bsave up\b|\bsaving\b|put aside|set aside|put away|collect|"
    r"säästä|säästää|säästäisin|spara|sparar",
    re.IGNORECASE,
)
# "€500", "500 €", "500e", "500 eur", "1 500 euros", "1,500€", "500 euroa".
_GOAL_AMOUNT = re.compile(
    r"€\s*(\d[\d\s.,]*\d|\d)|(\d[\d\s.,]*\d|\d)\s*(?:€|e\b|eur\b|euros?\b|euroa\b|euro\b)",
    re.IGNORECASE,
)
_NUMBER_WORDS = {
    "a": 1, "an": 1, "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6,
    "seven": 7, "eight": 8, "nine": 9, "ten": 10, "eleven": 11, "twelve": 12,
    "yks": 1, "yhd": 1, "kah": 2, "kak": 2, "kol": 3, "nel": 4, "vii": 5, "kuu": 6,
    "en": 1, "ett": 1, "två": 2, "tre": 3, "fyra": 4, "fem": 5, "sex": 6,
}
_GOAL_SPAN = re.compile(
    r"(\d{1,3}|[a-zåäö]+)\s+(months?|kuukau\w*|kk\b|månad\w*|years?|vuo\w*|år\b|åren\b)",
    re.IGNORECASE,
)
_HALF_YEAR = re.compile(r"half a year|puoli vuotta|puolessa vuodessa|ett halvår|halvår", re.IGNORECASE)


def _goal_cents(text: str) -> int | None:
    found = _GOAL_AMOUNT.search(text)
    if found is None:
        return None
    raw = re.sub(r"\s", "", found.group(1) or found.group(2))
    # A comma or dot followed by exactly three digits groups thousands; otherwise it is the decimal mark.
    raw = re.sub(r"[.,](?=\d{3}(?!\d))", "", raw).replace(",", ".")
    try:
        cents = js_round(float(raw) * 100)
    except ValueError:
        return None
    return cents if 0 < cents <= 100_000_000 else None


def _goal_months(text: str) -> int | None:
    if _HALF_YEAR.search(text):
        return 6
    for found in _GOAL_SPAN.finditer(text):
        word, unit = found.group(1).lower(), found.group(2).lower()
        if word.isdigit():
            count = int(word)
        else:
            count = next((value for key, value in _NUMBER_WORDS.items() if word.startswith(key) and (len(key) >= 3 or word == key)), None)
            if count is None:
                continue
        months = count * 12 if unit.startswith(("year", "vuo", "år")) else count
        if 1 <= months <= 120:
            return months
    if re.search(r"next month|this month|ensi kuussa|nästa månad", text, re.IGNORECASE):
        return 1
    return None


def savings_goal(question: str) -> tuple[int, int] | None:
    """``(goal_cents, months)`` when the question sets a savings target with a time, else None."""
    if not _GOAL_VERB.search(question):
        return None
    cents = _goal_cents(question)
    months = _goal_months(question)
    if cents is None or months is None:
        return None
    return cents, months


def _usual_left(book) -> tuple[int | None, int]:
    """Median of money in minus money out over up to six whole months before the latest one."""
    months = book.months_view()["months"]
    whole = [row for row in months if row["month"] < book.latest_month][-6:]
    return _median([row["in_cents"] - row["out_cents"] for row in whole]), len(whole)


def _savings_goal_card(book, goal_cents: int, months: int) -> dict:
    # Round up, so the months add up to at least the goal; the last month takes what is left.
    per_month = -(-goal_cents // months)
    last_month = goal_cents - per_month * (months - 1)
    usual, months_used = _usual_left(book)
    span = "1 month" if months == 1 else f"{months} months"

    items = [
        _item("goal", "Your goal", goal_cents),
        _item("per_month", "To put aside each month", per_month, highlight=True),
    ]
    verdict = verdict_detail = None
    if usual is not None:
        items.append(_item("usual_left", f"Usually left at month end, median of {months_used}", usual))
        if usual >= per_month:
            verdict = "Fits what you usually have left"
            verdict_detail = f"{eur(usual - per_month)} would still be left in a usual month"
        elif usual > 0:
            verdict = "More than you usually have left"
            verdict_detail = f"{eur(per_month - usual)} a month more than your usual month leaves"
        else:
            verdict = "Your usual month leaves nothing over"
            verdict_detail = f"The goal needs {eur(per_month)} a month from somewhere in your spending"
        if usual < per_month:
            spending = _spending_card(book)
            latest = book.spending_view(None)["month"]
            flexible = [
                item for item in spending["items"] if item["key"] not in _ESSENTIAL and item["key"] not in _SKIP
            ]
            if flexible:
                top = flexible[0]
                items.append(
                    _item(
                        top["key"],
                        f"{top['label']}, {month_name(latest)}",
                        top["amount_cents"],
                        detail="Your largest flexible category",
                    )
                )

    rows = []
    total = 0
    for number in range(1, months + 1):
        amount = last_month if number == months else per_month
        total += amount
        rows.append([f"Month {number}", eur(amount), eur(total)])

    return {
        "kind": "savings_goal",
        "title": f"Saving {eur(goal_cents)} in {span}",
        "context": f"Goal {eur(goal_cents)} · {span}",
        "headline": _figure("To put aside each month", per_month),
        "reference": _figure(f"Usually left at month end, median of {months_used}", usual),
        "verdict": verdict,
        "verdict_detail": verdict_detail,
        "items": _with_bars(items),
        "options": None,
        "how": (
            f"The goal split over {span}, rounded up to the cent so the months reach it. "
            f"What is usually left is money in minus money out, the median of the {months_used} "
            "whole months before this one, from your S-Bank transactions. Money out includes investment deposits. "
            "Nothing here moves money. The AI only wrote the sentence."
        ),
        "table": _table("Your plan", ["Month", "Put aside", "Saved so far"], rows),
        "screen": "save",
    }


# Extra blocks for the five fixed questions. They do not change the shared card keys.
_ESSENTIAL = {
    "Housing",
    "Utilities",
    "Health",
    "Transportation",
    "Rent",
    "Bills",
    "Transport pass",
    "Repair",
    "Pharmacy",
}
_SKIP = {"Salary", "Invest Deposit"}
_BIG_CENTS = 10_000
_SPENDER_Q = re.compile(r"spender|rahankäyttäjä|spenderare", re.IGNORECASE)
_SAVE_Q = re.compile(
    r"save more|säästää enemmän|spara mer|eat out|eating out|ate out|syö ulkona|ät ute|"
    r"what if i (?:eat|ate|skip|cut|spend less)|skip(?:ped)? (?:\d+ )?(?:restaurant|visit|meal)|cut back|"
    r"mitä jos (?:söisin|syön|jättäisin)|tänk om jag (?:åt|äter|hoppade)",
    re.IGNORECASE,
)
_PAYMENTS = re.compile(r"(\d+)\s+payments?")


def enrich_card(book, question: str, card: dict) -> dict:
    """Add the spender reading or the visit cut when the question asks for that screen."""
    extra: dict = {}
    if _SPENDER_Q.search(question):
        extra["profile"] = spender_profile(book)
    if _SAVE_Q.search(question) and card.get("kind") == "spending_breakdown":
        cut = visit_cut(card.get("items") or [])
        if cut is not None:
            extra["cut"] = cut
    if not extra:
        return card
    return {**card, **extra}


def spender_profile(book) -> dict:
    """Purchases outside essentials over the last three months this customer has.

    A big buy is one of those payments of at least €100. Big-buys means those
    payments are at least four and at most half of the flexible payments.
    """
    months = list(book.months)[-3:]
    big = 0
    flex = 0
    essentials_months = 0
    for month in months:
        essential_paid = False
        for row in book.months[month]:
            cents = row["amount_cents"]
            name = row["category"]
            if cents >= 0 or name in _SKIP:
                continue
            if name in _ESSENTIAL:
                essential_paid = True
                continue
            flex += 1
            if -cents >= _BIG_CENTS:
                big += 1
        if essential_paid:
            essentials_months += 1
    if big >= 4 and flex > 0 and big * 2 <= flex:
        key = "big_buys"
    elif flex >= 8:
        key = "steady"
    else:
        key = "everyday"
    first, last = months[0], months[-1]
    window = _month_short(first) if first == last else f"{_month_short(first)} – {_month_short(last)}"
    return {
        "key": key,
        "window": window,
        "big_buys": big,
        "threshold_cents": _BIG_CENTS,
        "flex_payments": flex,
        "months_with_essentials": essentials_months,
        "months": len(months),
    }


def visit_cut(items: list[dict]) -> dict | None:
    """What skipping visits in the largest flexible category would set aside.

    The per-visit amount is the category total divided by its payment count.
    Each option is that amount times the visits skipped, for the month and for a year.
    """
    flexible = [
        item
        for item in items
        if item.get("key") not in _ESSENTIAL and item.get("key") not in _SKIP and item.get("amount_cents", 0) > 0
    ]
    if not flexible:
        return None
    chosen = next((item for item in flexible if item["key"] == "Restaurants"), flexible[0])
    detail = chosen.get("detail") or ""
    found = _PAYMENTS.search(detail)
    count = int(found.group(1)) if found else 0
    if count < 1:
        return None
    visit = js_round(chosen["amount_cents"] / count)
    span = min(count, 12)
    return {
        "category": chosen["key"],
        "visits": count,
        "visit_cents": visit,
        "selected": min(2, span),
        "options": [
            {"skip": n, "month_cents": visit * n, "year_cents": visit * n * 12}
            for n in range(span + 1)
        ],
    }


# "What if I invest that money?": the monthly saving from the visit cut, grown at an example return.
_INVEST_Q = re.compile(
    r"\b(?:what if|if) i invest|\binvest (?:that|this) (?:money|surplus|saving|savings|amount)\b",
    re.IGNORECASE,
)
_PER_MONTH = re.compile(r"a month|per month|monthly|every month|each month", re.IGNORECASE)
INVEST_MAX_MONTHLY_CENTS = 1_000_000  # 10 000 euros: keeps every total inside the app's Int cents
_INVEST_AMOUNT = re.compile(r"(\d[\d\s]*(?:[.,]\d{1,2})?)\s*(?:€|e\b|eur\b|euros?\b)", re.IGNORECASE)
INVEST_RATE_PCT = 5
INVEST_YEARS = (1, 5, 10, 20)
INVEST_KID = (
    "Think of a snowball. You roll a small ball of snow down a hill. As it rolls, it picks up more snow, "
    "so it gets bigger. And a bigger ball picks up even more snow each time it turns. "
    "Money can work the same way: the money you put in earns a little extra, and next year that extra "
    "earns extra too. That is the snowball effect. The longer it rolls, the bigger it gets."
)
INVEST_NOTE = (
    "This is an example, not a promise or advice. It uses 5 % a year. "
    "Real investments go up and down, and you can get back less than you put in."
)


_VISIT_WORD = {"Restaurants": "restaurant"}


def invest_question(question: str) -> bool:
    """A what-if about investing money (not a question about past savings)."""
    return bool(_INVEST_Q.search(question))


def invest_growth(monthly_cents: int, rate_pct: float = INVEST_RATE_PCT, years=INVEST_YEARS) -> list[dict]:
    """Monthly deposits at the end of each month, compounded monthly at rate_pct a year."""
    rate = rate_pct / 100 / 12
    rows = []
    for year in years:
        months = year * 12
        total = js_round(monthly_cents * ((1 + rate) ** months - 1) / rate) if rate else monthly_cents * months
        put_in = monthly_cents * months
        rows.append({"years": year, "put_in_cents": put_in, "growth_cents": total - put_in, "total_cents": total})
    return rows


def _invest_monthly(book, question: str) -> tuple[int, str] | None:
    """The amount to invest: an amount named in the question, else the visit cut's monthly saving."""
    found = _INVEST_AMOUNT.search(question)
    if found and _PER_MONTH.search(question):
        number = found.group(1).replace(" ", "").replace(",", ".")
        cents = js_round(float(number) * 100)
        if 0 < cents <= INVEST_MAX_MONTHLY_CENTS:
            return cents, "question"
    cut = visit_cut(_spending_card(book, "restaurants").get("items") or [])
    if cut is None:
        return None
    option = next((o for o in cut["options"] if o["skip"] == cut["selected"]), None)
    if option is None or option["month_cents"] <= 0:
        return None
    return option["month_cents"], cut["category"]


def invest_card(book, question: str) -> dict | None:
    picked = _invest_monthly(book, question)
    if picked is None:
        return None
    monthly, source = picked
    rows = invest_growth(monthly)
    last = rows[-1]
    where = "a month" if source == "question" else f"a month: what skipping 2 {_VISIT_WORD.get(source, source.lower())} visits saves"
    return {
        "kind": "invest_growth",
        "title": "What if I invest that money?",
        "context": f"Example: {INVEST_RATE_PCT} % a year",
        "headline": {"label": f"In {last['years']} years", "amount_cents": last["total_cents"]},
        "items": [],
        "how": (
            f"{eur(monthly)} put in at the end of every month, growing {INVEST_RATE_PCT} % a year, "
            "added up month by month. Calculated by SpendSight's tools, not guessed by AI."
        ),
        "invest": {
            "monthly_cents": monthly,
            "monthly_from": where,
            "rate_pct": INVEST_RATE_PCT,
            "years": rows,
            "kid": INVEST_KID,
            "note": INVEST_NOTE,
        },
    }


_SNOWBALL_Q = re.compile(
    r"snow\s?ball|compound(?:ing)? (?:interest|effect|growth)|explain .{0,40}(?:like|to) (?:a |an )?"
    r"(?:5|five)[- ]?(?:year|yr)|\beli5\b",
    re.IGNORECASE,
)
_EXAMPLE_FOLLOWUP = re.compile(
    r"^\W*(?:(?:can|could) you |please )?(?:(?:give|show)(?: me)? (?:a |an |another )?(?:concrete |real |simple )?example"
    r"|explain(?: it| that)?(?: again| simpler| more)?|what do you mean|how does that work|i don't get it|why)\W*$",
    re.IGNORECASE,
)


def snowball_question(question: str) -> bool:
    return bool(_SNOWBALL_Q.search(question))


def example_followup(question: str) -> bool:
    """A short follow-up like "Give a concrete example" (only meaningful right after an invest answer)."""
    return bool(_EXAMPLE_FOLLOWUP.search(question))


def invest_lead(card: dict, mode: str = "whatif") -> tuple[str, str]:
    """The conversational sentences above the card. mode: whatif, explain or example."""
    data = card["invest"]
    rows = {row["years"]: row for row in data["years"]}
    last = data["years"][-1]
    monthly = eur(data["monthly_cents"])
    rate = data["rate_pct"]
    if mode == "explain":
        lead = (
            "Picture a small snowball rolling down a hill. It picks up snow as it goes, so the bigger it gets, "
            "the faster it grows. Money can do the same: what you put in earns a little, and then that extra "
            "earns too."
        )
        detail = (
            f"With your {monthly} a month, that could be {eur(last['total_cents'])} in {last['years']} years "
            f"(an example at {rate} % a year), and {eur(last['growth_cents'])} of it is growth you didn't have to save."
        )
        return lead, detail
    if mode == "example":
        ten = rows.get(10, last)
        lead = (
            f"Here's a real example. Say you put away {monthly} every month. After 1 year you'd have "
            f"{eur(rows[1]['total_cents'])}, after 10 years {eur(ten['total_cents'])}, and after "
            f"{last['years']} years {eur(last['total_cents'])}."
        )
        detail = (
            f"Of that last amount, you paid in {eur(last['put_in_cents'])} and the other {eur(last['growth_cents'])} "
            f"is growth. It's an example at {rate} % a year, not a promise."
        )
        return lead, detail
    lead = (
        f"If you put that {monthly} aside every month, in {last['years']} years you could have "
        f"{eur(last['total_cents'])}, at an example {rate} % a year."
    )
    detail = (
        f"You would put in {eur(last['put_in_cents'])}, and the other {eur(last['growth_cents'])} is growth. "
        "That is the snowball effect."
    )
    return lead, detail
