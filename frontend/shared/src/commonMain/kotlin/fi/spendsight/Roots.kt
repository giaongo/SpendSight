package fi.spendsight

import androidx.compose.foundation.Canvas
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val CardText = Color(0xFF4A4A4F)

@Composable
fun LanguageMenu(store: SpendSightStore, onDark: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (onDark) Color.White.copy(alpha = 0.16f) else Fill)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                store.language.short,
                color = if (onDark) Color.White else Green,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                t(R.string.T_language),
                color = Tertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Lang.entries.forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (store.language == lang) "${lang.endonym}  ✓" else lang.endonym,
                            color = Ink,
                            fontWeight = if (store.language == lang) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        store.chooseLanguage(lang)
                        open = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(t(R.string.T_about), color = Ink) },
                onClick = {
                    open = false
                    store.sheet = Sheet.About
                },
            )
        }
    }
}

/** Zone 2: the content under the header. It scrolls; the header does not. */
@Composable
private fun ColumnScope.RootBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .dismissKeyboardOnTap()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        content = content,
    )
}

@Composable
private fun masked(hidden: Boolean, cents: Int): String = if (hidden) "••••••" else eur(cents)

// ---------------------------------------------------------------- Home

private data class Slide(
    val kicker: String,
    val fill: Color,
    val tone: Color,
    val dot: Color,
    val value: String,
    val line: String,
    val cta: String?,
    val onClick: () -> Unit,
)

