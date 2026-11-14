package fi.spendsight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HeroMuted = Color(0xFFD7E8DC)
private val EverydayBar = Color(0xFF5B8FA8)

@Composable
fun LanguageMenu(store: SpendSightStore, onDark: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            store.language.short,
            color = if (onDark) Color.White else Green,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (onDark) Color.White.copy(alpha = 0.18f) else Fill)
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
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

@Composable
private fun GreenHero(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Forest)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 22.dp),
        content = content,
    )
}

@Composable
fun HomeRoot(store: SpendSightStore, modifier: Modifier = Modifier) {
    val today = store.customer
    val spending = store.spendingView
    val month = monthName(spending.month, store.language)
    val look = worthALook(spending.categories, null)
    val hidden = store.balanceHidden
    Column(modifier.dismissKeyboardOnTap().verticalScroll(rememberScrollState())) {
        GreenHero {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(formatAsOf(today.asOf, store.language), color = HeroMuted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LanguageMenu(store, onDark = true)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { store.balanceHidden = !store.balanceHidden }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        HideEye(open = !hidden)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (hidden) t(R.string.T_show) else t(R.string.T_hide),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(t(R.string.T_home), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(t(R.string.T_main_account), color = HeroMuted, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                masked(hidden, today.balanceCents),
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${t(R.string.T_see_transactions)}  ›",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { store.push(Screen.Spending) },
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(t(R.string.T_for_you), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            ForYou(store, today, month, look, hidden)
            FunFact(store, spending, look, hidden)
            today.latestSalaryCents?.let { cents ->
                Note(
                    t(R.string.T_latest_on, masked(hidden, cents), today.latestSalaryDate ?: today.month),
                )
            }
        }
    }
}

@Composable
private fun HideEye(open: Boolean) {
    Canvas(Modifier.size(14.dp)) {
        val s = size.minDimension / 24f
        val stroke = Stroke(width = 1.8f * s, cap = StrokeCap.Round)
        val eye = androidx.compose.ui.graphics.Path().apply {
            moveTo(2.2f * s, 12f * s)
            cubicTo(6.4f * s, 6.6f * s, 17.6f * s, 6.6f * s, 21.8f * s, 12f * s)
            cubicTo(17.6f * s, 17.4f * s, 6.4f * s, 17.4f * s, 2.2f * s, 12f * s)
            close()
        }
        drawPath(eye, Color.White, style = stroke)
        if (open) {
            drawCircle(Color.White, radius = 2.2f * s, center = Offset(12f * s, 12f * s))
        } else {
            drawLine(Color.White, Offset(5f * s, 18f * s), Offset(19f * s, 6f * s), strokeWidth = 1.8f * s, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun masked(hidden: Boolean, cents: Int): String = if (hidden) "••••" else eur(cents)

@Composable
private fun ForYou(store: SpendSightStore, today: CustomerTodayDto, month: String, look: Look?, hidden: Boolean) {
    val scroll = rememberScrollState()
    Row(Modifier.horizontalScroll(scroll).height(156.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ForYouCard(Modifier.width(272.dp).fillMaxHeight(), Mint) {
            DotLabel(t(R.string.T_left_the_account), Forest)
            Text(masked(hidden, today.monthOutCents), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(month, color = Secondary, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(
                t(R.string.T_see_how),
                color = Forest,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { store.push(Screen.Insight) },
            )
        }
        if (look != null) {
            ForYouCard(
                Modifier.width(272.dp).fillMaxHeight().clickable { store.push(Screen.Category(look.name)) },
                Mist,
            ) {
                DotLabel(categoryLabel(look.name), EverydayBar)
                Text(masked(hidden, look.nowCents), color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(month, color = Secondary, fontSize = 13.sp)
            }
        }
    }
    if (look != null) {
        val page = if (scroll.maxValue == 0) 0 else if (scroll.value > scroll.maxValue / 2) 1 else 0
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(2) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (page == index) 7.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (page == index) Green else Pace),
                )
            }
        }
    }
}

@Composable
private fun ForYouCard(modifier: Modifier, fill: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(fill).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
private fun DotLabel(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Secondary, fontSize = 13.sp)
    }
}

@Composable
private fun FunFact(store: SpendSightStore, spending: SpendingDto, look: Look?, hidden: Boolean) {
    val prior = store.priorSpending
    val line = if (look == null) {
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Mint).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(t(R.string.T_fun_fact), color = Deep, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(line, color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
fun InsightRoot(store: SpendSightStore, modifier: Modifier = Modifier) {
    val spending = store.spendingView
    val month = monthName(spending.month, store.language)
    val essentials = essentialsCents(spending.categories)
    val everyday = everydayCents(spending.categories)
    val prior = store.priorSpending
    val look = worthALook(spending.categories, prior?.categories)
    Column(modifier.dismissKeyboardOnTap().verticalScroll(rememberScrollState())) {
        GreenHero {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LanguageMenu(store, onDark = true)
            }
            Text("SpendSight", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(t(R.string.T_left_the_account), color = HeroMuted, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(eur(spending.totalOutCents), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(t(R.string.T_not_a_limit), color = HeroMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(month.replaceFirstChar { it.uppercase() }, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (essentials + everyday > 0) {
                CardBox {
                    StackBar(listOf(Forest to essentials, EverydayBar to everyday))
                    Spacer(Modifier.height(10.dp))
                    LegendLine(t(R.string.T_essentials), eur(essentials), Forest)
                    LegendLine(t(R.string.T_everyday), eur(everyday), EverydayBar)
                }
            }
            val safeWeek = t(R.string.T_safe_week)
            val whereGo = t(R.string.T_where_go)
            val whereQuestion = t(R.string.T_where_question)
            TextLink(safeWeek) { store.ask(safeWeek) }
            Text(t(R.string.T_worth_a_look), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (look == null) {
                Note(t(R.string.T_none_here))
            } else {
                val detail = when {
                    look.deltaCents == null || prior == null -> eur(look.nowCents)
                    look.deltaCents > 0 -> t(R.string.T_more_than_month, eur(look.deltaCents), monthName(prior.month, store.language))
                    look.deltaCents < 0 -> t(R.string.T_less_than_month, eur(-look.deltaCents), monthName(prior.month, store.language))
                    else -> t(R.string.T_same_as, monthName(prior.month, store.language))
                }
                CardBox(onClick = { store.push(Screen.Category(look.name)) }) {
                    Text(categoryLabel(look.name), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(detail, color = Secondary, fontSize = 14.sp)
                }
            }
            outflowRows(spending.categories).sortedBy { it.amountCents }.take(5).forEach { cat ->
                ChevronRow(categoryLabel(cat.name), eur(-cat.amountCents)) { store.push(Screen.Category(cat.name)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AskChip(whereGo, Mint) { store.ask(whereQuestion) }
                AskChip(safeWeek, Color(0xFFE7EEF4)) { store.ask(safeWeek) }
            }
        }
    }
}

@Composable
private fun LegendLine(label: String, amount: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Secondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(amount, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AskChip(label: String, fill: Color, onClick: () -> Unit) {
    Text(
        label,
        color = Deep,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
fun SavingsRoot(store: SpendSightStore, modifier: Modifier = Modifier) {
    val save = store.customerSave
    val countLine = when (save.deposits) {
        0 -> t(R.string.T_no_deposit_in_window)
        1 -> t(R.string.T_one_deposit)
        else -> t(R.string.T_deposits_window, save.deposits)
    }
    Column(modifier.dismissKeyboardOnTap().verticalScroll(rememberScrollState())) {
        GreenHero {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LanguageMenu(store, onDark = true)
            }
            Text(t(R.string.T_savings), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(t(R.string.T_saved_in_total), color = HeroMuted, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(eur(save.depositedCents), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(countLine, color = HeroMuted, fontSize = 13.sp)
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(listOf(1, 3, 5).map { n -> "$n ${if (n == 1) t(R.string.T_year) else t(R.string.T_years)}" to (save.years == n) }) { index ->
                store.loadCustomerSave(listOf(1, 3, 5)[index])
            }
            CardBox {
                Text(t(R.string.T_recorded_deposits), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("${save.windowStart} – ${save.windowEnd}", color = Secondary, fontSize = 13.sp)
                val latest = save.latestDepositCents
                val latestDate = save.latestDepositDate
                if (latest != null && latestDate != null) {
                    Text(t(R.string.T_latest_on, eur(latest), latestDate), color = Ink, fontSize = 15.sp)
                }
                Note(knownNote(save.note))
            }
            Section(t(R.string.T_where_a_euro_can_sit))
            accounts(store.language).forEach { account ->
                ChevronRow(account.title, account.subtitle) { store.sheet = Sheet.Account(account.id) }
            }
        }
    }
}

private sealed interface OfferPeek {
    data class Coupon(val id: String) : OfferPeek
    data class Campaign(val id: String) : OfferPeek
}

@Composable
fun OffersRoot(store: SpendSightStore, modifier: Modifier = Modifier) {
    LaunchedEffect(store.ledger?.session?.customerId) { store.loadOffers() }
    var segment by remember { mutableStateOf(0) }
    var peek by remember { mutableStateOf<OfferPeek?>(null) }
    val loaded = store.coupons
    val campaigns = store.campaigns
    Column(modifier.dismissKeyboardOnTap().verticalScroll(rememberScrollState())) {
        GreenHero {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LanguageMenu(store, onDark = true)
            }
            Text(t(R.string.T_offers), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(t(R.string.T_coupons), color = HeroMuted, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (loaded == null) t(R.string.T_reading_offers) else t(R.string.T_active_of, loaded.activatedCount, loaded.count),
                color = Color.White,
                fontSize = if (loaded == null) 18.sp else 36.sp,
                fontWeight = FontWeight.Bold,
            )
            if (loaded != null && loaded.note.isNotBlank()) {
                Text(loaded.note, color = HeroMuted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            Note(loaded.note.ifBlank { t(R.string.T_none_here) })
                        } else {
                            loaded.coupons.forEachIndexed { index, coupon ->
                                OfferCard(listOf(Mint, Mist, Cream)[index % 3], onClick = { peek = OfferPeek.Coupon(coupon.couponId) }) {
                                    CouponRow(store, coupon)
                                }
                            }
                        }
                    } else if (campaigns == null || campaigns.campaigns.isEmpty()) {
                        Note(campaigns?.note?.ifBlank { t(R.string.T_none_here) } ?: t(R.string.T_none_here))
                    } else {
                        campaigns.campaigns.forEachIndexed { index, campaign ->
                            OfferCard(listOf(Mint, Mist, Cream)[index % 3], onClick = { peek = OfferPeek.Campaign(campaign.campaignId) }) {
                                CampaignRow(campaign)
                            }
                        }
                    }
                }
            }
        }
    }
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
private fun OfferCard(fill: Color, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun CouponRow(store: SpendSightStore, coupon: CouponDto) {
    val busy = store.activatingId == coupon.couponId
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (coupon.benefit.isNotBlank()) {
            Text(
                coupon.benefit,
                color = Forest,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp,
                modifier = Modifier.weight(0.36f).padding(end = 10.dp),
            )
        }
        Column(Modifier.weight(if (coupon.benefit.isNotBlank()) 0.64f else 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(coupon.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (coupon.description.isNotBlank()) {
                Text(coupon.description, color = Secondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            ActivatePill(store, coupon, busy)
        }
    }
}

@Composable
private fun CampaignRow(campaign: CampaignDto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(0.34f).padding(end = 10.dp)) {
            Text("${campaign.daysLeft}", color = Forest, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(t(R.string.T_days), color = Secondary, fontSize = 12.sp)
        }
        Column(Modifier.weight(0.66f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(campaign.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (campaign.description.isNotBlank()) {
                Text(campaign.description, color = Secondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${campaign.couponCount} ${t(R.string.T_coupons)}",
                color = Secondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ActivatePill(store: SpendSightStore, coupon: CouponDto, busy: Boolean) {
    val label = if (coupon.activated) t(R.string.T_active) else t(R.string.T_activate)
    Text(
        label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (coupon.activated) Tertiary else Forest)
            .clickable(enabled = !coupon.activated && !busy) { store.activateCoupon(coupon) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun OfferInfoDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Card)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    "×",
                    color = Secondary,
                    fontSize = 26.sp,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
                )
            }
            content()
        }
    }
}

@Composable
private fun CouponInfo(store: SpendSightStore, coupon: CouponDto) {
    if (coupon.benefit.isNotBlank()) {
        Text(coupon.benefit, color = Forest, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
    ActivatePill(store, coupon, store.activatingId == coupon.couponId)
}

@Composable
private fun CampaignInfo(campaign: CampaignDto) {
    Text("${campaign.daysLeft}", color = Forest, fontSize = 28.sp, fontWeight = FontWeight.Bold)
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
