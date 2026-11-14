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
| `mtg_negotiation_history.csv` | Negotiation events for those applications |

Transactions are indexed in `backend/.cache/junction.sqlite` (set `SPENDSIGHT_CACHE_DIR` to put it elsewhere, for example `/data/cache` on the server). A later start opens that file when it is newer than the CSV. Amounts in responses are integer cents. A display string such as `€4,723.58` uses a unicode minus for negative values.

`/today`, `/spending`, and `/save` use the first customer, **CUST00001**. Every other customer is under `/customers/{customer_id}`.

`POST /coach/ask` is a separate call. It answers from the design sample, not from these transactions.

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

`GET /healthz` returns `200 {"ok": true, "index": {"status": "ready", ...}}` once the transaction index is open, and `503` otherwise. The container `HEALTHCHECK` uses it.

## Database

`GET /database` returns the whole index: customer count, mortgage counts by decision, transaction count, first and last date, category totals, and transaction-type totals.

`GET /junction/status` is the same dataset with the index path and a note that a mortgage decision here is not the design sample's outcome.

## Statistics Finland

`GET /statistics/education-assets` queries Statistics Finland's public PxWeb API table `15jx.px` for listed shares, mutual funds, and deposits by level of education and sex. It returns the upstream JSON-stat2 response; a Statistics Finland request failure returns 502.

## Customers

`GET /junction/customers?limit=50&offset=0&postal_code=&q=` lists customers. `limit` is 1–200. `q` matches the customer id.

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/customers/{customer_id}` | Balance, dates, transaction count, and links |
| GET | `/customers/{customer_id}/today` | Latest balance, latest salary, and the latest month |
| GET | `/customers/{customer_id}/spending?month=YYYY-MM` | That month's categories and payments. Omit `month` for the latest month |
| GET | `/customers/{customer_id}/save?years=1\|3\|5` | Recorded investment deposits in that window. Nothing is moved |
| GET | `/customers/{customer_id}/months` | Every month, with money in and money out |
| GET | `/junction/customers/{customer_id}` | Postal code, age, and mortgage link if one exists |
| GET | `/junction/customers/{customer_id}/transactions` | Raw transactions. `limit` 1–200, plus `offset`, `category`, `date_from`, `date_to` |

Unknown customer ids return 404. A `month` the customer does not have returns 400. `years` other than 1, 3, or 5 returns 400.

### Ask about one customer

`POST /customers/{customer_id}/coach/ask` with `{ "text": "How much did I spend last month?" }`.

The answer uses the same language as the question. The service detects that language, for example English, Finnish, Swedish, or German, and the reply is in that language.

A question about that customer's spending, balance, salary, bills, savings, or mortgage is answered from their transactions. The service computes the figures and sends them with the question. The model may phrase the answer. It does not choose the cents.

Any other question is answered on its own. Their transactions are not included. `uses_customer_data` is true only for the spending case.

The call is `POST <VERDA_BASE_URL>/v1/chat/completions`. The question is not stored.

The system prompt (`SPENDING_PROMPT` and `OTHER_PROMPT` in `app/verda.py`) sets SpendSight's voice in the S-Pankki app, forbids investment, tax, legal, and loan advice, and marks the question as untrusted data. Before the question leaves the service, a henkilötunnus, IBAN, card number, email, phone, customer id, and long digit runs are replaced with placeholders, and control, zero-width, and bidi characters and chat-template markers are removed. The question goes to the model as a JSON string. Every `*_cents` figure is sent as the service's euro string (`€1,539.16`), so the model never divides cents.

The call needs `backend/.env`:

| Name | What to put |
| --- | --- |
| `VERDA_API_KEY` | Inference API key from Verda → Credentials → Inference API Keys |
| `VERDA_BASE_URL` | Containers API URL, `https://containers.verda.com/<deployment-name>` |
| `VERDA_MODEL` | Model id from `GET <base>/v1/models`. The tutorial model is `Qwen/Qwen2.5-1.5B-Instruct` |

A missing key or base URL returns 503. A deployment error returns 502. The response is `customer_id`, `answer`, `model`, `source` (`verda`), `uses_customer_data`, and `disclaimer`.

CUST00001 is age 63, postal code 00990, account `ACC000001`, 581 transactions, balance €4,723.58 on 2026-09-30. CUST00002 is account `ACC000002`, balance €1,539.16 on the same date, with no investment deposits.

## Mortgages

| Method | Path | Returns |
| --- | --- | --- |
| GET | `/junction/mortgages?decision=&limit=&offset=` | Applications. `decision` filters the final decision |
| GET | `/junction/mortgages/{mortgage_customer_id}` | One application and its events |

`/loan/sketch` is arithmetic on a price and rate. It does not read these files and it does not approve a loan.

## Coach

`POST /coach/ask` with `{ "text": "..." }`. A customer id in the body is ignored. The card is the design sample. Refusals carry no ledger amounts.

`GET /thread`, `DELETE /thread`, and `GET /audit` are that coach session. The audit stores the intent, the tool, the time, and the account. It does not store the question.
