package fi.spendsight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The five home questions each draw their own card. Figures come from the coach card.
 * A weekly limit, a household spending average, and a savings total are shown only when
 * the service counted them.
 */
@Composable
fun CoachAnswer(message: CoachMessage, onAsk: (String) -> Unit, onOpen: (Screen) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (message.homeAsk) {
            HomeAsk.Where -> WhereAnswer(message, onOpen)
            HomeAsk.Spender -> SpenderAnswer(message, onAsk)
            HomeAsk.Week -> WeekAnswer(message, onOpen)
            HomeAsk.Save -> SaveAnswer(message)
            HomeAsk.Household -> HouseholdAnswer(message)
            null -> GenericAnswer(message)
        }
        CaptionLine(message)
        AskNext(message, onAsk)
    }
}

@Composable
private fun WhereAnswer(message: CoachMessage, onOpen: (Screen) -> Unit) {
    val card = message.card
    val headline = card?.headline
    if (headline != null) {
        Lead(t(R.string.T_went_out, eur(headline.amountCents), monthLabel(card.context)))
    } else {
        Prose(message)
    }
    val detail = message.detail ?: card?.verdictDetail
    if (!detail.isNullOrBlank()) Detail(detail)
    if (card == null) return
    val (essentials, unexpected, everyday) = buckets(card.items)
    val rows = everydayRows(card.items)
    CardBox {
        Text(card.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        val chip = monthLabel(card.context)
        if (chip.isNotEmpty()) {
            Text(
                chip,
                color = Forest,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mint)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        val total = essentials + unexpected + everyday
        if (total > 0) {
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (essentials > 0) Box(Modifier.fillMaxHeight().weight(essentials.toFloat()).background(EssentialTeal))
                if (unexpected > 0) Box(Modifier.fillMaxHeight().weight(unexpected.toFloat()).background(Cream))
                if (everyday > 0) Box(Modifier.fillMaxHeight().weight(everyday.toFloat()).background(EverydayBlue))
            }
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BucketPill(t(R.string.T_essentials), essentials, EssentialTeal, Modifier.weight(1f))
            BucketPill(t(R.string.T_unexpected), unexpected, Cream, Modifier.weight(1f))
            BucketPill(t(R.string.T_everyday), everyday, EverydayBlue, Modifier.weight(1f))
        }
        if (rows.isNotEmpty()) {
            Text(
                t(R.string.T_everyday_top),
                color = Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            rows.forEach { item ->
                Hairline(0.dp)
                ChevronRow(categoryLabel(item.label), eur(item.amountCents)) { onOpen(Screen.Category(item.key)) }
            }
        }
        val footer = card.verdictDetail
        if (!footer.isNullOrBlank()) {
            Text(footer, color = Secondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun SpenderAnswer(message: CoachMessage, onAsk: (String) -> Unit) {
    val profile = message.card?.profile
    val saveMore = t(R.string.T_save_more)
    if (profile == null) {
        Prose(message)
        message.card?.let { GenericCardBody(it) }
        return
    }
    val title = spenderTitle(profile.key)
    Lead(t(R.string.T_you_are, title))
    if (profile.bigBuys > 0) {
        Detail(t(R.string.T_buys_over, profile.bigBuys, eur(profile.thresholdCents), profile.window))
    }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Mint),
                contentAlignment = Alignment.Center,
            ) { Glyph(Icon.Wallet, Forest, 24.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(spenderLine(profile.key), color = Secondary, fontSize = 14.sp, lineHeight = 19.sp)
                if (profile.window.isNotBlank()) {
                    Text(profile.window, color = Tertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        Text(t(R.string.T_your_strength), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(
            t(R.string.T_essentials_paid, profile.monthsWithEssentials, profile.months),
            color = Secondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(t(R.string.T_try_this), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text(saveMore, color = Secondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        PrimaryButton(t(R.string.T_try_eating_less)) { onAsk(saveMore) }
    }
}

@Composable
private fun WeekAnswer(message: CoachMessage, onOpen: (Screen) -> Unit) {
    Prose(message)
    val card = message.card
    val headline = card?.headline
    if (card == null || headline == null) return
    var open by remember(message) { mutableStateOf(false) }
    CardBox {
        Text(card.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            eur(headline.amountCents),
            color = Ink,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            style = Tabular,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(headline.label, color = Secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
        val calm = card.verdict?.startsWith("Less") == true || card.verdict?.startsWith("The same") == true
        if (!card.verdict.isNullOrBlank()) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (calm) {
                    Glyph(Icon.Sight, Forest, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(t(R.string.T_on_track), color = Forest, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(" · ", color = Secondary, fontSize = 15.sp)
                }
                Text(card.verdict, color = Ink, fontSize = 15.sp)
            }
        }
        card.verdictDetail?.let { Text(it, color = Secondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp)) }
        Text(t(R.string.T_week_not_limit), color = Secondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
        Text(t(R.string.T_how_worked), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextLink(t(R.string.T_how_i_got)) { open = !open }
            TextLink(t(R.string.T_show_transactions)) { onOpen(Screen.Spending) }
        }
        if (open && !card.how.isNullOrBlank()) {
            Text(card.how, color = Secondary, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun SaveAnswer(message: CoachMessage) {
    val cut = message.card?.cut
    if (cut == null || cut.options.isEmpty()) {
        Prose(message)
        message.card?.let { GenericCardBody(it) }
        return
    }
    val max = cut.options.maxOf { it.skip }
    var skip by remember(cut) { mutableIntStateOf(cut.selected.coerceIn(0, max)) }
    val option = cut.options.firstOrNull { it.skip == skip } ?: cut.options.first()
    val keep = (cut.visits - skip).coerceAtLeast(0)
    val lead = when {
        skip <= 0 -> t(R.string.T_keep_every_visit)
        skip == 1 -> t(R.string.T_eat_less_one, eur(option.monthCents))
        else -> t(R.string.T_eat_less_many, skip, eur(option.monthCents))
    }
    Lead(lead)
    message.detail?.let { Detail(it) }
    CardBox {
        Text(categoryLabel(cut.category), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            if (cut.visits == 1) t(R.string.T_one_visit_about, eur(cut.visitCents))
            else t(R.string.T_visits_about, cut.visits, eur(cut.visitCents)),
            color = Secondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (max > 0) {
            Text(
                t(R.string.T_visits_to_skip),
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp),
            )
            Slider(
                value = skip.toFloat(),
                onValueChange = { skip = it.roundToInt().coerceIn(0, max) },
                valueRange = 0f..max.toFloat(),
                steps = (max - 1).coerceAtLeast(0),
            )
            Text(t(R.string.T_you_keep_skip, keep, skip), color = Secondary, fontSize = 13.sp)
            if (cut.visits > 0) {
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    repeat(cut.visits.coerceAtMost(24)) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (index < keep) Forest else Fill),
                        )
                    }
                }
            }
        }
        Column(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Mint)
                .padding(16.dp),
        ) {
            Text(t(R.string.T_you_would_save), color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(eur(option.monthCents), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, style = Tabular)
            Text(t(R.string.T_a_month), color = Secondary, fontSize = 14.sp)
            Text(
                t(R.string.T_in_a_year, eur(option.yearCents)),
                color = Forest,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun HouseholdAnswer(message: CoachMessage) {
    Prose(message)
    val card = message.card
    if (card != null || message.lead?.contains("Statistics") == true) {
        Text(t(R.string.T_deposits_not_spend), color = Secondary, fontSize = 14.sp, lineHeight = 20.sp)
    }
    if (card == null) return
    val choices = card.options?.choices.orEmpty()
    var selected by remember(card) { mutableStateOf(card.options?.selected) }
    val choice = choices.find { it.key == selected }
    val items = choice?.items ?: card.items
    val verdict = choice?.verdict ?: card.verdict
    val verdictDetail = choice?.verdictDetail ?: card.verdictDetail
    CardBox {
        Text(card.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        card.context?.let { Text(it, color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (!verdict.isNullOrBlank()) {
            Text(
                verdict,
                color = Forest,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mint)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        verdictDetail?.let { Text(it, color = Secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
        items.forEach { item ->
            CompareBar(item.label, item.amountCents, item.barPct ?: 0, item.highlight)
        }
        if (choices.size > 1) {
            Text(
                card.options?.label ?: "",
                color = Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp),
            )
            card.options?.note?.let { Text(it, color = Tertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
            AgeRow(choices, selected) { selected = it }
        }
        HowLine(card.how)
    }
}

@Composable
private fun GenericAnswer(message: CoachMessage) {
    Prose(message)
    message.card?.let { GenericCardBody(it) }
}

@Composable
private fun GenericCardBody(card: AnswerCardDto) {
    CardBox {
        Text(card.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        card.context?.let { Text(it, color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        card.headline?.let { figure ->
            Text(figure.label, color = Secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            Text(eur(figure.amountCents), color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, style = Tabular)
        }
        card.verdict?.let { Text(it, color = Forest, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        card.verdictDetail?.let { Text(it, color = Secondary, fontSize = 14.sp, lineHeight = 20.sp) }
        card.items.forEach { item ->
            CompareBar(categoryLabel(item.label), item.amountCents, item.barPct ?: 0, item.highlight)
        }
        HowLine(card.how)
    }
}

@Composable
private fun Prose(message: CoachMessage) {
    val lead = message.lead
    if (!lead.isNullOrBlank()) MarkdownBody(presentMoneyIn(lead))
    else if (message.paragraphs.isNotEmpty()) message.paragraphs.forEach { MarkdownBody(presentMoneyIn(it)) }
    message.detail?.let { Detail(it) }
}

@Composable
private fun Lead(text: String) {
    Text(text, color = Ink, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Detail(text: String) {
    Text(text, color = Secondary, fontSize = 15.sp, lineHeight = 21.sp)
}

@Composable
private fun BucketPill(label: String, cents: Int, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(label, color = Secondary, fontSize = 11.sp, maxLines = 1)
        Text(eur(cents), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = Tabular, maxLines = 1)
    }
}

@Composable
private fun CompareBar(label: String, cents: Int, pct: Int, highlight: Boolean) {
    Column(Modifier.padding(top = 12.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Ink, fontSize = 14.sp, fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
            Text(eur(cents), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, style = Tabular)
        }
        Box(
            Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Fill),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((pct.coerceIn(0, 100) / 100f).coerceAtLeast(0.02f))
                    .background(if (highlight) Forest else EverydayBlue),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgeRow(choices: List<CardChoiceDto>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { choice ->
            val on = choice.key == selected
            Text(
                choice.label,
                color = if (on) Color.White else Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Forest else Fill)
                    .clickable { onSelect(choice.key) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun HowLine(how: String?) {
    if (how.isNullOrBlank()) return
    var open by remember { mutableStateOf(false) }
    TextLink(t(R.string.T_how_i_got)) { open = !open }
    if (open) Text(how, color = Secondary, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun CaptionLine(message: CoachMessage) {
    Text(captionOf(message), color = Tertiary, fontSize = 13.sp, lineHeight = 18.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskNext(message: CoachMessage, onAsk: (String) -> Unit) {
    if (message.followUps.isEmpty()) return
    Text(t(R.string.T_ask_next), color = Secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        message.followUps.forEachIndexed { index, chip ->
            val ask = followUpQuestion(chip.text)
            QuestionChip(ask, QuestionTints[index % QuestionTints.size]) { onAsk(ask) }
        }
    }
}

@Composable
private fun followUpQuestion(text: String): String = when (text) {
    "Where did my money go?" -> t(R.string.T_where_go)
    "How did my spending change over the last months?" -> t(R.string.T_follow_months)
    "How do my savings compare with people my age?" -> t(R.string.T_follow_peers)
    "How much have I saved?" -> t(R.string.T_follow_saved)
    else -> text
}

@Composable
private fun captionOf(message: CoachMessage): String = when (message.caption) {
    "AI answer · numbers come from your transactions" -> t(R.string.T_ai_answer)
    "AI answer · numbers come from your transactions and Statistics Finland" -> t(R.string.T_ai_peer)
    "AI answer · not based on your transactions" -> t(R.string.T_ai_general)
    null, "" -> t(R.string.T_ai_answer)
    else -> message.caption
}

@Composable
private fun spenderTitle(key: String): String = when (key) {
    "big_buys" -> t(R.string.T_big_buys)
    "steady" -> t(R.string.T_steady_spender)
    else -> t(R.string.T_everyday_spender)
}

@Composable
private fun spenderLine(key: String): String = when (key) {
    "big_buys" -> t(R.string.T_big_buys_line)
    "steady" -> t(R.string.T_steady_line)
    else -> t(R.string.T_everyday_line)
}

@Composable
private fun monthLabel(context: String?): String {
    if (context.isNullOrBlank()) return ""
    val head = context.substringBefore(",").trim()
    val english = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    val name = head.substringBefore(" ")
    val index = english.indexOf(name)
    if (index < 0) return head
    val label = t(
        when (index + 1) {
            1 -> R.string.T_month_1
            2 -> R.string.T_month_2
            3 -> R.string.T_month_3
            4 -> R.string.T_month_4
            5 -> R.string.T_month_5
            6 -> R.string.T_month_6
            7 -> R.string.T_month_7
            8 -> R.string.T_month_8
            9 -> R.string.T_month_9
            10 -> R.string.T_month_10
            11 -> R.string.T_month_11
            else -> R.string.T_month_12
        },
    )
    val rest = head.removePrefix(name).trim()
    return if (rest.isEmpty()) label else "$label $rest"
}

private fun buckets(items: List<CardItemDto>): Triple<Int, Int, Int> {
    var essentials = 0
    var everyday = 0
    for (item in items) {
        if (isEssential(item.key)) essentials += item.amountCents else everyday += item.amountCents
    }
    return Triple(essentials, 0, everyday)
}

private fun everydayRows(items: List<CardItemDto>): List<CardItemDto> {
    val everyday = items.filter { !isEssential(it.key) }.sortedByDescending { it.amountCents }
    return (if (everyday.isNotEmpty()) everyday else items.sortedByDescending { it.amountCents }).take(5)
}
