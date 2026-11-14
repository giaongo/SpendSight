package fi.spendsight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tokens from the "SpendSight – MVP screens v2" design.
val Green = Color(0xFF00703C)
val GreenPress = Color(0xFF005A30)
val Forest = Color(0xFF005A30)
val Deep = Color(0xFF005A30)
val Ink = Color(0xFF1C1C1E)
val Secondary = Color(0xFF636366)
val Tertiary = Color(0xFF8A8A8E)
val Graphite = Color(0xFF3A3A3C)
val Bg = Color(0xFFECF1ED)
val Mint = Color(0xFFD9F2E3)
val Mist = Color(0xFFDDE8FA)
val Cream = Color(0xFFF7E9CC)
val Lilac = Color(0xFFE9E3F7)
val HeroMuted = Color(0xFFC8F0D8)
val EssentialTeal = Color(0xFF0F5C5C)
val EverydayBlue = Color(0xFF3D6FB6)

/** True while the finger is on the ask field, so an outside tap can hide the keyboard. */
internal class EditorGate {
    var down: Boolean = false
}

@Composable
internal fun hideKeyboard(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return {
        focusManager.clearFocus()
        keyboard?.hide()
    }
}

/** Taps on empty page space clear the ask field. Child clicks still win. */
@Composable
fun Modifier.dismissKeyboardOnTap(): Modifier {
    val hide = hideKeyboard()
    val interaction = remember { MutableInteractionSource() }
    return clickable(interactionSource = interaction, indication = null, onClick = hide)
}
val Card = Color.White
val Line = Color(0xFFD1D1D6)
val Slip = Color(0xFFC26A3A)
val Gold = Color(0xFFC4A15A)
val Fill = Color(0xFFE5E5EA)
val TagYellow = Color(0xFFFFE14A)
val Pace = Color(0xFFC7C7CC)

fun basketColor(basket: Basket) = when (basket) {
    Basket.Regular -> Deep
    Basket.Irregular -> Gold
    Basket.Everyday -> Green
}

@Composable
fun Eyebrow(text: String) {
    Text(text, color = Tertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
}

@Composable
fun Hero(text: String, size: Int = 40) {
    Text(text, color = Ink, fontSize = size.sp, fontWeight = FontWeight.Bold, lineHeight = (size + 4).sp)
}

@Composable
fun Lede(text: String, strong: Boolean = false) {
    Text(text, color = if (strong) Ink else Secondary, fontSize = 15.sp, lineHeight = 21.sp)
}

@Composable
fun Note(text: String) {
    Text(text, color = Tertiary, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
fun Section(text: String) {
    Text(text, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
}

@Composable
fun CardBox(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun HoldRow(title: String, detail: String, amount: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Secondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Text(amount, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SumRow(label: String, amount: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Spacer(Modifier.width(12.dp))
        Text(amount, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun ChevronRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Secondary, fontSize = 13.sp)
        }
        Text("›", color = Tertiary, fontSize = 22.sp)
    }
}

@Composable
fun TextLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Green,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

@Composable
fun PrimaryButton(label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(25.dp))
            .background(if (on) Deep else Green)
            .clickable(onClick = onClick)
            .heightIn(min = 50.dp)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

@Composable
fun ChoiceButton(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) Color(0xFFE5F6EB) else Fill)
            .border(1.dp, if (on) Green else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) Deep else Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun Segmented(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Fill).padding(3.dp),
    ) {
        options.forEachIndexed { index, (label, on) ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Card else Color.Transparent)
                    .clickable { onSelect(index) }
                    .heightIn(min = 44.dp)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Deep else Graphite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun EffortPicker(store: SpendSightStore) {
    val selected = store.pendingEffort ?: store.settings.selected
    Segmented(store.settings.efforts.map { it.name to (it.id == selected) }) { index ->
        store.selectEffort(store.settings.efforts[index].id)
    }
}

@Composable
fun MonthPicker(store: SpendSightStore) {
    Segmented(listOf(Month.Oct.heading() to (store.month == Month.Oct), Month.Nov.heading() to (store.month == Month.Nov))) { index ->
        store.month = if (index == 0) Month.Oct else Month.Nov
    }
}

@Composable
fun StackBar(parts: List<Pair<Color, Int>>) {
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Fill),
    ) {
        parts.filter { it.second > 0 }.forEach { (color, amount) ->
            Box(Modifier.fillMaxHeight().weight(amount.toFloat() / total).background(color))
        }
    }
}

@Composable
fun BasketLegend(store: SpendSightStore, month: Month) {
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Basket.entries.forEach { basket ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(basketColor(basket)))
                Spacer(Modifier.width(8.dp))
                Text(basket.heading(), color = Secondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(eur(store.basketSum(month, basket)), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun Tags(leftLabel: String, leftValue: String, onLeft: () -> Unit, rightLabel: String, rightValue: String, onRight: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tag(leftLabel, leftValue, Modifier.weight(1f), onLeft)
        Tag(rightLabel, rightValue, Modifier.weight(1f), onRight)
    }
}

@Composable
fun Tag(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TagYellow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Text(label, color = Deep, fontSize = 12.sp)
        Text(value, color = Deep, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Trio(items: List<Triple<String, String, () -> Unit>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (value, label, onClick) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Card)
                    .clickable(onClick = onClick)
                    .padding(12.dp),
            ) {
                Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(label, color = Secondary, fontSize = 12.sp, lineHeight = 15.sp)
            }
        }
    }
}

@Composable
fun ChartBlock(rows: List<ChartRow>) {
    val max = rows.maxOf { maxOf(it.octoberCents, it.novemberCents) }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Text(categoryLabel(row.label), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            BarLine(Pace, row.octoberCents, max)
            BarLine(Green, row.novemberCents, max)
        }
    }
}

@Composable
private fun BarLine(color: Color, cents: Int, max: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Fill),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(cents.toFloat() / max)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
        Text(eur(cents), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}
