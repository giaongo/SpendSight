"""S-Pankki coupons and campaigns.

Every coupon and campaign here is made-up demo data. S-Pankki is the only
issuer: no partner or merchant offers. Nothing here is a real S-Pankki offer.

A coupon is offered to a customer by a rule over facts the Junction data
already holds for them: age, the categories they paid in during their last
three months, whether they have investment deposits, and whether they have a
mortgage application. Activations are kept in memory for the running process.
"""

from __future__ import annotations

import threading
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import date, datetime, timezone

from app.customer_data import CustomerBook
from app.money import eur

ISSUER = "S-Pankki"
SAMPLE_NOTE = "Sample offers for the demo. They are not real S-Pankki offers."
RECENT_MONTHS = 3


@dataclass(frozen=True)
class Rule:
    """Who a coupon is offered to. Every condition that is set must hold."""

    categories: tuple[str, ...] = ()  # paid in any of these in the last three months
    min_age: int | None = None
    max_age: int | None = None
    has_investments: bool | None = None
    has_mortgage: bool | None = None


@dataclass(frozen=True)
class Coupon:
    coupon_id: str
    campaign_id: str | None
    title: str
    description: str
    kind: str  # bonus_boost | cashback | fee_waiver | rate_boost | service
    benefit: str
    product: str
    valid_from: date
    valid_to: date
    terms: str
    rule: Rule = field(default_factory=Rule)
    value_cents: int | None = None
    percent: float | None = None
    min_spend_cents: int | None = None


@dataclass(frozen=True)
class Campaign:
    campaign_id: str
    title: str
    description: str
    starts_on: date
    ends_on: date
    terms: str


CAMPAIGNS: tuple[Campaign, ...] = (
    Campaign(
        campaign_id="CMP-WINTER-2026",
        title="Winter Bonus Weeks",
        description="Extra Bonus and cashback on everyday card payments through the holiday season.",
        starts_on=date(2026, 11, 1),
        ends_on=date(2026, 12, 31),
        terms="Pay with an S-Pankki card. Activate each coupon in the app before you pay.",
    ),
    Campaign(
        campaign_id="CMP-SAVER-AUTUMN-2026",
        title="Saver's Autumn",
        description="Start or grow your saving with S-Pankki accounts and funds.",
        starts_on=date(2026, 10, 15),
        ends_on=date(2026, 12, 15),
        terms="For S-Pankki customers with an S-Tili. One coupon of each kind per customer.",
    ),
    Campaign(
        campaign_id="CMP-TRAVEL-2026",
        title="Travel Light",
        description="Card benefits for trips abroad over the winter holidays.",
        starts_on=date(2026, 11, 10),
        ends_on=date(2027, 1, 31),
        terms="Applies to S-Pankki Visa Debit and S-Etukortti Visa payments outside Finland.",
    ),
    Campaign(
        campaign_id="CMP-SUMMER-2026",
        title="Summer Cashback",
        description="Cashback on summer card payments.",
        starts_on=date(2026, 6, 1),
        ends_on=date(2026, 8, 31),
        terms="Ended 31 Aug 2026.",
    ),
    Campaign(
        campaign_id="CMP-SPRING-2027",
        title="Spring Saving Start",
        description="A fresh start for monthly fund saving.",
        starts_on=date(2027, 3, 1),
        ends_on=date(2027, 4, 30),
        terms="Starts 1 Mar 2027.",
    ),
)


