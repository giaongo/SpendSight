# SpendSight backlog

The phone is Kotlin Multiplatform in `frontend/`. The service is Python in `backend/`. Screens match `design/my-money/`. The service owns every money figure. The app renders the cents it returns.

The signed-in customer for the demo is Aino Korhonen. The body of a request carries no customer id. A customer id in the body is ignored. Today in the demo is 13 Nov. October is one line per place. November is each payment from 1 to 13 Nov.

**Now** is the coach and Today. **Next** is the rest of her own money. **Later** is Home, the loan sketch, and anything the design keeps off. **Platform** is hosting the service and the coach model on Verda.

The oracle is `selfCheck` in `design/my-money/index.html`. A story that names a figure below is done when the service returns that figure and the screen shows it.

| Check                                              | Cents                            |
| -------------------------------------------------- | -------------------------------- |
| October out                                        | 224390                           |
| Must every month / must when it happens / everyday | 103790 / 21000 / 99600           |
| October slip / S-group                             | 35600 / 65200                    |
| October Bonus                                      | 2282                             |
| November out / S-group / Bonus                     | 121640 / 21400 / 321             |
| Holds / left after salary                          | 138590 / 263830                  |
| Safe to spend / bills before payday                | 102830 / 7590                    |
| Steady slip line                                   | 6400                             |
| Runway                                             | 38 days, 3049 a day, 15 payments |
| Impulse peak                                       | 12 Nov, 3 payments, 5140         |
| Dining fifth / one meal a week / two               | 3720 / 8268 / 16536              |
| Subscriptions kept                                 | 4990 a month, 59880 a year       |
| Jacket Bonus gain / net cost                       | 285 / 8615                       |
| Steady pile, 5 years, no interest                  | 2400000                          |
| Loan sketch at the default                         | 104512 a month; 127572 at 6%     |

## Shared

- **S-01 · One contract.** Write the request and response shapes for the routes below, and keep the app and the service on that file. Every coach answer has `refused`, `disclaimer`, `source`, and, when allowed, `fact` (`kicker`, `figure`, `because`), `next`, and `screen`.
- **S-02 · Demo session.** One signed-in session for Aino. The app sends the question text only. The service reads her ledger, her filings, her holds, and her save setting.

## Now · mobile

- **APP-01 · App shell.** Kotlin Multiplatform project for Android and iOS. Four tabs: Today, Spending, Save, SpendSight. A stack for screens pushed from a tab. Back, and a drag from the left edge, pop that stack. Sheets follow the finger and flick closed. Reduced motion skips the travel and keeps the change. Colours, type, and the yellow shelf tags follow the design.
- **APP-02 · Today.** Two tags and Small / Steady / Big. Opens on Steady: safe to spend €1,028.30, save €400.00. The slip line is coffee, taxis, and meals. Since Friday, €5.60 of the Steady line (€64.00) is gone. Small is the ASP minimum €50, slip €83.07. Big saves €800.00, slip €40.00. Groceries stay outside the slip. Copy states that passing the line sends one note and does not block the card.
- **APP-03 · SpendSight thread.** Composer, disclaimer under the field, and the coach’s opening line. A reply is the fixed card: label, one figure, one reason, a next sentence, the source. The thread can open the screen named in `screen`. `?ask=` in the design is a deep link that opens SpendSight on that question.
- **APP-04 · Six fact screens.** Spender, This week, Dining out, Impulse, Days left, Subscriptions. Each screen shows the figure the service returned for that tool. Dining has the 20% control and the one-meal and two-meal readings.
- **APP-05 · Refusals and the boundary line.** A refused answer shows “Outside what I answer”, no fact, and no ledger amounts. Your data states that the reading stays inside the bank, that questions are not used to train an external model, and that a move of money waits for a confirmation. Booking a person points at the existing S-Pankki and Prisma service and does not send the chat.

## Now · backend

