package fi.spendsight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenBody(store: SpendSightStore, modifier: Modifier = Modifier) {
    if (store.current == Screen.Coach) {
        CoachScreen(store, modifier)
        return
    }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val screen = store.current) {
            Screen.Today -> TodayScreen(store)
            Screen.Spending -> SpendingScreen(store)
            is Screen.Category -> CategoryScreen(store, screen.name)
            Screen.Baskets -> BasketsScreen(store)
            Screen.Charts -> ChartsScreen(store)
            Screen.Spender -> SpenderScreen(store)
            Screen.Week -> WeekScreen(store)
            Screen.Cut -> CutScreen(store)
            Screen.Save -> SaveScreen(store)
            Screen.Home -> HomeScreen(store)
            Screen.Loan -> LoanScreen(store)
            Screen.Purchase -> PurchaseScreen(store)
            Screen.Bonus -> BonusScreen(store)
            Screen.Subs -> SubsScreen(store)
            Screen.Watch -> WatchScreen(store)
            Screen.Impulse -> ImpulseScreen(store)
            Screen.Runway -> RunwayScreen(store)
            Screen.Coach -> Unit
        }
    }
}

@Composable
private fun TodayScreen(store: SpendSightStore) {
    val today = store.customer
    Eyebrow("${today.customerId} · ${today.postalCode} · age ${today.age}")
    Hero(today.balance)
    Lede(today.note)
    CardBox {
        HoldRow("As of", today.accountId, today.asOf)
        HoldRow("Latest salary", today.latestSalaryDate ?: "No salary in the file", today.latestSalaryCents?.let { eur(it) } ?: "—")
        HoldRow("Money in, ${today.month}", "Not part of spending", eur(today.monthInCents))
        HoldRow("Money out, ${today.month}", "Left the account", eur(today.monthOutCents))
        SumRow("Transactions on file", today.transactionCount.toString())
    }
    Note("From ${today.firstDate} to ${today.asOf}. ${today.transactionCount} transactions.")
    if (today.mortgageCustomerId == null) {
        Note("No mortgage id on this customer.")
    } else {
        Note("Mortgage ${today.mortgageCustomerId}. Decision: ${today.mortgageDecision ?: "none"}.")
    }
    TextLink("Open ${today.month}") { store.push(Screen.Spending) }
    TextLink("Open saving") { store.push(Screen.Save) }
    TextLink("Ask the coach") { store.push(Screen.Coach) }
}

@Composable
private fun SpendingScreen(store: SpendSightStore) {
    val spending = store.spendingView
    val newest = store.newestSpendMonth
    Eyebrow(spending.customerId)
    Hero(eur(spending.totalOutCents))
    Lede(spending.note)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextLink("Earlier") { store.loadSpending(shiftMonth(spending.month, -1)) }
        Text(spending.month, color = Ink, fontWeight = FontWeight.SemiBold)
        if (newest == null || spending.month < newest) {
            TextLink("Later") { store.loadSpending(shiftMonth(spending.month, 1)) }
        } else {
            Note("Latest")
        }
    }
    CardBox {
        HoldRow("Money in", "Salary and other credits", eur(spending.totalInCents))
        SumRow("${spending.lineCount} lines", eur(spending.totalOutCents))
    }
    spending.categories.forEach { cat ->
        ChevronRow(cat.name, "${cat.count} · ${cat.amount}") { store.push(Screen.Category(cat.name)) }
    }
}