COUPONS: tuple[Coupon, ...] = (
    Coupon(
        coupon_id="CPN-RESTO-BONUS",
        campaign_id="CMP-WINTER-2026",
        title="2% extra Bonus at restaurants",
        description="Get 2% extra Bonus on restaurant and café payments with your S-Etukortti Visa.",
        kind="bonus_boost",
        benefit="+2% Bonus",
        product="S-Etukortti Visa",
        percent=2.0,
        valid_from=date(2026, 11, 1),
        valid_to=date(2026, 12, 31),
        terms="Extra Bonus up to €20 in total. Paid with the next monthly Bonus.",
        rule=Rule(categories=("Restaurants",)),
    ),
    Coupon(
        coupon_id="CPN-GROCERY-5",
        campaign_id="CMP-WINTER-2026",
        title="€5 back on groceries",
        description="Pay €200 or more for groceries with an S-Pankki card in one calendar month and get €5 back.",
        kind="cashback",
        benefit="€5 back",
        product="S-Pankki Visa Debit",
        value_cents=500,
        min_spend_cents=20_000,
        valid_from=date(2026, 11, 1),
        valid_to=date(2026, 12, 31),
        terms="Once per customer. Paid to your S-Tili within 14 days of the month end.",
        rule=Rule(categories=("Groceries",)),
    ),
    Coupon(
        coupon_id="CPN-TRANSPORT-3",
        campaign_id="CMP-WINTER-2026",
        title="€3 back on travel cards and fuel",
        description="Pay €60 or more for transport with an S-Pankki card in December and get €3 back.",
        kind="cashback",
        benefit="€3 back",
        product="S-Pankki Visa Debit",
        value_cents=300,
        min_spend_cents=6_000,
        valid_from=date(2026, 12, 1),
        valid_to=date(2026, 12, 31),
        terms="Once per customer. Paid to your S-Tili in January 2027.",
        rule=Rule(categories=("Transportation",)),
    ),
    Coupon(
        coupon_id="CPN-FUND-NOFEE",
        campaign_id="CMP-SAVER-AUTUMN-2026",
        title="No fee on your first fund saving",
        description="Start a monthly saving plan in an S-Pankki fund with no subscription fee for 12 months.",
        kind="fee_waiver",
        benefit="0% subscription fee",
        product="S-Pankki funds",
        percent=0.0,
        valid_from=date(2026, 10, 15),
        valid_to=date(2026, 12, 15),
        terms="For customers with no fund saving yet. Funds can lose value. This is not investment advice.",
        rule=Rule(has_investments=False),
    ),
    Coupon(
        coupon_id="CPN-FUND-TOPUP",
        campaign_id="CMP-SAVER-AUTUMN-2026",
        title="€10 on top of a fund deposit",
        description="Make a one-off deposit of €500 or more to an S-Pankki fund and S-Pankki adds €10.",
        kind="cashback",
        benefit="€10 added",
        product="S-Pankki funds",
        value_cents=1_000,
        min_spend_cents=50_000,
        valid_from=date(2026, 10, 15),
        valid_to=date(2026, 12, 15),
        terms="For customers who already save in funds. Funds can lose value. This is not investment advice.",
        rule=Rule(has_investments=True),
    ),
    Coupon(
        coupon_id="CPN-SAVINGS-RATE",
        campaign_id="CMP-SAVER-AUTUMN-2026",
        title="+0.50% on the S-Pankki savings account",
        description="Earn 0.50 percentage points more interest on new money in your savings account for 3 months.",
        kind="rate_boost",
        benefit="+0.50% interest",
        product="S-Pankki savings account",
        percent=0.5,
        valid_from=date(2026, 10, 15),
        valid_to=date(2026, 12, 15),
        terms="On new deposits up to €10,000. The boost runs for 3 months from activation.",
    ),
    Coupon(
        coupon_id="CPN-TRAVEL-FX",
        campaign_id="CMP-TRAVEL-2026",
        title="No currency fee abroad",
        description="Pay abroad in any currency with no exchange fee for 30 days.",
        kind="fee_waiver",
        benefit="0% currency fee",
        product="S-Pankki Visa Debit",
        percent=0.0,
        valid_from=date(2026, 11, 10),
        valid_to=date(2027, 1, 31),
        terms="The 30 days start when you activate. Cash withdrawals are not included.",
        rule=Rule(categories=("Travel",)),
    ),
    Coupon(
        coupon_id="CPN-YOUNG-CARD",
        campaign_id=None,
        title="First year of S-Etukortti Visa free",
        description="No annual card fee for the first year of a new S-Etukortti Visa.",
        kind="fee_waiver",
        benefit="€0 card fee",
        product="S-Etukortti Visa",
        value_cents=0,
        valid_from=date(2026, 9, 1),
        valid_to=date(2027, 6, 30),
        terms="For customers aged 18–29 who apply for a new card. Credit is subject to approval.",
        rule=Rule(min_age=18, max_age=29),
    ),
    Coupon(
        coupon_id="CPN-MONEY-REVIEW",
        campaign_id=None,
        title="Free money review with an adviser",
        description="Book a 45-minute call with an S-Pankki adviser about saving, pensions, and everyday banking.",
        kind="service",
        benefit="Free 45-min call",
        product="S-Pankki advice",
        valid_from=date(2026, 9, 1),
        valid_to=date(2027, 3, 31),
        terms="For customers aged 55 and over. Book by phone or in the app after you activate.",
        rule=Rule(min_age=55),
    ),
    Coupon(
        coupon_id="CPN-HOME-EXTRA",
        campaign_id=None,
        title="No fee on an extra home loan payment",
        description="Make one extra repayment on your S-Pankki home loan with no handling fee.",
        kind="fee_waiver",
        benefit="€0 handling fee",
        product="S-Pankki home loan",
        value_cents=0,
        valid_from=date(2026, 11, 1),
        valid_to=date(2027, 2, 28),
        terms="For customers with a home loan application at S-Pankki. One extra repayment.",
        rule=Rule(has_mortgage=True),
    ),
    Coupon(
        coupon_id="CPN-SUMMER-ICE",
        campaign_id="CMP-SUMMER-2026",
        title="€2 back on summer treats",
        description="Ended. Shown only to check that expired coupons stay out.",
        kind="cashback",
        benefit="€2 back",
        product="S-Pankki Visa Debit",
        value_cents=200,
        valid_from=date(2026, 6, 1),
        valid_to=date(2026, 8, 31),
        terms="Ended 31 Aug 2026.",
    ),
    Coupon(
        coupon_id="CPN-SPRING-FUND",
        campaign_id="CMP-SPRING-2027",
        title="Double first month of fund saving",
        description="Not started yet. S-Pankki matches your first monthly fund saving up to €25.",
        kind="cashback",
        benefit="Up to €25 matched",
        product="S-Pankki funds",
        value_cents=2_500,
        valid_from=date(2027, 3, 1),
        valid_to=date(2027, 4, 30),
        terms="Starts 1 Mar 2027. Funds can lose value. This is not investment advice.",
    ),
)


