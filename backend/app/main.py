"""SpendSight HTTP API.

Today, Spending, and Save without an id return the first customer in the
Junction database. /customers/{customer_id} reads any customer. The coach
call is separate and still answers from the design sample.
"""

from __future__ import annotations

from fastapi import APIRouter, FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse, RedirectResponse

from app.junction_db import IndexNotReady, JunctionLibrary, index_ready, query_transactions
from app.ledger import SLIP_CATEGORIES, loan_sketch
from app.schemas import (
    AskRequest,
    CustomerCoachResponse,
    CoachResponse,
    EffortRequest,
    FilingPreviewRequest,
    FilingRequest,
)
from app.store import Session
from app.verda import VerdaConfigError, VerdaError, ask_customer


def _clamp(limit: int) -> int:
    return max(1, min(limit, 200))


router = APIRouter()


API_DESCRIPTION = """
The service reads the Junction 2026 S-Pankki files when it starts: 5,100 customers,
200 mortgage applications, their negotiation events, and 3,626,755 transactions.

`/today`, `/spending`, and `/save` are the first customer, CUST00001.
Add the customer id for anyone else, for example `/customers/CUST00002/today`.

Money fields named `*_cents` are integer cents. Display strings use `€` and a
unicode minus. The coach at `POST /coach/ask` answers from the design sample,
not from these transactions.

Interactive reference: `/docs`. Written reference: `backend/API.md`.
"""


def create_app() -> FastAPI:
    """Build the app after the Junction CSVs and the transaction index are in memory."""
    app = FastAPI(title="SpendSight", version="0.1.0", description=API_DESCRIPTION)
    app.state.session = Session()
    app.state.junction = JunctionLibrary()
    app.include_router(router)
    return app


def _session(request: Request) -> Session:
    return request.app.state.session


def _junction(request: Request) -> JunctionLibrary:
    library = request.app.state.junction
    if library is None:
        raise HTTPException(status_code=503, detail="Junction data is missing")
    return library


@router.get("/", include_in_schema=False)
def root() -> RedirectResponse:
    return RedirectResponse(url="/docs")


@router.get("/health")
def health(request: Request) -> dict:
    library = request.app.state.junction
    transactions = library.transactions if library is not None else {}
    return {
        "ok": True,
        "service": "spendsight",
        "customers": None if library is None else len(library.customers),
        "transactions_rows": transactions.get("rows"),
        "customer_id": None if library is None else library.primary.customer_id,
    }


@router.get("/healthz")
def healthz(request: Request) -> JSONResponse:
    """Liveness and readiness for the container. 503 until the Junction index is ready."""
    index = index_ready()
    ready = request.app.state.junction is not None and index["status"] == "ready"
    body = {"ok": ready, "index": index}
    return JSONResponse(body, status_code=200 if ready else 503)


@router.get("/session")
def session(request: Request) -> dict:
    return _junction(request).primary.session_view()


@router.get("/today")
def today(request: Request) -> dict:
    return _junction(request).primary.today_view()


@router.get("/spending")
def spending(request: Request, month: str | None = None) -> dict:
    try:
        return _junction(request).primary.spending_view(month)
    except KeyError:
        raise HTTPException(
            status_code=400,
            detail="month is YYYY-MM for a month this customer has",
        ) from None


@router.get("/save")
def save(request: Request, years: int = 5) -> dict:
    if years not in {1, 3, 5}:
        raise HTTPException(status_code=400, detail="years is 1, 3, or 5")
    return _junction(request).primary.save_view(years)


def _book(request: Request, customer_id: str):
    try:
        return _junction(request).book(customer_id)
    except KeyError:
        raise HTTPException(status_code=404, detail="No customer with that id") from None
    except LookupError:
        raise HTTPException(status_code=404, detail="That customer has no transactions") from None


@router.get("/database", tags=["Database"])
def database(request: Request) -> dict:
    """Counts, dates, categories, and transaction types for the whole index."""
    return _junction(request).database_view()


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


@router.get("/customers/{customer_id}/months", tags=["Customers"])
def customer_months(customer_id: str, request: Request) -> dict:
    """Every month this customer has, with money in and money out."""
    return _book(request, customer_id).months_view()


@router.post(
    "/customers/{customer_id}/coach/ask",
    tags=["Customers"],
    response_model=CustomerCoachResponse,
)
def customer_coach_ask(customer_id: str, body: AskRequest, request: Request) -> dict:
    """Answer one question with Verda, using only this customer's transactions."""
    try:
        return ask_customer(_book(request, customer_id), body.text)
    except VerdaConfigError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except VerdaError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@router.get("/settings/effort")
def get_effort(request: Request) -> dict:
    return _session(request).ledger.effort_view()


@router.put("/settings/effort")
def put_effort(body: EffortRequest, request: Request) -> dict:
    view = _session(request).ledger.set_effort(body.effort)
    view["today"] = _session(request).ledger.today_view()
    return view


@router.get("/ledger/summary")
def ledger_summary(request: Request) -> dict:
    return _session(request).ledger.summary()


@router.get("/payments")
def payments(request: Request, month: str | None = None) -> dict:
    ledger = _session(request).ledger
    if month is not None and month not in {"oct", "nov"}:
        raise HTTPException(status_code=400, detail="month is oct or nov")
    rows = ledger.payments if month is None else ledger.month(month)
    return {"payments": [ledger.payment_view(row) for row in rows]}


@router.get("/payments/{payment_id}")
def payment(payment_id: int, request: Request) -> dict:
    ledger = _session(request).ledger
    try:
        row = ledger.payment(payment_id)
    except LookupError:
        raise HTTPException(status_code=404, detail="No payment with that id") from None
    return ledger.payment_view(row)


