"""Classify a question, then refuse or run one named tool.

The five refusals run first, in this order: foul play, paying a loan off,
other customers, manipulation, not a payment. A later pattern must not
answer a question that should have stopped.

The tool is chosen here. No model sees a tool list, and no model changes
a figure. These tools read Aino's ledger only. They do not open the
Junction files.
"""

from __future__ import annotations

import re

from app.ledger import (
    ASP_GOAL_CENTS,
    ASP_MONTHS_DONE,
    ASP_MONTHS_NEED,
    ASP_SAVED_CENTS,
    SLIP_CATEGORIES,
    Ledger,
)
from app.money import eur

DISCLAIMER = (
    "Answers can be wrong. Check a decision before you act. This is not a person at the bank."
)
OPENING = (
    "I answer from your payments only. The figures are counted, and I can still be wrong. "
    "Check anything that would move money. I am not a person at the bank, and I do not move money."
)
LIMIT = "Outside what I answer"

_REFUSALS: tuple[tuple[str, re.Pattern[str], str, str | None], ...] = (
    (
        "foul_play",
        re.compile(
            r"hide money|launder|fake (income|salary)|lie (on|to) the|evade tax|tax evasion|"
            r"don't tell the bank|dont tell the bank|cheat the bonus|false application|"
            r"bonus (that|which) (was|i) (not|never) earned|unearned bonus"
        ),
        "I can’t help with hiding money, a false application, or a Bonus that was not earned. "
        "If a charge is not yours, that belongs in Watch.",
        "watch",
    ),
    (
        "pay_off_loan",
        re.compile(r"pay off|payoff|pay the loan|early repayment|get out of (the |my )?loan"),
        "I don’t answer how to pay a loan off, or how to shape an application. "
        "A later screen can sketch a monthly payment. It is not an offer.",
        None,
    ),
    (
        "other_customers",
        re.compile(
            r"peer|people like me|same class|other people|customers like|average person|"
            r"neighbour|neighbor|what do people"
        ),
        "Comparing you with other households is later. It would use anonymised open figures, "
        "such as Statistics Finland. I will not invent an average.",
        None,
    ),
    (
        "manipulation",
        re.compile(
            r"ignore (your|the) (rules|instructions)|jailbreak|developer mode|"
            r"pretend you are|you are now a"
        ),
        "I only answer questions about your own spending. I can’t switch roles or ignore that limit.",
        None,
    ),
    (
        "not_a_payment",
        re.compile(
            r"boyfriend|girlfriend|pregnant|diagnos|depress|politic|religion|password|"
            r"pin code|henkilötunnus|another person|my (mom|dad|wife|husband|friend)'s"
        ),
        "I only answer questions about your payments. I don’t answer about health, "
        "relationships, beliefs, secrets, or another person’s money.",
        None,
    ),
)


def _card(
    *,
    refused: bool,
    intent: str,
    tool: str | None,
    source: str | None = None,
    fact: dict | None = None,
    next_text: str | None = None,
    screen: str | None = None,
    limit: str | None = None,
) -> dict:
    return {
        "refused": refused,
        "disclaimer": DISCLAIMER,
        "source": source,
        "fact": fact,
        "next": next_text,
        "screen": screen,
        "limit": limit,
        "intent": intent,
        "tool": tool,
    }


def _fact(kicker: str, figure: str, because: str) -> dict:
    return {"kicker": kicker, "figure": figure, "because": because}


def _refuse(intent: str, text: str, screen: str | None) -> dict:
    return _card(
        refused=True,
        intent=intent,
        tool=None,
        next_text=text,
        screen=screen,
        limit=LIMIT,
    )


def _counted(period: str, category: str, count: int, noun: str | None = None) -> str:
    word = noun or ("payment" if count == 1 else "payments")
    return f"{period} · {category} · {count} {word}"