class CouponNotFound(LookupError):
    pass


class CouponNotOffered(LookupError):
    pass


class CouponNotActive(ValueError):
    pass


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _is_live(starts: date, ends: date, today: date) -> bool:
    return starts <= today <= ends


def _recent_categories(book: CustomerBook) -> set[str]:
    months = sorted(book.months)[-RECENT_MONTHS:]
    return {
        row["category"]
        for month in months
        for row in book.months[month]
        if row["amount_cents"] < 0 and row["category"]
    }


def _reason(rule: Rule, book: CustomerBook, recent: set[str]) -> str | None:
    """Why the coupon is offered, or None when the customer does not qualify."""
    reasons: list[str] = []
    if rule.categories:
        matched = [name for name in rule.categories if name in recent]
        if not matched:
            return None
        reasons.append(f"you paid for {', '.join(matched)} in the last {RECENT_MONTHS} months")
    age = book.age
    if rule.min_age is not None or rule.max_age is not None:
        if age is None:
            return None
        if rule.min_age is not None and age < rule.min_age:
            return None
        if rule.max_age is not None and age > rule.max_age:
            return None
        reasons.append(f"you are {age}")
    if rule.has_investments is not None:
        has = bool(book.deposits)
        if has != rule.has_investments:
            return None
        reasons.append("you already save in funds" if has else "you have no fund saving yet")
    if rule.has_mortgage is not None:
        has = book.mortgage is not None
        if has != rule.has_mortgage:
            return None
        reasons.append("you have a home loan application" if has else "you have no home loan application")
    if not reasons:
        return "Offered to every S-Pankki customer."
    text = " and ".join(reasons)
    return f"Offered because {text}."