@router.post("/payments/{payment_id}/filing")
def refile(payment_id: int, body: FilingRequest, request: Request) -> dict:
    ledger = _session(request).ledger
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


@router.post("/ledger/file")
def preview_file(body: FilingPreviewRequest, request: Request) -> dict:
    ledger = _session(request).ledger
    try:
        return ledger.preview_filing(body.place, body.category)
    except KeyError:
        raise HTTPException(
            status_code=400,
            detail="Unknown category. A new merchant stays unfiled until you file it into a known category.",
        ) from None


@router.get("/tools/week")
def tool_week(request: Request) -> dict:
    ledger = _session(request).ledger
    week = ledger.safe_week()
    week["safe_to_spend"] = ledger.today_view()["safe_to_spend"]
    week["slip_cents"] = ledger.slip_cents()
    week["effort"] = ledger.effort
    week["note"] = "The save setting does not change this figure. Bills after payday stay out."
    return week


@router.get("/tools/spender")
def tool_spender(request: Request) -> dict:
    ledger = _session(request).ledger
    return {
        "october_out_cents": ledger.sum_month("oct"),
        "must_every_month_cents": ledger.sum_month("oct", lambda row: row.basket == "regular"),
        "must_when_it_happens_cents": ledger.sum_month("oct", lambda row: row.basket == "irregular"),
        "everyday_cents": ledger.sum_month("oct", lambda row: row.basket == "everyday"),
        "slip_cents": ledger.sum_month("oct", lambda row: row.category in SLIP_CATEGORIES),
        "lines": len(ledger.month("oct")),
    }


@router.get("/tools/dining")
def tool_dining(request: Request) -> dict:
    return _session(request).ledger.dining()


@router.get("/tools/impulse")
def tool_impulse(request: Request) -> dict:
    return _session(request).ledger.impulse()


@router.get("/tools/runway")
def tool_runway(request: Request) -> dict:
    return _session(request).ledger.runway()


@router.get("/tools/subscriptions")
def tool_subscriptions(request: Request) -> dict:
    return _session(request).ledger.subscription_cut()


@router.get("/charts")
def charts(request: Request) -> dict:
    return {
        "rows": _session(request).ledger.charts(),
        "note": "Her months only. Last year is not loaded, so it is not drawn.",
    }


@router.get("/bonus")
def bonus(request: Request) -> dict:
    return _session(request).ledger.bonus_view()


@router.get("/purchase")
def purchase(request: Request) -> dict:
    return _session(request).ledger.jacket()


@router.get("/watch")
def watch(request: Request) -> dict:
    return {
        "items": _session(request).ledger.watch,
        "note": "These stay out of the spending total. Marking them does not change S-Tili.",
    }


@router.get("/loan/sketch")
def loan(
    price_eur: int = 220_000,
    rate_percent: float = 4.0,
    years: int = 25,
    down_percent: float = 10.0,
) -> dict:
    if years < 1 or years > 40:
        raise HTTPException(status_code=400, detail="years must be from 1 to 40")
    if not 0 <= rate_percent <= 20:
        raise HTTPException(status_code=400, detail="rate_percent must be from 0 to 20")
    if not 0 <= down_percent <= 100:
        raise HTTPException(status_code=400, detail="down_percent must be from 0 to 100")
    return loan_sketch(price_eur, rate_percent, years, down_percent)


@router.post("/coach/ask", response_model=CoachResponse)
def coach_ask(body: AskRequest, request: Request) -> dict:
    return _session(request).ask(body.text)


@router.get("/thread")
def export_thread(request: Request) -> dict:
    return _session(request).export_thread()


@router.delete("/thread", status_code=204)
def delete_thread(request: Request) -> None:
    _session(request).delete_thread()


@router.get("/audit")
def audit(request: Request) -> dict:
    session = _session(request)
    return {
        "account": session.ledger.account,
        "rows": session.audit,
        "note": "The question text is not stored here and is not sent out as training data.",
    }


@router.get("/junction/status")
def junction_status(request: Request) -> dict:
    return _junction(request).status()


@router.get("/junction/customers")
def junction_customers(
    request: Request,
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    postal_code: str | None = None,
    q: str | None = None,
) -> dict:
    return _junction(request).list_customers(
        limit=_clamp(limit),
        offset=offset,
        postal_code=postal_code,
        q=q,
    )


@router.get("/junction/customers/{customer_id}")
def junction_customer(customer_id: str, request: Request) -> dict:
    row = _junction(request).customer(customer_id)
    if row is None:
        raise HTTPException(status_code=404, detail="No Junction customer with that id")
    return row


@router.get("/junction/customers/{customer_id}/transactions")
def junction_transactions(
    customer_id: str,
    request: Request,
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    category: str | None = None,
    date_from: str | None = None,
    date_to: str | None = None,
) -> dict:
    library = _junction(request)
    if library.customer(customer_id) is None:
        raise HTTPException(status_code=404, detail="No Junction customer with that id")
    try:
        return query_transactions(
            customer_id,
            limit=_clamp(limit),
            offset=offset,
            category=category,
            date_from=date_from,
            date_to=date_to,
        )
    except IndexNotReady as exc:
        raise HTTPException(status_code=503, detail={"message": str(exc), "index": exc.status}) from exc


@router.get("/junction/mortgages")
def junction_mortgages(
    request: Request,
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
    decision: str | None = None,
) -> dict:
    return _junction(request).list_mortgages(limit=_clamp(limit), offset=offset, decision=decision)


@router.get("/junction/mortgages/{mortgage_customer_id}")
def junction_mortgage(mortgage_customer_id: str, request: Request) -> dict:
    row = _junction(request).mortgage(mortgage_customer_id)
    if row is None:
        raise HTTPException(status_code=404, detail="No mortgage application with that id")
    return row


app = create_app()