def _spender(ledger: Ledger, _q: str) -> dict:
    regular = ledger.sum_month("oct", lambda row: row.basket == "regular")
    slip = ledger.sum_month("oct", lambda row: row.category in SLIP_CATEGORIES)
    groceries = ledger.sum_month("oct", lambda row: row.category == "Groceries")
    irregular = ledger.sum_month("oct", lambda row: row.basket == "irregular")
    coffee_visits = sum(row.visit_count() for row in ledger.month("oct") if row.category == "Coffee & drinks")
    wolt_orders = next(row for row in ledger.month("oct") if row.key == "wolt").visit_count()
    lines = len(ledger.month("oct"))
    return _card(
        refused=False,
        intent="allowed",
        tool="spenderPortrait",
        fact=_fact(
            "October, the last full month",
            "A regular payer.",
            f"{eur(regular)} left on a schedule. {eur(slip)} slipped through in small payments. "
            f"Robert’s was {coffee_visits} visits. Wolt was {wolt_orders} orders.",
        ),
        next_text=(
            f"Groceries were {eur(groceries)}, mostly Prisma and S-market. "
            f"The repair and the dentist were {eur(irregular)}, and the day was not yours to pick. "
            "October’s small payments are rolled up by place, so that month has no weekday."
        ),
        source=_counted("October", "Three baskets", lines, "lines"),
        screen="spender",
    )


def _safe_week(ledger: Ledger, _q: str) -> dict:
    week = ledger.safe_week()
    helen = next(bill for bill in week["bills"] if bill["name"] == "Helen")
    fressi = next(bill for bill in week["bills"] if bill["name"] == "Fressi")
    slip = ledger.slip_cents()
    return _card(
        refused=False,
        intent="allowed",
        tool="safeWeek",
        fact=_fact(
            "Balance − bills before payday − buffer",
            eur(week["spend_cents"]),
            f"{eur(ledger.balance_cents)} on S-Tili, minus Helen {eur(helen['amount_cents'])} "
            f"and Fressi {eur(fressi['amount_cents'])}, minus a {eur(week['buffer_cents'])} buffer.",
        ),
        next_text=(
            "Elisa, the season ticket, insurance, Netflix, and December rent are after Thursday, "
            "so they are not in this number. "
            f"Coffee, taxis, and meals have a separate line of {eur(slip)}. "
            "Passing that line sends one note. The card is not blocked."
        ),
        source="13 Nov · Helen and Fressi · 2 bills, plus the buffer",
        screen="week",
    )


def _dining(ledger: Ledger, _q: str) -> dict:
    plan = ledger.dining()
    return _card(
        refused=False,
        intent="allowed",
        tool="diningCut",
        fact=_fact(
            "Dining out, minus 20%",
            eur(plan["fifth_cents"]),
            f"A month, from October’s {eur(plan['dining_cents'])}. "
            f"Across a year that is {eur(plan['fifth_cents'] * 12)}. "
            f"It can come from Wolt, {eur(plan['wolt_cents'])}. Rosso stays.",
        ),
        next_text=(
            f"Once a week instead of twice would save {eur(plan['once_cents'])} a month, "
            f"{eur(plan['once_cents'] * 12)} a year. Two meals a week less would save "
            f"{eur(plan['twice_cents'])} a month, and about one of the {plan['orders']} October meals "
            f"would remain. Rosso, {eur(plan['rosso_cents'])}, earns Bonus."
        ),
        source=_counted("October", "Dining out", plan["orders"]),
        screen="cut",
    )


def _once_a_week(ledger: Ledger, _q: str) -> dict:
    plan = ledger.dining()
    return _card(
        refused=False,
        intent="allowed",
        tool="diningCut",
        fact=_fact(
            "Once a week, not twice",
            eur(plan["once_cents"]),
            f"October had {plan['orders']} dining payments, about two a week. "
            f"The average was {eur(plan['meal_cents'])}. One fewer each week, for four weeks, "
            f"is {eur(plan['once_cents'])} a month, {eur(plan['once_cents'] * 12)} a year.",
        ),
        next_text=(
            f"Two meals a week less would be {eur(plan['twice_cents'])} a month. "
            f"October only had {plan['orders']} payments, so about one meal would remain. "
            f"A 20% cut is smaller, {eur(plan['fifth_cents'])} a month, "
            f"{eur(plan['fifth_cents'] * 12)} a year, and it can come from Wolt. "
            f"Rosso, {eur(plan['rosso_cents'])}, earns Bonus."
        ),
        source=_counted("October", "Dining out", plan["orders"]),
        screen="cut",
    )


