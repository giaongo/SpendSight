package fi.spendsight

import androidx.compose.runtime.Composable
import kotlin.math.abs

/** Finnish and Swedish grouping space. It does not break the amount across lines. */
private const val GroupNordic = '\u00A0'

/** Finnish and Swedish share a grouping space, a decimal comma, and a space before %. */
private fun Lang.nordic(): Boolean = this == Lang.Fi || this == Lang.Sv

/**
 * Formats cents for the screen. This is presentation, not a ledger rule.
 * Finnish and Swedish: €1 028,30. English: €1,028.30. The sign is the Unicode minus.
 */
fun eur(cents: Int, lang: Lang): String {
    val sign = if (cents < 0) "−" else ""
    val value = abs(cents)
    val whole = value / 100
    val frac = (value % 100).toString().padStart(2, '0')
    val grouped = group(whole.toLong(), if (lang.nordic()) GroupNordic else ',')
    val decimal = if (lang.nordic()) ',' else '.'
    return "$sign€$grouped$decimal$frac"
}

@Composable
fun eur(cents: Int): String = eur(cents, LocalLang.current)

/** Whole euros, for labels such as the loan price ends. */
fun euros(amount: Int, lang: Lang): String {
    val grouped = group(amount.toLong(), if (lang.nordic()) GroupNordic else ',')
    return "€$grouped"
}

@Composable
fun euros(amount: Int): String = euros(amount, LocalLang.current)

fun Payment.shownAmount(lang: Lang): String = eur(amountCents, lang)

@Composable
fun Payment.shownAmount(): String = shownAmount(LocalLang.current)

/**
 * A money string the service already formatted in English, such as €1,028.30 or −€5.60.
 * Finnish and Swedish screens show the same digits with a space and a comma.
 */
fun presentMoney(text: String, lang: Lang): String {
    if (!lang.nordic()) return text
    val match = ServiceMoney.matchEntire(text.trim()) ?: return text
    val sign = if (match.groupValues[1].isNotEmpty()) "−" else ""
    val whole = match.groupValues[2].replace(",", "")
    val frac = match.groupValues[3]
    return "$sign€${group(whole.toLong(), GroupNordic)},$frac"
}

@Composable
fun presentMoney(text: String): String = presentMoney(text, LocalLang.current)

private val ServiceMoney = Regex("""^([−\-])?€(\d{1,3}(?:,\d{3})*|\d+)\.(\d{2})$""")

/** Whole percents stay whole. 1.5 stays 1.5 in English and 1,5 in Finnish and Swedish. */
fun formatPct(pct: Double, lang: Lang): String {
    val whole = pct.toLong()
    val text = if (pct == whole.toDouble()) whole.toString() else pct.toString()
    return if (lang.nordic()) text.replace('.', ',') else text
}

@Composable
fun formatPct(pct: Double): String = formatPct(pct, LocalLang.current)

@Composable
fun pct(value: Int): String = if (LocalLang.current.nordic()) "$value %" else "$value%"

@Composable
fun pct(value: Double): String {
    val number = formatPct(value, LocalLang.current)
    return if (LocalLang.current.nordic()) "$number %" else "$number%"
}

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
