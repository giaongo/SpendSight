package fi.spendsight

enum class Month(val label: String, val eyebrow: String, val code: String) {
    Oct("October", "October, by place", "oct"),
    Nov("1–13 Nov", "1–13 November, every payment", "nov"),
    ;

    companion object {
        fun fromCode(code: String): Month = entries.first { it.code == code }
    }
}

enum class Basket(val title: String, val line: String, val code: String) {
    Regular("Must, every month", "These leave even when you buy nothing else.", "regular"),
    Irregular("Must, when it happens", "You cannot skip these. You also cannot pick the day.", "irregular"),
    Everyday(
        "Everyday",
        "Chosen one payment at a time. Food for the house is in here, and so is the money that slips through.",
        "everyday",
    ),
    ;

    companion object {
        fun fromCode(code: String): Basket = entries.first { it.code == code }
    }
}

data class Payment(
    val id: Int,
    val month: Month,
    val place: String,
    val amountCents: Int,
    val category: String,
    val basket: Basket,
    val earnsBonus: Boolean,
    val whenLabel: String,
    val detail: String,
    val key: String,
    val filedByCustomer: Boolean = false,
    val inSlip: Boolean = false,
    val reason: String = "",
    val amount: String = "",
)

data class CategoryTotal(val name: String, val basket: Basket, val amountCents: Int)

data class ChartRow(val label: String, val octoberCents: Int, val novemberCents: Int)

data class ImpulseDay(
    val whenLabel: String,
    val count: Int,
    val places: List<String>,
    val amountCents: Int,
)

data class AccountCopy(val id: String, val title: String, val subtitle: String, val body: String)

data class CoachFact(val kicker: String, val figure: String, val because: String)

data class CoachMessage(
    val role: String,
    val text: String? = null,
    val paragraphs: List<String> = emptyList(),
    val refused: Boolean = false,
    val fact: CoachFact? = null,
    val source: String? = null,
    val actionLabel: String? = null,
    val action: Screen? = null,
    val chart: List<ChartRow>? = null,
    val limit: String? = null,
)

sealed interface Screen {
    val title: String

    data object Today : Screen { override val title = "Today" }
    data object Spending : Screen { override val title = "Spending" }
    data class Category(val name: String) : Screen { override val title = name }
    data object Baskets : Screen { override val title = "Baskets" }
    data object Charts : Screen { override val title = "Charts" }
    data object Spender : Screen { override val title = "Spender" }
    data object Week : Screen { override val title = "This week" }
    data object Cut : Screen { override val title = "Dining out" }
    data object Save : Screen { override val title = "Save" }
    data object Home : Screen { override val title = "Home" }
    data object Loan : Screen { override val title = "Loan sketch" }
    data object Purchase : Screen { override val title = "This purchase" }
    data object Bonus : Screen { override val title = "Bonus" }
    data object Subs : Screen { override val title = "Subscriptions" }
    data object Watch : Screen { override val title = "Watch" }
    data object Impulse : Screen { override val title = "Impulse" }
    data object Runway : Screen { override val title = "Days left" }
    data object Coach : Screen { override val title = "SpendSight" }
}

enum class Tab(val label: String, val root: Screen) {
    Today("Today", Screen.Today),
    Spending("Spending", Screen.Spending),
    Save("Save", Screen.Save),
    Coach("SpendSight", Screen.Coach),
}

fun Screen.tab(): Tab = when (this) {
    is Screen.Spending, is Screen.Category, Screen.Charts, Screen.Baskets -> Tab.Spending
    Screen.Save, Screen.Home, Screen.Loan, Screen.Bonus, Screen.Subs, Screen.Watch -> Tab.Save
    Screen.Coach, Screen.Cut, Screen.Purchase, Screen.Spender, Screen.Impulse, Screen.Runway -> Tab.Coach
    else -> Tab.Today
}

sealed interface Sheet {
    data object About : Sheet
    data object Privacy : Sheet
    data object Book : Sheet
    data class Account(val id: String) : Sheet
    data class PaymentSheet(val id: Int) : Sheet
}

enum class Confirm { PrepareTransfer, CancelTransfer, SendBonus, KeepBonus }

data class Dialog(
    val title: String,
    val body: String,
    val dismissLabel: String,
    val confirmLabel: String,
    val confirm: Confirm,
)
