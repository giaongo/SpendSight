package fi.spendsight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenBody(store: SpendSightStore, modifier: Modifier = Modifier) {
    when (store.current) {
        Screen.Coach -> {
            CoachScreen(store, modifier)
            return
        }
        Screen.Today -> {
            HomeRoot(store, modifier)
            return
        }
        Screen.Insight -> {
            InsightRoot(store, modifier)
            return
        }
        Screen.Save -> {
            SavingsRoot(store, modifier)
            return
        }
        Screen.Offers -> {
            OffersRoot(store, modifier)
            return
        }
        else -> Unit
    }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val screen = store.current) {
            Screen.Spending -> SpendingScreen(store)
            is Screen.Category -> CategoryScreen(store, screen.name)
            Screen.Baskets -> BasketsScreen(store)
            Screen.Charts -> ChartsScreen(store)
            Screen.Spender -> SpenderScreen(store)
            Screen.Week -> WeekScreen(store)
            Screen.Cut -> CutScreen(store)
            Screen.Home -> HomeScreen(store)
            Screen.Loan -> LoanScreen(store)
            Screen.Purchase -> PurchaseScreen(store)
            Screen.Bonus -> BonusScreen(store)
            Screen.Subs -> SubsScreen(store)
            Screen.Watch -> WatchScreen(store)
            Screen.Impulse -> ImpulseScreen(store)
            Screen.Runway -> RunwayScreen(store)
            Screen.Today, Screen.Insight, Screen.Save, Screen.Offers, Screen.Coach -> Unit
        }
    }
}

@Composable
private fun SpendingScreen(store: SpendSightStore) {
    val spending = store.spendingView
    val newest = store.newestSpendMonth
    Eyebrow(spending.customerId)
    Hero(eur(spending.totalOutCents))
    Lede(knownNote(spending.note))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextLink(t(R.string.T_earlier)) { store.loadSpending(shiftMonth(spending.month, -1)) }
        Text(spending.month, color = Ink, fontWeight = FontWeight.SemiBold)
        if (newest == null || spending.month < newest) {
            TextLink(t(R.string.T_later)) { store.loadSpending(shiftMonth(spending.month, 1)) }
        } else {
            Note(t(R.string.T_latest))
        }
    }
    CardBox {
        HoldRow(t(R.string.T_money_in_2), t(R.string.T_salary_and_other_credits), eur(spending.totalInCents))
        SumRow("${spending.lineCount} ${t(R.string.T_lines)}", eur(spending.totalOutCents))
    }
    spending.categories.forEach { cat ->
        ChevronRow(categoryLabel(cat.name), "${cat.count} · ${eur(cat.amountCents)}") { store.push(Screen.Category(cat.name)) }
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
        yes > 0 && no > 0 -> t(R.string.T_earns_bonus_does_not, eur(yes), eur(no))
        yes > 0 -> t(R.string.T_all_of_this_earns_bonus)
        else -> t(R.string.T_none_of_this_earns_bonus)
    }
    val basket = rows.firstOrNull()?.basket ?: Basket.Everyday
    Eyebrow("${basket.heading()} · ${store.month.heading()}")
    Hero(eur(total))
    Lede(bonusLine)
    MonthPicker(store)
    if (rows.isEmpty()) {
        Note(t(R.string.T_no_payments_in_this_category))
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
    val rows = spending.transactions.filter { it.category == name }.sortedByDescending { it.txDate }
    val total = rows.sumOf { it.amountCents }
    val month = monthName(spending.month, store.language).replaceFirstChar { it.uppercase() }
    Text("${categoryLabel(name)} · $month", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Ink, fontWeight = FontWeight.Bold)) { append(eur(total)) }
            append(" · ")
            append(if (rows.size == 1) t(R.string.T_one_visit) else t(R.string.T_n_visits, rows.size))
        },
        color = Secondary,
        fontSize = 15.sp,
    )
    if (rows.isEmpty()) {
        Note(t(R.string.T_no_lines_in_this_category_for, spending.month))
        return
    }
    Text(t(R.string.T_tap_group), color = Graphite, fontSize = 15.sp)
    val groups = rows.groupBy { timeBucket(it) }.toList().sortedBy { it.first.ordinal }
    var open by remember(name) { mutableStateOf(if (groups.size == 1) groups.first().first else null) }
    WhiteList {
        groups.forEachIndexed { index, (bucket, list) ->
            if (index > 0) Hairline()
            val expanded = open == bucket
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clickable { open = if (expanded) null else bucket }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(bucket.label(), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (list.size == 1) t(R.string.T_one_visit) else t(R.string.T_n_visits, list.size), color = Secondary, fontSize = 13.sp)
                }
                Text(eur(list.sumOf { it.amountCents }), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, style = Tabular)
                Spacer(Modifier.width(12.dp))
                Glyph(Icon.ChevronRight, Tertiary, 16.dp, stroke = 2.6f, modifier = Modifier.rotate(if (expanded) 90f else 0f))
            }
            if (expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    list.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 32.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${formatAsOf(row.txDate, store.language)} · ${row.merchantId ?: typeLabel(row.transactionType)}",
                                color = Graphite,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(eur(row.amountCents), color = Graphite, fontSize = 15.sp, style = Tabular)
                        }
                    }
                }
            }
        }
    }
}