class OfferBook:
    """The catalog plus the activations made while the process runs."""

    def __init__(self, today: Callable[[], date] = date.today) -> None:
        self.today = today
        self.coupons = {coupon.coupon_id: coupon for coupon in COUPONS}
        self.campaigns = {campaign.campaign_id: campaign for campaign in CAMPAIGNS}
        self._activations: dict[tuple[str, str], str] = {}
        self._lock = threading.Lock()

    def campaigns_view(self) -> dict:
        today = self.today()
        rows = [
            self._campaign(campaign, today)
            for campaign in CAMPAIGNS
            if _is_live(campaign.starts_on, campaign.ends_on, today)
        ]
        return {
            "as_of": today.isoformat(),
            "issuer": ISSUER,
            "campaigns": rows,
            "count": len(rows),
            "note": SAMPLE_NOTE,
        }

    def coupons_view(self, book: CustomerBook) -> dict:
        today = self.today()
        recent = _recent_categories(book)
        rows = []
        for coupon in COUPONS:
            if not _is_live(coupon.valid_from, coupon.valid_to, today):
                continue
            reason = _reason(coupon.rule, book, recent)
            if reason is None:
                continue
            rows.append(self._coupon(coupon, book.customer_id, today, reason))
        return {
            "customer_id": book.customer_id,
            "as_of": today.isoformat(),
            "issuer": ISSUER,
            "coupons": rows,
            "count": len(rows),
            "activated_count": sum(1 for row in rows if row["activated"]),
            "note": SAMPLE_NOTE,
        }

    def activate(self, book: CustomerBook, coupon_id: str) -> dict:
        coupon = self.coupons.get(coupon_id)
        if coupon is None:
            raise CouponNotFound(coupon_id)
        today = self.today()
        if not _is_live(coupon.valid_from, coupon.valid_to, today):
            raise CouponNotActive(coupon_id)
        reason = _reason(coupon.rule, book, _recent_categories(book))
        if reason is None:
            raise CouponNotOffered(coupon_id)
        with self._lock:
            self._activations.setdefault((book.customer_id, coupon_id), _now())
        return self._coupon(coupon, book.customer_id, today, reason)

    def _campaign(self, campaign: Campaign, today: date) -> dict:
        coupon_ids = [coupon.coupon_id for coupon in COUPONS if coupon.campaign_id == campaign.campaign_id]
        return {
            "campaign_id": campaign.campaign_id,
            "issuer": ISSUER,
            "title": campaign.title,
            "description": campaign.description,
            "starts_on": campaign.starts_on.isoformat(),
            "ends_on": campaign.ends_on.isoformat(),
            "days_left": (campaign.ends_on - today).days,
            "terms": campaign.terms,
            "coupon_ids": coupon_ids,
            "coupon_count": len(coupon_ids),
        }

    def _coupon(self, coupon: Coupon, customer_id: str, today: date, reason: str) -> dict:
        activated_at = self._activations.get((customer_id, coupon.coupon_id))
        campaign = self.campaigns.get(coupon.campaign_id) if coupon.campaign_id else None
        return {
            "coupon_id": coupon.coupon_id,
            "issuer": ISSUER,
            "campaign_id": coupon.campaign_id,
            "campaign_title": None if campaign is None else campaign.title,
            "title": coupon.title,
            "description": coupon.description,
            "kind": coupon.kind,
            "benefit": coupon.benefit,
            "product": coupon.product,
            "value_cents": coupon.value_cents,
            "value": None if coupon.value_cents is None else eur(coupon.value_cents),
            "percent": coupon.percent,
            "min_spend_cents": coupon.min_spend_cents,
            "min_spend": None if coupon.min_spend_cents is None else eur(coupon.min_spend_cents),
            "valid_from": coupon.valid_from.isoformat(),
            "valid_to": coupon.valid_to.isoformat(),
            "days_left": (coupon.valid_to - today).days,
            "terms": coupon.terms,
            "reason": reason,
            "activated": activated_at is not None,
            "activated_at": activated_at,
            "activate": f"/customers/{customer_id}/coupons/{coupon.coupon_id}/activate",
        }