def _impulse(ledger: Ledger, _q: str) -> dict:
    found = ledger.impulse()
    peak = found["peak"]
    places = ", ".join(peak["places"])
    return _card(
        refused=False,
        intent="allowed",
        tool="impulseWhen",
        fact=_fact(
            f"{peak['when']} · busiest dated day",
            f"{peak['count']} payments",
            f"{places}, {eur(peak['amount_cents'])}. October’s coffee is {found['coffee_visits']} "
            "visits rolled up by place, so that month has no weekday.",
        ),
        next_text=(
            "Groceries and rent are not counted as impulse. "
            "No payment here has a time of day, so I will not guess evening or payday."
        ),
        source=_counted("1–13 Nov", "Dining, coffee, and taxis", found["count"]),
        screen="impulse",
    )


def _runway(ledger: Ledger, _q: str) -> dict:
    pace = ledger.runway()
    return _card(
        refused=False,
        intent="allowed",
        tool="runwayDays",
        fact=_fact(
            "Rent left out of the pace",
            f"{pace['left_days']} days",
            f"{eur(ledger.balance_cents)} divided by {eur(pace['per_day_cents'])} a day. "
            f"{eur(pace['variable_cents'])} left in {pace['days']} days, not counting rent that already left.",
        ),
        next_text=(
            "Bills after Thursday are not subtracted here. Safe to spend until payday is the number that does that."
        ),
        source=_counted("1–13 Nov", "All payments except rent", pace["count"]),
        screen="runway",
    )


def _subs_cut(ledger: Ledger, _q: str) -> dict:
    cut = ledger.subscription_cut()
    names = ", ".join(f"{item['name']} {eur(item['amount_cents'])}" for item in cut["items"])
    return _card(
        refused=False,
        intent="allowed",
        tool="subscriptionCut",
        fact=_fact(
            "If all three stop",
            eur(cut["month_cents"]),
            f"{eur(cut['year_cents'])} across a year. Fressi has a charge and no visit. "
            "Netflix and Spotify have no use count, so this is not a measured ranking of use.",
        ),
        next_text=(
            f"{names}. Spotify already left on 5 Nov, so a cancel starts next time. "
            "Nothing here cancels a charge."
        ),
        source=_counted("Oct–Nov", "Fressi, Netflix, Spotify", 2, "charges") + " and 1 hold",
        screen="subs",
    )


def _chart(ledger: Ledger, q: str) -> dict:
    charts = ledger.charts()
    if re.search(r"coffee|drink", q):
        rows = [row for row in charts if row["label"] == "Coffee & drinks"]
    elif re.search(r"din|wolt|restaurant", q):
        rows = [row for row in charts if row["label"] == "Dining out"]
    elif re.search(r"taxi|bolt", q):
        rows = [row for row in charts if row["label"] == "Taxis"]
    elif re.search(r"grocer|food", q):
        rows = [row for row in charts if row["label"] == "Groceries"]
    else:
        rows = charts
    if len(rows) == 1:
        row = rows[0]
        fact = _fact(
            f"{row['label']} · 13 days",
            eur(row["november_cents"]),
            f"1–13 November. October’s pace for the same 13 days is {eur(row['october_pace_cents'])}.",
        )
        category = row["label"]
        count = 1
        noun = "category"
    else:
        fact = _fact(
            "1–13 November against October",
            f"{len(rows)} categories",
            "Grey is October’s pace for 13 days. Green is what has left so far in November.",
        )
        category = "Groceries, coffee, dining, taxis"
        count = len(rows)
        noun = "categories"
    return _card(
        refused=False,
        intent="allowed",
        tool="monthCompare",
        fact=fact,
        next_text=(
            "The same 13 days last year are not loaded, so they are not drawn. Other households are not drawn."
        ),
        source=_counted("1–13 Nov", category, count, noun),
        screen="charts",
    )


