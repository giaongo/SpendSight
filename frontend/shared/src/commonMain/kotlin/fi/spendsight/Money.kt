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

fun counted(period: String, category: String, count: Int, noun: String? = null): String {
    val word = noun ?: if (count == 1) "payment" else "payments"
    return "$period · $category · $count $word"
}
