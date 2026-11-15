"""SpendSight HTTP API.

Two separate data worlds. The Junction 2026 S-Pankki CSVs back the customer
routes (Today, Spending, Save, the coach, coupons). Aino Korhonen's
hard-coded design sample backs the ledger, tools, and chart routes.
"""

from __future__ import annotations

from fastapi import APIRouter, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, RedirectResponse

from app.junction_db import JunctionLibrary, index_ready
from app.ledger import SLIP_CATEGORIES, Ledger, loan_sketch
from app.offers import CouponNotActive, CouponNotFound, CouponNotOffered, OfferBook
from app.schemas import AskRequest, CustomerCoachResponse, EffortRequest, FilingRequest
from app.statfi import StatisticsCache
from app.verda import VerdaConfigError, VerdaError, ask_customer

router = APIRouter()

SAMPLE = "Design sample"

API_DESCRIPTION = """
Every route here is called by the SpendSight phone app, except the health checks.

**Customers, Coach, Offers** read the Junction 2026 S-Pankki files loaded at startup
(5,100 customers, 200 mortgage applications, 3,626,755 transactions). The app opens
`GET /customers/{customer_id}` and follows its `reads` paths for Today, Spending, and Save.

**Design sample** routes read Aino Korhonen's hard-coded ledger ("today" is 13 Nov).
They never read the Junction files, and the Junction figures never come from them.

Money fields named `*_cents` are integer cents. Display strings use `€` and a
unicode minus. Coupons and campaigns are sample S-Pankki offers, not real ones.

Written reference: `backend/API.md`.
"""

TAGS = [
    {"name": "Customers", "description": "One Junction customer: summary, Today, Spending, Save."},
    {"name": "Coach", "description": "Questions answered by the model from one customer's transactions."},
    {"name": "Offers", "description": "Sample S-Pankki campaigns and coupons."},
    {"name": SAMPLE, "description": "Aino Korhonen's demo ledger. Not Junction data."},
    {"name": "Health", "description": "Liveness and readiness checks."},
]


def create_app() -> FastAPI:
    """Build the app after the Junction CSVs and the transaction index are in memory."""
    app = FastAPI(
        title="SpendSight",
        version="0.1.0",
        description=API_DESCRIPTION,
        openapi_tags=TAGS,
    )
    app.state.ledger = Ledger()
    app.state.junction = JunctionLibrary()
    app.state.offers = OfferBook()
    app.state.statistics = StatisticsCache()
    app.include_router(router)
    return app


def _ledger(request: Request) -> Ledger:
    return request.app.state.ledger


def _junction(request: Request) -> JunctionLibrary:
    library = request.app.state.junction
    if library is None:
        raise HTTPException(status_code=503, detail="Junction data is missing")
    return library


@router.get("/", include_in_schema=False)
def root() -> RedirectResponse:
    return RedirectResponse(url="/docs")


@router.get("/health", tags=["Health"])
def health(request: Request) -> dict:
    """The service is up. Counts what was loaded."""
    library = request.app.state.junction
    transactions = library.transactions if library is not None else {}
    return {
        "ok": True,
        "service": "spendsight",
        "customers": None if library is None else len(library.customers),
        "transactions_rows": transactions.get("rows"),
    }


@router.get("/healthz", tags=["Health"])
def healthz(request: Request) -> JSONResponse:
    """Liveness and readiness for the container. 503 until the Junction index is ready."""
    index = index_ready()
    ready = request.app.state.junction is not None and index["status"] == "ready"
    body = {"ok": ready, "index": index}
    return JSONResponse(body, status_code=200 if ready else 503)


def _book(request: Request, customer_id: str):
    try:
        return _junction(request).book(customer_id)
    except KeyError:
        raise HTTPException(status_code=404, detail="No customer with that id") from None
    except LookupError:
        raise HTTPException(status_code=404, detail="That customer has no transactions") from None


@router.get("/customers/{customer_id}", tags=["Customers"])
def customer_summary(customer_id: str, request: Request) -> dict:
    """Balance, dates, and links for one customer."""
    return _book(request, customer_id).summary_view()


@router.get("/customers/{customer_id}/today", tags=["Customers"])
def customer_today(customer_id: str, request: Request) -> dict:
    """Latest balance, latest salary, and the latest month for one customer."""
    return _book(request, customer_id).today_view()


