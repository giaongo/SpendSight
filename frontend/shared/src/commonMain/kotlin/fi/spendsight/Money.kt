package fi.spendsight

import androidx.compose.runtime.Composable
import kotlin.math.abs

/** Grouping space, and the space before €. Neither breaks the amount across lines. */
private const val Nbsp = '\u00A0'

/** Finnish and Swedish share a decimal comma and a space before %. */
private fun Lang.nordic(): Boolean = this == Lang.Fi || this == Lang.Sv

/**
 * Formats cents for the screen. This is presentation, not a ledger rule.
 * Every language uses the design's euro format: 1 028,30 €. The sign is the Unicode minus: −5,60 €.
 */
@Suppress("UNUSED_PARAMETER")
fun eur(cents: Int, lang: Lang): String {
    val sign = if (cents < 0) "−" else ""
    val value = abs(cents)
    val whole = value / 100
    val frac = (value % 100).toString().padStart(2, '0')
    return "$sign${group(whole.toLong(), Nbsp)},$frac$Nbsp€"
}

@Composable
fun eur(cents: Int): String = eur(cents, LocalLang.current)

/** Whole euros, for labels such as the loan price ends: 150 000 €. */
@Suppress("UNUSED_PARAMETER")
fun euros(amount: Int, lang: Lang): String = "${group(amount.toLong(), Nbsp)}$Nbsp€"

@Composable
fun euros(amount: Int): String = euros(amount, LocalLang.current)

fun Payment.shownAmount(lang: Lang): String = eur(amountCents, lang)

@Composable
fun Payment.shownAmount(): String = shownAmount(LocalLang.current)

/**
 * A money string the service already formatted in English, such as €1,028.30 or −€5.60.
 * The screen shows the same digits in the design's format: 1 028,30 € and −5,60 €.
 */
@Suppress("UNUSED_PARAMETER")
fun presentMoney(text: String, lang: Lang): String {
    val match = ServiceMoney.matchEntire(text.trim()) ?: return text
    return serviceMoney(match)
}

@Composable
fun presentMoney(text: String): String = presentMoney(text, LocalLang.current)

/** Rewrites every service-formatted amount inside a sentence, such as a coach answer. */
fun presentMoneyIn(text: String): String = ServiceMoneyInText.replace(text) { serviceMoney(it) }

private fun serviceMoney(match: MatchResult): String {
    val sign = if (match.groupValues[1].isNotEmpty()) "−" else ""
    val whole = match.groupValues[2].replace(",", "")
    val frac = match.groupValues[3]
    return "$sign${group(whole.toLong(), Nbsp)},$frac$Nbsp€"
}

private val ServiceMoney = Regex("""^([−\-])?€(\d{1,3}(?:,\d{3})*|\d+)\.(\d{2})$""")
private val ServiceMoneyInText = Regex("""([−\-])?€(\d{1,3}(?:,\d{3})+|\d+)\.(\d{2})(?!\d)""")

/** Whole percents stay whole. 1.5 stays 1.5 in English and 1,5 in Finnish and Swedish. */
fun formatPct(pct: Double, lang: Lang): String {
    val whole = pct.toLong()
    val text = if (pct == whole.toDouble()) whole.toString() else pct.toString()
    return if (lang.nordic()) text.replace('.', ',') else text
}

@Composable
fun formatPct(pct: Double): String = formatPct(pct, LocalLang.current)

/** The design writes a percent with a non-breaking space in every language: 63 %. */
@Composable
fun pct(value: Int): String = "$value %"

@Composable
fun pct(value: Double): String = "${formatPct(value, LocalLang.current)} %"

@Composable
fun rateLabel(tenths: Int): String {
    val number = if (LocalLang.current.nordic()) "${tenths / 10},${tenths % 10}" else "${tenths / 10}.${tenths % 10}"
    return if (LocalLang.current.nordic()) "$number %" else "$number%"
}

private fun group(whole: Long, separator: Char): String {
    val text = whole.toString()
    if (text.length <= 3) return text
    return text.reversed().chunked(3).joinToString(separator.toString()).reversed()
}

/**
 * At 20% the dining tool's fifth_cents is the figure.
 * Any other share is a what-if on the dining total the service already returned.
 */
fun diningWhatIf(diningCents: Int, fifthCents: Int, percent: Int): Int =
    if (percent == 20) fifthCents else (diningCents * percent + 50) / 100

fun shiftMonth(month: String, delta: Int): String {
    val parts = month.split("-")
    var year = parts[0].toInt()
    var value = parts[1].toInt() + delta
    while (value < 1) {
        value += 12
        year -= 1
    }
    while (value > 12) {
        value -= 12
        year += 1
    }
    val monthText = value.toString().padStart(2, '0')
    return "$year-$monthText"
}

fun counted(period: String, category: String, count: Int, noun: String? = null): String {
    val word = noun ?: if (count == 1) "payment" else "payments"
    return "$period · $category · $count $word"
}
