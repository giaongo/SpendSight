# SpendSight

Open `index.html` in a browser. The list on the left jumps to a screen. The phone opens on SpendSight. Nothing here is connected to S-Bank, and the coach does not call a model. Every figure comes from one sample ledger, the holds, or a sample ASP plan. A reply names the category, the period, and the number of payments it used. How the real app and the Java backend would be split is in `STRUCTURE.md`. `?ask=` opens the coach on that question.

The customer is Aino Korhonen, Helsinki, one person, co-op member, S-Etukortti Visa. The ledger is October, rolled up by place, and every payment from 1 to 13 November. Today in the prototype is 13 Nov. The yellow tags are the shelf-label prices: what to spend, and what to save.

## What the phone does

The MVP is SpendSight. It answers six questions, in a fixed shape: a label, one figure, one reason, then a next sentence, then the source. The questions are what kind of spender she is, what is safe this week, how much dining out would save at 20% or one or two meals a week less, when impulse payments cluster, how many days the current pace lasts, and what cancelling Fressi, Netflix, and Spotify would keep. A line under the field says answers can be wrong, and that a decision should be checked before she acts. Your data says the reading stays inside the bank, that the questions are not used to train an external model, and that a move of money waits for a confirmation. Booking a person points at the existing S-Pankki and Prisma service and does not send the chat.

Safe to spend is the balance minus bills due before payday minus the buffer: €1,184.20 − Helen €46.00 − Fressi €29.90 − €80.00 = €1,028.30. Helen left on 14 Oct, so the same day this month is before Thursday 15 Nov. Fressi left on 2 Oct and has not left in November, so it is already due. Elisa on 18 Nov, the season ticket on 20 Nov, insurance, Netflix, and December rent are after payday and stay out of that number.

Some questions stop. Other households, paying a loan off, hiding money, a request to ignore the limit, and anything that is not a payment. People like me is on the coach so that stop can be seen. A later version could use anonymised open figures, such as Statistics Finland. This version does not invent one. Home and the loan sketch stay in the list under Later. The chat will give this month’s save and will not say whether a bank should lend.

Today leads with two tags and a Small / Steady / Big control. The spend tag is €1,028.30. Steady save is €400.00. Small saves the ASP minimum, €50, and its slip line is October’s weekly pace, €83.07. Big saves €800.00 and sets the slip line at €40.00. The slip line is coffee, taxis, and meals. Since Friday, €5.60 of it has gone. Passing it sends one note. The payment is not blocked. The slip line is not the safe-to-spend number.

Open any payment and file it into another category. SpendSight uses that filing the next time it counts. October is one line per place, so the filing covers the whole line. A filing does not change whether the place earns Bonus.

The baskets screen adds a pie of the same three totals. Save shows the pile if the chosen monthly amount is set aside for 1, 3, or 5 years, with no interest added. Preparing the monthly move asks first. It does not open an account.

Save explains why €1,500, the most ASP allows in a month, is not this month’s number. A row opens S-Tili, the ASP account, S-Tuottotili, S-Määräaikaistili, or Säästäjä. Home is the first-home plan: €6,400 saved toward €22,000, 14 of 20 months, and a cushion that is a different job from the ASP account. Loan sketch drags a price and a rate. The payment updates with the finger. It is arithmetic, not an offer.

This purchase pauses an €89 Sokos jacket against the week and against Bonus. Subscriptions asks about Netflix and Fressi before treating them as wanted. Watch holds a second Bolt charge and a Prisma refund that are not in the spending total. Charts compare 1–13 November with October’s pace. The coach answers in full sentences from the same numbers, and can draw one of those charts in the thread.

Spending lists every category. A row opens the places. A place opens a sheet you can drag down. Baskets are the three kinds of payment: must every month, must when it happens, and everyday. Inside everyday, groceries stay separate from the part that slips through.

The back control and a drag from the left edge return along the path you came. Sheets follow the finger, take the release velocity, and settle. A flick closes. The dining share and the loan price and rate track the finger. Reduced motion removes the travel and keeps the change.

## The ledger

October out is €2,243.90.

- Must, every month: €1,037.90. Rent €820, bills €143.90, HSL season ticket €74. The bills include Fressi €29.90 on 2 Oct.
- Must, when it happens: €210.00. Repair €165, dentist €45.
- Everyday: €996.00. Groceries €640, dining out €186, coffee and drinks €86, taxis €45, pharmacy €24, Sokos €15.
- Of the everyday money, €356.00 slips through. Groceries are the other €640.00.

S-group places in October, the ones that earn Bonus: Prisma €400, S-market €129, Rosso €68, Picnic €24, ABC €16, Sokos €15. That is €652.00. Fressi does not earn Bonus.

November so far is €1,216.40, of which rent is €820. S-group purchases are €214.00. Spotify €12 has already left. Netflix €8 and the next Fressi €29.90 are holds, not payments yet.

## Safe to spend, safe to save

S-Tili on 13 Nov is a sample balance of €1,184.20. Salary on Thursday 15 Nov is €2,840.00. Together, €4,024.20.

Held back: December rent €820, Elisa €30, Helen €46, HSL €74, If €18, Netflix €8, Fressi €29.90, groceries still to buy €280, cushion €80. Held, €1,385.90. Left for the rest of the cycle, €2,638.30. That number runs to 15 Dec. It is not the week’s pocket money, and it is not all available to lock away.

The week’s safe-to-spend number is €1,028.30, as above. The slip is a separate line. October’s slips were €356 over 30 days, which is €83.07 for seven days. That is the Small setting. Steady, the one the phone opens on, is €64.00. Big is €40.00. Groceries are outside all three.