internal enum class TimeBucket { WeekdayEvening, Weekend, WeekdayDay }

@Composable
private fun TimeBucket.label(): String = when (this) {
    TimeBucket.WeekdayEvening -> t(R.string.T_weekday_evening)
    TimeBucket.Weekend -> t(R.string.T_weekend)
    TimeBucket.WeekdayDay -> t(R.string.T_weekday_day)
}

/** Matches the hour in `03OCT24:09:04:04` and in `2024-10-03T09:04:04`. */
private val HourPattern = Regex("[T :]([0-9]{2}):[0-9]{2}")

internal fun timeBucket(row: SpendTxDto): TimeBucket {
    val parts = row.txDate.split("-").mapNotNull { it.toIntOrNull() }
    val weekday = if (parts.size == 3) (daysSinceEpoch(parts[0], parts[1], parts[2]) + 3).mod(7) else 0
    if (weekday >= 5) return TimeBucket.Weekend
    val hour = HourPattern.find(row.txTimestamp)?.groupValues?.get(1)?.toIntOrNull() ?: 12
    return if (hour >= 17) TimeBucket.WeekdayEvening else TimeBucket.WeekdayDay
}

@Composable
private fun BasketsScreen(store: SpendSightStore) {
    SampleBanner()
    Lede(t(R.string.T_every_payment_sits_in_one_basket_the_first_two_hav))
    MonthPicker(store)
    val parts = Basket.entries.map { basketColor(it) to store.basketSum(store.month, it) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pie(parts)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { BasketLegend(store, store.month) }
    }
    Basket.entries.forEach { basket ->
        CardBox {
            Eyebrow(basket.heading())
            Hero(eur(store.basketSum(store.month, basket)), 32)
            Lede(basket.detail())
            if (basket == Basket.Everyday) {
                Spacer(Modifier.height(8.dp))
                StackBar(listOf(Green to store.catSum(store.month, "Groceries"), Slip to store.slip(store.month)))
                Spacer(Modifier.height(6.dp))
                Note("${categoryLabel("Groceries")} ${eur(store.catSum(store.month, "Groceries"))} · ${t(R.string.T_slips_through)} ${eur(store.slip(store.month))}")
            }
            if (basket == Basket.Regular && store.month == Month.Nov) {
                Spacer(Modifier.height(8.dp))
                Note(t(R.string.T_bills_due_before_payday_are_on_this_week_later_hol))
            }
            store.categories().filter { it.basket == basket }.forEach { cat ->
                ChevronRow(categoryLabel(cat.name)) { store.push(Screen.Category(cat.name)) }
            }
            if (store.categories().none { it.basket == basket }) {
                Note(t(R.string.T_nothing_in_this_basket_for_this_month_october_s_mu, eur(store.basketSum(Month.Oct, Basket.Irregular))))
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
    Eyebrow(t(R.string.T_n_1_13_november_against_october))
    Hero(t(R.string.T_your_own_pace), 34)
    Lede(t(R.string.T_grey_is_october_stretched_to_13_days_green_is_what))
    ChartBlock(rows)
    Note(t(R.string.T_grey_is_october_s_pace_green_is_1_13_november))
    if (dining != null && taxis != null) {
        Lede(t(R.string.T_dining_is_against_a_pace_of_taxis_are_against, eur(dining.novemberCents), eur(dining.octoberCents), eur(taxis.novemberCents), eur(taxis.octoberCents)))
    }
    Note(charts.note)
    TextLink(t(R.string.T_ask_for_a_different_chart)) { store.push(Screen.Coach) }
}

@Composable
private fun SpenderScreen(store: SpendSightStore) {
    val spender = store.ledger?.spender ?: return
    val impulse = store.ledger?.impulse ?: return
    val peak = impulse.peak
    SampleBanner()
    Eyebrow(t(R.string.T_from_october_the_last_full_month))
    Hero(t(R.string.T_a_regular_payer))
    Lede(t(R.string.T_most_of_the_month_leaves_on_a_schedule_what_slips))
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
    Lede(t(R.string.T_left_on_a_schedule_rent_bills_and_the_season_ticke, eur(spender.mustEveryMonthCents)))
    Lede(t(R.string.T_had_to_be_paid_when_it_happened, eur(spender.mustWhenItHappensCents)))
    Lede(t(R.string.T_was_everyday_groceries_were_the_slip_was, eur(spender.everydayCents), eur(store.catSum(Month.Oct, "Groceries")), eur(spender.slipCents)))
    CardBox {
        Eyebrow(t(R.string.T_where_the_small_ones_go))
        Spacer(Modifier.height(6.dp))
        Lede(t(R.string.T_coffee_was_visits_wolt_was_orders_october_rolls_th, impulse.coffeeVisits, impulse.woltOrders, peak.whenLabel, peak.places.joinToString(", "), eur(peak.amountCents)), strong = true)
    }
    Note(counted(t(R.string.T_october), t(R.string.T_three_baskets), spender.lines, t(R.string.T_lines)) + t(R.string.T_groceries_and_dining_are_rolled_up_by_place))
    TextLink(t(R.string.T_what_is_safe_this_week)) { store.push(Screen.Week) }
    TextLink(t(R.string.T_when_the_impulse_payments_land)) { store.push(Screen.Impulse) }
    TextLink(t(R.string.T_if_dining_out_drops_by_a_fifth)) { store.push(Screen.Cut) }
}

@Composable
private fun WeekScreen(store: SpendSightStore) {
    val week = store.ledger?.week ?: return
    val effort = store.effortRow
    val summary = store.ledger?.summary ?: return
    SampleBanner()
    Eyebrow(t(R.string.T_until_payday))
    Hero(presentMoney(week.safeToSpend))
    Lede(t(R.string.T_balance_minus_bills_due_before_payday_minus_a_buff))
    CardBox {
        week.bills.forEach { HoldRow(it.name, it.detail, "−${eur(it.amountCents)}") }
        HoldRow(t(R.string.T_buffer), t(R.string.T_kept_back_from_this_week), "−${eur(week.bufferCents)}")
        SumRow(t(R.string.T_safe_to_spend), presentMoney(week.safeToSpend))
    }
    Note(counted(t(R.string.T_this_week), week.bills.joinToString(t(R.string.T_and)) { it.name }, week.bills.size, t(R.string.T_bills_2)) + t(R.string.T_plus_the_buffer) + week.note)
    Section(t(R.string.T_a_separate_line_for_what_slips))
    EffortPicker(store)
    CardBox {
        HoldRow("${effort.name} · ${t(R.string.T_coffee_taxis_meals)}", t(R.string.T_a_separate_line), eur(effort.slipCents))
        Note(t(R.string.T_passing_this_line_sends_one_note_it_does_not_block, presentMoney(week.safeToSpend), eur(effort.saveCents)))
    }
    Section(t(R.string.T_after_payday))
    CardBox {
        HoldRow(t(R.string.T_every_hold), t(R.string.T_the_service_returns_the_total_not_each_later_bill), "−${eur(summary.holdsCents)}")
        SumRow(t(R.string.T_left_after_salary_and_every_hold), eur(summary.leftAfterSalaryCents))
    }
    Note(t(R.string.T_that_last_figure_is_the_design_sample_balance_plus))
    TextLink(t(R.string.T_how_many_days_the_pace_lasts)) { store.push(Screen.Runway) }
    TextLink(t(R.string.T_what_is_safe_to_save)) { store.push(Screen.Save) }
}

@Composable
private fun CutScreen(store: SpendSightStore) {
    val dining = store.ledger?.dining ?: return
    val saved = diningWhatIf(dining.diningCents, dining.fifthCents, store.cutPercent)
    SampleBanner()
    Eyebrow(t(R.string.T_if_october_s_dining_out_is_lower))
    Hero(eur(saved))
    Lede(if (store.cutPercent == 20) t(R.string.T_kept_in_a_month_this_is_the_service_s_one_fifth_fi) else t(R.string.T_kept_in_a_month_as_a_what_if_on_the_dining_total_d))
    Slider(
        value = store.cutPercent / 50f,
        onValueChange = { store.cutPercent = (it * 50f).toInt().coerceIn(0, 50) },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Note(pct(0))
        Text(pct(store.cutPercent), color = Ink, fontWeight = FontWeight.SemiBold)
        Note(pct(50))
    }
    Lede(t(R.string.T_a_month_a_year, eur(saved), eur(saved * 12)))
    Lede(
        if (saved <= dining.woltCents) {
            t(R.string.T_this_can_come_from_wolt_which_was_rosso_stays_and, eur(dining.woltCents))
        } else {
            t(R.string.T_this_is_more_than_wolt_which_was_part_of_it_would, eur(dining.woltCents))
        },
    )
    CardBox {
        HoldRow(t(R.string.T_once_a_week_not_twice), t(R.string.T_from_october_payments, dining.orders), eur(dining.onceCents))
        HoldRow(t(R.string.T_two_meals_a_week_less), t(R.string.T_from_the_same_payments, dining.orders), eur(dining.twiceCents))
    }
    CardBox {
        HoldRow("Wolt", t(R.string.T_does_not_earn_bonus), eur(dining.woltCents))
        HoldRow("Rosso", t(R.string.T_earns_bonus), eur(dining.rossoCents))
    }
    TextLink(t(R.string.T_what_rosso_does_for_the_step)) { store.push(Screen.Bonus) }
}

@Composable
private fun HomeScreen(store: SpendSightStore) {
    val bonus = store.ledger?.bonus ?: return
    val buffer = store.ledger?.week?.bufferCents
    SampleBanner()
    Eyebrow(t(R.string.T_no_home_plan_on_the_service))
    Hero(t(R.string.T_not_in_this_reading), 34)
    Lede(t(R.string.T_the_service_does_not_return_asp_savings_the_goal_o))
    Note(store.settings.note)
    CardBox {
        Row {
            Column(Modifier.weight(1f)) {
                Note(t(R.string.T_buffer_in_the_design_sample))
                Text(if (buffer == null) "—" else eur(buffer), color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Column(Modifier.weight(1f)) {
                Note(t(R.string.T_october_bonus))
                Text(eur(bonus.octoberBonusCents), color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Note(t(R.string.T_october_s_bonus_is_already_on_s_tili_sending_the_n))
    }
    PrimaryButton(t(R.string.T_sketch_a_loan)) { store.push(Screen.Loan) }
    TextLink(t(R.string.T_why_the_save_stays_inside_the_asp_range)) { store.push(Screen.Save) }
}

@Composable
private fun LoanScreen(store: SpendSightStore) {
    val loan = store.ledger?.loan ?: return
    val price = store.loanPriceEuro
    val rate = store.loanRateTenths
    val matches = store.loanMatches()
    SampleBanner()
    Eyebrow(if (matches) t(R.string.T_arithmetic_not_an_offer) else t(R.string.T_updating_the_sketch))
    Hero(if (matches) eur(loan.monthlyCents) else "…", 44)
    Lede(if (matches) loan.note else t(R.string.T_waiting_for_the_service_to_count_this_price_and_ra))
    Section(t(R.string.T_home_price))
    Lede(eur(price * 100), strong = true)
    Slider(
        value = (price - 150_000) / 250_000f,
        onValueChange = {
            store.homeEuro = (150_000 + it * 250_000).toInt()
            store.scheduleLoan()
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Note(euros(150_000))
        Note(euros(400_000))
    }
    Section(t(R.string.T_rate_if_it_stayed_this_number))
    Lede("${rateLabel(rate)} · ${t(R.string.T_n_25_years)}", strong = true)
    Slider(
        value = (rate - 20) / 40f,
        onValueChange = {
            store.rateTenths = (20 + it * 40).toInt()
            store.scheduleLoan()
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Note(pct(2))
        Note(pct(6))
    }
    if (matches) {
        Note(t(R.string.T_at_6_the_same_loan_is_a_month, eur(loan.stress6pctCents)))
        CardBox {
            Lede(t(R.string.T_the_10_down_payment_on_this_price_is_the_sketch_us, eur(loan.downCents), loan.note), strong = true)
        }
    }
    Note(t(R.string.T_nothing_here_is_an_application_the_chat_does_not_a))
}

@Composable
private fun PurchaseScreen(store: SpendSightStore) {
    val buy = store.ledger?.purchase ?: return
    val effort = store.effortRow
    val safeText = store.ledger?.week?.safeToSpend
    val safe = if (safeText == null) "" else presentMoney(safeText)
    val note = when (store.purchaseChoice) {
        "wait" -> t(R.string.T_you_chose_to_wait_the_jacket_is_still_you_can_chan, eur(buy.priceCents))
        "take" -> t(R.string.T_you_chose_to_take_it_from_the_slip_line_it_is_over, eur(buy.overSlipCents), eur(effort.slipCents))
        "skip" -> t(R.string.T_you_left_it_the_slip_line_stays, eur(effort.slipCents))
        else -> t(R.string.T_this_pause_is_for_the_one_payment_in_front_of_you)
    }
    SampleBanner()
    Eyebrow(t(R.string.T_sokos_not_paid))
    Hero(eur(buy.priceCents))
    Lede(t(R.string.T_safe_to_spend_on_the_design_sample_is_so_the_jacke, safe, eur(buy.leftInWeekCents), eur(effort.slipCents), eur(buy.overSlipCents), eur(buy.sgroupWithJacketCents), eur(buy.gainCents), eur(buy.lossCents)))
    Note(note)
    PrimaryButton(t(R.string.T_wait_until_payday), store.purchaseChoice == "wait") { store.purchaseChoice = "wait" }
    ChoiceButton(t(R.string.T_take_it_from_the_slip_line), store.purchaseChoice == "take") { store.purchaseChoice = "take" }
    ChoiceButton(t(R.string.T_not_this_one), store.purchaseChoice == "skip") { store.purchaseChoice = "skip" }
    TextLink(t(R.string.T_the_shop_that_does_raise_bonus)) { store.push(Screen.Bonus) }
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
    Eyebrow(t(R.string.T_november))
    Hero(eur(heroCents))
    Lede(
        if (store.bonusPreview && gap != null && bonus.nextRatePct != null) {
            t(R.string.T_bonus_if_the_next_stays_at_s_group_step, eur(gap), formatPct(bonus.nextRatePct))
        } else {
            t(R.string.T_bonus_so_far_step_on_at_s_group, formatPct(bonus.novemberRatePct), eur(bonus.novemberSgroupCents))
        },
    )
    if (gap != null && nextBonus != null && nextAt != null) {
        CardBox {
            Eyebrow(t(R.string.T_use_the_green_card_for_more, eur(gap)))
            Spacer(Modifier.height(4.dp))
            Lede(t(R.string.T_with_the_grocery_shop_and_the_caf_visits_s_group_p, eur(bonus.weekAtCents), eur(nextBonus)), strong = true)
        }
        ChoiceButton(t(R.string.T_preview_that_on_the_green_card, eur(gap)), store.bonusPreview) {
            store.bonusPreview = !store.bonusPreview
        }
        if (bonus.visaOnGapCents != null) {
            Note(t(R.string.T_pay_the_with_s_etukortti_visa_and_the_payment_bene, eur(gap), eur(bonus.visaOnGapCents), bonus.note))
        }
        if (bonus.gainCents != null && bonus.lossCents != null) {
            Note(t(R.string.T_if_that_is_extra_shopping_you_pay_and_bonus_rises, eur(gap), eur(bonus.gainCents), eur(bonus.lossCents)))
        }
    }
    CardBox {
        bonus.steps.asReversed().forEach { step ->
            val here = step.atCents == mark
            val note = when {
                here && store.bonusPreview -> t(R.string.T_if_the_next_is_at_s_group, eur(gap ?: 0))
                step.atCents == hereCents -> t(R.string.T_you_are_here, eur(bonus.novemberSgroupCents))
                step.atCents == nextAt && gap != null -> t(R.string.T_to_this_step, eur(gap))
                else -> ""
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (here) Green else Fill))
                Text(pct(step.pct), Modifier.padding(horizontal = 10.dp).width(48.dp), color = Ink, fontWeight = FontWeight.Bold)
                Text(note, Modifier.weight(1f), color = Secondary, fontSize = 12.sp)
                Text(eur(step.atCents), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
    CardBox {
        Eyebrow(t(R.string.T_if_the_grocery_plan_and_the_caf_visits_stay_there))
        Spacer(Modifier.height(6.dp))
        Lede(t(R.string.T_together_with_today_s_november_reaches_bonus_at_th, eur(bonus.novemberSgroupCents), eur(bonus.monthAtCents), eur(bonus.monthBonusCents)), strong = true)
        Lede(t(R.string.T_the_groceries_alone_would_land_at_bonus, eur(bonus.foodOnlyCents), eur(bonus.foodOnlyBonusCents)))
    }
    CardBox {
        Eyebrow(t(R.string.T_october_already_closed))
        Spacer(Modifier.height(6.dp))
        Lede(t(R.string.T_s_group_purchases_were_step_bonus, eur(bonus.octoberSgroupCents), formatPct(bonus.octoberRatePct), eur(bonus.octoberBonusCents)), strong = true)
    }
    ChevronRow(
        if (store.saver) t(R.string.T_next_bonus_goes_to_s_st_j) else t(R.string.T_send_the_next_bonus_to_s_st_j),
        if (store.saver) t(R.string.T_you_can_keep_it_on_s_tili_instead) else t(R.string.T_october_s_is_already_on_s_tili, eur(bonus.octoberBonusCents)),
        store::toggleSaver,
    )
}

@Composable
private fun SubsScreen(store: SpendSightStore) {
    val subs = store.ledger?.subs ?: return
    val marked = subs.items.filter { store.subs[it.id] == "cancel" }
    SampleBanner()
    Eyebrow(t(R.string.T_if_the_repeating_ones_stop))
    Hero(eur(subs.monthCents))
    Lede(t(R.string.T_a_month_across_a_year_these_are_the_subscriptions, eur(subs.yearCents)))
    CardBox {
        subs.items.forEach { item ->
            HoldRow(item.name, item.use, eur(item.amountCents))
        }
    }
    Note(
        (if (marked.isEmpty()) "" else t(R.string.T_you_already_marked_as_cancelled_the_hold_stays_unt, marked.joinToString(t(R.string.T_and)) { it.name })) +
            t(R.string.T_a_reminder_is_not_a_cancellation),
    )
    Section(t(R.string.T_your_choice_on_each_one))
    subs.items.forEach { item ->
        SubCard(store, item.id, "${item.name} · ${eur(item.amountCents)}", item.use)
    }
    Note(t(R.string.T_money_moves_only_when_you_or_the_club_stop_the_cha))
}

@Composable
private fun SubCard(store: SpendSightStore, id: String, title: String, detail: String) {
    val choice = store.subs[id] ?: "open"
    val line = when (choice) {
        "keep" -> t(R.string.T_you_are_keeping_it)
        "ask" -> t(R.string.T_the_chat_will_ask_again_before_the_next_charge)
        "cancel" -> t(R.string.T_marked_as_cancelled_the_next_charge_is_still_in_th)
        else -> t(R.string.T_no_choice_yet)
    }
    CardBox {
        Eyebrow(title)
        Spacer(Modifier.height(6.dp))
        Lede(detail, strong = true)
        Note(line)
        ChoiceButton(t(R.string.T_keep_it), choice == "keep") { store.subs[id] = "keep" }
        ChoiceButton(t(R.string.T_ask_me_next_month), choice == "ask") { store.subs[id] = "ask" }
        ChoiceButton(
            if (choice == "cancel") t(R.string.T_undo_i_still_have_this) else t(R.string.T_i_have_cancelled_it),
            choice == "cancel",
        ) { store.subs[id] = if (choice == "cancel") "open" else "cancel" }
    }
}

@Composable
private fun WatchScreen(store: SpendSightStore) {
    val watch = store.ledger?.watch ?: return
    SampleBanner()
    Eyebrow(t(R.string.T_look_twice))
    Lede(t(R.string.T_two_things_the_list_of_payments_does_not_settle_on))
    watch.items.forEach { item ->
        val mark = store.watch[item.id] ?: "open"
        CardBox {
            Eyebrow("${item.name} · ${eur(item.amountCents)}")
            Spacer(Modifier.height(6.dp))
            Lede(watchLine(store.language, item, mark), strong = true)
            if (item.id == "refund") {
                ChoiceButton(t(R.string.T_still_waiting), mark == "wait") { store.watch[item.id] = "wait" }
                ChoiceButton(t(R.string.T_it_landed), mark == "landed") { store.watch[item.id] = "landed" }
            } else {
                ChoiceButton(t(R.string.T_that_s_mine), mark == "mine") { store.watch[item.id] = "mine" }
                ChoiceButton(t(R.string.T_that_isn_t_mine), mark == "not") { store.watch[item.id] = "not" }
            }
        }
    }
    Note(watch.note)
}

private fun watchLine(lang: Lang, item: WatchItemDto, mark: String): String {
    val choice = when {
        item.id == "refund" && mark == "landed" -> t(lang, R.string.T_you_marked_it_as_landed_today_s_balance_does_not_c)
        item.id == "refund" -> t(lang, R.string.T_it_is_not_on_s_tili_yet)
        mark == "mine" -> t(lang, R.string.T_you_recognised_it_it_stays_out_of_the_spending_tot)
        mark == "not" -> t(lang, R.string.T_marked_as_not_yours_here_it_only_marks_the_row)
        else -> t(lang, R.string.T_it_stays_out_of_the_spending_total_until_you_recog)
    }
    return "${item.whenLabel}. ${item.detail} $choice"
}

@Composable
private fun ImpulseScreen(store: SpendSightStore) {
    val impulse = store.ledger?.impulse ?: return
    val peak = impulse.peak
    SampleBanner()
    Eyebrow("${peak.whenLabel} · ${t(R.string.T_the_busiest_dated_day)}")
    Hero("${peak.count} ${if (peak.count == 1) t(R.string.T_payment) else t(R.string.T_payments)}")
    Lede(t(R.string.T_these_are_coffee_dining_taxis_and_shopping_groceri, peak.places.joinToString(", "), eur(peak.amountCents), impulse.coffeeVisits, impulse.woltOrders))
    Note(counted(t(R.string.T_n_1_13_nov), t(R.string.T_dining_coffee_taxis_and_shopping), impulse.count) + t(R.string.T_no_payment_in_this_ledger_has_a_time_of_day))
    CardBox {
        impulse.days.forEach { day ->
            HoldRow(day.whenLabel, "${day.count} ${if (day.count == 1) t(R.string.T_payment) else t(R.string.T_payments)} · ${day.places.joinToString(", ")}", eur(day.amountCents))
        }
    }
    TextLink(t(R.string.T_pause_one_purchase)) { store.push(Screen.Purchase) }
    TextLink(t(R.string.T_the_october_portrait)) { store.push(Screen.Spender) }
}

@Composable
private fun RunwayScreen(store: SpendSightStore) {
    val runway = store.ledger?.runway ?: return
    val safeText = store.ledger?.week?.safeToSpend
    val safe = if (safeText == null) null else presentMoney(safeText)
    SampleBanner()
    Eyebrow(t(R.string.T_at_november_s_pace_rent_left_out))
    Hero("${runway.leftDays} ${t(R.string.T_days)}")
    Lede(t(R.string.T_days_at_a_day_it_is_not_a_promise_bills_after_payd, runway.leftDays, eur(runway.perDayCents)))
    CardBox {
        HoldRow(t(R.string.T_november_so_far), t(R.string.T_n_1_nov_payments_other_than_rent, runway.days, runway.count), eur(runway.variableCents))
        HoldRow(t(R.string.T_rent_left_out), t(R.string.T_it_does_not_repeat_inside_this_month), eur(runway.rentCents))
        HoldRow(t(R.string.T_a_day), t(R.string.T_across_days_rounded, eur(runway.variableCents), runway.days), eur(runway.perDayCents))
        SumRow(t(R.string.T_s_tili_a_day), "${runway.leftDays} ${t(R.string.T_days)}")
    }
    Note(counted(t(R.string.T_n_1_nov, runway.days), t(R.string.T_all_payments_except_rent), runway.count) + t(R.string.T_safe_to_spend_on_the_design_sample_is_it_does_not, safe ?: "—"))
    TextLink(t(R.string.T_see_safe_to_spend)) { store.push(Screen.Week) }
}

@Composable
private fun SampleBanner() {
    Note(t(R.string.T_design_sample_today_spending_and_save_are_this_cus))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoachScreen(store: SpendSightStore, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(store.messages.size, store.asking) {
        val last = store.messages.lastIndex
        if (last >= 0) listState.animateScrollToItem(last)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().dismissKeyboardOnTap(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (store.messages.isEmpty()) {
            item {
                Text(coastOpening(store.language), color = Ink, fontSize = 17.sp, lineHeight = 24.sp)
            }
        }
        items(store.messages.size) { index ->
            Bubble(
                store.messages[index],
                onAction = { action -> if (action != null) store.push(action) },
                onEdit = { store.draft = it },
            )
        }
        if (store.asking) {
            item { Text(t(R.string.T_writing_the_answer), color = Secondary, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (store.disclaimer.isNotBlank()) {
                        Text(store.disclaimer, color = Secondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Text(
                        t(R.string.T_your_data),
                        color = Green,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { store.sheet = Sheet.Privacy }.padding(vertical = 6.dp),
                    )
                    Text(t(R.string.T_ask_next), color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        coachChips(store.language).forEachIndexed { i, (label, ask) ->
                            QuestionChip(label, if (i == 0) Lilac else Fill) { store.ask(ask) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun Bubble(message: CoachMessage, onAction: (Screen?) -> Unit, onEdit: (String) -> Unit) {
    val mine = message.role == "user"
    val paragraphs = message.paragraphs.ifEmpty { listOfNotNull(message.text) }
    if (mine) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Column(
                Modifier
                    .fillMaxWidth(0.78f)
                    .wrapContentWidth(Alignment.End)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Green)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                paragraphs.forEach { Text(it, color = Color.White, fontSize = 17.sp, lineHeight = 23.sp) }
            }
            message.text?.let { sent ->
                Text(
                    t(R.string.T_edit),
                    color = Green,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onEdit(sent) }.padding(top = 8.dp, bottom = 4.dp, end = 4.dp),
                )
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (message.refused) {
            Text(message.limit ?: t(R.string.T_outside_what_i_answer), color = Slip, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        message.fact?.let { fact ->
            CardBox {
                Text(fact.kicker, color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(presentMoney(fact.figure), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = Tabular)
                Text(fact.because, color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
        paragraphs.forEach { MarkdownBody(presentMoneyIn(it)) }
        message.chart?.let { CardBox { ChartBlock(it) } }
        if (message.actionLabel != null && message.action != null) {
            Text(
                message.actionLabel,
                color = Green,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onAction(message.action) }.padding(vertical = 6.dp),
            )
        }
        // The opening note is fixed app copy, not an answer.
        if (paragraphs.none { it in coastOpenings }) {
            Text(
                listOfNotNull(t(R.string.T_ai_answer), message.source).joinToString(" · "),
                color = Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Zone 3 on SpendSight, Savings and the chat: suggested questions (SpendSight only) and the ask field. */
@Composable
fun Composer(store: SpendSightStore) {
    val text = store.draft
    val gate = LocalEditorGate.current
    val hide = hideKeyboard()
    Column(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .drawBehind { drawLine(Line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f) }
            .dismissKeyboardOnTap()
            .padding(top = 10.dp, bottom = 8.dp),
    ) {
        if (store.current == Screen.Insight && text.isBlank()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                homeQuestions(store.language).forEachIndexed { index, question ->
                    QuestionChip(question, QuestionTints[index % QuestionTints.size]) { store.ask(question) }
                }
            }
        }
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Card)
                .border(1.dp, Tertiary, RoundedCornerShape(24.dp))
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(Icon.Chat, Green, 20.dp)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = text,
                onValueChange = { store.draft = it.take(280) },
                modifier = Modifier.weight(1f).padding(vertical = 12.dp).pointerInput(gate) {
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Main)
                        gate.down = true
                        waitForUpOrCancellation()
                    }
                },
                textStyle = TextStyle(color = Ink, fontSize = 17.sp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(t(R.string.T_ask_about_your_money), color = Secondary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            val enabled = text.isNotBlank() && !store.asking
            SendDot(
                enabled,
                Modifier.clip(CircleShape).clickable(enabled = enabled) {
                    hide()
                    store.ask(text)
                },
            )
        }
    }
}
