package fi.spendsight

/** Account explanations. These are not ledger figures. */
fun accounts(lang: Lang): List<AccountCopy> = listOf(
    AccountCopy(
        "tili",
        "S-Tili",
        t(lang, R.string.T_everyday_bonus_lands_here),
        t(lang, R.string.T_everyday_account_salary_rent_and_bonus_land_here_t),
    ),
    AccountCopy(
        "asp",
        t(lang, R.string.T_asp_account),
        t(lang, R.string.T_the_home_plan_to_leave_it_there),
        t(lang, R.string.T_for_a_first_home_a_month_is_50_to_1_500_and_the_lo),
    ),
    AccountCopy(
        "tuotto",
        "S-Tuottotili",
        t(lang, R.string.T_savings_you_can_take),
        t(lang, R.string.T_savings_you_can_take_out_when_you_need_them_the_cu),
    ),
    AccountCopy(
        "maara",
        "S-Määräaikaistili",
        t(lang, R.string.T_stays_until_a_date),
        t(lang, R.string.T_a_fixed_date_the_money_stays_until_that_date_use_i),
    ),
    AccountCopy(
        "saastaja",
        "Säästäjä",
        t(lang, R.string.T_bonus_card_rounding_or_a_monthly_sum_a_fund_can_fa),
        t(lang, R.string.T_four_ways_to_save_send_bonus_to_a_fund_set_aside_0),
    ),
)

/** Shown before the first coach answer. The service writes the disclaimer after that. */
fun coastOpening(lang: Lang): String = t(lang, R.string.T_ask_about_this_customer_s_transactions_the_service)

val coastOpenings: Set<String> = Lang.entries.map { coastOpening(it) }.toSet()

/** Questions shown above the ask field. Each one is sent as written. */
fun homeQuestions(lang: Lang): List<String> = listOf(
    t(lang, R.string.T_where_go),
    t(lang, R.string.T_kind_of_spender),
    t(lang, R.string.T_safe_week),
    t(lang, R.string.T_save_more),
    t(lang, R.string.T_typical_household),
)

/** Which of the five fixed chat screens a sent question opens, in any of the three languages. */
fun homeAsk(text: String): HomeAsk? {
    val asked = text.trim()
    for (lang in Lang.entries) {
        val index = homeQuestions(lang).indexOf(asked)
        if (index >= 0) return HomeAsk.entries[index]
    }
    return null
}

enum class HomeAsk { Where, Spender, Week, Save, Household }

/** Chip label and the question sent to this customer's coach endpoint. */
fun coachChips(lang: Lang): List<Pair<String, String>> = listOf(
    t(lang, R.string.T_balance) to t(lang, R.string.T_what_is_my_balance),
    t(lang, R.string.T_last_salary) to t(lang, R.string.T_when_was_my_latest_salary_and_how_much_was_it),
    t(lang, R.string.T_last_month) to t(lang, R.string.T_how_much_left_my_account_last_month),
    t(lang, R.string.T_biggest_category) to t(lang, R.string.T_what_did_i_spend_the_most_on),
    t(lang, R.string.T_recent_months) to t(lang, R.string.T_how_did_money_in_and_money_out_change_over_the_rec),
    t(lang, R.string.T_mortgage) to t(lang, R.string.T_is_there_a_mortgage_decision_on_this_customer),
)

/** Categories the filing route accepts. Unknown names are rejected. These stay English on the request. */
val filingOrder = listOf(
    "Rent",
    "Bills",
    "Transport pass",
    "Repair",
    "Health",
    "Groceries",
    "Dining out",
    "Coffee & drinks",
    "Taxis",
    "Pharmacy",
    "Shopping",
)
