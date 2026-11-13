# App and backend

SpendSight is the coach inside S-mobiili. `index.html` is the design of the phone. It is not the iOS app, the Android app, or the Java service. The figures in it are one sample ledger. A real service recomputes the same figures from the signed-in customer’s own payments. The model never does the arithmetic.

S-Pankki’s customer apps stay native: Swift, and the Objective-C already in the iOS app; Kotlin, and the Java already in the Android app. The hackathon screens can be React components inside a web view. Swift, Kotlin, and React call the same contract. The ledger math lives once, in the Java service beside the core. It does not live in each client, and this service does not replace the core banking APIs.

## One question

The customer asks in the coach. The phone sends the text and nothing else about identity. The session already knows who she is. A customer id in the body is ignored.

The application does not answer the chat. The Python service in `backend/` does. That answer is its own call, separate from the reads for Today, Spending, and Save.

```
POST /coach/ask
{ "text": "What is safe for me to spend this week?" }
```

The Kotlin app sends the question to that endpoint and renders the card that comes back. A sample reply kept on the phone is only a stand-in for when the service is not connected. The service does four things, in this order.

1. Classify the question.
2. If the class is refused, return the refusal. Do not read the ledger.
3. If the class is allowed, run one named tool on that customer’s ledger only.
4. Fill a fixed answer. A model may phrase the reason. It may not change the figure, and it is not given a list of tools to call on its own.

Every answer carries the same line the phone already shows: answers can be wrong, check a decision before acting, and this is not a person at the bank. Every answer names the category, the period, and the number of payments the figure used.

```
{
  "refused": false,
  "disclaimer": "Answers can be wrong. Check a decision before you act. This is not a person at the bank.",
  "source": "13 Nov · Helen and Fressi · 2 bills, plus the buffer",
  "fact": {
    "kicker": "Balance − bills before payday − buffer",
    "figure": "€1,028.30",
    "because": "€1,184.20 on S-Tili, minus Helen €46.00 and Fressi €29.90, minus a €80.00 buffer."
  },
  "next": "Bills dated after Thursday are not in this number. The card is not blocked.",
  "screen": "week"
}
```

A refusal has `"refused": true`, the line “Outside what I answer”, no fact, and no amounts from the ledger.

## What stops before any read

These classes return before a tool runs. The order is the order in the design, because a later pattern would otherwise answer a question that should have stopped.

| Class | Examples | What she hears |
| --- | --- | --- |
| Foul play | Hide money, a false application, a Bonus that was not earned | It will not help. A charge that is not hers belongs in Watch. |
| Paying a loan off | Pay off, early repayment, how to get out of a loan | It does not answer. The later sketch is a monthly payment, not an offer. |
| Other customers | People like me, same class, an average, a neighbour | Later. It would use anonymised open figures, such as Statistics Finland. It will not invent an average. |
| Manipulation | Ignore the rules, switch roles | It only answers questions about her own spending. |
| Not a payment | Health, a relationship, beliefs, a password, another person’s money | Only her payments. |

“What kind of spender am I?” is allowed. It is her payments, not a personal question. “Show me coffee against October” is allowed. “Compare me with other people” is refused, and that check runs first.

## Tools the MVP may run

Each tool reads one customer and returns numbers. The template writes the card.

| Tool | Question | Figure |
| --- | --- | --- |
| `spenderPortrait` | What kind of spender am I? | A regular payer, plus where the small payments go. October’s weekday is absent because those lines are rolled up. |
| `safeWeek` | What is safe to spend this week? | Balance, minus bills due before payday, minus the buffer. In the sample, €1,184.20 − €46.00 − €29.90 − €80.00. |
| `diningCut` | Dining minus 20%, once a week, or two meals a week less. | A fifth of October’s dining, four average meals, or eight. The reply says October had 9 payments. |
| `impulseWhen` | When do I tend to impulse spend? | The dated day with the most coffee, dining, taxi, and shopping payments. No time of day is guessed. |
| `runwayDays` | How many days until the money runs out? | Balance divided by November’s daily pace, with rent left out. Bills after payday are named as not subtracted. |
| `subscriptionCut` | What if the top 3 least-used subscriptions stop? | Fressi, Netflix, and Spotify. Only Fressi has a use signal. The reply says the other two are not a measured ranking. |

