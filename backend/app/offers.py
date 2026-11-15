"""Demo coupons and campaigns from S Group brands.

Every offer here is invented for the Junction demo: S Group brand names with
made-up discounts. Nothing here is a real S Group or S-Pankki offer.

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
from app.demo import demo_today
from app.money import eur

ISSUER = "S Group"
SAMPLE_NOTE = "Demo offers for Junction: S Group brands, invented discounts."
RECENT_MONTHS = 3
FITS_TAG = "Fits your everyday spending"


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
    brand: str
    title: str
    line: str  # the one line under the title on the coupon card
    description: str
    kind: str  # discount | cashback
    benefit: str  # the stub on the coupon card, such as "−15 %"
    product: str
    valid_from: date
    valid_to: date
    terms: str
    rule: Rule = field(default_factory=Rule)
    value_cents: int | None = None
    percent: float | None = None
    min_spend_cents: int | None = None
    uses_per_month: int | None = None  # a stamp card: this many discounted visits a month
    renews_monthly: bool = False


@dataclass(frozen=True)
class Campaign:
    campaign_id: str
    brand: str
    title: str
    description: str
    starts_on: date
    ends_on: date
    terms: str


CAMPAIGNS: tuple[Campaign, ...] = (
    Campaign(
        campaign_id="CMP-PRISMA-HOME-2026",
        brand="Prisma",
        title="Prisma autumn home week",
        description=(
            "Home goods, kitchen and electronics in Prisma stores and at Prisma.fi. "
            "Pay with your S-Bank card and collect Bonus as usual."
        ),
        starts_on=date(2026, 9, 28),
        ends_on=date(2026, 10, 11),
        terms="Demo campaign. The discounts are invented.",
    ),
    Campaign(
        campaign_id="CMP-SOKOS-WEEKENDS-2026",
        brand="Sokos Hotels",
        title="Sokos Hotels autumn weekends",
        description="Weekend stays around Finland. Book with your S-Bank card and collect Bonus.",
        starts_on=date(2026, 9, 1),
        ends_on=date(2026, 10, 31),
        terms="Demo campaign. The discounts are invented.",
    ),
    Campaign(
        campaign_id="CMP-SUMMER-2026",
        brand="S-market",
        title="Summer Cashback",
        description="Cashback on summer card payments.",
        starts_on=date(2026, 6, 1),
        ends_on=date(2026, 8, 31),
        terms="Ended 31 Aug 2026.",
    ),
)


COUPONS: tuple[Coupon, ...] = (
    Coupon(
        coupon_id="CPN-ROSSO",
        campaign_id=None,
        brand="Rosso",
        title="Rosso coupon",
        line="−15 % on your next 4 visits · renews monthly",
        description="15 % off your next 4 visits to Rosso. Show your S-Etukortti when you pay. It renews every month.",
        kind="discount",
        benefit="−15 %",
        product="S-Etukortti",
        percent=15.0,
        uses_per_month=4,
        renews_monthly=True,
        valid_from=date(2026, 9, 1),
        valid_to=date(2026, 12, 31),
        terms="4 discounted visits each calendar month. Unused visits do not carry over.",
        rule=Rule(categories=("Restaurants",)),
    ),
    Coupon(
        coupon_id="CPN-SMARKET-FRUIT",
        campaign_id=None,
        brand="S-market",
        title="S-market: fruit and vegetables",
        line="Until 11 Oct",
        description="10 % off fruit and vegetables at S-market. Show your S-Etukortti when you pay.",
        kind="discount",
        benefit="−10 %",
        product="S-Etukortti",
        percent=10.0,
        valid_from=date(2026, 9, 28),
        valid_to=date(2026, 10, 11),
        terms="Once per visit.",
    ),
    Coupon(
        coupon_id="CPN-ALEPA-SALAD",
        campaign_id=None,
        brand="Alepa",
        title="Alepa: lunch salads",
        line="Until 15 Oct",
        description="15 % off lunch salads at Alepa. Show your S-Etukortti when you pay.",
        kind="discount",
        benefit="−15 %",
        product="S-Etukortti",
        percent=15.0,
        valid_from=date(2026, 9, 28),
        valid_to=date(2026, 10, 15),
        terms="Once per visit.",
    ),
    Coupon(
        coupon_id="CPN-SUMMER-ICE",
        campaign_id="CMP-SUMMER-2026",
        brand="S-market",
        title="€2 back on summer treats",
        line="Ended",
        description="Ended. Shown only to check that expired coupons stay out.",
        kind="cashback",
        benefit="€2 back",
        product="S-Pankki Visa Debit",
        value_cents=200,
        valid_from=date(2026, 6, 1),
        valid_to=date(2026, 8, 31),
        terms="Ended 31 Aug 2026.",
    ),
)


class CouponNotFound(LookupError):
    pass


class CouponNotOffered(LookupError):
    pass


class CouponNotActive(ValueError):
    pass


def _next_month_start(today: date) -> date:
    return date(today.year + (today.month == 12), today.month % 12 + 1, 1)


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

    def __init__(self, today: Callable[[], date] = demo_today) -> None:
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

    def deactivate(self, book: CustomerBook, coupon_id: str) -> dict:
        coupon = self.coupons.get(coupon_id)
        if coupon is None:
            raise CouponNotFound(coupon_id)
        reason = _reason(coupon.rule, book, _recent_categories(book))
        if reason is None:
            raise CouponNotOffered(coupon_id)
        with self._lock:
            self._activations.pop((book.customer_id, coupon_id), None)
        return self._coupon(coupon, book.customer_id, self.today(), reason)

    def reset(self) -> None:
        """Forget every activation, for a fresh demo run."""
        with self._lock:
            self._activations.clear()

    def _campaign(self, campaign: Campaign, today: date) -> dict:
        coupon_ids = [coupon.coupon_id for coupon in COUPONS if coupon.campaign_id == campaign.campaign_id]
        return {
            "campaign_id": campaign.campaign_id,
            "issuer": ISSUER,
            "brand": campaign.brand,
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
            "brand": coupon.brand,
            "title": coupon.title,
            "line": coupon.line,
            "tag": FITS_TAG if coupon.rule.categories else None,
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
            "uses_per_month": coupon.uses_per_month,
            # Uses happen at the till, which the Junction data does not record, so none are used yet.
            "used_this_month": 0 if coupon.uses_per_month else None,
            "renews_monthly": coupon.renews_monthly,
            "renews_on": _next_month_start(today).isoformat() if coupon.renews_monthly else None,
            "activate": f"/customers/{customer_id}/coupons/{coupon.coupon_id}/activate",
            "deactivate": f"/customers/{customer_id}/coupons/{coupon.coupon_id}/deactivate",
        }
