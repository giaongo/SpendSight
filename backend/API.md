# SpendSight backend

Python FastAPI service. It reads the Junction 2026 S-Pankki files when the process starts and answers from that data. The phone that calls it is documented in [`frontend/APP.md`](../frontend/APP.md).

The interactive reference is [https://127.0.0.1:8000/docs](https://127.0.0.1:8000/docs). The certificate is local and self-signed, so the browser warns once. Continue past that warning.

## Run

From `backend/`:

```bash
.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000 \
  --ssl-certfile .cache/certs/localhost.crt \
  --ssl-keyfile .cache/certs/localhost.key
```

The process listens on this computer only. `127.0.0.1` from a phone or the Android emulator is that other device, not this Mac.

## Data

Loaded at startup from `Junction-2026-S-Pankki/`:

| File | What it holds |
| --- | --- |
| `tx_customers_junction.csv` | 5,100 customers: id, postal code, age |
| `tx_junction_transactions.csv` | 3,626,755 transactions, 2024-10-01 through 2026-09-30 |
| `mtg_tx_customer_link_and_final_mtg_infio.csv` | 200 mortgage applications and the final decision |

The dataset also has `mtg_negotiation_history.csv`. The service does not read it.

Transactions are indexed in `backend/.cache/junction.sqlite` (set `SPENDSIGHT_CACHE_DIR` to put it elsewhere, for example `/data/cache` on the server). A later start opens that file when it is newer than the CSV. Amounts in responses are integer cents. A display string such as `€4,723.58` uses a unicode minus for negative values.

Every route the service exposes is called by the phone, except `/health` and `/healthz`. The routes fall into two worlds that never mix:

- **Junction data:** `/customers/{customer_id}…`, the coach, coupons, and campaigns.
- **Design sample:** Aino Korhonen's hard-coded ledger at `/settings/effort`, `/ledger/summary`, `/payments`, `/tools/*`, `/charts`, `/bonus`, `/purchase`, `/watch`, and `/loan/sketch`. These never read the Junction files.

## Container

`backend/Dockerfile` builds the deployed image: `python:3.12-slim`, `uv sync --frozen`, a non-root user (UID 1000), and uvicorn on `0.0.0.0:8000` over plain HTTP with one worker, because the demo session lives in memory. TLS is Caddy's job in production; `python -m app` keeps the self-signed HTTPS mode for local runs.

```bash
docker build -t spendsight-api backend
docker run --rm -p 8000:8000 \
  -v "$PWD/Junction-2026-S-pankki:/data/junction:ro" \
  -v "$PWD/backend/.cache:/data/cache" \
  spendsight-api
```

The image sets `JUNCTION_DIR=/data/junction` and `SPENDSIGHT_CACHE_DIR=/data/cache`. The cache mount must be writable by UID 1000. `.dockerignore` keeps `.venv`, `.cache`, `.env`, and the CSVs out of the image.

## Health

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/health` | `{"ok": true, "service": "spendsight", "customers": …, "transactions_rows": …}` |
| GET | `/healthz` | `200 {"ok": true, "index": {"status": "ready", ...}}` once the transaction index is open, and `503` otherwise. The container `HEALTHCHECK`, CI, and `infra/smoke.sh` use it |

## Customers

The phone opens `GET /customers/{customer_id}` and follows the three paths in its `reads` object (`today`, `spending`, `save`).

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/customers/{customer_id}` | Balance, dates, transaction count, mortgage link, and `reads` |
| GET | `/customers/{customer_id}/today` | Latest balance, latest salary, and the latest month |
| GET | `/customers/{customer_id}/spending?month=YYYY-MM` | That month's categories and payments. Omit `month` for the latest month |
| GET | `/customers/{customer_id}/save?years=1\|3\|5` | Recorded investment deposits in that window. Nothing is moved |

Unknown customer ids return 404. A `month` the customer does not have returns 400. `years` other than 1, 3, or 5 returns 400.

### Ask about one customer

`POST /customers/{customer_id}/coach/ask` with `{ "text": "How much did I spend last month?" }`.

The answer uses the same language as the question. The service detects that language, for example English, Finnish, Swedish, or German, and the reply is in that language.

A question about that customer's spending, balance, salary, bills, savings, or mortgage is answered from their transactions. The service computes the figures and sends them with the question. The model may phrase the answer. It does not choose the cents.

A question falls into one of three groups. Personal finance uses this customer's transactions and a card counted here (`spending_breakdown`, `month_trend`, `balance`, or `savings`). Peer comparison uses Statistics Finland table 15jx (public PxWeb API, cached for six hours), which is deposits and funds by age, not household spending. A general question is answered on its own. `uses_customer_data` is true for the personal and peer groups.

The response keeps `answer` as the model's text and adds `category`, `lead`, `detail`, `card`, `caption`, and `follow_ups`. The card's figures are counted here. The model only writes the lead and the detail. If Statistics Finland cannot be reached, a peer question returns no card and does not call the model.

The call is `POST <VERDA_BASE_URL>/v1/chat/completions`. The question is not stored.

The system prompt (`SPENDING_PROMPT` and `OTHER_PROMPT` in `app/verda.py`) sets SpendSight's voice in the S-Pankki app, forbids investment, tax, legal, and loan advice, and marks the question as untrusted data. Before the question leaves the service, a henkilötunnus, IBAN, card number, email, phone, customer id, and long digit runs are replaced with placeholders, and control, zero-width, and bidi characters and chat-template markers are removed. The question goes to the model as a JSON string. Every `*_cents` figure is sent as the service's euro string (`€1,539.16`), so the model never divides cents.

The call needs `backend/.env`:

| Name | What to put |
| --- | --- |
| `VERDA_API_KEY` | Inference API key from Verda → Credentials → Inference API Keys |
| `VERDA_BASE_URL` | Containers API URL, `https://containers.verda.com/<deployment-name>` |
| `VERDA_MODEL` | Model id from `GET <base>/v1/models`. The tutorial model is `Qwen/Qwen2.5-1.5B-Instruct` |

A missing key or base URL returns 503. A deployment error returns 502. The response is `customer_id`, `answer`, `model`, `source` (`verda`), `uses_customer_data`, `disclaimer`, `category`, `lead`, `detail`, `card`, `caption`, and `follow_ups`. `card` is null for a general question and when the statistics table cannot be loaded.

CUST00001 is age 63, postal code 00990, account `ACC000001`, 581 transactions, balance €4,723.58 on 2026-09-30. CUST00002 is account `ACC000002`, balance €1,539.16 on the same date, with no investment deposits.

## Coupons and campaigns

Sample S-Pankki offers (`app/offers.py`). S-Pankki is the only issuer: there are no partner or merchant offers. None of them are real S-Pankki offers.

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/campaigns` | Campaigns running today, with their coupon ids and `days_left` |
| GET | `/customers/{customer_id}/coupons` | Coupons valid today that this customer qualifies for, with `activated` and `activated_at` |
| POST | `/customers/{customer_id}/coupons/{coupon_id}/activate` | That coupon, now activated. Activating again returns the same `activated_at` |

A coupon is valid from `valid_from` through `valid_to`, compared with today's date. A customer qualifies by a rule over their Junction data: categories paid in their last three months, age, whether they have investment deposits, and whether they have a mortgage application. `reason` says which rule matched. Amounts are `value_cents` and `min_spend_cents`, with display strings `value` and `min_spend`. `percent` is set for Bonus, rate, and fee coupons.

Activations are kept in memory and are lost when the service restarts. An unknown customer or coupon returns 404, a coupon this customer does not qualify for returns 404, and a coupon outside its dates returns 409.

## Design sample

Aino Korhonen's ledger from the design (`app/ledger.py`). "Today" is 13 Nov and salary lands on 15 Nov. The figures must match the oracle table in `BACKLOG.md`, checked by `tests/test_oracle.py`. The effort setting and refiled payments are kept in memory, which is why the container runs one worker.

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/settings/effort` | The save setting (`small`, `steady`, `big`) and what each sets aside |
| PUT | `/settings/effort` | Body `{ "effort": "steady" }`. The settings plus the updated Today figures in `today` |
| GET | `/ledger/summary` | Every oracle figure in one read |
| GET | `/payments?month=oct\|nov` | Payments with category, basket, Bonus, and whether they recur. Omit `month` for both |
| POST | `/payments/{payment_id}/filing` | Body `{ "category": "Groceries" }`. Refiles the line. Bonus stays with the place. An unknown category returns 400, an unknown id 404 |
| GET | `/tools/week` | Safe to spend: balance − bills before payday − buffer |
| GET | `/tools/spender` | October split into must-every-month, must-when-it-happens, and everyday |
| GET | `/tools/dining` | A fifth less dining out, and one or two fewer meals a week |
| GET | `/tools/impulse` | November dining, coffee, taxi, and shopping payments by day, and the peak day |
| GET | `/tools/runway` | Days until the balance runs out at the November pace, rent left out |
| GET | `/tools/subscriptions` | Three subscriptions to cancel, with month and year totals |
| GET | `/charts` | November so far against the October pace, by category |
| GET | `/bonus` | S-Group Bonus steps and the gap to the next one |
| GET | `/purchase` | The jacket purchase: Bonus gained against net cost |
| GET | `/watch` | Charges to check. They stay out of the spending total |
| GET | `/loan/sketch?price_eur=&rate_percent=&years=&down_percent=` | Monthly payment and the 6% stress figure |

`/loan/sketch` is arithmetic on a price and rate. It does not read the Junction mortgage files and it does not approve a loan. `years` is 1–40, `rate_percent` 0–20, and `down_percent` 0–100; anything else returns 400.