def _house(ledger: Ledger, _q: str) -> dict:
    spec = ledger.effort_spec()
    return _card(
        refused=False,
        intent="allowed",
        tool="monthSave",
        fact=_fact(
            "This month, not a loan decision",
            eur(spec["save"]),
            f"That is the save that matches your {spec['name'].lower()} setting. The multi-year home screen is later.",
        ),
        next_text=(
            f"The later plan has {eur(ASP_SAVED_CENTS)} of {eur(ASP_GOAL_CENTS)}, "
            f"and {ASP_MONTHS_DONE} of {ASP_MONTHS_NEED} months. "
            "I will not say whether a bank should lend, and I will not answer how to pay a loan off."
        ),
        source=f"Your save setting · {spec['name']} · not a count of payments",
        screen="home",
    )


def _save(ledger: Ledger, _q: str) -> dict:
    spec = ledger.effort_spec()
    week = ledger.safe_week()
    return _card(
        refused=False,
        intent="allowed",
        tool="monthSave",
        fact=_fact(
            f"{spec['name']} save",
            eur(spec["save"]),
            f"{spec['salary_percent']}% of Thursday’s salary. "
            f"Safe to spend until payday is {eur(week['spend_cents'])}. €1,500 would still be one ASP month.",
        ),
        next_text=(
            f"The cushion is {eur(week['buffer_cents'])}, and it is already the buffer in the safe-to-spend number. "
            "Keep anything above this save where you can take it."
        ),
        source=f"Your save setting · {spec['name']} · not a count of payments",
        screen="save",
    )


def _jacket(ledger: Ledger, _q: str) -> dict:
    story = ledger.jacket()
    slip = ledger.slip_cents()
    return _card(
        refused=False,
        intent="allowed",
        tool="thisPurchase",
        fact=_fact(
            "Sokos jacket",
            eur(story["price_cents"]),
            f"This week’s line is {eur(slip)}, so it is {eur(story['over_slip_cents'])} over. "
            f"As new S-group spending, Bonus rises {eur(story['gain_cents'])} and you are "
            f"{eur(story['loss_cents'])} poorer.",
        ),
        next_text=(
            f"Thursday’s salary is {eur(ledger.salary_cents)}. Waiting is the calm choice. "
            "You can still take it from the slip line."
        ),
        source="13 Nov · S-group purchases plus this jacket · 7 payments",
        screen="purchase",
    )


def _loan(ledger: Ledger, _q: str) -> dict:
    from app.ledger import loan_sketch

    sketch = loan_sketch()
    return _card(
        refused=False,
        intent="allowed",
        tool="loanSketch",
        fact=_fact(
            "A price and a rate you set",
            sketch["monthly"],
            f"{eur(sketch['price_eur'] * 100)} with 10% down, 25 years, at {sketch['rate_percent']:.1f}%. "
            f"At 6% the same loan is {sketch['stress_6pct']}. Rent today is {eur(82_000)}.",
        ),
        next_text=(
            "This is arithmetic, not an offer, and it is not how much the bank will lend. "
            "The mortgage files are not this outcome."
        ),
        source="A price and a rate you set · 25 years · not your payments",
        screen="loan",
    )


def _cushion(ledger: Ledger, _q: str) -> dict:
    week = ledger.safe_week()
    regular = ledger.sum_month("oct", lambda row: row.basket == "regular")
    lines = sum(1 for row in ledger.month("oct") if row.basket == "regular")
    return _card(
        refused=False,
        intent="allowed",
        tool="cushion",
        fact=_fact(
            "Cushion, not the ASP account",
            eur(week["buffer_cents"]),
            f"One quiet month of must-pay in October was {eur(regular)}.",
        ),
        next_text="Keep the cushion where you can take it. The ASP account is for the home.",
        source=_counted("October", "Must, every month", lines, "lines"),
        screen="home",
    )


def _taxi(ledger: Ledger, _q: str) -> dict:
    taxis = ledger.sum_month("oct", lambda row: row.category == "Taxis")
    rides = sum(row.visit_count() for row in ledger.month("oct") if row.category == "Taxis")
    hsl = sum(row.amount_cents for row in ledger.month("oct") if row.key == "hsl")
    return _card(
        refused=False,
        intent="allowed",
        tool="category",
        fact=_fact(
            "October taxis",
            eur(taxis),
            f"{rides} Bolt rides. The HSL season ticket was {eur(hsl)}. "
            "That one is the plan. The rides are the part that slips through.",
        ),
        next_text="The season ticket is the plan. The rides are the part that slips through.",
        source=_counted("October", "Taxis", rides, "rides"),
        screen="spending",
    )


