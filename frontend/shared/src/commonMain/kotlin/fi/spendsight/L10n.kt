package fi.spendsight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Top-bar order. English stays the default. */
enum class Lang(val code: String, val short: String, val endonym: String) {
    Fi("fi", "FI", "Suomi"),
    En("en", "EN", "English"),
    Sv("sv", "SW", "Svenska"),
}

val LocalLang = staticCompositionLocalOf { Lang.En }

/**
 * Looks up a UI string the way the balance app's `TranslationUtils.t` does.
 * [name] is a `T_*` key from `translations/values/strings.xml`, with Finnish in `values-fi-rFI`
 * and Swedish in `values-sv-rFI`.
 * The in-app language wins over the phone locale. Placeholders are `%1$s`.
 */
fun t(lang: Lang, name: String, vararg formatArgs: Any): String =
    formatString(StringTable.get(lang, name), formatArgs)

@Composable
fun t(name: String, vararg formatArgs: Any): String = t(LocalLang.current, name, *formatArgs)

internal fun formatString(template: String, args: Array<out Any>): String {
    if (args.isEmpty() && !template.contains('%')) return template
    val out = StringBuilder()
    var i = 0
    while (i < template.length) {
        if (template[i] == '%' && i + 1 < template.length) {
            if (template[i + 1] == '%') {
                out.append('%')
                i += 2
                continue
            }
            val match = Placeholder.find(template, i)
            if (match != null && match.range.first == i) {
                val index = match.groupValues[1].toInt() - 1
                if (index in args.indices) out.append(args[index].toString())
                else out.append(match.value)
                i = match.range.last + 1
                continue
            }
        }
        out.append(template[i])
        i++
    }
    return out.toString()
}

private val Placeholder = Regex("%(\\d+)[$]s")

@Composable
fun Screen.heading(): String = when (this) {
    Screen.Today -> t(R.string.T_today)
    Screen.Spending -> t(R.string.T_spending)
    is Screen.Category -> categoryLabel(name)
    Screen.Baskets -> t(R.string.T_baskets)
    Screen.Charts -> t(R.string.T_charts)
    Screen.Spender -> t(R.string.T_spender)
    Screen.Week -> t(R.string.T_this_week)
    Screen.Cut -> t(R.string.T_dining_out)
    Screen.Save -> t(R.string.T_save)
    Screen.Home -> t(R.string.T_home)
    Screen.Loan -> t(R.string.T_loan_sketch)
    Screen.Purchase -> t(R.string.T_this_purchase)
    Screen.Bonus -> t(R.string.T_bonus)
    Screen.Subs -> t(R.string.T_subscriptions)
    Screen.Watch -> t(R.string.T_watch)
    Screen.Impulse -> t(R.string.T_impulse)
    Screen.Runway -> t(R.string.T_days_left)
    Screen.Coach -> "SpendSight"
}

@Composable
fun Tab.heading(): String = when (this) {
    Tab.Today -> t(R.string.T_today)
    Tab.Spending -> t(R.string.T_spending)
    Tab.Save -> t(R.string.T_save)
    Tab.Coach -> "SpendSight"
}

@Composable
fun Month.heading(): String = when (this) {
    Month.Oct -> t(R.string.T_october)
    Month.Nov -> t(R.string.T_n_1_13_nov)
}

@Composable
fun Basket.heading(): String = when (this) {
    Basket.Regular -> t(R.string.T_must_every_month)
    Basket.Irregular -> t(R.string.T_must_when_it_happens)
    Basket.Everyday -> t(R.string.T_everyday)
}

@Composable
fun Basket.detail(): String = when (this) {
    Basket.Regular -> t(R.string.T_these_leave_even_when_you_buy_nothing_else)
    Basket.Irregular -> t(R.string.T_you_cannot_skip_these_you_also_cannot_pick_the_day)
    Basket.Everyday -> t(R.string.T_chosen_one_payment_at_a_time_food_for_the_house_is)
}

/**
 * Category names the phone knows how to show. The filing request still sends the English name.
 * A name that is not in this list stays as the service sent it.
 */
fun categoryLabel(lang: Lang, name: String): String = when (name) {
    "Rent" -> t(lang, R.string.T_rent)
    "Bills" -> t(lang, R.string.T_bills)
    "Transport pass" -> t(lang, R.string.T_transport_pass)
    "Repair" -> t(lang, R.string.T_repair)
    "Health" -> t(lang, R.string.T_health)
    "Groceries" -> t(lang, R.string.T_groceries)
    "Dining out" -> t(lang, R.string.T_dining_out)
    "Coffee & drinks" -> t(lang, R.string.T_coffee_drinks)
    "Taxis" -> t(lang, R.string.T_taxis)
    "Pharmacy" -> t(lang, R.string.T_pharmacy)
    "Shopping" -> t(lang, R.string.T_shopping)
    "Entertainment" -> t(lang, R.string.T_entertainment)
    "Home & Tech" -> t(lang, R.string.T_home_tech)
    "Housing" -> t(lang, R.string.T_housing)
    "Invest Deposit" -> t(lang, R.string.T_invest_deposit)
    "Lifestyle" -> t(lang, R.string.T_lifestyle)
    "Restaurants" -> t(lang, R.string.T_restaurants)
    "Salary" -> t(lang, R.string.T_salary)
    "Transportation" -> t(lang, R.string.T_transportation)
    "Travel" -> t(lang, R.string.T_travel)
    "Utilities" -> t(lang, R.string.T_utilities)
    else -> name
}

@Composable
fun categoryLabel(name: String): String = categoryLabel(LocalLang.current, name)

/** Payment types on the customer’s transactions. An unknown type stays as sent. */
fun typeLabel(lang: Lang, name: String): String = when (name) {
    "housing_payment" -> t(lang, R.string.T_housing_payment)
    "investment_deposit" -> t(lang, R.string.T_investment_deposit)
    "purchase" -> t(lang, R.string.T_purchase)
    "salary" -> t(lang, R.string.T_salary)
    "utilities_payment" -> t(lang, R.string.T_utilities_payment)
    else -> name
}

@Composable
fun typeLabel(name: String): String = typeLabel(LocalLang.current, name)

fun decisionLabel(lang: Lang, name: String): String = when (name) {
    "approved" -> t(lang, R.string.T_approved)
    else -> name
}

@Composable
fun decisionLabel(name: String): String = decisionLabel(LocalLang.current, name)

/** Fixed sentences this service returns. Anything else, including a Verda answer, stays as sent. */
fun knownNote(lang: Lang, text: String): String = when (text) {
    "The balance is after their latest transaction." -> t(lang, R.string.T_note_balance)
    "Left the account in total_out_cents. Salary is total_in_cents, not part of the spending." ->
        t(lang, R.string.T_note_spending)
    "Recorded investment deposits for this customer. This is not a forecast, and it does not move money." ->
        t(lang, R.string.T_note_save)
    "This session is the first customer in the database. Pass a customer id to /customers/{customer_id} for anyone else." ->
        t(lang, R.string.T_note_session)
    else -> text
}

@Composable
fun knownNote(text: String): String = knownNote(LocalLang.current, text)
