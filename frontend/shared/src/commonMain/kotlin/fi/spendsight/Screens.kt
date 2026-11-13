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
import androidx.compose.foundation.lazy.items
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
    val effort = store.effort
    val food = store.month
    Tags(
        "Safe to spend", eur(Sample.safeSpend), { store.push(Screen.Week) },
        "Safe to save", eur(effort.saveCents), { store.push(Screen.Save) },
    )
    EffortPicker(store)
    Lede("${eur(Sample.balance)} on S-Tili, minus ${eur(Sample.billsBefore)} due before Thursday, minus a ${eur(Sample.buffer)} buffer. ${effort.name} save is ${eur(effort.saveCents)}. ${eur(Sample.aspMax)} would still count as one ASP month, and it would take ${eur(Sample.aspMax - effort.saveCents)} from the ${eur(Sample.leftUntilDecember)} that has to last until 15 December.")
    CardBox(onClick = { store.push(Screen.Coach) }) {
        Note("Slip line · one note, not a block")
        Spacer(Modifier.height(4.dp))
        Lede("${eur(Sample.slippedSinceFriday)} of ${eur(effort.slipCents)} has slipped since Friday, in coffee, taxis, and meals. Passing that line sends one note. The card is not blocked, and this line is not the safe-to-spend number.", strong = true)
    }
    CardBox {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Note("S-Tili now")
                Text(eur(Sample.balance), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
            }
            Column(Modifier.weight(1f)) {
                Note("Salary Thursday")
                Text(eur(Sample.salary), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
            }
        }
    }
    Section("Food and drink")
    MonthPicker(store)
    Trio(listOf("Groceries", "Coffee & drinks", "Dining out").map { name ->
        Triple(eur(store.catSum(food, name)), name) { store.push(Screen.Category(name)) }
    })
    CardBox(onClick = { store.push(Screen.Bonus) }) {
        Eyebrow("Use the green card for ${eur(Sample.bonusGap)} more")
        Spacer(Modifier.height(4.dp))
        Lede("Bonus goes from ${eur(Sample.novemberBonus)} to ${eur(Sample.bonusAtNext)}. The shop stays the same size. It moves to Prisma.", strong = true)
    }
    Section("October, in three baskets")
    CardBox(onClick = { store.push(Screen.Baskets) }) {
        StackBar(Basket.entries.map { basketColor(it) to store.basketSum(Month.Oct, it) })
        BasketLegend(store, Month.Oct)
        Spacer(Modifier.height(8.dp))
        Note("Everyday includes groceries. ${eur(store.slip(Month.Oct))} of it slipped through: dining, coffee, taxis, the pharmacy, Sokos.")
    }
    CardBox(onClick = { store.push(Screen.Purchase) }) {
        Note("Before you pay")
        Spacer(Modifier.height(4.dp))
        Lede("A Sokos jacket is ${eur(Sample.jacket)}. The week has ${eur(effort.slipCents)}. The pause sits here, before the payment.", strong = true)
    }
    CardBox(onClick = { store.push(Screen.Subs) }) {
        Note("Still leaving the account")
        Spacer(Modifier.height(4.dp))
        Lede("Fressi was ${eur(Sample.fressi)} on 2 Oct and is due again. Netflix, ${eur(Sample.netflix)}, has not left yet.", strong = true)
    }
}

@Composable
private fun SpendingScreen(store: SpendSightStore) {
    val cats = store.categories()
    Eyebrow(store.month.eyebrow)
    Hero(eur(store.total()))
    Lede("Left the account. Salary is not in this number.")
    MonthPicker(store)
    Trio(listOf("Groceries", "Coffee & drinks", "Dining out").map { name ->
        Triple(eur(store.catSum(store.month, name)), name) { store.push(Screen.Category(name)) }
    })
    Column {
        cats.forEach { cat ->
            ChevronRow(cat.name, cat.basket.title) { store.push(Screen.Category(cat.name)) }
        }
    }
    val count = store.rows().size
    Note(
        if (store.month == Month.Oct) {
            "$count lines, rolled up by place. A green dot on the next screen means the place earns Bonus. Open a payment to file it yourself."
        } else {
            "$count payments. Open one to file it yourself. SpendSight uses that filing next time."
        },
    )
    TextLink("Compare with October’s pace") { store.push(Screen.Charts) }
    StackBar(cats.map { basketColor(it.basket) to it.amountCents })
}