def _bonus(ledger: Ledger, _q: str) -> dict:
    story = ledger.bonus_view()
    return _card(
        refused=False,
        intent="allowed",
        tool="bonus",
        fact=_fact(
            f"{story['november_rate_pct']:g}% step",
            eur(story["november_bonus_cents"]),
            f"S-group purchases are {eur(story['november_sgroup_cents'])}. "
            f"The next step is {story['next_rate_pct']:g}% at {eur(story['next_step_cents'])}, "
            f"{eur(story['gap_cents'])} away. Bonus at that step is {eur(story['bonus_at_next_cents'])}.",
        ),
        next_text=(
            f"If that {eur(story['gap_cents'])} is new spending, Bonus rises {eur(story['gain_cents'])} "
            f"and you are {eur(story['loss_cents'])} poorer. "
            "The Visa 0.5% sits beside the step. Nothing here moves the Bonus."
        ),
        source=_counted("1–13 Nov", "S-group places", 6),
        screen="bonus",
    )


def _coffee(ledger: Ledger, _q: str) -> dict:
    total = ledger.sum_month("oct", lambda row: row.category == "Coffee & drinks")
    visits = sum(row.visit_count() for row in ledger.month("oct") if row.category == "Coffee & drinks")
    return _card(
        refused=False,
        intent="allowed",
        tool="category",
        fact=_fact(
            "October coffee and drinks",
            eur(total),
            "Robert’s Coffee, Picnic, and ABC. Picnic and ABC earn Bonus.",
        ),
        next_text=f"Two café visits this week, {eur(1_600)}, are part of the move to the next step.",
        source=_counted("October", "Coffee and drinks", visits, "visits"),
        screen="spending",
    )


def _groceries(ledger: Ledger, _q: str) -> dict:
    total = ledger.sum_month("oct", lambda row: row.category == "Groceries")
    lines = sum(1 for row in ledger.month("oct") if row.category == "Groceries")
    return _card(
        refused=False,
        intent="allowed",
        tool="category",
        fact=_fact(
            "October groceries",
            eur(total),
            "Prisma and S-market earn Bonus. K-Market and Lidl do not.",
        ),
        next_text="Groceries stay outside the slip.",
        source=_counted("October", "Groceries", lines, "lines"),
        screen="spending",
    )


def _bills(ledger: Ledger, _q: str) -> dict:
    regular = ledger.sum_month("oct", lambda row: row.basket == "regular")
    rent = sum(row.amount_cents for row in ledger.month("oct") if row.key == "rent")
    hsl = sum(row.amount_cents for row in ledger.month("oct") if row.key == "hsl")
    bills = regular - rent - hsl
    lines = sum(1 for row in ledger.month("oct") if row.basket == "regular")
    return _card(
        refused=False,
        intent="allowed",
        tool="category",
        fact=_fact(
            "Must, every month",
            eur(regular),
            f"Rent {eur(rent)}, bills {eur(bills)}, and the season ticket {eur(hsl)}.",
        ),
        next_text=(
            "November’s rent has already left. Bills due before Thursday are in the safe-to-spend number. "
            "The later ones are not."
        ),
        source=_counted("October", "Must, every month", lines, "lines"),
        screen="baskets",
    )


def _watch(ledger: Ledger, _q: str) -> dict:
    bolt = ledger.watch[0]
    refund = ledger.watch[1]
    return _card(
        refused=False,
        intent="allowed",
        tool="watchItems",
        fact=_fact(
            "Kept out of the spending total",
            "2 items",
            f"A second Bolt of {eur(bolt['amount_cents'])} on {bolt['when']}. "
            f"A Prisma refund of {eur(refund['amount_cents'])} on {refund['when']}.",
        ),
        next_text="Marking them does not change S-Tili.",
        source="12 Nov and 11 Nov · a second Bolt and a refund · 2 items kept out of the total",
        screen="watch",
    )