- **API-01 · Service and the sample ledger.** Python service. Load Aino’s October lines, November payments, holds, balance €1,184.20, and salary €2,840.00 on Thursday 15 Nov. The checks in the table above are tests. The Junction transaction file stays out of this load.
- **API-02 · Filing rules.** A known merchant maps to a category and a basket: must every month, must when it happens, or everyday. Groceries stay in everyday and outside the slip. A new merchant stays unfiled until a rule or the customer files it. October is one line per place, so a refile covers that line. A refile does not change Bonus. A recurring bill is the same merchant, a similar amount, and about a month between charges.
- **API-03 · `POST /coach/ask`.** Classify, then either refuse or run one named tool, then fill the fixed answer. Order of refusal: foul play, paying a loan off, other customers, manipulation, not a payment. “What kind of spender am I?” and “Show me coffee against October” are allowed. “Compare me with other people” is refused. The model may phrase `because`. It may not change the figure, choose a tool, or see a tool list.
- **API-04 · The six tools.** `spenderPortrait`, `safeWeek`, `diningCut`, `impulseWhen`, `runwayDays`, `subscriptionCut`. Each reads this customer only. `safeWeek` is balance minus bills due before payday minus the €80 buffer: €1,184.20 − Helen €46.00 − Fressi €29.90 − €80.00. Elisa, the season ticket, insurance, Netflix, and December rent stay out. `runwayDays` leaves rent out of the pace and names bills after payday. `subscriptionCut` is Fressi, Netflix, and Spotify, and says only Fressi has a use signal.
- **API-05 · Save setting.** Store Small, Steady, or Big. The setting changes the save amount and the slip line. It does not change `safeWeek`. Rent, the food plan, and the buffer stay put. Steady is the default.
- **API-06 · Audit.** Store the intent class, the tool name, the time, and the account. Support export and delete of the thread she sees. Questions are not sent out as training data.

## Next · mobile

- **APP-06 · Spending.** Category list, a place list, and a payment sheet that drags down. The sheet explains the basket and whether the place earns Bonus. She can refile the line. The next screen uses the filing the service stored.
- **APP-07 · Baskets.** Three totals and a pie: must every month €1,037.90, must when it happens €210.00, everyday €996.00. Inside everyday, groceries €640.00 stay apart from the €356.00 that slips through.
- **APP-08 · Charts.** 1–13 November against October’s 13-day pace. Groceries €234.00 vs €277.33, coffee €36.00 vs €37.27, dining €88.40 vs €80.60, taxis €26.00 vs €19.50. The same 13 days last year are absent, so they are not drawn.
- **APP-09 · Save.** The pile is the monthly amount times twelve times 1, 3, or 5 years, with no interest. Explain why €1,500, the ASP monthly cap, is not this month’s number. Rows open S-Tili, the ASP account, S-Tuottotili, S-Määräaikaistili, and Säästäjä. Preparing the monthly move asks first. Confirm sends the draft. Cancel leaves the money on S-Tili. The screen does not open an account.
- **APP-10 · This purchase.** The €89 Sokos jacket against the week (€939.30 left), against each slip line, and against Bonus. She can still take it from the slip line.
- **APP-11 · Bonus.** November is on the 1.5% step, €3.21. The next step is 2% at €300, gap €86. The preview says those €86 of new spend would raise Bonus €2.79 and leave her €83.21 poorer. The 0.5% S-Etukortti Visa benefit sits beside the step. Säästäjä is a confirmation. October’s Bonus stays on S-Tili.
- **APP-12 · Subscriptions.** Netflix and Fressi are asked about before they count as wanted. Marking one cancelled does not remove it from the holds. Spotify already left on 5 Nov.
- **APP-13 · Watch.** The second Bolt of €11.80 and the Prisma refund of €4.20 stay out of the spending total. Marking them does not change S-Tili.
- **APP-14 · Slip note.** When coffee, taxis, and meals since Friday pass the slip line, the shell sends one push. The payload is that line. The card still works.

## Next · backend

- **API-07 · Spending reads.** Categories, places, and one payment, with basket, Bonus, and the filing reason. `POST` a refile. The next tool call uses it.
- **API-08 · Baskets, charts, purchase, Bonus.** One read each, using the oracle figures. Charts are her months only. Bonus steps: 0 under €50, then 1% at €50, 1.5% at €200, 2% at €300, 2.5% at €400, 3% at €500, 3.5% at €600, 4% at €700, 4.5% at €800, 5% at €900. The Visa 0.5% is a separate line on the moved amount.
- **API-09 · Save reads and the draft.** Return the three efforts, the pile, and the account copy. `POST /transfers/draft` with `from`, `to`, and `cents`. The amount is the save on her setting. The ASP account must already exist. Confirm and cancel are their own calls. The coach cannot call this path.
- **API-10 · Subscriptions and Watch.** Return the three subscriptions and the two watch items. A cancelled subscription stays in the holds. A watch mark stays out of the spending total and does not change the balance.