@router.get("/customers/{customer_id}/spending", tags=["Customers"])
def customer_spending(customer_id: str, request: Request, month: str | None = None) -> dict:
    """One month of payments. `month` is YYYY-MM. The default is that customer's latest month."""
    try:
        return _book(request, customer_id).spending_view(month)
    except KeyError:
        raise HTTPException(
            status_code=400,
            detail="month is YYYY-MM for a month this customer has",
        ) from None


@router.get("/customers/{customer_id}/save", tags=["Customers"])
def customer_save(customer_id: str, request: Request, years: int = 5) -> dict:
    """Recorded investment deposits over 1, 3, or 5 years. This does not move money."""
    if years not in {1, 3, 5}:
        raise HTTPException(status_code=400, detail="years is 1, 3, or 5")
    return _book(request, customer_id).save_view(years)


def _offers(request: Request) -> OfferBook:
    return request.app.state.offers


@router.get("/campaigns", tags=["Offers"])
def campaigns(request: Request) -> dict:
    """Demo campaigns running today. Invented offers, not real ones."""
    return _offers(request).campaigns_view()


@router.get("/customers/{customer_id}/coupons", tags=["Offers"])
def customer_coupons(customer_id: str, request: Request) -> dict:
    """Demo coupons valid today that this customer qualifies for, with activation state."""
    return _offers(request).coupons_view(_book(request, customer_id))


@router.post("/customers/{customer_id}/coupons/{coupon_id}/activate", tags=["Offers"])
def activate_coupon(customer_id: str, coupon_id: str, request: Request) -> dict:
    """Activate one coupon for this customer. Activating again returns the same coupon."""
    book = _book(request, customer_id)
    try:
        return _offers(request).activate(book, coupon_id)
    except CouponNotFound:
        raise HTTPException(status_code=404, detail="No coupon with that id") from None
    except CouponNotOffered:
        raise HTTPException(status_code=404, detail="That coupon is not offered to this customer") from None
    except CouponNotActive:
        raise HTTPException(status_code=409, detail="That coupon is not valid today") from None


@router.post("/customers/{customer_id}/coupons/{coupon_id}/deactivate", tags=["Offers"])
def deactivate_coupon(customer_id: str, coupon_id: str, request: Request) -> dict:
    """Turn one coupon off again. Turning off a coupon that is not on returns it unchanged."""
    book = _book(request, customer_id)
    try:
        return _offers(request).deactivate(book, coupon_id)
    except CouponNotFound:
        raise HTTPException(status_code=404, detail="No coupon with that id") from None
    except CouponNotOffered:
        raise HTTPException(status_code=404, detail="That coupon is not offered to this customer") from None


@router.post("/demo/reset", tags=["Offers"])
def demo_reset(request: Request) -> dict:
    """Turn every coupon off again, for a fresh demo run."""
    _offers(request).reset()
    return {"ok": True}


@router.post(
    "/customers/{customer_id}/coach/ask",
    tags=["Coach"],
    response_model=CustomerCoachResponse,
)
def customer_coach_ask(customer_id: str, body: AskRequest, request: Request) -> dict:
    """Answer one question with Verda, using only this customer's transactions."""
    try:
        return ask_customer(_book(request, customer_id), body.text, getattr(request.app.state, "statistics", None))
    except VerdaConfigError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except VerdaError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@router.get("/settings/effort", tags=[SAMPLE])
def get_effort(request: Request) -> dict:
    """The save setting (small, steady, big) and what each one sets aside."""
    return _ledger(request).effort_view()


@router.put("/settings/effort", tags=[SAMPLE])
def put_effort(body: EffortRequest, request: Request) -> dict:
    """Change the save setting. Returns the settings and the updated Today figures."""
    view = _ledger(request).set_effort(body.effort)
    view["today"] = _ledger(request).today_view()
    return view


@router.get("/ledger/summary", tags=[SAMPLE])
def ledger_summary(request: Request) -> dict:
    """Every oracle figure in one read (see BACKLOG.md)."""
    return _ledger(request).summary()


@router.get("/payments", tags=[SAMPLE])
def payments(request: Request, month: str | None = None) -> dict:
    """Payments with category, basket, Bonus, and whether they recur. `month` is oct or nov."""
    ledger = _ledger(request)
    if month is not None and month not in {"oct", "nov"}:
        raise HTTPException(status_code=400, detail="month is oct or nov")
    rows = ledger.payments if month is None else ledger.month(month)
    return {"payments": [ledger.payment_view(row) for row in rows]}