def _subscriptions(ledger: Ledger, _q: str) -> dict:
    cut = ledger.subscription_cut()
    by_name = {item["name"]: item["amount_cents"] for item in cut["items"]}
    return _card(
        refused=False,
        intent="allowed",
        tool="subscriptionCut",
        fact=_fact(
            "Fressi, Netflix, Spotify",
            eur(cut["month_cents"]),
            f"Spotify, {eur(by_name['Spotify'])}, already left on 5 Nov. "
            f"Netflix, {eur(by_name['Netflix'])}, has not left yet. "
            f"Fressi was {eur(by_name['Fressi'])} on 2 Oct and is due again. "
            "The ledger has the charge and no visit.",
        ),
        next_text="Marking one cancelled does not remove it from the holds. Nothing here cancels a charge.",
        source=_counted("Oct–Nov", "Fressi, Netflix, Spotify", 2, "charges") + " and 1 hold",
        screen="subs",
    )


def _safe_week_gate(ledger: Ledger, q: str) -> dict | None:
    # "Safe to save" is a different question and is matched earlier.
    if "save" in q:
        return None
    return _safe_week(ledger, q)


# Tool order follows the design, after the refusals have already run.
_TOOLS: tuple[tuple[re.Pattern[str], object], ...] = (
    (re.compile(r"impulse|when do i tend|spur of the moment|unplanned"), _impulse),
    (re.compile(r"run out|days until|money last|current pace"), _runway),
    (re.compile(r"top 3|least-used|least used|cancel my"), _subs_cut),
    (re.compile(r"buy|jacket|afford this|purchase"), _jacket),
    (re.compile(r"home plan|first home|how is the home|asp account|save up for a house|saving for a house"), _house),
    (re.compile(r"safe to save|how much should i save|how much is safe to save|saving too much|save too much|\basp\b"), _save),
    (re.compile(r"subscri|gym|fressi|netflix|forgot|recurring"), _subscriptions),
    (re.compile(r"refund|fraud|odd|isn't mine|isnt mine|billing|second bolt"), _watch),
    (re.compile(r"chart|against october|show me .*(coffee|dining|taxi|grocer|october)|compare .*october"), _chart),
    (re.compile(r"loan|mortgage|afford a home|house price"), _loan),
    (re.compile(r"emergency|cushion"), _cushion),
    (re.compile(r"taxi|bolt|uber"), _taxi),
    (re.compile(r"spender|kind of spender"), _spender),
    (re.compile(r"once a week|twice a week"), _once_a_week),
    (re.compile(r"safe|this week|pocket"), _safe_week_gate),
    (re.compile(r"dining|wolt|restaurant|20%|20 percent|\bcut\b"), _dining),
    (re.compile(r"bonus|tier|green card|säästä|saasta|prisma"), _bonus),
    (re.compile(r"coffee|drink|robert|picnic"), _coffee),
    (re.compile(r"grocer|food|lidl|k-market|\beat\b"), _groceries),
    (re.compile(r"rent|bill|hsl|salary"), _bills),
)


def _fallback() -> dict:
    return _card(
        refused=False,
        intent="unmatched",
        tool=None,
        next_text=(
            "I can answer what kind of spender you are, what is safe this week, a smaller dining habit, "
            "when impulse payments cluster, how many days the current pace lasts, and what cancelling "
            "three subscriptions would keep. I stop for other people’s money, for paying a loan off, "
            "and for anything that is not a payment."
        ),
    )


def answer(ledger: Ledger, raw: str) -> dict:
    q = raw.lower()
    for intent, pattern, text, screen in _REFUSALS:
        if pattern.search(q):
            return _refuse(intent, text, screen)
    for pattern, builder in _TOOLS:
        if pattern.search(q):
            built = builder(ledger, q)
            if built is not None:
                return built
    return _fallback()


def public_answer(payload: dict) -> dict:
    """The phone contract. Intent and tool stay on the audit row, not in this body."""
    return {
        "refused": payload["refused"],
        "disclaimer": payload["disclaimer"],
        "source": payload["source"],
        "fact": payload["fact"],
        "next": payload["next"],
        "screen": payload["screen"],
        "limit": payload["limit"],
    }