@Composable
fun HomeRoot(store: SpendSightStore, modifier: Modifier = Modifier) {
    val today = store.customer
    val spending = store.spendingView
    val month = monthName(spending.month, store.language)
    val prior = store.priorSpending
    val look = worthALook(spending.categories, prior?.categories)
    val hidden = store.balanceHidden
    val safeWeek = t(R.string.T_safe_week)
    LaunchedEffect(store.ledger?.session?.customerId) {
        if (store.coupons == null) store.loadOffers()
    }
    Column(modifier) {
        RootHeader(
            title = t(R.string.T_home),
            keyLabel = t(R.string.T_main_account),
            keyNumber = masked(hidden, today.balanceCents),
            keySub = {
                Row(
                    Modifier.clickable { store.push(Screen.Spending) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t(R.string.T_see_transactions), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Glyph(Icon.ChevronRight, Color.White, 16.dp, stroke = 2.6f)
                }
            },
        ) {
            Text(formatAsOf(today.today ?: today.asOf, store.language), color = HeroMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LanguageMenu(store, onDark = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable { store.balanceHidden = !store.balanceHidden }
                        .padding(start = 10.dp, end = 14.dp),
                ) {
                    Glyph(if (hidden) Icon.EyeOff else Icon.EyeOpen, Color.White, 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (hidden) t(R.string.T_show) else t(R.string.T_hide),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        RootBody {
            val slides = buildList {
                // The design's first card: what is left of this week's budget.
                val safe = today.safeToSpend
                if (safe != null) {
                    val status = when (safe.status) {
                        "overdraft_risk" -> t(R.string.T_safe_over)
                        "tight" -> t(R.string.T_safe_tight)
                        else -> t(R.string.T_safe_on_track)
                    }
                    add(
                        Slide(
                            t(R.string.T_safe_to_spend), Mint, Deep, Color(0xFF00A651),
                            masked(hidden, safe.leftCents), t(R.string.T_safe_line, status), t(R.string.T_see_how),
                        ) { store.ask(safeWeek) },
                    )
                } else {
                    add(
                        Slide(
                            t(R.string.T_left_the_account), Mint, Deep, Color(0xFF00A651),
                            masked(hidden, today.monthOutCents), month, t(R.string.T_see_how),
                        ) { store.jump(Screen.Insight) },
                    )
                }
                // The design's order: spending change, its Friday evenings, then the coupon that fits it.
                val highlights = spending.highlights
                highlights?.spendingChange?.let { change ->
                    val name = categoryLabel(change.category)
                    add(
                        Slide(
                            t(R.string.T_spending_change), Mist, Color(0xFF1F4E99), EverydayBlue,
                            if (hidden) masked(true, 0) else "+" + eur(change.deltaCents),
                            t(R.string.T_more_on, name.lowercase(), monthName(change.priorMonth, store.language)),
                            t(R.string.T_see_named, name.lowercase()),
                        ) { store.push(Screen.Category(change.category)) },
                    )
                }
                highlights?.fridayEvenings?.let { friday ->
                    add(
                        Slide(
                            t(R.string.T_friday_evenings), Cream, Color(0xFF6B4A00), Color(0xFF8A6A1F),
                            masked(hidden, friday.amountCents),
                            t(R.string.T_friday_visits, friday.count, visitNoun(store.language, friday.category), month),
                            t(R.string.T_see_when),
                        ) { store.push(Screen.Category(friday.category)) },
                    )
                }
                if (highlights?.spendingChange == null && look != null) {
                    val delta = look.deltaCents
                    val name = categoryLabel(look.name)
                    val change = delta != null && prior != null && delta != 0
                    add(
                        Slide(
                            t(R.string.T_spending_change), Mist, Color(0xFF1F4E99), EverydayBlue,
                            if (change && delta != null) {
                                if (hidden) masked(true, 0) else (if (delta > 0) "+" else "") + eur(delta)
                            } else {
                                masked(hidden, look.nowCents)
                            },
                            if (change && prior != null) {
                                t(R.string.T_more_on, name.lowercase(), monthName(prior.month, store.language))
                            } else {
                                month
                            },
                            t(R.string.T_see_named, name.lowercase()),
                        ) { store.push(Screen.Category(look.name)) },
                    )
                }
                // The coupon that fits this customer's spending comes first (the Rosso coupon in the demo).
                store.coupons?.coupons?.let { list -> list.firstOrNull { it.tag != null } ?: list.firstOrNull() }?.let { coupon ->
                    val line = if (coupon.line.isNotBlank()) "${coupon.title}: ${coupon.line.substringBefore(" · ")}" else coupon.title
                    add(
                        Slide(
                            t(R.string.T_coupon), Lilac, Color(0xFF4B3A8C), Color(0xFF6B4FA0),
                            coupon.benefit.ifBlank { coupon.title },
                            t(R.string.T_activate_in_offers, line),
                            if (coupon.activated) t(R.string.T_activated) else t(R.string.T_activate),
                        ) { store.jump(Screen.Offers) },
                    )
                }
            }
            // The design pulls "Did you know?" 8 up under the carousel.
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ForYou(slides)
                FunFact(store, spending, look, hidden)
            }
            Overview(store, spending, month, hidden)
            MainAccount(store, today, spending, hidden)
        }
    }
}

/** "restaurant" in "4 restaurant visits": the category label, singular in English. */
private fun visitNoun(lang: Lang, category: String): String {
    val label = categoryLabel(lang, category).lowercase()
    return if (lang == Lang.En && label.endsWith("s")) label.dropLast(1) else label
}

/** Card width 300, gap 12: one snap step, as in the design's carousel. */
private val SlideWidth = 300.dp
private val SlideGap = 12.dp
private val PageInset = 20.dp

/**
 * "For you" carousel from the design: the section bleeds to the screen edges, cards snap to the
 * page inset, all cards share the tallest card's height, and the dots and arrows follow the scroll.
 */
@Composable
private fun ForYou(slides: List<Slide>) {
    if (slides.isEmpty()) return
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val step = with(density) { (SlideWidth + SlideGap).toPx() }
    val current by remember(slides.size) {
        derivedStateOf {
            if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue) {
                slides.size - 1
            } else {
                (scroll.value / step).roundToInt().coerceIn(0, slides.size - 1)
            }
        }
    }
    fun go(index: Int) {
        val target = index.coerceIn(0, slides.size - 1)
        scope.launch { scroll.animateScrollTo((target * step).roundToInt().coerceAtMost(scroll.maxValue)) }
    }
    // As in the design: a swipe of more than 40 moves one card in that direction, otherwise it settles back.
    var swipeStart by remember { mutableStateOf(0) }
    val threshold = with(density) { 40.dp.toPx() }
    LaunchedEffect(scroll.isScrollInProgress) {
        if (scroll.isScrollInProgress) {
            swipeStart = scroll.value
            return@LaunchedEffect
        }
        val base = (swipeStart / step).roundToInt()
        val delta = scroll.value - swipeStart
        val index = when {
            delta > threshold -> base + 1
            delta < -threshold -> base - 1
            else -> base
        }.coerceIn(0, slides.size - 1)
        val target = (index * step).roundToInt().coerceAtMost(scroll.maxValue)
        if (target != scroll.value) scroll.animateScrollTo(target)
    }
    Column(
        Modifier.bleed(PageInset),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(start = 24.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t(R.string.T_for_you), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NavCircle(Icon.ChevronLeft, current > 0) { go(current - 1) }
                NavCircle(Icon.ChevronRight, current < slides.size - 1) { go(current + 1) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(top = 4.dp, bottom = 10.dp)
                .padding(horizontal = PageInset)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(SlideGap),
        ) {
            slides.forEach { slide ->
                Column(
                    Modifier
                        .width(SlideWidth)
                        .fillMaxHeight()
                        .heightIn(min = 176.dp)
                        .shadow(4.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
                        .clip(RoundedCornerShape(22.dp))
                        .background(slide.fill)
                        .clickable(onClick = slide.onClick)
                        .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(slide.dot))
                        Spacer(Modifier.width(8.dp))
                        Text(slide.kicker, color = slide.tone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(slide.value, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, lineHeight = 36.sp, style = Tabular, maxLines = 1)
                    Text(slide.line, color = CardText, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                    slide.cta?.let {
                        Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                            Text(it, color = Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            slides.indices.forEach { i ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).clickable { go(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    val width by animateDpAsState(if (i == current) 20.dp else 8.dp)
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == current) Ink else Tertiary),
                    )
                }
            }
        }
    }
}

/** Lays the content out wider than its parent by [inset] on both sides, like `margin: 0 -20px`. */
private fun Modifier.bleed(inset: Dp): Modifier = layout { measurable, constraints ->
    val extra = inset.roundToPx()
    val width = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

@Composable
private fun FunFact(store: SpendSightStore, spending: SpendingDto, look: Look?, hidden: Boolean) {
    val prior = store.priorSpending
    val change = spending.highlights?.spendingChange
    val line = if (change != null) {
        t(
            R.string.T_fact_visits,
            change.count,
            visitNoun(store.language, change.category),
            masked(hidden, change.priorCents),
            monthName(change.priorMonth, store.language),
            masked(hidden, change.amountCents),
            monthName(change.month, store.language),
        )
    } else if (look == null) {
        if (hidden) t(R.string.T_none_here) else knownNote(spending.note)
    } else if (prior == null) {
        t(R.string.T_largest_share, categoryLabel(look.name), masked(hidden, look.nowCents))
    } else {
        val then = outflowRows(prior.categories).firstOrNull { it.name == look.name }?.amountCents?.let { -it } ?: 0
        t(
            R.string.T_fact_compare,
            categoryLabel(look.name),
            masked(hidden, then),
            monthName(prior.month, store.language),
            masked(hidden, look.nowCents),
            monthName(spending.month, store.language),
        )
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 0.dp).clip(RoundedCornerShape(22.dp)).background(Mint).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(Icon.Bulb, Deep, 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(t(R.string.T_fun_fact), color = Deep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(line, color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
        AskFieldButton { store.push(Screen.Coach) }
        HomeSuggestions(store, spending)
    }
}

/** Question chips under the Home ask bar. Each one is a question the coach already answers. */
@Composable
private fun HomeSuggestions(store: SpendSightStore, spending: SpendingDto) {
    val ateOut = t(R.string.T_ask_ate_out)
    val safeWeek = t(R.string.T_safe_week)
    val where = t(R.string.T_where_question)
    val save = t(R.string.T_save_more)
    val spike = spending.highlights?.spendingChange?.category == "Restaurants"
    val questions = buildList {
        if (spike) add(ateOut)
        if (store.customer.safeToSpend != null) add(safeWeek)
        add(where)
        add(save)
    }.distinct().take(3)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        questions.forEach { question -> QuestionChip(question, Card) { store.ask(question) } }
    }
}

@Composable
private fun Overview(store: SpendSightStore, spending: SpendingDto, month: String, hidden: Boolean) {
    val rows = outflowRows(spending.categories)
    if (rows.isEmpty()) return
    val essentials = rows.filter { isEssential(it.name) }.sortedBy { it.amountCents }
    val everyday = rows.filter { !isEssential(it.name) }.sortedBy { it.amountCents }
    var open by remember { mutableStateOf<Int?>(null) }
    DesignSection {
        SectionHead(t(R.string.T_overview))
        WhiteList {
            listOf(
                t(R.string.T_essentials_in, month) to essentials,
                t(R.string.T_everyday_in, month) to everyday,
            ).forEachIndexed { index, (label, items) ->
                if (index > 0) Hairline()
                val expanded = open == index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { open = if (expanded) null else index }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = Ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(masked(hidden, items.sumOf { -it.amountCents }), color = Secondary, fontSize = 17.sp, fontWeight = FontWeight.Medium, style = Tabular)
                    Spacer(Modifier.width(12.dp))
                    Glyph(Icon.ChevronRight, Pace, 16.dp, stroke = 2.6f, modifier = Modifier.rotate(if (expanded) 90f else 0f))
                }
                if (expanded) {
                    items.forEach { cat ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { store.push(Screen.Category(cat.name)) }
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(categoryLabel(cat.name), color = Graphite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(masked(hidden, -cat.amountCents), color = Graphite, fontSize = 15.sp, fontWeight = FontWeight.Medium, style = Tabular)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainAccount(store: SpendSightStore, today: CustomerTodayDto, spending: SpendingDto, hidden: Boolean) {
    DesignSection {
        SectionHead(t(R.string.T_main_account), t(R.string.T_see_all)) { store.push(Screen.Spending) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat(t(R.string.T_income_month), masked(hidden, today.monthInCents), Deep, Modifier.weight(1f))
            MiniStat(t(R.string.T_outcome_month), masked(hidden, today.monthOutCents), Ink, Modifier.weight(1f))
        }
        val latest = spending.transactions.sortedByDescending { it.txDate }.take(4)
        if (latest.isNotEmpty()) {
            WhiteList {
                latest.forEachIndexed { index, tx ->
                    if (index > 0) Hairline()
                    val credit = tx.amountCents > 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .clickable { store.push(Screen.Category(tx.category)) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(categoryLabel(tx.category), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatAsOf(tx.txDate, store.language)} · ${typeLabel(tx.transactionType)}", color = Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (credit && !hidden) "+${eur(tx.amountCents)}" else masked(hidden, tx.amountCents),
                            color = if (credit) Deep else Ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = Tabular,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Card).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = Secondary, fontSize = 13.sp)
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Tabular, maxLines = 1)
    }
}

// ---------------------------------------------------------------- SpendSight

@Composable
fun InsightRoot(store: SpendSightStore, modifier: Modifier = Modifier) {
    val spending = store.spendingView
    val month = monthName(spending.month, store.language)
    // The service splits the baskets (taxis are everyday, not transport essentials).
    val essentials = spending.baskets?.essentialsCents ?: essentialsCents(spending.categories)
    val everyday = spending.baskets?.everydayCents ?: everydayCents(spending.categories)
    val prior = store.priorSpending
    val look = worthALook(spending.categories, prior?.categories)
    val whereQuestion = t(R.string.T_where_question)
    val safeWeek = t(R.string.T_safe_week)
    // The design's header is this week's safe-to-spend; customers with no salary keep the month's total.
    val safe = store.customer.safeToSpend
    val safeStatus = when (safe?.status) {
        "overdraft_risk" -> t(R.string.T_safe_over)
        "tight" -> t(R.string.T_safe_tight)
        else -> t(R.string.T_safe_on_track)
    }.replaceFirstChar { it.uppercase() }
    Column(modifier) {
        RootHeader(
            title = "SpendSight",
            keyLabel = if (safe != null) t(R.string.T_safe_week_label) else t(R.string.T_left_the_account),
            keyNumber = if (safe != null) eur(safe.leftCents) else eur(spending.totalOutCents),
            keySub = {
                KeySubText(
                    if (safe != null) t(R.string.T_safe_header_sub, safeStatus, eur(safe.weeklyCents))
                    else month.replaceFirstChar { it.uppercase() },
                )
            },
        ) {
            Spacer(Modifier.width(1.dp))
            LanguageMenu(store, onDark = true)
        }
        RootBody {
            DesignSection {
                SectionHead(month.replaceFirstChar { it.uppercase() })
                CardBox {
                    val total = essentials + everyday
                    Column(
                        Modifier.fillMaxWidth().clickable { store.ask(whereQuestion) },
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (total > 0) {
                            Text(
                                t(R.string.T_basket_total, eur(total)),
                                color = Ink,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                style = Tabular,
                            )
                            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (essentials > 0) Box(Modifier.fillMaxHeight().weight(essentials.toFloat()).background(EssentialTeal))
                                if (everyday > 0) Box(Modifier.fillMaxHeight().weight(everyday.toFloat()).background(EverydayBlue))
                            }
                            BasketLegendRow(EssentialTeal, t(R.string.T_essentials), t(R.string.T_basket_essentials_what), essentials, total)
                            BasketLegendRow(EverydayBlue, t(R.string.T_everyday), t(R.string.T_basket_everyday_what), everyday, total)
                        }
                    }
                    Text(
                        t(R.string.T_see_week_budget),
                        color = Green,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp).clickable { store.ask(safeWeek) }.padding(vertical = 10.dp),
                    )
                }
            }
            DesignSection {
                SectionHead(t(R.string.T_worth_a_look))
                if (look == null) {
                    Note(t(R.string.T_none_here))
                } else {
                    val delta = look.deltaCents
                    val text = buildAnnotatedString {
                        append("${categoryLabel(look.name)}: ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(
                                when {
                                    delta == null || prior == null -> eur(look.nowCents)
                                    delta > 0 -> t(R.string.T_more_than_month, eur(delta), monthName(prior.month, store.language))
                                    delta < 0 -> t(R.string.T_less_than_month, eur(-delta), monthName(prior.month, store.language))
                                    else -> t(R.string.T_same_as, monthName(prior.month, store.language))
                                },
                            )
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Card)
                            .clickable { store.push(Screen.Category(look.name)) }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(Icon.Trend, EverydayBlue, 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(text, color = Ink, fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Glyph(Icon.ChevronRight, Pace, 18.dp, stroke = 2.4f)
                    }
                }
            }
        }
    }
}

/** One line of the basket legend: a colour dot, the group, what is in it, its share and its amount. */
@Composable
private fun BasketLegendRow(color: Color, label: String, what: String, cents: Int, total: Int) {
    val share = if (total > 0) ((cents * 100L + total / 2) / total).toInt() else 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$label · $share\u00A0%",
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(what, color = Secondary, fontSize = 13.sp)
        }
        Text(eur(cents), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
    }
}

// ---------------------------------------------------------------- Savings

@Composable
fun SavingsRoot(store: SpendSightStore, modifier: Modifier = Modifier) {
    val save = store.customerSave
    if (save.savingsAccounts.isNotEmpty()) {
        SavingsAccountsRoot(store, save, modifier)
        return
    }
    val countLine = when (save.deposits) {
        0 -> t(R.string.T_no_deposit_in_window)
        1 -> t(R.string.T_one_deposit)
        else -> t(R.string.T_deposits_window, save.deposits)
    }
    Column(modifier) {
        RootHeader(
            title = t(R.string.T_savings),
            keyLabel = t(R.string.T_saved_in_total),
            keyNumber = eur(save.depositedCents),
            keySub = { KeySubText(countLine) },
        ) {
            Spacer(Modifier.width(1.dp))
            LanguageMenu(store, onDark = true)
        }
        RootBody {
            DesignSection {
                Segmented(listOf(1, 3, 5).map { n -> "$n ${if (n == 1) t(R.string.T_year) else t(R.string.T_years)}" to (save.years == n) }) { index ->
                    store.loadCustomerSave(listOf(1, 3, 5)[index])
                }
                CardBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icon.Calendar, Mint, Deep)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t(R.string.T_recorded_deposits), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("${save.windowStart} – ${save.windowEnd}", color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Text(eur(save.depositedCents), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Tabular)
                    }
                    val latest = save.latestDepositCents
                    val latestDate = save.latestDepositDate
                    if (latest != null && latestDate != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(t(R.string.T_latest_on, eur(latest), latestDate), color = Ink, fontSize = 17.sp, lineHeight = 24.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(knownNote(save.note), color = Secondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
                }
            }
            DesignSection {
                SectionHead(t(R.string.T_your_savings_accounts))
                val tiles = listOf(
                    Triple(Icon.Wallet, Mint, Deep),
                    Triple(Icon.House, Mint, Deep),
                    Triple(Icon.Coin, Mist, Color(0xFF1F4E99)),
                    Triple(Icon.Calendar, Cream, Color(0xFF6B4A00)),
                    Triple(Icon.Sprout, Lilac, Color(0xFF4B3A8C)),
                )
                accounts(store.language).forEachIndexed { index, account ->
                    val (icon, fill, ink) = tiles[index % tiles.size]
                    CardBox(onClick = { store.sheet = Sheet.Account(account.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(icon, fill, ink)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(account.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(account.subtitle, color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Text(
                            t(R.string.T_read_more),
                            color = Green,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The design's Savings tab: the customer's savings accounts and their goals. */
@Composable
private fun SavingsAccountsRoot(store: SpendSightStore, save: CustomerSaveDto, modifier: Modifier) {
    val accounts = save.savingsAccounts
    Column(modifier) {
        RootHeader(
            title = t(R.string.T_savings),
            keyLabel = t(R.string.T_saved_in_total),
            keyNumber = eur(save.savingsTotalCents),
            keySub = { KeySubText(t(R.string.T_in_savings_accounts, accounts.size)) },
        ) {
            Spacer(Modifier.width(1.dp))
            LanguageMenu(store, onDark = true)
        }
        RootBody {
            DesignSection {
                SectionHead(t(R.string.T_your_savings_accounts))
                accounts.forEach { account -> SavingsAccountCard(store, account) }
            }
        }
    }
}

@Composable
private fun SavingsAccountCard(store: SpendSightStore, account: SavingsAccountDto) {
    val (icon, fill, ink) = if (account.kind == "asp") Triple(Icon.House, Mint, Deep) else Triple(Icon.Coin, Mist, Color(0xFF1F4E99))
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, fill, ink)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(account.name, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (account.subtitle.isNotBlank()) {
                    Text(account.subtitle, color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text(eur(account.balanceCents), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Tabular)
        }
        val goal = account.goalCents
        if (goal != null && goal > 0) {
            val amount = eur(goal)
            val line = t(R.string.T_goal_for, amount, account.goalFor ?: "")
            val at = line.indexOf(amount)
            Text(
                buildAnnotatedString {
                    append(line)
                    if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + amount.length)
                },
                color = Ink,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 14.dp),
            )
            Box(
                Modifier.padding(top = 10.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Fill),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((account.goalShare ?: 0.0).toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF00A651)),
                )
            }
            Text(
                t(R.string.T_goal_there, "${account.goalPercent ?: 0}\u00A0%"),
                color = Secondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Text(
                t(R.string.T_no_goal_yet),
                color = Secondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 10.dp),
            )
            // As in the design, "Set a goal" opens the save-more answer.
            val question = t(R.string.T_save_more)
            Box(Modifier.heightIn(min = 44.dp).clickable { store.ask(question) }, contentAlignment = Alignment.CenterStart) {
                Text(t(R.string.T_set_a_goal), color = Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun IconTile(icon: Icon, fill: Color, ink: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(fill), contentAlignment = Alignment.Center) {
        Glyph(icon, ink, 22.dp)
    }
}

// ---------------------------------------------------------------- Offers

private sealed interface OfferPeek {
    data class Coupon(val id: String) : OfferPeek
    data class Campaign(val id: String) : OfferPeek
}

private val StubTints = listOf(Mint to Deep, Mist to Color(0xFF1F4E99), Cream to Color(0xFF6B4A00), Lilac to Color(0xFF4B3A8C))

@Composable
fun OffersRoot(store: SpendSightStore, modifier: Modifier = Modifier) {
    LaunchedEffect(store.ledger?.session?.customerId) { store.loadOffers() }
    var segment by remember { mutableStateOf(0) }
    var peek by remember { mutableStateOf<OfferPeek?>(null) }
    var cardOpen by remember { mutableStateOf(false) }
    val loaded = store.coupons
    val campaigns = store.campaigns
    val activeLabel = if (loaded == null) "" else t(R.string.T_active_of, loaded.activatedCount, loaded.count)
    Column(modifier) {
        RootHeader(
            title = t(R.string.T_offers),
            keyLabel = t(R.string.T_active_coupons),
            keyNumber = if (loaded == null) "–" else activeLabel,
            keySub = { KeySubText(t(R.string.T_claim_hint)) },
        ) {
            Spacer(Modifier.width(1.dp))
            LanguageMenu(store, onDark = true)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                store.offersError != null && loaded == null -> {
                    Lede(store.offersError ?: "")
                    PrimaryButton(t(R.string.T_try_again)) { store.loadOffers() }
                }
                loaded == null -> Note(t(R.string.T_reading_offers))
                else -> {
                    Segmented(
                        listOf(
                            t(R.string.T_coupons) to (segment == 0),
                            t(R.string.T_campaigns) to (segment == 1),
                        ),
                    ) { segment = it }
                    if (segment == 0) {
                        if (loaded.coupons.isEmpty()) {
                            Note(t(R.string.T_none_here))
                        } else {
                            loaded.coupons.forEachIndexed { index, coupon ->
                                CouponCard(store, coupon, StubTints[index % StubTints.size]) { peek = OfferPeek.Coupon(coupon.couponId) }
                            }
                        }
                    } else if (campaigns == null || campaigns.campaigns.isEmpty()) {
                        Note(t(R.string.T_none_here))
                    } else {
                        campaigns.campaigns.forEach { campaign ->
                            CampaignCard(campaign) { peek = OfferPeek.Campaign(campaign.campaignId) }
                        }
                    }
                    Text(
                        t(R.string.T_demo_offers),
                        color = Secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
                    )
                    // The design's status line: under the list, so it never covers the header or a coupon.
                    store.couponStatus?.let { status ->
                        Text(
                            status,
                            color = Deep,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().background(Bg).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(Green)
                    .clickable { cardOpen = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(Icon.Card, Color.White, 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(t(R.string.T_show_card), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (cardOpen) MemberCardSheet(store, activeLabel) { cardOpen = false }
    when (val current = peek) {
        is OfferPeek.Coupon -> loaded?.coupons?.firstOrNull { it.couponId == current.id }?.let { coupon ->
            OfferInfoDialog(onDismiss = { peek = null }) {
                CouponInfo(store, coupon)
            }
        }
        is OfferPeek.Campaign -> campaigns?.campaigns?.firstOrNull { it.campaignId == current.id }?.let { campaign ->
            OfferInfoDialog(onDismiss = { peek = null }) {
                CampaignInfo(campaign)
            }
        }
        null -> Unit
    }
}

@Composable
private fun CouponCard(store: SpendSightStore, coupon: CouponDto, tint: Pair<Color, Color>, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(22.dp))
            .background(Card)
            .clickable(onClick = onOpen),
    ) {
        Box(
            Modifier.width(84.dp).fillMaxHeight().background(tint.first).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                coupon.benefit.ifBlank { "%" },
                color = tint.second,
                fontSize = if (coupon.benefit.length > 7) 16.sp else 22.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
        Canvas(Modifier.width(2.dp).fillMaxHeight()) {
            drawLine(
                Color.White,
                Offset(size.width / 2, 0f),
                Offset(size.width / 2, size.height),
                strokeWidth = size.width,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )
        }
        Column(
            Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            coupon.tag?.takeIf { it.isNotBlank() }?.let { tag ->
                Box(
                    Modifier.heightIn(min = 28.dp).clip(RoundedCornerShape(14.dp)).background(Mint).padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(tag, color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(coupon.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = coupon.line.ifBlank { coupon.description }
            if (sub.isNotBlank()) {
                Text(sub, color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            ActivatePill(store, coupon)
            if (coupon.activated) StampRow(store, coupon)
        }
    }
}

@Composable
private fun CampaignCard(campaign: CampaignDto, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Card)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)).background(Mint),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                campaign.brand.ifBlank { "${campaign.couponCount} ${t(R.string.T_coupons)}" },
                color = Green,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(campaign.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (campaign.description.isNotBlank()) {
            Text(campaign.description, color = Ink, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (campaign.endsOn.isNotBlank()) t(R.string.T_ends_on, campaign.endsOn) else "${campaign.daysLeft} ${t(R.string.T_days)}",
            color = Secondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(t(R.string.T_read_more), color = Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun ActivatePill(store: SpendSightStore, coupon: CouponDto) {
    val busy = store.activatingId == coupon.couponId
    val on = coupon.activated
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (on) Card else Green)
            .border(2.dp, Green, RoundedCornerShape(22.dp))
            .clickable(enabled = !busy) { store.toggleCoupon(coupon) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (on) t(R.string.T_activated) else t(R.string.T_activate),
            color = if (on) Green else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The stamp card under an activated coupon with monthly uses: one circle per discounted visit. */
@Composable
private fun StampRow(store: SpendSightStore, coupon: CouponDto) {
    val total = coupon.usesPerMonth ?: return
    val used = (coupon.usedThisMonth ?: 0).coerceIn(0, total)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Bg).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(total) { index ->
                val filled = index < used
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (filled) Deep else Card)
                        .then(if (filled) Modifier else Modifier.dashedCircle(Tertiary)),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(Icon.Ticket, if (filled) Color.White else Tertiary, 18.dp)
                }
            }
        }
        val renews = coupon.renewsOn?.let { formatDayMonth(it, store.language) } ?: ""
        Text(t(R.string.T_stamps_used, used, total, renews), color = CardText, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

private fun Modifier.dashedCircle(color: Color): Modifier = drawBehind {
    val stroke = 2.dp.toPx()
    drawCircle(
        color,
        radius = size.minDimension / 2 - stroke / 2,
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberCardSheet(store: SpendSightStore, activeLabel: String, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val session = store.ledger?.session
    val customer = session?.customerId ?: ""
    val last4 = customer.filter { it.isDigit() }.takeLast(4).padStart(4, '0')
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Card) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("S-Etukortti", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.size(44.dp).clip(CircleShape).background(Bg).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Glyph(Icon.Close, Ink, 18.dp, stroke = 2.4f)
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).headerGlow().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("S-Etukortti", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(t(R.string.T_member), color = HeroMuted, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(customer, color = Color.White, fontSize = 15.sp)
                    Text("•••• $last4", color = Color.White, fontSize = 15.sp, style = Tabular)
                }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Barcode(customer)
                    Text("6012 •••• •••• $last4", color = Ink, fontSize = 13.sp, letterSpacing = 2.sp, style = Tabular)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Mint).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(Icon.Nfc, Deep, 40.dp)
                Spacer(Modifier.width(14.dp))
                Text(t(R.string.T_card_reader, activeLabel), color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
    }
}

/** A drawn stand-in for the card barcode, stable for one customer. */
@Composable
private fun Barcode(seed: String) {
    val widths = remember(seed) {
        var h = seed.fold(17) { acc, c -> acc * 31 + c.code }
        List(56) {
            h = h * 1103515245 + 12345
            1 + ((h ushr 16) and 3)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val unit = size.width / widths.sum().toFloat()
        var x = 0f
        widths.forEachIndexed { index, w ->
            if (index % 2 == 0) drawRect(Ink, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(w * unit, size.height))
            x += w * unit
        }
    }
}

@Composable
private fun OfferInfoDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Card)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Bg).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Glyph(Icon.Close, Ink, 18.dp, stroke = 2.4f)
                }
            }
            content()
        }
    }
}

@Composable
private fun CouponInfo(store: SpendSightStore, coupon: CouponDto) {
    if (coupon.benefit.isNotBlank()) {
        Text(coupon.benefit, color = Deep, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
    Text(coupon.title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    if (coupon.description.isNotBlank()) Lede(coupon.description)
    if (coupon.product.isNotBlank()) Text(coupon.product, color = Secondary, fontSize = 14.sp)
    coupon.campaignTitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = Tertiary, fontSize = 13.sp) }
    if (coupon.reason.isNotBlank()) Text(coupon.reason, color = Tertiary, fontSize = 13.sp, lineHeight = 18.sp)
    coupon.minSpend?.let { Text(t(R.string.T_from_amount, presentMoney(it)), color = Ink, fontSize = 14.sp) }
    if (coupon.validFrom.isNotBlank() && coupon.validTo.isNotBlank()) {
        Text("${coupon.validFrom} – ${coupon.validTo}", color = Secondary, fontSize = 13.sp)
    }
    Text("${coupon.daysLeft} ${t(R.string.T_days)}", color = Secondary, fontSize = 13.sp)
    if (coupon.terms.isNotBlank()) {
        Text(t(R.string.T_terms), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Note(coupon.terms)
    }
    Spacer(Modifier.height(4.dp))
    ActivatePill(store, coupon)
}

@Composable
private fun CampaignInfo(campaign: CampaignDto) {
    Text(campaign.title, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    if (campaign.description.isNotBlank()) Lede(campaign.description)
    Text("${campaign.daysLeft} ${t(R.string.T_days)} · ${campaign.couponCount} ${t(R.string.T_coupons)}", color = Secondary, fontSize = 14.sp)
    if (campaign.startsOn.isNotBlank() && campaign.endsOn.isNotBlank()) {
        Text("${campaign.startsOn} – ${campaign.endsOn}", color = Secondary, fontSize = 13.sp)
    }
    if (campaign.terms.isNotBlank()) {
        Text(t(R.string.T_terms), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Note(campaign.terms)
    }
}
