package fi.spendsight

private val EssentialNames = setOf(
    "Housing",
    "Utilities",
    "Health",
    "Transportation",
    "Rent",
    "Bills",
    "Transport pass",
    "Repair",
    "Pharmacy",
)

private val SkipNames = setOf("Salary", "Invest Deposit")

fun outflowRows(categories: List<SpendCategoryDto>): List<SpendCategoryDto> =
    categories.filter { it.amountCents < 0 && it.name !in SkipNames }

fun essentialsCents(categories: List<SpendCategoryDto>): Int =
    outflowRows(categories).filter { it.name in EssentialNames }.sumOf { -it.amountCents }

fun everydayCents(categories: List<SpendCategoryDto>): Int =
    outflowRows(categories).filter { it.name !in EssentialNames }.sumOf { -it.amountCents }

data class Look(val name: String, val nowCents: Int, val deltaCents: Int?)

fun worthALook(current: List<SpendCategoryDto>, prior: List<SpendCategoryDto>?): Look? {
    val now = outflowRows(current).associate { it.name to -it.amountCents }
    if (now.isEmpty()) return null
    if (prior == null) {
        val top = now.maxBy { it.value }
        return Look(top.key, top.value, null)
    }
    val then = outflowRows(prior).associate { it.name to -it.amountCents }
    val name = (now.keys + then.keys).maxBy { (now[it] ?: 0) - (then[it] ?: 0) }
    return Look(name, now[name] ?: 0, (now[name] ?: 0) - (then[name] ?: 0))
}

internal fun daysSinceEpoch(year: Int, month: Int, day: Int): Int {
    val y = year - if (month <= 2) 1 else 0
    val era = if (y >= 0) y / 400 else (y - 399) / 400
    val yoe = y - era * 400
    val mp = if (month > 2) month - 3 else month + 9
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun weekdayKey(index: Int): String = when (index) {
    0 -> R.string.T_wd_mon
    1 -> R.string.T_wd_tue
    2 -> R.string.T_wd_wed
    3 -> R.string.T_wd_thu
    4 -> R.string.T_wd_fri
    5 -> R.string.T_wd_sat
    else -> R.string.T_wd_sun
}

private fun monthKey(month: Int): String = when (month) {
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
}

fun monthName(isoMonth: String, lang: Lang): String {
    val month = isoMonth.split("-").getOrNull(1)?.toIntOrNull() ?: return isoMonth
    if (month !in 1..12) return isoMonth
    return t(lang, monthKey(month))
}

fun formatAsOf(iso: String, lang: Lang): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val year = parts[0].toIntOrNull() ?: return iso
    val month = parts[1].toIntOrNull() ?: return iso
    val day = parts[2].toIntOrNull() ?: return iso
    if (month !in 1..12 || day !in 1..31) return iso
    val weekday = (daysSinceEpoch(year, month, day) + 3).mod(7)
    val dayName = t(lang, weekdayKey(weekday))
    return if (lang == Lang.Fi) "$dayName $day.$month." else "$dayName $day ${t(lang, monthKey(month))}"
}