House and loan stay off this list. If she asks about saving for a house, the answer is this month’s save and a link to the later plan. It does not say whether a bank should lend, and it does not answer how to pay a loan off. Peer comparison stays off the list until an anonymised open source, such as Statistics Finland, is actually loaded. The service does not invent the average.

Charts of her own months are a later tool with the same rule: her ledger only, no last year unless that year was loaded, no other customers.

## Money is a different door

The coach has no tool that moves money, opens an account, or reads another customer.

A prepared move is its own call, and only the confirm button on Save makes it.

```
POST /transfers/draft
{ "from": "s-tili", "to": "asp", "cents": 40000 }
```

The amount is the save on her setting. Steady is €400.00. The ASP account must already exist. The call does not open one. She can cancel the draft before Thursday’s salary. Undoing it leaves the money on S-Tili. The model is not able to call this path.

Säästäjä stays a confirmation in the same way. A Bonus preview does not send the Bonus anywhere.

## How a payment gets a category

A known merchant maps to a category and a basket: must every month, must when it happens, or everyday. Groceries stay in everyday and outside the slip. A new merchant stays unfiled until a rule or the customer files it.

She can refile any payment from the payment sheet. The next tool call uses her filing. October is stored as one line per place, so a refile covers that line. Refiling does not change whether the merchant earns Bonus.

A recurring bill is the same merchant, a similar amount, and about a month between charges. The phone says why it was filed that way. “Least used” is only as strong as the ledger. A charge with no visit outranks a charge with no use count. The coach does not invent plays, visits, or hours.

## What she sets, and what the service stores

Small, Steady, and Big are a stored setting for the save, and for a separate slip line. The model does not infer them. They do not change `safeWeek`. Small saves the ASP minimum, €50, and the slip line is October’s weekly pace, €83.07. Steady saves €400.00 and the slip line is €64.00. Big saves €800.00 and the slip line is €40.00. Rent, the food plan, and the buffer do not move.

The slip line is a note. When coffee, taxis, and meals since Friday pass it, she gets one notification. The card still works. The note is sent by the app’s existing push path. The payload is that line, not a sentence the model wrote to decline the payment, and it is not the safe-to-spend figure.

The pile on Save is the monthly amount times twelve times the years she picks. Interest is not added. That demonstration stays on the screen. It is not a yield the coach promises.

## GDPR

The purpose is to answer her questions about her payments. The service reads the months on screen, the holds, her filings, and the save setting, until she asks about a wider range. It does not read other customers. Processing stays inside the bank.

Questions are not used to train an external model. The audit row is the intent class, the tool name, the time, and the account. The thread she sees can be exported or deleted. In the design it lives only in the page. A real app keeps it only as long as the thread needs, and deletion removes it.

A booking uses the customer service S-Pankki and Prisma already run. This service does not reserve a time and does not send the chat.

## EU AI Act

The phone says the coach is automated, that an answer can be wrong, and that she should check a decision before she acts. The service does not grant a loan and does not block a payment. The later loan screen is arithmetic on the price and rate she sets. It does not say approved or rejected, and it does not estimate a loan the bank has offered.

## What each client owns

The native shell owns sign-in, the session, secure storage, and push. React, if the hackathon screen is a web view, owns the layout already drawn in `index.html`: the coach thread, the fact card, the shelf tags, the three-basket pie, the confirm alert, and the sheets.

The shell and the web view both refuse to compute a bonus, a week line, or a loan payment locally. They render the cents the service returned. The coach thread is filled from `POST /coach/ask`, not from a calculation on the phone. The design file still checks its own sample with `selfCheck`, so the sample cannot drift while the screens are being drawn.