€400 is 14% of the salary. ASP allows €50–€1,500 in a month. €1,500 would still count as one of the 20 months, and it would take €1,100 from the €2,638.30. The cushion for a broken machine is €80. The extra stays where she can take it.

## Home, and the accounts

The sample home is €220,000. The ASP goal is 10%, €22,000. Saved so far is a sample €6,400 across 14 accepted months. At €400 a month the euros take 39 months. Six months are still needed before 20. Pauses are allowed. One large month does not skip a month.

S-Pankki’s published terms, used as terms and not as a forecast: 1% while the money stays, and another 4% when the home is bought, for the start year and up to five calendar years after that. Both are tax-free. The extra interest is paid at the purchase. Opening an ASP account happens with S-Pankki. This screen does not open one.

S-Tili is the everyday account. S-Tuottotili is savings she can take. That is where the cushion belongs. S-Määräaikaistili stays until a date. Säästäjä can send Bonus to a fund, set aside €0.50–€5 on a debit-card payment, move a monthly sum, or add extra. The fund can fall. October’s Bonus stays on S-Tili unless she sends the next one. No investment performance is drawn.

One quiet month of must-pay was €1,037.90. The cushion is €80, about half of the €165 repair. Rent is €820, 29% of the salary. No other loan is in the ledger.

The loan sketch defaults to that €220,000 home, 10% down, 4% and 25 years: €1,045.12 a month, 36.8% of salary. At 6% the same loan is €1,275.72. State interest support on an ASP loan, above 3.8% for up to 10 years, is mentioned as a term that applies only if a bank grants the loan. The city limit on an ASP loan is not looked up. The 200 mortgage applications that came with the challenge are not shown as her outcome. In that file, refusals were most often collateral, then income, equity, and debt already in place, which is why the screen refuses to say yes or no.

## A purchase, a subscription, a charge

The jacket is €89 at Sokos. It fits inside the €1,028.30 safe-to-spend number, with €939.30 left. Against the Steady slip line, €64.00, it is €25.00 over. Against Small it is €5.93 over, and against Big it is €49.00 over. Added as new S-group spending it reaches €303.00, Bonus rises €2.85, and she is €86.15 poorer. Waiting until Thursday does not pay Sokos. Taking it from the Steady slip line would leave that line at −€25.00. She can still do that. The small repeats sit beside it: coffee was 11 visits in October, Wolt was 7. The busiest dated impulse day is 12 Nov, three payments, €51.40. October’s coffee has no weekday because those lines are rolled up.

At November’s pace, excluding rent, €396.40 left over 13 days, €30.49 a day. €1,184.20 divided by that is 38 days. Rent is left out because it already left and does not repeat this month. Bills after payday are not subtracted in that division.

Fressi €29.90, Netflix €8, and Spotify €12 are the three repeating subscriptions outside the bills that keep the house running. Only Fressi has a charge and no visit. The other two have no use count, so the list is not a measured ranking of use. Stopping all three would keep €49.90 a month, €598.80 a year. Spotify already left on 5 Nov. Marking one cancelled does not remove it from the holds.

Fressi €29.90 left on 2 Oct and is due again. The ledger has the charge and no visit. Netflix €8 has not left. Marking one as cancelled does not remove it from the holds.

Watch keeps two items out of the spending total: a second Bolt of €11.80 four minutes after the 12 Nov ride, and a Prisma refund of €4.20 from 11 Nov. Marking them does not change S-Tili.

## Charts

October is counted as 30 days, the same way This week counts it. Thirteen days of that pace against 1–13 November: groceries €277.33 against €234.00, coffee €37.27 against €36.00, dining €80.60 against €88.40, taxis €19.50 against €26.00. Dining and taxis are ahead of the pace. Groceries and coffee are under it.

The same 13 days last year are not in the sample, so they are not drawn. Other households are not drawn. A later version could use anonymised open figures, such as Statistics Finland. No such figure is invented here. The transaction file in the challenge folder is a Git LFS pointer, so it was not used.

## Bonus

The step applies to the whole month once the household reaches it. Under €50 the Bonus is €0. The steps used here are 1% at €50, 1.5% at €200, 2% at €300, 2.5% at €400, 3% at €500, 3.5% at €600, 4% at €700, 4.5% at €800, and 5% at €900.

November is on the 1.5% step. €214.00 × 1.5% is €3.21. The next step is 2% at €300. The gap is €86.00: a €70 grocery shop and €16 of café visits. €300.00 × 2% is €6.00. Those €86 were going to be spent anyway. The preview shows that.

If the €86 is new spending on top of the same shops elsewhere, Bonus rises €2.79 and the person is €83.21 poorer. The screen says so. The jacket is the same kind of mistake, one size larger.

The food plan for the rest of November is €280, the same hold as on This week. With the café visits, S-group purchases reach €510, the 3% step, Bonus €15.30. Groceries alone would stop at €494, the 2.5% step, Bonus €12.35.

October closed at €652, the 3.5% step, Bonus €22.82, treated as paid on 10 Nov. €48 more at Prisma, taken from the €111 at K-Market and Lidl, would have made 4% and €28.00.

Paying the moved amount with S-Etukortti Visa adds the 0.5% payment benefit on that amount only. It is shown beside the step, not folded into it. Säästäjä is a confirmation, and the preview does not move money. October’s Bonus is already on S-Tili.

## Left as they are

No national average is invented beside Aino’s baskets. The coach’s replies are written for this ledger, not generated. Fuel is not in the month, so the ABC rows are shop and coffee, on the ordinary step, not cents per litre.
