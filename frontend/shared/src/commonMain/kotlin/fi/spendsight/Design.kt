package fi.spendsight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Line icons from the design, as SVG path data on a 24 × 24 grid. */
enum class Icon(vararg val paths: String) {
    Home("M3 11l9-7 9 7", "M5 10v10h5v-6h4v6h5V10"),
    Sight("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", "M9 14v-2M12 14v-4M15 14v-3"),
    Sprout("M12 21v-9", "M12 12c0-4 3-6 7-6 0 4-3 6-7 6z", "M12 14c0-3-2.5-5-6-5 0 3 2.5 5 6 5z"),
    Ticket(
        "M3 8a2 2 0 0 0 2-2h14a2 2 0 0 0 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 0-2 2H5a2 2 0 0 0-2-2v-2a2 2 0 0 0 0-4V8z",
        "M10 9v1M10 12v1M10 15v0",
    ),
    EyeOpen("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0z"),
    EyeOff(
        "M3 3l18 18",
        "M10.6 5.1A10.8 10.8 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4.2M6.5 6.6C3.7 8.4 2 12 2 12s3.5 7 10 7a9.6 9.6 0 0 0 4.3-1",
    ),
    ChevronRight("M9 6l6 6-6 6"),
    ChevronLeft("M15 6l-6 6 6 6"),
    Back("M15 5l-7 7 7 7"),
    Chat("M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"),
    Send("M12 19V5M6 11l6-6 6 6"),
    Bulb("M9 18h6M10 21h4", "M12 3a6 6 0 0 0-3.6 10.8c.6.5 1 1.2 1 2V17h5.2v-1.2c0-.8.4-1.5 1-2A6 6 0 0 0 12 3z"),
    Trend("M3 17l6-6 4 4 8-8", "M15 7h6v6"),
    Close("M6 6l12 12M18 6L6 18"),
    Card("M5 5h14a2.5 2.5 0 0 1 2.5 2.5v9a2.5 2.5 0 0 1-2.5 2.5H5a2.5 2.5 0 0 1-2.5-2.5v-9A2.5 2.5 0 0 1 5 5z", "M2.5 9.5h19M6.5 15h4"),
    House("M3 11l9-7 9 7", "M5 10v10h14V10", "M10 20v-5h4v5"),
    Coin(
        "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z",
        "M14.5 9.5c-.5-1-1.5-1.5-2.5-1.5-1.5 0-2.5.8-2.5 2s1 1.7 2.5 2 2.5.8 2.5 2-1 2-2.5 2c-1 0-2-.5-2.5-1.5M12 6.5V8M12 16v1.5",
    ),
    Calendar("M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M3 10h18M8 3v4M16 3v4"),
    Wallet("M4 6h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z", "M16 13h.5M3 9h17"),
    Nfc("M7 8.5a5 5 0 0 1 0 7M10.5 6a8.5 8.5 0 0 1 0 12M14 3.5a12 12 0 0 1 0 17"),
    Info("M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z", "M12 11v6M12 7.5v.5"),
}

@Composable
fun Glyph(icon: Icon, color: Color, size: Dp = 24.dp, stroke: Float = 2f, fill: Color? = null, modifier: Modifier = Modifier) {
    val paths = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, pivot = Offset.Zero) {
            paths.forEachIndexed { index, path ->
                if (fill != null && index == 0) drawPath(path, fill, style = Fill)
                drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Forest green with the soft light in the top right corner. */
fun Modifier.headerGlow(): Modifier = this.background(Forest).drawBehind {
    drawRect(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.12f),
            1f to Color.Transparent,
            center = Offset(size.width * 0.88f, 0f),
            radius = size.width * 0.5f,
        ),
    )
}

val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * Zone 1 of every tab: the fixed green header. A top row, the title, then the key figure.
 * The design is 280 tall including a 47 status bar.
 */
@Composable
fun RootHeader(
    title: String,
    keyLabel: String,
    keyNumber: String,
    keySub: (@Composable RowScope.() -> Unit)? = null,
    topRow: @Composable RowScope.() -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .headerGlow()
            .statusBarsPadding()
            .heightIn(min = 226.dp)
            .padding(start = 20.dp, end = 20.dp, top = 7.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = topRow,
        )
        Column(Modifier.padding(top = 8.dp)) {
        Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, lineHeight = 41.sp)
        Spacer(Modifier.height(14.dp))
        Text(keyLabel, color = HeroMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            keyNumber,
            color = Color.White,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp,
            lineHeight = 48.sp,
            style = Tabular,
            maxLines = 1,
        )
        if (keySub != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically, content = keySub)
        }
        }
    }
}

@Composable
fun RowScope.KeySubText(text: String) {
    Text(text, color = HeroMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, maxLines = 1)
}

/** A 20 sp section heading with an optional link on the right. */
@Composable
fun SectionHead(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        if (action != null && onAction != null) {
            Text(action, color = Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onAction).padding(vertical = 8.dp))
        }
    }
}

@Composable
fun DesignSection(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** A white list with hairlines between rows, radius 16. */
@Composable
fun WhiteList(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card), content = content)
}

@Composable
fun Hairline(inset: Dp = 16.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(0.5.dp).background(Line))
}

/** The suggestion colours t1–t4 from the design. */
val QuestionTints = listOf(Mint, Mist, Cream, Lilac)

@Composable
fun QuestionChip(label: String, fill: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

/** A field-shaped button. It opens the chat, where the real field is. */
@Composable
fun AskFieldButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Card)
            .border(1.dp, Tertiary, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Icon.Chat, Green, 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(t(R.string.T_ask_about_your_money), color = Secondary, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        SendDot(enabled = true)
    }
}

@Composable
fun SendDot(enabled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(36.dp).clip(CircleShape).background(if (enabled) Green else Green.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(Icon.Send, Color.White, 18.dp, stroke = 2.4f)
    }
}

/** Round white 44 button with a chevron, for the carousel. Disabled is the whole button at 35 %. */
@Composable
fun NavCircle(icon: Icon, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.35f)
            .size(44.dp)
            .shadow(1.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(CircleShape)
            .background(Card)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(icon, Ink, 18.dp, stroke = 2.4f)
    }
}
