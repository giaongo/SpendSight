package fi.spendsight

import kotlin.math.abs

/** Formats cents the way the phone shows them. This is presentation, not a ledger rule. */
fun eur(cents: Int): String {
    val sign = if (cents < 0) "−" else ""
    val value = abs(cents)
    val whole = value / 100
    val frac = (value % 100).toString().padStart(2, '0')
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return "$sign€$grouped.$frac"
}

fun Payment.shownAmount(): String = amount.ifBlank { eur(amountCents) }

/** Whole percents stay whole. 1.5 stays 1.5. */
fun formatPct(pct: Double): String {
    val whole = pct.toLong()
    return if (pct == whole.toDouble()) whole.toString() else pct.toString()
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