## Later

- **APP-15 · Home.** €6,400 saved toward €22,000, 14 of 20 months. The cushion is a different job from the ASP account. At €400 a month the euros take 39 months. Six months are still needed before 20. No lending decision.
- **APP-16 · Loan sketch.** Price and rate follow the finger. Default €220,000, 10% down, 4%, 25 years: €1,045.12 a month. At 6%, €1,275.72. The screen states the terms it uses and does not say approved or rejected.
- **API-11 · Home plan.** The ASP progress, the cushion, and the month count. Interest terms are labels, not a forecast. The call does not open an account and does not say whether a bank should lend.
- **API-12 · Loan arithmetic.** Price, deposit, rate, and years in. Monthly payment out. No offer, no refusal, no payoff plan. The mortgage files in `Junction-2026-S-pankki/` are not her outcome.
- **API-13 · Wider data.** Anonymised open figures, such as Statistics Finland, only after a source is loaded. Last year only after that year is loaded. Until then the coach refuses peer comparison and the chart omits the empty series.

## Platform · Verda

The service runs on one Verda CPU instance with Docker Compose: Caddy for HTTPS, and FastAPI with the SQLite index on the instance’s disk. The coach’s wording comes from an open-weight model on a Verda serverless GPU container in the EU, which only the service calls. Python keeps every figure, tool choice, and refusal. If the model is down, the coach falls back to today’s fixed wording. The two data worlds stay apart: the coach and the model never read the Junction dataset, and Aino’s sample ledger stays in code. GitHub Actions tests every push and deploys `master`. Terraform creates the VM.