@Composable
private fun CategoryScreen(store: SpendSightStore, name: String) {
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
                Text(eur(row.amountCents), color = Ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun BasketsScreen(store: SpendSightStore) {
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
                Note("Elisa, Helen, the season ticket, insurance, Netflix, and Fressi have not left yet. They are held on This week.")
            }
            store.categories().filter { it.basket == basket }.forEach { cat ->
                ChevronRow(cat.name) { store.push(Screen.Category(cat.name)) }
            }
            if (store.categories().none { it.basket == basket }) {
                Note("Nothing in this basket yet. In October the repair and the dentist were ${eur(store.basketSum(Month.Oct, Basket.Irregular))}.")
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
    val dining = Sample.charts.first { it.label == "Dining out" }
    val taxis = Sample.charts.first { it.label == "Taxis" }
    Eyebrow("1–13 November against October")
    Hero("Your own pace.", 34)
    Lede("Grey is October stretched to 13 days, counted as a 30-day month, the same way This week counts it. Green is what has actually left.")
    ChartBlock(Sample.charts)
    Note("Grey is October’s pace. Green is 1–13 November.")
    Lede("Dining is ${eur(dining.novemberCents)} against a pace of ${eur(dining.octoberCents)}. Taxis are ${eur(taxis.novemberCents)} against ${eur(taxis.octoberCents)}. Groceries and coffee are under that pace.")
    Note("The same 13 days last year are not in this sample, so they are not drawn. Other households are a later feature. It would use anonymised open figures, such as Statistics Finland. No such figure is on this screen.")
    TextLink("Ask for a different chart") { store.push(Screen.Coach) }
}

@Composable
private fun SpenderScreen(store: SpendSightStore) {
    val peak = store.impulseDays().maxByOrNull { it.count * 100000 + it.amountCents }
    Eyebrow("From October, the last full month")
    Hero("A regular payer.")
    Lede("Most of the month leaves on a schedule. What slips through goes out in small payments, often at the same places.")
    CardBox {
        StackBar(Basket.entries.map { basketColor(it) to store.basketSum(Month.Oct, it) })
        BasketLegend(store, Month.Oct)
    }
    Lede("${eur(store.basketSum(Month.Oct, Basket.Regular))} left on a schedule: rent, bills, and the season ticket.")
    Lede("${eur(store.basketSum(Month.Oct, Basket.Irregular))} had to be paid when it happened: a repair and the dentist.")
    Lede("${eur(store.basketSum(Month.Oct, Basket.Everyday))} was everyday. Groceries were ${eur(store.catSum(Month.Oct, "Groceries"))}, mostly Prisma and S-market. Dining, coffee, taxis, the pharmacy, and Sokos were ${eur(store.slip(Month.Oct))}.")
    CardBox {
        Eyebrow("Where the small ones go")
        Spacer(Modifier.height(6.dp))
        Lede("Robert’s Coffee was ${store.coffeeVisits()} visits. Wolt was ${store.woltOrders()} orders. October rolls those up by place, so it has no weekday. In November the busiest dated day is ${peak?.whenLabel}: ${peak?.places?.joinToString(", ")}, ${eur(peak?.amountCents ?: 0)}.", strong = true)
    }
    Note(counted("October", "Three baskets", store.rows(Month.Oct).size, "lines") + ". Groceries and dining are rolled up by place.")
    TextLink("What is safe this week") { store.push(Screen.Week) }
    TextLink("When the impulse payments land") { store.push(Screen.Impulse) }
    TextLink("If dining out drops by a fifth") { store.push(Screen.Cut) }
}

@Composable
private fun WeekScreen(store: SpendSightStore) {
    val effort = store.effort
    val before = Sample.holds.filter { it.beforePayday }
    val later = Sample.holds.filter { !it.beforePayday && !it.buffer }
    Eyebrow("Until Thursday’s salary")
    Hero(eur(Sample.safeSpend))
    Lede("Balance, minus bills due before payday, minus a buffer. Groceries still to buy are a plan, so they are not in this number.")
    CardBox {
        HoldRow("S-Tili now", "Sample balance on 13 Nov", eur(Sample.balance))
        before.forEach { HoldRow(it.name, it.detail, "−${eur(it.amountCents)}") }
        val buffer = Sample.holds.first { it.buffer }
        HoldRow("Buffer", buffer.detail, "−${eur(buffer.amountCents)}")
        SumRow("Safe to spend", eur(Sample.safeSpend))
    }
    Note(counted("13 Nov", "Helen and Fressi", before.size, "bills") + ", plus the buffer. Elisa, the season ticket, insurance, Netflix, and December rent are dated after Thursday, so they stay out.")
    Section("A separate line for what slips")
    EffortPicker(store)
    CardBox {
        HoldRow("${effort.name} · coffee, taxis, meals", "A separate line", eur(effort.slipCents))
        Note("${eur(Sample.slippedSinceFriday)} has slipped since Friday. Passing this line sends one note. It does not block the card, and it does not change the ${eur(Sample.safeSpend)} above. The matching save is ${eur(effort.saveCents)}.")
    }
    Section("After payday, still to come")
    CardBox {
        HoldRow("Salary Thursday", "15 Nov", eur(Sample.salary))
        later.forEach { HoldRow(it.name, it.detail, "−${eur(it.amountCents)}") }
        SumRow("Left until 15 Dec, with salary and every hold", eur(Sample.leftUntilDecember))
    }
    Note("That last figure adds Thursday’s salary and then subtracts every hold, including December rent and the grocery plan. It is the rest of the cycle. It is not this week’s safe-to-spend number.")
    TextLink("How many days the pace lasts") { store.push(Screen.Runway) }
    TextLink("What is safe to save") { store.push(Screen.Save) }
}

@Composable
private fun CutScreen(store: SpendSightStore) {
    val saved = (Sample.octoberDining * store.cutPercent + 50) / 100
    Eyebrow("If October’s dining out is lower")
    Hero(eur(saved))
    Lede("kept in a month. Drag to choose the share.")
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
        if (saved <= Sample.woltOctober) {
            "This can come from Wolt, which was ${eur(Sample.woltOctober)}. Rosso stays, and Rosso earns Bonus."
        } else {
            "This is more than Wolt. Part of it would be Rosso, and Rosso is the dining that earns Bonus."
        },
    )
    CardBox {
        HoldRow("Once a week, not twice", "Four average meals saved, from ${Sample.diningOrders} October payments", eur(Sample.diningOnce))
        HoldRow("Two meals a week less", "Eight meals. October had ${Sample.diningOrders}, so about one meal would remain.", eur(Sample.diningTwice))
    }
    CardBox {
        HoldRow("Wolt", "Does not earn Bonus", eur(store.keySum(Month.Oct, "wolt")))
        HoldRow("Rosso", "Earns Bonus", eur(store.keySum(Month.Oct, "rosso")))
    }
    TextLink("What Rosso does for the step") { store.push(Screen.Bonus) }
}

@Composable
private fun SaveScreen(store: SpendSightStore) {
    val effort = store.effort
    val pile = effort.piles.getValue(store.years)
    Eyebrow("After Thursday’s salary · ${effort.name}")
    Tags("Save this month", eur(effort.saveCents), { store.push(Screen.Home) }, "Not this month", eur(Sample.aspMax), null)
    EffortPicker(store)
    Lede("${eur(effort.saveCents)} is ${effort.salaryPercent}% of the ${eur(Sample.salary)} salary, inside the ${eur(Sample.aspMin)}–${eur(Sample.aspMax)} ASP allows. Safe to spend until payday is ${eur(Sample.safeSpend)}. Coffee, taxis, and meals still have a separate line of ${eur(effort.slipCents)}. ${Sample.aspMonthsNeed - Sample.aspMonthsDone} months still have to be counted before 20. The euros, at ${eur(effort.saveCents)} a month, take ${effort.monthsForEuros} months. A ${eur(Sample.aspMax)} month fills euros faster and still counts as one month.")
    CardBox {
        Lede("The money left until 15 December, after the holds, is ${eur(Sample.leftUntilDecember)}. Moving ${eur(Sample.aspMax)} instead of ${eur(effort.saveCents)} takes ${eur(Sample.aspMax - effort.saveCents)} of it. The cushion for a broken machine is ${eur(Sample.buffer)}. Keep that extra where you can take it.", strong = true)
    }
    CardBox {
        Eyebrow("If you set aside ${eur(effort.saveCents)} a month")
        Hero(eur(pile))
        Lede("in ${store.years} ${if (store.years == 1) "year" else "years"}. This is only the money you put in. Interest is not added, so the pile is not a forecast.", strong = true)
        Segmented(listOf(1, 3, 5).map { n -> "$n ${if (n == 1) "year" else "years"}" to (store.years == n) }) { index ->
            store.years = listOf(1, 3, 5)[index]
        }
    }
    PrimaryButton(if (store.transferPrepared) "Monthly move prepared" else "Prepare the monthly move", store.transferPrepared, store::prepareTransfer)
    Note(
        if (store.transferPrepared) {
            "A move of ${eur(effort.saveCents)} from S-Tili is prepared for after Thursday. It has not run. You can cancel it."
        } else {
            "The coach can prepare a move. It does not open an account, and it does not run, until you confirm."
        },
    )
    Section("Where a euro can sit")
    Sample.accounts.forEach { account ->
        ChevronRow(account.title, account.subtitle) { store.sheet = Sheet.Account(account.id) }
    }
    TextLink("Open the home plan") { store.push(Screen.Home) }
    TextLink("Open the Bonus step") { store.push(Screen.Bonus) }
}

@Composable
private fun HomeScreen(store: SpendSightStore) {
    Eyebrow("Later · not in the MVP chat")
    Hero(eur(Sample.aspSaved))
    Lede("of ${eur(Sample.aspGoal)}, the 10% of a ${eur(Sample.homeEuro * 100)} home. ${Sample.aspMonthsDone} of ${Sample.aspMonthsNeed} months are counted. ${eur(Sample.aspGoal - Sample.aspSaved)} of savings is still ahead.")
    StackBar(listOf(Green to Sample.aspSaved, Color.Transparent to (Sample.aspGoal - Sample.aspSaved)))
    CardBox {
        Row {
            Column(Modifier.weight(1f)) {
                Note("Saving from salary")
                Text("14%", color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Column(Modifier.weight(1f)) {
                Note("Rent from salary")
                Text("29%", color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Note("No other loan is in this ledger. October’s Bonus, ${eur(Sample.octoberBonus)}, is already on S-Tili.")
    }
    CardBox {
        Eyebrow("This month’s ${eur(40_000)}")
        Spacer(Modifier.height(6.dp))
        Lede("It keeps both clocks moving: one more counted month, and ${eur(40_000)} closer to the 10%. The account pays 1% while you save. Another 4% is paid when you buy the home, on the years that qualify. Both are tax-free. This is the account’s term, not a forecast of a fund.", strong = true)
    }
    CardBox {
        Eyebrow("Cushion, a different job")
        Spacer(Modifier.height(6.dp))
        Lede("One quiet month of rent, bills, and the season ticket was ${eur(Sample.octoberMust)}. The cushion is ${eur(Sample.buffer)}, about half of October’s ${eur(Sample.repair)} repair. A calm target is one such month, kept on S-Tuottotili, where you can take it. The ASP account is the pocket you do not plan to take from.", strong = true)
    }
    Note("Säästäjä can take the next Bonus. The fund can fall, so this screen does not show a performance.")
    PrimaryButton("Sketch a loan") { store.push(Screen.Loan) }
    TextLink("Why not €1,500") { store.push(Screen.Save) }
}

@Composable
private fun LoanScreen(store: SpendSightStore) {
    val price = (store.homeEuro / 10_000) * 10_000
    val rate = ((store.rateTenths - 20) / 5) * 5 + 20
    val pay = Sample.loanPaymentCents(price, rate)
    val stress = Sample.loanStressCents(price)
    val down = price * 10
    val gap = down - Sample.aspSaved
    Eyebrow("Arithmetic, not an offer")
    Hero(eur(pay), 44)
    Lede("a month · ${shareLabel(pay)} of the ${eur(Sample.salary)} salary. Your rent is ${eur(Sample.rentOctober)}, 29% of that salary.")
    Section("Home price")
    Lede(eur(price * 100), strong = true)
    Slider(
        value = (price - 150_000) / 250_000f,
        onValueChange = { store.homeEuro = (150_000 + it * 250_000).toInt() },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Note("€150,000")
        Note("€400,000")
    }
    Section("Rate, if it stayed this number")
    Lede("${rate / 10}.${rate % 10}% · 25 years", strong = true)
    Slider(
        value = (rate - 20) / 40f,
        onValueChange = { store.rateTenths = (20 + it * 40).toInt() },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Note("2%")
        Note("6%")
    }
    Note("At 6%, the same loan is ${eur(stress)} a month. An ASP loan can include state interest support when the rate is above 3.8%, for up to 10 years, and only if a bank grants the loan.")
    CardBox {
        Lede(
            (if (gap > 0) {
                "The 10% for this price is ${eur(down)}. You have ${eur(Sample.aspSaved)}. The gap is ${eur(gap)}."
            } else {
                "You already have the 10% for this price, ${eur(down)}."
            }) + " The sketch uses that 10% as the down payment, not today’s balance. The city also limits how large an ASP loan can be. This screen does not look that up.",
            strong = true,
        )
    }
    Note("A real decision also looks at other loans, the collateral, and whether the payment still fits after rent becomes a loan. Nothing here is an application. The chat does not answer how to pay a loan off.")
}

@Composable
private fun PurchaseScreen(store: SpendSightStore) {
    val effort = store.effort
    val note = when (store.purchase) {
        "wait" -> "You chose to wait. Thursday’s salary is ${eur(Sample.salary)}. The jacket is still ${eur(Sample.jacket)}. You can change this."
        "take" -> "You chose to take it from the slip line. That line would be ${eur(effort.slipCents - Sample.jacket)}. Safe to spend until payday would still cover it. This preview does not pay Sokos."
        "skip" -> "You left it. The slip line stays ${eur(effort.slipCents)}."
        else -> "The small repeats are the other kind of impulse: coffee was 11 visits in October, Wolt was 7. This pause is for the one payment in front of you."
    }
    Eyebrow("Sokos · not paid")
    Hero(eur(Sample.jacket))
    Lede("Safe to spend until payday is ${eur(Sample.safeSpend)}, so the jacket fits inside it with ${eur(Sample.jacketRoom)} left. The slip line for coffee, taxis, and meals is ${eur(effort.slipCents)}, and the jacket is ${eur(effort.jacketOverCents)} over that line. Paying this as new money takes S-group purchases to ${eur(Sample.jacketWithSgroup)}. Bonus rises by ${eur(Sample.jacketGain)}. You would be ${eur(Sample.jacketLoss)} poorer.")
    Note(note)
    PrimaryButton("Wait until Thursday", store.purchase == "wait") { store.purchase = "wait" }
    ChoiceButton("Take it from the slip line", store.purchase == "take") { store.purchase = "take" }
    ChoiceButton("Not this one", store.purchase == "skip") { store.purchase = "skip" }
    TextLink("The shop that does raise Bonus") { store.push(Screen.Bonus) }
}

@Composable
private fun BonusScreen(store: SpendSightStore) {
    val hereAt = if (store.bonusPreview) 30_000 else 20_000
    Eyebrow("November · day 13")
    Hero(if (store.bonusPreview) eur(Sample.bonusAtNext) else eur(Sample.novemberBonus))
    Lede(
        if (store.bonusPreview) {
            "Bonus if the next ${eur(Sample.bonusGap)} stays at S-group. Step 2%."
        } else {
            "Bonus so far. Step 1.5% on ${eur(Sample.novemberSgroup)} at S-group."
        },
    )
    CardBox {
        Eyebrow("Use the green card for ${eur(Sample.bonusGap)} more")
        Spacer(Modifier.height(4.dp))
        Lede("A grocery shop of ${eur(Sample.weekShop)} and two café visits, ${eur(Sample.coffeePlan)}. You are already going to spend it. Bonus becomes ${eur(Sample.bonusAtNext)}. The 2% covers the whole ${eur(30_000)}, including what you already bought.", strong = true)
    }
    ChoiceButton(
        "Preview that ${eur(Sample.bonusGap)} on the green card",
        store.bonusPreview,
    ) { store.bonusPreview = !store.bonusPreview }
    Note("Pay the ${eur(Sample.bonusGap)} with S-Etukortti Visa and the payment benefit adds ${eur(Sample.visaOnGap)} on top. The step is the part that changes the month.")
    Note("If that ${eur(Sample.bonusGap)} is extra shopping, you pay ${eur(Sample.bonusGap)} and Bonus rises by ${eur(Sample.bonusGain)}. You would be ${eur(Sample.bonusLoss)} poorer. The coach keeps the shop you already need, and moves the place.")
    CardBox {
        Sample.bonusLadder.forEach { (pct, at) ->
            val here = at == hereAt
            val note = when {
                here && store.bonusPreview -> "If the next ${eur(Sample.bonusGap)} is at S-group"
                here -> "You are here · ${eur(Sample.novemberSgroup)}"
                !store.bonusPreview && at == 30_000 -> "${eur(Sample.bonusGap)} to this step"
                else -> ""
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (here) Green else Fill))
                Text("$pct%", Modifier.padding(horizontal = 10.dp).width(48.dp), color = Ink, fontWeight = FontWeight.Bold)
                Text(note, Modifier.weight(1f), color = Secondary, fontSize = 12.sp)
                Text(eur(at), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
    CardBox {
        Eyebrow("If the whole food plan stays there")
        Spacer(Modifier.height(6.dp))
        Lede("The grocery plan is ${eur(Sample.groceryPlan)}, and the café visits are ${eur(Sample.coffeePlan)}. Together with today’s ${eur(Sample.novemberSgroup)}, November reaches ${eur(Sample.monthAt)}. The step is 3%, and Bonus is ${eur(Sample.monthBonus)}.", strong = true)
        Lede("The groceries alone would land at ${eur(Sample.foodOnly)}, step 2.5%, Bonus ${eur(Sample.foodOnlyBonus)}. The café visits are what reach 3%.")
    }
    CardBox {
        Eyebrow("October already closed")
        Spacer(Modifier.height(6.dp))
        Lede("S-group purchases were ${eur(Sample.octoberSgroup)}, step 3.5%, Bonus ${eur(Sample.octoberBonus)}, paid on 10 Nov. ${eur(Sample.octoberNextGap)} more at Prisma, taken from the ${eur(Sample.otherGrocers)} at K-Market and Lidl, would have made 4% and ${eur(Sample.octoberNextBonus)}.", strong = true)
    }
    ChevronRow(
        if (store.saver) "Next Bonus goes to Säästäjä" else "Send the next Bonus to Säästäjä",
        if (store.saver) "You can keep it on S-Tili instead" else "October’s ${eur(Sample.octoberBonus)} is already on S-Tili",
        store::toggleSaver,
    )
}

@Composable
private fun SubsScreen(store: SpendSightStore) {
    val marked = listOf("gym" to "Fressi", "netflix" to "Netflix", "spotify" to "Spotify").filter { store.subs[it.first] == "cancel" }
    Eyebrow("If the three repeating ones stop")
    Hero(eur(Sample.subsMonth))
    Lede("a month, ${eur(Sample.subsYear)} across a year. These are the three subscriptions outside rent, phone, power, insurance, and the season ticket. Only Fressi has a sign of low use. Netflix and Spotify have no use count, so this is not a measured ranking.")
    CardBox {
        HoldRow("Fressi", "Charged on 2 Oct. No visit is in the ledger.", eur(Sample.fressi))
        HoldRow("Netflix", "No play count. The November charge has not left.", eur(Sample.netflix))
        HoldRow("Spotify", "Charged on 5 Nov. No play count is in the ledger.", eur(Sample.spotify))
    }
    Note(
        (if (marked.isEmpty()) "" else "You already marked ${marked.joinToString(" and ") { it.second }} as cancelled. The hold stays until the bank confirms it. ") +
            "A reminder is not a cancellation. Spotify already left on 5 Nov, so a cancel starts from the next charge. " +
            counted("Oct–Nov", "Fressi, Netflix, Spotify", 2, "charges") + " and 1 hold.",
    )
    Section("Your choice on each one")
    SubCard(store, "gym", "Fressi · ${eur(Sample.fressi)}", "Filed under Bills, because the same amount left in October and is due again. No visit is in the ledger. Open the October payment if that filing is wrong. SpendSight uses your filing from then on.")
    SubCard(store, "netflix", "Netflix · ${eur(Sample.netflix)}", "Due this month. In October it was inside the streaming line with Spotify. No play count is in the ledger.")
    SubCard(store, "spotify", "Spotify · ${eur(Sample.spotify)}", "Already left on 5 Nov. No play count is in the ledger, so it is not proven unused.")
    Note("Money moves only when you, or the club, stop the charge.")
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
    val bolt = store.watch["bolt"] ?: "open"
    val refund = store.watch["refund"] ?: "open"
    Eyebrow("Look twice")
    Lede("Two things the list of payments does not settle on its own.")
    CardBox {
        Eyebrow("A second Bolt · ${eur(1_180)}")
        Spacer(Modifier.height(6.dp))
        Lede(
            "12 Nov, four minutes after the ${eur(1_180)} ride that is already in the ledger. " + when (bolt) {
                "mine" -> "You recognised it. It stays out of the spending total in this design."
                "not" -> "Marked as not yours. In the bank app this would open a dispute. Here it only marks the row."
                else -> "It is not in the spending total until you recognise it."
            },
            strong = true,
        )
        ChoiceButton("That’s mine", bolt == "mine") { store.watch["bolt"] = "mine" }
        ChoiceButton("That isn’t mine", bolt == "not") { store.watch["bolt"] = "not" }
    }
    CardBox {
        Eyebrow("Prisma refund · ${eur(420)}")
        Spacer(Modifier.height(6.dp))
        Lede("A return from 11 Nov. " + if (refund == "landed") "Marked as landed. Today’s balance does not change in this design." else "It is not on S-Tili yet.", strong = true)
        ChoiceButton("Still waiting", refund == "wait") { store.watch["refund"] = "wait" }
        ChoiceButton("It landed", refund == "landed") { store.watch["refund"] = "landed" }
    }
    Note("Confirming a row here does not change S-Tili. The point is to notice the charge, and to remember the refund.")
}

@Composable
private fun ImpulseScreen(store: SpendSightStore) {
    val days = store.impulseDays()
    val peak = days.maxByOrNull { it.count * 100000 + it.amountCents }
    Eyebrow("${peak?.whenLabel ?: "12 Nov"} · the busiest dated day")
    Hero("${peak?.count ?: Sample.impulsePeakCount} payments")
    Lede("${peak?.places?.joinToString(", ")}. ${eur(peak?.amountCents ?: Sample.impulsePeakAmount)}. These are coffee, dining, taxis, and shopping. Groceries and rent are not in this reading. October’s ${store.coffeeVisits()} coffee visits and ${store.woltOrders()} Wolt orders are rolled up by place, so I will not name a weekday for them.")
    Note(counted("1–13 Nov", "Dining, coffee, and taxis", days.sumOf { it.count }) + ". No payment in this ledger has a time of day.")
    CardBox {
        days.forEach { day ->
            HoldRow(day.whenLabel, "${day.count} ${if (day.count == 1) "payment" else "payments"} · ${day.places.joinToString(", ")}", eur(day.amountCents))
        }
    }
    TextLink("Pause one purchase") { store.push(Screen.Purchase) }
    TextLink("The October portrait") { store.push(Screen.Spender) }
}

@Composable
private fun RunwayScreen(store: SpendSightStore) {
    Eyebrow("At November’s pace, rent left out")
    Hero("${Sample.runwayDays} days")
    Lede("${eur(Sample.balance)} on S-Tili divided by ${eur(Sample.runwayPerDay)} a day. That is ${Sample.runwayDays} days from today if nothing else changes. It is not a promise. Bills after payday are not subtracted here.")
    CardBox {
        HoldRow("November so far", "1–13 Nov, ${Sample.runwayCount} payments other than rent", eur(Sample.runwayVariable))
        HoldRow("Rent, left out", "Already left on 3 Nov. It does not repeat inside this month.", eur(Sample.runwayRent))
        HoldRow("A day", "${eur(Sample.runwayVariable)} across 13 days, rounded", eur(Sample.runwayPerDay))
        SumRow("S-Tili ÷ a day", "${Sample.runwayDays} days")
    }
    Note(counted("1–13 Nov", "All payments except rent", Sample.runwayCount) + ". Safe to spend until Thursday is a different sum: it subtracts Helen, Fressi, and the buffer, and it does not divide by the daily pace.")
    TextLink("See safe to spend") { store.push(Screen.Week) }
}

@Composable
private fun CoachScreen(store: SpendSightStore, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(store.messages.size) {
        if (store.messages.isNotEmpty()) listState.animateScrollToItem(store.messages.lastIndex)
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
            if (message.refused) Text("Outside what I answer", color = Slip, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            "Answers can be wrong. Check a decision before you act. This is not a person at the bank.",
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
            val enabled = text.isNotBlank()
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