@router.post("/payments/{payment_id}/filing", tags=[SAMPLE])
def refile(payment_id: int, body: FilingRequest, request: Request) -> dict:
    """File one payment into another category. Bonus stays with the place."""
    ledger = _ledger(request)
    try:
        row = ledger.refile(payment_id, body.category)
    except KeyError:
        raise HTTPException(
            status_code=400,
            detail="Unknown category. A payment stays unfiled until it is filed into a known category.",
        ) from None
    except LookupError:
        raise HTTPException(status_code=404, detail="No payment with that id") from None
    return ledger.payment_view(row)


@router.get("/tools/week", tags=[SAMPLE])
def tool_week(request: Request) -> dict:
    """Safe to spend before payday: balance minus bills before payday minus the buffer."""
    ledger = _ledger(request)
    week = ledger.safe_week()
    week["safe_to_spend"] = ledger.today_view()["safe_to_spend"]
    week["slip_cents"] = ledger.slip_cents()
    week["effort"] = ledger.effort
    week["note"] = "The save setting does not change this figure. Bills after payday stay out."
    return week


@router.get("/tools/spender", tags=[SAMPLE])
def tool_spender(request: Request) -> dict:
    """October split into must-every-month, must-when-it-happens, and everyday."""
    ledger = _ledger(request)
    return {
        "october_out_cents": ledger.sum_month("oct"),
        "must_every_month_cents": ledger.sum_month("oct", lambda row: row.basket == "regular"),
        "must_when_it_happens_cents": ledger.sum_month("oct", lambda row: row.basket == "irregular"),
        "everyday_cents": ledger.sum_month("oct", lambda row: row.basket == "everyday"),
        "slip_cents": ledger.sum_month("oct", lambda row: row.category in SLIP_CATEGORIES),
        "lines": len(ledger.month("oct")),
    }


@router.get("/tools/dining", tags=[SAMPLE])
def tool_dining(request: Request) -> dict:
    """What a fifth less dining out, or one or two fewer meals a week, would save."""
    return _ledger(request).dining()


@router.get("/tools/impulse", tags=[SAMPLE])
def tool_impulse(request: Request) -> dict:
    """November dining, coffee, taxi, and shopping payments by day, and the peak day."""
    return _ledger(request).impulse()


@router.get("/tools/runway", tags=[SAMPLE])
def tool_runway(request: Request) -> dict:
    """Days until the balance runs out at the November pace, rent left out."""
    return _ledger(request).runway()


@router.get("/tools/subscriptions", tags=[SAMPLE])
def tool_subscriptions(request: Request) -> dict:
    """Three subscriptions to cancel, with the month and year totals."""
    return _ledger(request).subscription_cut()


@router.get("/charts", tags=[SAMPLE])
def charts(request: Request) -> dict:
    """November so far against the October pace, by category."""
    return {
        "rows": _ledger(request).charts(),
        "note": "Her months only. Last year is not loaded, so it is not drawn.",
    }


@router.get("/bonus", tags=[SAMPLE])
def bonus(request: Request) -> dict:
    """S-Group Bonus steps and the gap to the next one."""
    return _ledger(request).bonus_view()


@router.get("/purchase", tags=[SAMPLE])
def purchase(request: Request) -> dict:
    """The jacket purchase: Bonus gained against the net cost."""
    return _ledger(request).jacket()


@router.get("/watch", tags=[SAMPLE])
def watch(request: Request) -> dict:
    """Charges to check, such as a second Bolt ride. They stay out of the spending total."""
    return {
        "items": _ledger(request).watch,
        "note": "These stay out of the spending total. Marking them does not change S-Tili.",
    }


@router.get("/loan/sketch", tags=[SAMPLE])
def loan(
    price_eur: int = 220_000,
    rate_percent: float = 4.0,
    years: int = 25,
    down_percent: float = 10.0,
) -> dict:
    """Monthly payment and the 6% stress figure. Arithmetic, not a loan offer."""
    if years < 1 or years > 40:
        raise HTTPException(status_code=400, detail="years must be from 1 to 40")
    if not 0 <= rate_percent <= 20:
        raise HTTPException(status_code=400, detail="rate_percent must be from 0 to 20")
    if not 0 <= down_percent <= 100:
        raise HTTPException(status_code=400, detail="down_percent must be from 0 to 100")
    return loan_sketch(price_eur, rate_percent, years, down_percent)


app = create_app()