@Composable
private fun CategoryScreen(store: SpendSightStore, name: String) {
    val live = store.ledger?.spending?.categories?.any { it.name == name } == true
    if (live) {
        CustomerCategory(store, name)
        return
    }
    val rows = store.rows().filter { it.category == name }
    val total = rows.sumOf { it.amountCents }
    val yes = rows.filter { it.earnsBonus }.sumOf { it.amountCents }
    val no = total - yes
    val bonusLine = when {
        yes > 0 && no > 0 -> "${eur(yes)} earns Bonus. ${eur(no)} does not."
        yes > 0 -> "All of this earns Bonus."
        else -> "None of this earns Bonus."
    }
    val basket = rows.firstOrNull()?.basket ?: Basket.Everyday
    Eyebrow("${basket.title} · ${if (store.month == Month.Oct) "October" else "1–13 Nov"}")
    Hero(eur(total))
    Lede(bonusLine)
    MonthPicker(store)
    if (rows.isEmpty()) {
        Note("No payments in this category.")
    } else {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().clickable { store.sheet = Sheet.PaymentSheet(row.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (row.earnsBonus) Green else Line))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.place, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("${row.detail}${if (row.whenLabel.isNotEmpty()) " · ${row.whenLabel}" else ""}", color = Secondary, fontSize = 13.sp)
                }
                Text(row.shownAmount(), color = Ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CustomerCategory(store: SpendSightStore, name: String) {
    val spending = store.spendingView
    val rows = spending.transactions.filter { it.category == name }
    val total = rows.sumOf { it.amountCents }
    Eyebrow("${spending.month} · ${spending.customerId}")
    Hero(eur(total))
    Lede("${rows.size} lines in $name. Amounts are the ones the service sent.")
    if (rows.isEmpty()) {
        Note("No lines in this category for ${spending.month}.")
    } else {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.merchantId ?: row.transactionType, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("${row.txDate} · ${row.transactionType}", color = Secondary, fontSize = 13.sp)
                }
                Text(row.amount, color = Ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BasketsScreen(store: SpendSightStore) {
    SampleBanner()
    Lede("Every payment sits in one basket. The first two have to be paid. The third is where a choice still exists.")
    MonthPicker(store)
    val parts = Basket.entries.map { basketColor(it) to store.basketSum(store.month, it) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pie(parts)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { BasketLegend(store, store.month) }
    }
    Basket.entries.forEach { basket ->
        CardBox {
            Eyebrow(basket.title)
            Hero(eur(store.basketSum(store.month, basket)), 32)
            Lede(basket.line)
            if (basket == Basket.Everyday) {
                Spacer(Modifier.height(8.dp))
                StackBar(listOf(Green to store.catSum(store.month, "Groceries"), Slip to store.slip(store.month)))
                Spacer(Modifier.height(6.dp))
                Note("Groceries ${eur(store.catSum(store.month, "Groceries"))} · slips through ${eur(store.slip(store.month))}")
            }
            if (basket == Basket.Regular && store.month == Month.Nov) {
                Spacer(Modifier.height(8.dp))
                Note("Bills due before payday are on This week. Later holds are one total there, not separate lines here.")
            }
            store.categories().filter { it.basket == basket }.forEach { cat ->
                ChevronRow(cat.name) { store.push(Screen.Category(cat.name)) }
            }
            if (store.categories().none { it.basket == basket }) {
                Note("Nothing in this basket for this month. October’s “must, when it happens” was ${eur(store.basketSum(Month.Oct, Basket.Irregular))}.")
            }
        }
    }
}

@Composable
private fun Pie(parts: List<Pair<Color, Int>>) {
    Canvas(Modifier.size(112.dp)) {
        val total = parts.sumOf { it.second }.coerceAtLeast(1).toFloat()
        var start = -90f
        parts.forEach { (color, amount) ->
            if (amount <= 0) return@forEach
            val sweep = amount / total * 360f
            drawArc(color, start, sweep, useCenter = true, topLeft = Offset.Zero, size = Size(size.width, size.height))
            start += sweep
        }
    }
}

@Composable
private fun ChartsScreen(store: SpendSightStore) {
    val charts = store.ledger?.charts ?: return
    val rows = charts.rows.map { it.toRow() }
    val dining = rows.find { it.label == "Dining out" }
    val taxis = rows.find { it.label == "Taxis" }
    SampleBanner()
    Eyebrow("1–13 November against October")
    Hero("Your own pace.", 34)
    Lede("Grey is October stretched to 13 days. Green is what has actually left. Both bars are the service’s figures.")
    ChartBlock(rows)
    Note("Grey is October’s pace. Green is 1–13 November.")
    if (dining != null && taxis != null) {
        Lede("Dining is ${eur(dining.novemberCents)} against a pace of ${eur(dining.octoberCents)}. Taxis are ${eur(taxis.novemberCents)} against ${eur(taxis.octoberCents)}.")
    }
    Note(charts.note)
    TextLink("Ask for a different chart") { store.push(Screen.Coach) }
}

@Composable
private fun SpenderScreen(store: SpendSightStore) {
    val spender = store.ledger?.spender ?: return
    val impulse = store.ledger?.impulse ?: return
    val peak = impulse.peak
    SampleBanner()
    Eyebrow("From October, the last full month")
    Hero("A regular payer.")
    Lede("Most of the month leaves on a schedule. What slips through goes out in small payments, often at the same places.")
    CardBox {
        StackBar(
            listOf(
                basketColor(Basket.Regular) to spender.mustEveryMonthCents,
                basketColor(Basket.Irregular) to spender.mustWhenItHappensCents,
                basketColor(Basket.Everyday) to spender.everydayCents,
            ),
        )
        BasketLegend(store, Month.Oct)
    }
    Lede("${eur(spender.mustEveryMonthCents)} left on a schedule: rent, bills, and the season ticket.")
    Lede("${eur(spender.mustWhenItHappensCents)} had to be paid when it happened.")
    Lede("${eur(spender.everydayCents)} was everyday. Groceries were ${eur(store.catSum(Month.Oct, "Groceries"))}. The slip was ${eur(spender.slipCents)}.")
    CardBox {
        Eyebrow("Where the small ones go")
        Spacer(Modifier.height(6.dp))
        Lede("Coffee was ${impulse.coffeeVisits} visits. Wolt was ${impulse.woltOrders} orders. October rolls those up by place, so it has no weekday. In November the busiest dated day is ${peak.whenLabel}: ${peak.places.joinToString(", ")}, ${eur(peak.amountCents)}.", strong = true)
    }
    Note(counted("October", "Three baskets", spender.lines, "lines") + ". Groceries and dining are rolled up by place.")
    TextLink("What is safe this week") { store.push(Screen.Week) }
    TextLink("When the impulse payments land") { store.push(Screen.Impulse) }
    TextLink("If dining out drops by a fifth") { store.push(Screen.Cut) }
}

@Composable
private fun WeekScreen(store: SpendSightStore) {
    val week = store.ledger?.week ?: return
    val effort = store.effortRow
    val summary = store.ledger?.summary ?: return
    SampleBanner()
    Eyebrow("Until payday")
    Hero(week.safeToSpend)
    Lede("Balance, minus bills due before payday, minus a buffer. The service returns this figure. Groceries still to buy are a plan, so they are not in it.")
    CardBox {
        week.bills.forEach { HoldRow(it.name, it.detail, "−${eur(it.amountCents)}") }
        HoldRow("Buffer", "Kept back from this week", "−${eur(week.bufferCents)}")
        SumRow("Safe to spend", week.safeToSpend)
    }
    Note(counted("This week", week.bills.joinToString(" and ") { it.name }, week.bills.size, "bills") + ", plus the buffer. ${week.note}")
    Section("A separate line for what slips")
    EffortPicker(store)
    CardBox {
        HoldRow("${effort.name} · coffee, taxis, meals", "A separate line", effort.slip)
        Note("Passing this line sends one note. It does not block the card, and it does not change the ${week.safeToSpend} above. The matching save is ${effort.save}.")
    }
    Section("After payday")
    CardBox {
        HoldRow("Every hold", "The service returns the total, not each later bill.", "−${eur(summary.holdsCents)}")
        SumRow("Left after salary and every hold", eur(summary.leftAfterSalaryCents))
    }
    Note("That last figure is the design sample: balance plus salary, minus every hold. It is not this week’s safe-to-spend number, and it is not the customer on Today.")
    TextLink("How many days the pace lasts") { store.push(Screen.Runway) }
    TextLink("What is safe to save") { store.push(Screen.Save) }
}

@Composable
private fun CutScreen(store: SpendSightStore) {
    val dining = store.ledger?.dining ?: return
    val saved = diningWhatIf(dining.diningCents, dining.fifthCents, store.cutPercent)
    SampleBanner()
    Eyebrow("If October’s dining out is lower")
    Hero(eur(saved))
    Lede(if (store.cutPercent == 20) "kept in a month. This is the service’s one-fifth figure." else "kept in a month, as a what-if on the dining total. Drag to choose the share.")
    Slider(
        value = store.cutPercent / 50f,
        onValueChange = { store.cutPercent = (it * 50f).toInt().coerceIn(0, 50) },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Note("0%")
        Text("${store.cutPercent}%", color = Ink, fontWeight = FontWeight.SemiBold)
        Note("50%")
    }
    Lede("${eur(saved)} a month · ${eur(saved * 12)} a year")
    Lede(
        if (saved <= dining.woltCents) {
            "This can come from Wolt, which was ${eur(dining.woltCents)}. Rosso stays, and Rosso earns Bonus."
        } else {
            "This is more than Wolt, which was ${eur(dining.woltCents)}. Part of it would be Rosso, and Rosso is the dining that earns Bonus."
        },
    )
    CardBox {
        HoldRow("Once a week, not twice", "From ${dining.orders} October payments", eur(dining.onceCents))
        HoldRow("Two meals a week less", "From the same ${dining.orders} payments.", eur(dining.twiceCents))
    }
    CardBox {
        HoldRow("Wolt", "Does not earn Bonus", eur(dining.woltCents))
        HoldRow("Rosso", "Earns Bonus", eur(dining.rossoCents))
    }
    TextLink("What Rosso does for the step") { store.push(Screen.Bonus) }
}

@Composable
private fun SaveScreen(store: SpendSightStore) {
    val save = store.customerSave
    Eyebrow(save.customerId)
    Hero(save.deposited)
    Lede(save.note)
    Segmented(listOf(1, 3, 5).map { n -> "$n ${if (n == 1) "year" else "years"}" to (save.years == n) }) { index ->
        store.loadCustomerSave(listOf(1, 3, 5)[index])
    }
    CardBox {
        HoldRow("Window", "${save.windowStart} – ${save.windowEnd}", "${save.deposits} deposits")
        HoldRow("Latest deposit", save.latestDepositDate ?: "None in this window", save.latestDepositCents?.let { eur(it) } ?: "—")
        HoldRow("Balance", save.accountId, eur(save.balanceCents))
        SumRow("Deposited", save.deposited)
    }
    Section("Where a euro can sit")
    accountCopy.forEach { account ->
        ChevronRow(account.title, account.subtitle) { store.sheet = Sheet.Account(account.id) }
    }
}

@Composable
private fun HomeScreen(store: SpendSightStore) {
    val bonus = store.ledger?.bonus ?: return
    val buffer = store.ledger?.week?.bufferCents
    SampleBanner()
    Eyebrow("No home plan on the service")
    Hero("Not in this reading", 34)
    Lede("The service does not return ASP savings, the goal, or how many months are counted for this customer. This screen does not invent them.")
    Note(store.settings.note)
    CardBox {
        Row {
            Column(Modifier.weight(1f)) {
                Note("Buffer in the design sample")
                Text(if (buffer == null) "—" else eur(buffer), color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Column(Modifier.weight(1f)) {
                Note("October Bonus")
                Text(eur(bonus.octoberBonusCents), color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Note("October’s Bonus is already on S-Tili. Sending the next one to Säästäjä is a preview on this phone. It does not move money.")
    }
    PrimaryButton("Sketch a loan") { store.push(Screen.Loan) }
    TextLink("Why the save stays inside the ASP range") { store.push(Screen.Save) }
}

@Composable
private fun LoanScreen(store: SpendSightStore) {
    val loan = store.ledger?.loan ?: return
    val price = store.loanPriceEuro
    val rate = store.loanRateTenths
    val matches = store.loanMatches()
    SampleBanner()
    Eyebrow(if (matches) "Arithmetic, not an offer" else "Updating the sketch")
    Hero(if (matches) loan.monthly else "…", 44)
    Lede(if (matches) loan.note else "Waiting for the service to count this price and rate.")
    Section("Home price")
    Lede(eur(price * 100), strong = true)
    Slider(
        value = (price - 150_000) / 250_000f,
        onValueChange = {
            store.homeEuro = (150_000 + it * 250_000).toInt()
            store.scheduleLoan()
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Note("€150,000")
        Note("€400,000")
    }
    Section("Rate, if it stayed this number")
    Lede("${rate / 10}.${rate % 10}% · 25 years", strong = true)
    Slider(
        value = (rate - 20) / 40f,
        onValueChange = {
            store.rateTenths = (20 + it * 40).toInt()
            store.scheduleLoan()
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Note("2%")
        Note("6%")
    }
    if (matches) {
        Note("At 6%, the same loan is ${loan.stress6pct} a month.")
        CardBox {
            Lede("The 10% down payment on this price is ${eur(loan.downCents)}. The sketch uses that, not today’s balance. ${loan.note}", strong = true)
        }
    }
    Note("Nothing here is an application. The chat does not answer how to pay a loan off.")
}

@Composable
private fun PurchaseScreen(store: SpendSightStore) {
    val buy = store.ledger?.purchase ?: return
    val effort = store.effortRow
    val safe = store.ledger?.week?.safeToSpend ?: ""
    val note = when (store.purchaseChoice) {
        "wait" -> "You chose to wait. The jacket is still ${eur(buy.priceCents)}. You can change this. This preview does not pay Sokos."
        "take" -> "You chose to take it from the slip line. It is ${eur(buy.overSlipCents)} over ${effort.slip}. This preview does not pay Sokos."
        "skip" -> "You left it. The slip line stays ${effort.slip}."
        else -> "This pause is for the one payment in front of you. It does not pay Sokos."
    }
    SampleBanner()
    Eyebrow("Sokos · not paid")
    Hero(eur(buy.priceCents))
    Lede("Safe to spend on the design sample is $safe, so the jacket fits inside it with ${eur(buy.leftInWeekCents)} left. The slip line is ${effort.slip}, and the jacket is ${eur(buy.overSlipCents)} over that line. Paying this as new money takes S-group purchases to ${eur(buy.sgroupWithJacketCents)}. Bonus rises by ${eur(buy.gainCents)}. You would be ${eur(buy.lossCents)} poorer.")
    Note(note)
    PrimaryButton("Wait until payday", store.purchaseChoice == "wait") { store.purchaseChoice = "wait" }
    ChoiceButton("Take it from the slip line", store.purchaseChoice == "take") { store.purchaseChoice = "take" }
    ChoiceButton("Not this one", store.purchaseChoice == "skip") { store.purchaseChoice = "skip" }
    TextLink("The shop that does raise Bonus") { store.push(Screen.Bonus) }
}

@Composable
private fun BonusScreen(store: SpendSightStore) {
    val bonus = store.ledger?.bonus ?: return
    val gap = bonus.gapCents
    val nextAt = bonus.nextStepCents
    val nextBonus = bonus.bonusAtNextCents
    val hereCents = bonus.steps.filter { it.atCents <= bonus.novemberSgroupCents }.maxOfOrNull { it.atCents }
    val mark = if (store.bonusPreview) nextAt else hereCents
    val heroCents = if (store.bonusPreview && nextBonus != null) nextBonus else bonus.novemberBonusCents
    SampleBanner()
    Eyebrow("November")
    Hero(eur(heroCents))
    Lede(
        if (store.bonusPreview && gap != null && bonus.nextRatePct != null) {
            "Bonus if the next ${eur(gap)} stays at S-group. Step ${formatPct(bonus.nextRatePct)}%."
        } else {
            "Bonus so far. Step ${formatPct(bonus.novemberRatePct)}% on ${eur(bonus.novemberSgroupCents)} at S-group."
        },
    )
    if (gap != null && nextBonus != null && nextAt != null) {
        CardBox {
            Eyebrow("Use the green card for ${eur(gap)} more")
            Spacer(Modifier.height(4.dp))
            Lede("With the grocery shop and the café visits, S-group purchases reach ${eur(bonus.weekAtCents)}. Bonus becomes ${eur(nextBonus)}. The step covers the whole amount, including what you already bought.", strong = true)
        }
        ChoiceButton("Preview that ${eur(gap)} on the green card", store.bonusPreview) {
            store.bonusPreview = !store.bonusPreview
        }
        if (bonus.visaOnGapCents != null) {
            Note("Pay the ${eur(gap)} with S-Etukortti Visa and the payment benefit adds ${eur(bonus.visaOnGapCents)} on top. ${bonus.note}")
        }
        if (bonus.gainCents != null && bonus.lossCents != null) {
            Note("If that ${eur(gap)} is extra shopping, you pay ${eur(gap)} and Bonus rises by ${eur(bonus.gainCents)}. You would be ${eur(bonus.lossCents)} poorer. The coach keeps the shop you already need, and moves the place.")
        }
    }
    CardBox {
        bonus.steps.asReversed().forEach { step ->
            val here = step.atCents == mark
            val note = when {
                here && store.bonusPreview -> "If the next ${eur(gap ?: 0)} is at S-group"
                step.atCents == hereCents -> "You are here · ${eur(bonus.novemberSgroupCents)}"
                step.atCents == nextAt && gap != null -> "${eur(gap)} to this step"
                else -> ""
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (here) Green else Fill))
                Text("${formatPct(step.pct)}%", Modifier.padding(horizontal = 10.dp).width(48.dp), color = Ink, fontWeight = FontWeight.Bold)
                Text(note, Modifier.weight(1f), color = Secondary, fontSize = 12.sp)
                Text(eur(step.atCents), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
    CardBox {
        Eyebrow("If the grocery plan and the café visits stay there")
        Spacer(Modifier.height(6.dp))
        Lede("Together with today’s ${eur(bonus.novemberSgroupCents)}, November reaches ${eur(bonus.monthAtCents)}. Bonus at that point is ${eur(bonus.monthBonusCents)}.", strong = true)
        Lede("The groceries alone would land at ${eur(bonus.foodOnlyCents)}, Bonus ${eur(bonus.foodOnlyBonusCents)}.")
    }
    CardBox {
        Eyebrow("October already closed")
        Spacer(Modifier.height(6.dp))
        Lede("S-group purchases were ${eur(bonus.octoberSgroupCents)}, step ${formatPct(bonus.octoberRatePct)}%, Bonus ${eur(bonus.octoberBonusCents)}.", strong = true)
    }
    ChevronRow(
        if (store.saver) "Next Bonus goes to Säästäjä" else "Send the next Bonus to Säästäjä",
        if (store.saver) "You can keep it on S-Tili instead" else "October’s ${eur(bonus.octoberBonusCents)} is already on S-Tili",
        store::toggleSaver,
    )
}

@Composable
private fun SubsScreen(store: SpendSightStore) {
    val subs = store.ledger?.subs ?: return
    val marked = subs.items.filter { store.subs[it.id] == "cancel" }
    SampleBanner()
    Eyebrow("If the repeating ones stop")
    Hero(eur(subs.monthCents))
    Lede("a month, ${eur(subs.yearCents)} across a year. These are the subscriptions the service returned. A mark on this phone is not a cancellation.")
    CardBox {
        subs.items.forEach { item ->
            HoldRow(item.name, item.use, eur(item.amountCents))
        }
    }
    Note(
        (if (marked.isEmpty()) "" else "You already marked ${marked.joinToString(" and ") { it.name }} as cancelled. The hold stays until the bank confirms it. ") +
            "A reminder is not a cancellation.",
    )
    Section("Your choice on each one")
    subs.items.forEach { item ->
        SubCard(store, item.id, "${item.name} · ${eur(item.amountCents)}", item.use)
    }
    Note("Money moves only when you, or the club, stop the charge. The service has no cancel route.")
}

@Composable
private fun SubCard(store: SpendSightStore, id: String, title: String, detail: String) {
    val choice = store.subs[id] ?: "open"
    val line = when (choice) {
        "keep" -> "You are keeping it."
        "ask" -> "The coach will ask again before the next charge."
        "cancel" -> "Marked as cancelled. The next charge is still in the holds until the bank confirms it. You can undo this."
        else -> "No choice yet."
    }
    CardBox {
        Eyebrow(title)
        Spacer(Modifier.height(6.dp))
        Lede(detail, strong = true)
        Note(line)
        ChoiceButton("Keep it", choice == "keep") { store.subs[id] = "keep" }
        ChoiceButton("Ask me next month", choice == "ask") { store.subs[id] = "ask" }
        ChoiceButton(
            if (choice == "cancel") "Undo, I still have this" else "I have cancelled it",
            choice == "cancel",
        ) { store.subs[id] = if (choice == "cancel") "open" else "cancel" }
    }
}

@Composable
private fun WatchScreen(store: SpendSightStore) {
    val watch = store.ledger?.watch ?: return
    SampleBanner()
    Eyebrow("Look twice")
    Lede("Two things the list of payments does not settle on its own.")
    watch.items.forEach { item ->
        val mark = store.watch[item.id] ?: "open"
        CardBox {
            Eyebrow("${item.name} · ${eur(item.amountCents)}")
            Spacer(Modifier.height(6.dp))
            Lede(watchLine(item, mark), strong = true)
            if (item.id == "refund") {
                ChoiceButton("Still waiting", mark == "wait") { store.watch[item.id] = "wait" }
                ChoiceButton("It landed", mark == "landed") { store.watch[item.id] = "landed" }
            } else {
                ChoiceButton("That’s mine", mark == "mine") { store.watch[item.id] = "mine" }
                ChoiceButton("That isn’t mine", mark == "not") { store.watch[item.id] = "not" }
            }
        }
    }
    Note(watch.note)
}

private fun watchLine(item: WatchItemDto, mark: String): String {
    val choice = when {
        item.id == "refund" && mark == "landed" -> "You marked it as landed. Today’s balance does not change."
        item.id == "refund" -> "It is not on S-Tili yet."
        mark == "mine" -> "You recognised it. It stays out of the spending total."
        mark == "not" -> "Marked as not yours. Here it only marks the row."
        else -> "It stays out of the spending total until you recognise it."
    }
    return "${item.whenLabel}. ${item.detail} $choice"
}

@Composable
private fun ImpulseScreen(store: SpendSightStore) {
    val impulse = store.ledger?.impulse ?: return
    val peak = impulse.peak
    SampleBanner()
    Eyebrow("${peak.whenLabel} · the busiest dated day")
    Hero("${peak.count} payments")
    Lede("${peak.places.joinToString(", ")}. ${eur(peak.amountCents)}. These are coffee, dining, taxis, and shopping. Groceries and rent are not in this reading. October’s ${impulse.coffeeVisits} coffee visits and ${impulse.woltOrders} Wolt orders are rolled up by place, so I will not name a weekday for them.")
    Note(counted("1–13 Nov", "Dining, coffee, taxis, and shopping", impulse.count) + ". No payment in this ledger has a time of day.")
    CardBox {
        impulse.days.forEach { day ->
            HoldRow(day.whenLabel, "${day.count} ${if (day.count == 1) "payment" else "payments"} · ${day.places.joinToString(", ")}", eur(day.amountCents))
        }
    }
    TextLink("Pause one purchase") { store.push(Screen.Purchase) }
    TextLink("The October portrait") { store.push(Screen.Spender) }
}

@Composable
private fun RunwayScreen(store: SpendSightStore) {
    val runway = store.ledger?.runway ?: return
    val safe = store.ledger?.week?.safeToSpend
    SampleBanner()
    Eyebrow("At November’s pace, rent left out")
    Hero("${runway.leftDays} days")
    Lede("${runway.leftDays} days at ${eur(runway.perDayCents)} a day. It is not a promise. Bills after payday are not subtracted here.")
    CardBox {
        HoldRow("November so far", "1–${runway.days} Nov, ${runway.count} payments other than rent", eur(runway.variableCents))
        HoldRow("Rent, left out", "It does not repeat inside this month.", eur(runway.rentCents))
        HoldRow("A day", "${eur(runway.variableCents)} across ${runway.days} days, rounded", eur(runway.perDayCents))
        SumRow("S-Tili ÷ a day", "${runway.leftDays} days")
    }
    Note(counted("1–${runway.days} Nov", "All payments except rent", runway.count) + ". Safe to spend on the design sample is ${safe ?: "—"}. It does not divide by the daily pace.")
    TextLink("See safe to spend") { store.push(Screen.Week) }
}

@Composable
private fun SampleBanner() {
    Note("Design sample. Today, Spending, and Save are this customer’s transactions. This screen is not.")
}

@Composable
private fun CoachScreen(store: SpendSightStore, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(store.messages.size, store.asking) {
        val last = store.messages.lastIndex
        if (last >= 0) listState.animateScrollToItem(last)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(store.messages.size) { index ->
            Bubble(store.messages[index]) { action ->
                if (action != null) store.push(action)
            }
        }
        if (store.asking) {
            item { Note("Reading the ledger…") }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun Bubble(message: CoachMessage, onAction: (Screen?) -> Unit) {
    val mine = message.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(
            Modifier
                .fillMaxWidth(if (mine) 0.86f else 1f)
                .clip(RoundedCornerShape(18.dp))
                .background(if (mine) Deep else Card)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val color = if (mine) Color.White else Ink
            val muted = if (mine) Color(0xFFD5E4DB) else Secondary
            if (message.refused) {
                Text(message.limit ?: "Outside what I answer", color = Slip, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            message.fact?.let { fact ->
                Column {
                    Text(fact.kicker, color = muted, fontSize = 12.sp)
                    Text(fact.figure, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(fact.because, color = color, fontSize = 15.sp, lineHeight = 20.sp)
                }
            }
            val paragraphs = message.paragraphs.ifEmpty { listOfNotNull(message.text) }
            paragraphs.forEach { Text(it, color = color, fontSize = 15.sp, lineHeight = 21.sp) }
            message.chart?.let { ChartBlock(it) }
            message.source?.let { Text(it, color = muted, fontSize = 12.sp) }
            if (message.actionLabel != null && message.action != null && !mine) {
                Text(
                    message.actionLabel,
                    color = Green,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onAction(message.action) },
                )
            }
        }
    }
}

@Composable
fun Composer(store: SpendSightStore) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(Bg).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            store.disclaimer.ifBlank { store.ledger?.session?.note ?: "" },
            color = Tertiary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Text(
            "Your data",
            color = Green,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { store.sheet = Sheet.Privacy }.padding(bottom = 8.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            coachChips.forEach { (label, ask) ->
                Text(
                    label,
                    color = Deep,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Fill)
                        .clickable { store.ask(ask) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Card).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(280) },
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("Ask about your money", color = Tertiary, fontSize = 16.sp)
                        inner()
                    }
                },
            )
            val enabled = text.isNotBlank() && !store.asking
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Green else Fill)
                    .clickable(enabled = enabled) {
                        store.ask(text)
                        text = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("↑", color = if (enabled) Color.White else Tertiary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