- **OPS-01 · Verda VM in Terraform.** `infra/terraform/` uses Terraform, pinned with `required_version` (for example `>= 1.6`), and the `verda-cloud/verda` provider (`~> 1.0`). Commit `.terraform.lock.hcl`. Resources: `verda_ssh_key` for each team member, and one `verda_instance` of a CPU type with about 4 vCPU and 16 GB RAM, Ubuntu 24.04, on-demand rather than spot, with a disk of about 100 GB so `/data` fits on it. Look up the CPU type, image, and location codes in the Verda API and keep them in `variables.tf`, not hard-coded. The `startup_script` (`infra/terraform/startup.sh`) installs Docker with the Compose plugin, creates `/data`, and clones the repo. It is safe to run twice. Outputs: the instance id and public IP, which feed the Caddy name in OPS-03 and the deploy host in OPS-09. SSH by key only. Port 22 opens to the team’s IPs, and 80/443 to everyone, or only to the team if OPS-05 says so. Changing the instance type or image replaces the VM, so `/data` must be re-synced with OPS-04 after that. - DONE
- **OPS-02 · Container image.** `backend/Dockerfile` builds from `python:3.12-slim` with `uv sync --frozen`, runs as a non-root user, and starts uvicorn on `0.0.0.0:8000` over plain HTTP with one worker, because `Session` holds Aino’s demo session in memory. `app/__main__.py` keeps the self-signed HTTPS mode for local runs. Add a `SPENDSIGHT_CACHE_DIR` setting next to `JUNCTION_DIR` in `app/junction_db.py`, so the index can live on `/data`. Add `.dockerignore` so `.venv`, `.cache`, and the CSVs stay out of the image. Add a `/healthz` route that reports whether the Junction index is ready. - DONE
- **OPS-03 · Compose and Caddy.** `infra/docker-compose.yml` has two services. `api` uses the OPS-02 image, mounts `/data/junction` and `/data/cache`, and sets `JUNCTION_DIR` and `SPENDSIGHT_CACHE_DIR`. `caddy` listens on 80/443 and proxies to `api:8000`. The `Caddyfile` names `api.<domain>`, or an `sslip.io` name on the instance IP, and Caddy gets the Let’s Encrypt certificate itself. Both services restart on boot. Secrets such as the model token live in `/data/.env` on the instance, never in Git.
- **OPS-04 · Data on the instance.** `rsync -t` the four CSVs to `/data/junction` and the local `junction.sqlite` to `/data/cache`, so the index stays newer than the CSV and the service does not re-index on first boot. The CSVs are never in the image or in Git. If the index is lost, the service rebuilds it from the CSVs on start. That is the backup.
- **OPS-05 · Access to the dataset.** `/customers/*` serves S-Pankki data with no auth. Before the address is public, check the Junction data terms. If public hosting is not allowed, keep the OPS-01 IP allowlist, or require a shared API key header that the app sends and Caddy checks.
- **OPS-06 · Coach model on Verda Containers.** Deploy an open-weight instruct model, small enough for one GPU, on a Verda serverless container in an EU location. Serve it with vLLM’s OpenAI-compatible API, with request logging off, so question text is not kept in the container logs. Scale to zero when idle. The endpoint’s bearer token lives only in `/data/.env` and GitHub Secrets, never in the app. Warm the container before judging, because a cold start can take minutes.
- **OPS-07 · Coach calls the model.** Follows API-03. After `app/coach.py` has classified the question, chosen the tool, and computed the figure, it may send the model the question and that one tool’s result, and ask for one `because` sentence. The model never sees the ledger, a tool list, the Junction data, or another customer. It may also suggest an intent, which counts only when the Python classifier agrees. A refusal never calls the model. The service rejects a reply that has a euro amount or number not in the tool result, is too long, or times out after about 3 seconds, and then uses the fixed `because`. `COACH_MODEL_URL` unset means fixed wording only, which is how tests and local runs work. The audit still stores intent, tool, time, and account, never the question text. `test_oracle.py` stays green because figures never come from the model.
- **OPS-08 · CI.** GitHub Actions on every push and pull request. Backend: `uv sync --extra dev`, ruff, then pytest with `COACH_MODEL_URL` unset. Tests use a small fixture subset of the Junction CSVs in `backend/tests/fixtures/`, because the real dataset is gitignored. `test_oracle.py` must pass. A test covers the OPS-07 fallback with a fake model that times out, invents a number, or rambles. Frontend: `./gradlew :shared:allTests` and `:androidApp:assembleDebug` on JDK 17. The Docker image builds on every run, so a broken Dockerfile fails the PR.
- **OPS-09 · CD.** On `master`, after CI passes, a deploy job SSHes to the instance with a deploy key from GitHub Secrets and runs `git pull && docker compose up -d --build`. It then checks `/healthz` and one oracle route through Caddy. If the check fails, it checks out the previous commit and runs Compose again. Deploys go through a GitHub environment so they can be paused during judging.
- **OPS-10 · App base URL.** `defaultBaseUrl()` in each platform’s `Http.kt` takes the deployed URL from a build setting. Debug builds keep `10.0.2.2` and `127.0.0.1`. The self-signed trust stays limited to loopback and private LAN addresses. Caddy’s certificate needs no special trust.
- **OPS-11 · Terraform state and CI.** State goes in a shared remote backend with locking, such as an S3-compatible bucket or Terraform Cloud, never in Git. `VERDA_CLIENT_ID` and `VERDA_CLIENT_SECRET` stay in GitHub Secrets and each person’s shell, never in `.tf` files. On a pull request that touches `infra/terraform/`, CI runs `terraform fmt -check`, `validate`, and `plan`, and posts the plan as a comment. `apply` is a manual workflow behind the OPS-09 environment, so the VM is never replaced by accident. The OPS-06 model container can be added to the same Terraform later.
- **OPS-12 · Runbook and cost.** `infra/README.md` covers first setup, `docker compose logs`, restart, redeploy an older commit, re-sync the data, warm the model, and switch the model off by unsetting `COACH_MODEL_URL`. After judging, stop the instance and delete the container, or run `terraform destroy`, to stop the bill.

## Out of this backlog

Foul play, paying a loan off, other customers, manipulation, and questions that are not about her payments stop before a ledger read. The service does not move money, open an account, block a card, grant a loan, or read another customer. House and loan stay off the coach’s tool list. A question about saving for a house gets this month’s save and a link to Home.
