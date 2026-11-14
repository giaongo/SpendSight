package fi.spendsight

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerReadsDto(
    val today: String,
    val spending: String,
    val save: String,
    val months: String,
    val summary: String,
    val transactions: String,
)

@Serializable
data class CustomerSessionDto(
    @SerialName("customer_id") val customerId: String,
    @SerialName("postal_code") val postalCode: String,
    val age: Int,
    @SerialName("account_id") val accountId: String,
    @SerialName("mortgage_customer_id") val mortgageCustomerId: String? = null,
    @SerialName("mortgage_decision") val mortgageDecision: String? = null,
    @SerialName("as_of") val asOf: String,
    @SerialName("balance_cents") val balanceCents: Int,
    val balance: String,
    @SerialName("transaction_count") val transactionCount: Int,
    val reads: CustomerReadsDto,
    val note: String,
)

@Serializable
data class CustomerTodayDto(
    val kind: String,
    val screen: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("postal_code") val postalCode: String,
    val age: Int,
    @SerialName("account_id") val accountId: String,
    @SerialName("mortgage_customer_id") val mortgageCustomerId: String? = null,
    @SerialName("mortgage_decision") val mortgageDecision: String? = null,
    @SerialName("as_of") val asOf: String,
    @SerialName("balance_cents") val balanceCents: Int,
    val balance: String,
    @SerialName("latest_salary_cents") val latestSalaryCents: Int? = null,
    @SerialName("latest_salary_date") val latestSalaryDate: String? = null,
    val month: String,
    @SerialName("month_in_cents") val monthInCents: Int,
    @SerialName("month_out_cents") val monthOutCents: Int,
    @SerialName("transaction_count") val transactionCount: Int,
    @SerialName("first_date") val firstDate: String,
    val note: String,
)

@Serializable
data class SpendCategoryDto(
    val name: String,
    val count: Int,
    @SerialName("amount_cents") val amountCents: Int,
    val amount: String,
)

@Serializable
data class SpendTxDto(
    @SerialName("tx_date") val txDate: String,
    @SerialName("tx_timestamp") val txTimestamp: String,
    val category: String,
    @SerialName("amount_cents") val amountCents: Int,
    val amount: String,
    @SerialName("balance_cents") val balanceCents: Int,
    @SerialName("transaction_type") val transactionType: String,
    @SerialName("merchant_id") val merchantId: String? = null,
    @SerialName("mcc_code") val mccCode: String? = null,
)

@Serializable
data class SpendingDto(
    val kind: String,
    val screen: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("account_id") val accountId: String,
    val month: String,
    @SerialName("total_in_cents") val totalInCents: Int,
    @SerialName("total_out_cents") val totalOutCents: Int,
    @SerialName("line_count") val lineCount: Int,
    val categories: List<SpendCategoryDto>,
    val transactions: List<SpendTxDto>,
    val note: String,
)

@Serializable
data class CustomerSaveDto(
    val kind: String,
    val screen: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("account_id") val accountId: String,
    val years: Int,
    @SerialName("window_start") val windowStart: String,
    @SerialName("window_end") val windowEnd: String,
    val deposits: Int,
    @SerialName("deposited_cents") val depositedCents: Int,
    val deposited: String,
    @SerialName("latest_deposit_cents") val latestDepositCents: Int? = null,
    @SerialName("latest_deposit_date") val latestDepositDate: String? = null,
    @SerialName("balance_cents") val balanceCents: Int,
    val note: String,
)

@Serializable
data class CouponDto(
    @SerialName("coupon_id") val couponId: String,
    val title: String,
    val description: String = "",
    val benefit: String = "",
    val product: String = "",
    @SerialName("value_cents") val valueCents: Int? = null,
    val value: String? = null,
    val percent: Double? = null,
    @SerialName("min_spend_cents") val minSpendCents: Int? = null,
    @SerialName("min_spend") val minSpend: String? = null,
    @SerialName("days_left") val daysLeft: Int = 0,
    val reason: String = "",
    val terms: String = "",
    val activated: Boolean = false,
    val activate: String = "",
    @SerialName("campaign_title") val campaignTitle: String? = null,
    @SerialName("valid_from") val validFrom: String = "",
    @SerialName("valid_to") val validTo: String = "",
)

@Serializable
data class CouponsDto(
    @SerialName("customer_id") val customerId: String,
    val note: String = "",
    val count: Int = 0,
    @SerialName("activated_count") val activatedCount: Int = 0,
    val coupons: List<CouponDto> = emptyList(),
)

@Serializable
data class CampaignDto(
    @SerialName("campaign_id") val campaignId: String,
    val title: String,
    val description: String = "",
    @SerialName("days_left") val daysLeft: Int = 0,
    @SerialName("coupon_count") val couponCount: Int = 0,
    val terms: String = "",
    @SerialName("starts_on") val startsOn: String = "",
    @SerialName("ends_on") val endsOn: String = "",
)

@Serializable
data class CampaignsDto(
    val note: String = "",
    val count: Int = 0,
    val campaigns: List<CampaignDto> = emptyList(),
)

@Serializable
data class TodayDto(
    @SerialName("customer_name") val customerName: String,
    val today: String,
    val payday: String,
    val account: String,
    val effort: String,
    @SerialName("effort_name") val effortName: String,
    @SerialName("balance_cents") val balanceCents: Int,
    val balance: String,
    @SerialName("salary_cents") val salaryCents: Int,
    val salary: String,
    @SerialName("safe_to_spend_cents") val safeToSpendCents: Int,
    @SerialName("safe_to_spend") val safeToSpend: String,
    @SerialName("save_cents") val saveCents: Int,
    val save: String,
    @SerialName("slip_cents") val slipCents: Int,
    val slip: String,
    @SerialName("slipped_since_friday_cents") val slippedSinceFridayCents: Int,
    @SerialName("slipped_since_friday") val slippedSinceFriday: String,
    @SerialName("buffer_cents") val bufferCents: Int,
    @SerialName("card_blocked") val cardBlocked: Boolean = false,
    val note: String,
)

@Serializable
data class EffortRowDto(
    val id: String,
    val name: String,
    @SerialName("save_cents") val saveCents: Int,
    val save: String,
    @SerialName("slip_cents") val slipCents: Int,
    val slip: String,
    @SerialName("salary_percent") val salaryPercent: Int,
    val selected: Boolean,
    @SerialName("piles_cents") val pilesCents: Map<String, Int>,
)

@Serializable
data class EffortSettingsDto(
    val selected: String,
    val efforts: List<EffortRowDto>,
    @SerialName("asp_min_cents") val aspMinCents: Int,
    @SerialName("asp_max_cents") val aspMaxCents: Int,
    val note: String,
    val today: TodayDto? = null,
)

@Serializable
data class PaymentDto(
    val id: Int,
    val month: String,
    val place: String,
    @SerialName("amount_cents") val amountCents: Int,
    val amount: String,
    val category: String,
    val basket: String,
    @SerialName("basket_name") val basketName: String,
    @SerialName("earns_bonus") val earnsBonus: Boolean,
    @SerialName("when") val whenLabel: String,
    val detail: String,
    val key: String,
    @SerialName("filed_by_customer") val filedByCustomer: Boolean,
    val recurring: Boolean,
    @SerialName("in_slip") val inSlip: Boolean,
    val reason: String,
)

@Serializable
data class PaymentsDto(val payments: List<PaymentDto>)

@Serializable
data class BillDto(
    val name: String,
    @SerialName("amount_cents") val amountCents: Int,
    val detail: String,
)

@Serializable
data class WeekDto(
    val bills: List<BillDto>,
    @SerialName("bills_cents") val billsCents: Int,
    @SerialName("buffer_cents") val bufferCents: Int,
    @SerialName("spend_cents") val spendCents: Int,
    @SerialName("safe_to_spend") val safeToSpend: String,
    @SerialName("slip_cents") val slipCents: Int,
    val effort: String,
    val note: String,
)

@Serializable
data class SpenderDto(
    @SerialName("october_out_cents") val octoberOutCents: Int,
    @SerialName("must_every_month_cents") val mustEveryMonthCents: Int,
    @SerialName("must_when_it_happens_cents") val mustWhenItHappensCents: Int,
    @SerialName("everyday_cents") val everydayCents: Int,
    @SerialName("slip_cents") val slipCents: Int,
    val lines: Int,
)

@Serializable
data class DiningDto(
    @SerialName("dining_cents") val diningCents: Int,
    val orders: Int,
    @SerialName("meal_cents") val mealCents: Int,
    @SerialName("fifth_cents") val fifthCents: Int,
    @SerialName("once_cents") val onceCents: Int,
    @SerialName("twice_cents") val twiceCents: Int,
    @SerialName("wolt_cents") val woltCents: Int,
    @SerialName("rosso_cents") val rossoCents: Int,
)

@Serializable
data class ImpulseDayDto(
    @SerialName("when") val whenLabel: String,
    @SerialName("amount_cents") val amountCents: Int,
    val count: Int,
    val places: List<String>,
)

@Serializable
data class ImpulseDto(
    val count: Int,
    val days: List<ImpulseDayDto>,
    val peak: ImpulseDayDto,
    @SerialName("coffee_visits") val coffeeVisits: Int,
    @SerialName("wolt_orders") val woltOrders: Int,
)

@Serializable
data class RunwayDto(
    val days: Int,
    @SerialName("rent_cents") val rentCents: Int,
    @SerialName("variable_cents") val variableCents: Int,
    val count: Int,
    @SerialName("per_day_cents") val perDayCents: Int,
    @SerialName("left_days") val leftDays: Int,
)

@Serializable
data class SubItemDto(
    val id: String,
    val name: String,
    @SerialName("amount_cents") val amountCents: Int,
    val use: String,
)

@Serializable
data class SubsDto(
    val items: List<SubItemDto>,
    @SerialName("month_cents") val monthCents: Int,
    @SerialName("year_cents") val yearCents: Int,
)

@Serializable
data class ChartPointDto(
    val label: String,
    @SerialName("october_pace_cents") val octoberPaceCents: Int,
    @SerialName("november_cents") val novemberCents: Int,
)

@Serializable
data class ChartsDto(
    val rows: List<ChartPointDto>,
    val note: String,
)

@Serializable
data class BonusStepDto(
    @SerialName("at_cents") val atCents: Int,
    val pct: Double,
)

@Serializable
data class BonusDto(
    @SerialName("november_sgroup_cents") val novemberSgroupCents: Int,
    @SerialName("november_bonus_cents") val novemberBonusCents: Int,
    @SerialName("november_rate_pct") val novemberRatePct: Double,
    @SerialName("october_sgroup_cents") val octoberSgroupCents: Int,
    @SerialName("october_bonus_cents") val octoberBonusCents: Int,
    @SerialName("october_rate_pct") val octoberRatePct: Double,
    @SerialName("next_step_cents") val nextStepCents: Int? = null,
    @SerialName("next_rate_pct") val nextRatePct: Double? = null,
    @SerialName("gap_cents") val gapCents: Int? = null,
    @SerialName("bonus_at_next_cents") val bonusAtNextCents: Int? = null,
    @SerialName("gain_cents") val gainCents: Int? = null,
    @SerialName("loss_cents") val lossCents: Int? = null,
    @SerialName("visa_on_gap_cents") val visaOnGapCents: Int? = null,
    @SerialName("week_at_cents") val weekAtCents: Int,
    @SerialName("month_at_cents") val monthAtCents: Int,
    @SerialName("month_bonus_cents") val monthBonusCents: Int,
    @SerialName("food_only_cents") val foodOnlyCents: Int,
    @SerialName("food_only_bonus_cents") val foodOnlyBonusCents: Int,
    val steps: List<BonusStepDto>,
    val note: String,
)

@Serializable
data class PurchaseDto(
    @SerialName("price_cents") val priceCents: Int,
    @SerialName("sgroup_now_cents") val sgroupNowCents: Int,
    @SerialName("sgroup_with_jacket_cents") val sgroupWithJacketCents: Int,
    @SerialName("gain_cents") val gainCents: Int,
    @SerialName("loss_cents") val lossCents: Int,
    @SerialName("over_slip_cents") val overSlipCents: Int,
    @SerialName("left_in_week_cents") val leftInWeekCents: Int,
)

@Serializable
data class WatchItemDto(
    val id: String,
    val name: String,
    @SerialName("amount_cents") val amountCents: Int,
    @SerialName("when") val whenLabel: String,
    val detail: String,
)

@Serializable
data class WatchDto(
    val items: List<WatchItemDto>,
    val note: String,
)

@Serializable
data class LoanDto(
    @SerialName("price_eur") val priceEur: Int,
    @SerialName("down_cents") val downCents: Int,
    @SerialName("loan_cents") val loanCents: Int,
    val years: Int,
    @SerialName("rate_percent") val ratePercent: Double,
    @SerialName("monthly_cents") val monthlyCents: Int,
    val monthly: String,
    @SerialName("stress_6pct_cents") val stress6pctCents: Int,
    @SerialName("stress_6pct") val stress6pct: String,
    val offer: String? = null,
    val decision: String? = null,
    val note: String,
)

@Serializable
data class SummaryDto(
    @SerialName("left_after_salary_cents") val leftAfterSalaryCents: Int,
    @SerialName("holds_cents") val holdsCents: Int,
)

@Serializable
data class FactDto(
    val kicker: String,
    val figure: String,
    val because: String,
)

@Serializable
data class CustomerCoachResponseDto(
    @SerialName("customer_id") val customerId: String,
    val answer: String,
    val model: String,
    val source: String,
    val disclaimer: String,
)

@Serializable
data class CoachResponseDto(
    val refused: Boolean,
    val disclaimer: String,
    val source: String? = null,
    val fact: FactDto? = null,
    val next: String? = null,
    val screen: String? = null,
    val limit: String? = null,
)

@Serializable
data class AskBody(val text: String)

@Serializable
data class EffortBody(val effort: String)

@Serializable
data class FilingBody(val category: String)

data class WireBundle(
    val session: CustomerSessionDto,
    val customerToday: CustomerTodayDto,
    val spending: SpendingDto,
    val customerSave: CustomerSaveDto,
    val effort: EffortSettingsDto,
    val payments: List<PaymentDto>,
    val summary: SummaryDto,
    val week: WeekDto,
    val spender: SpenderDto,
    val dining: DiningDto,
    val impulse: ImpulseDto,
    val runway: RunwayDto,
    val subs: SubsDto,
    val charts: ChartsDto,
    val bonus: BonusDto,
    val purchase: PurchaseDto,
    val watch: WatchDto,
    val loan: LoanDto,
)

fun PaymentDto.toPayment(): Payment = Payment(
    id = id,
    month = Month.fromCode(month),
    place = place,
    amountCents = amountCents,
    category = category,
    basket = Basket.fromCode(basket),
    earnsBonus = earnsBonus,
    whenLabel = whenLabel,
    detail = detail,
    key = key,
    filedByCustomer = filedByCustomer,
    inSlip = inSlip,
    reason = reason,
    amount = amount,
)

fun ChartPointDto.toRow(): ChartRow = ChartRow(label, octoberPaceCents, novemberCents)

fun screenFor(name: String?): Screen? = when (name) {
    "today" -> Screen.Today
    "spending" -> Screen.Spending
    "baskets" -> Screen.Baskets
    "charts" -> Screen.Charts
    "spender" -> Screen.Spender
    "week" -> Screen.Week
    "cut" -> Screen.Cut
    "save" -> Screen.Save
    "home" -> Screen.Home
    "loan" -> Screen.Loan
    "purchase" -> Screen.Purchase
    "bonus" -> Screen.Bonus
    "subs" -> Screen.Subs
    "watch" -> Screen.Watch
    "impulse" -> Screen.Impulse
    "runway" -> Screen.Runway
    else -> null
}

fun CustomerCoachResponseDto.toMessage(): CoachMessage = CoachMessage(
    role = "coach",
    paragraphs = listOf(answer),
)

fun CoachResponseDto.toMessage(charts: List<ChartRow>?): CoachMessage {
    val target = screenFor(screen)
    return CoachMessage(
        role = "coach",
        paragraphs = listOfNotNull(next),
        refused = refused,
        fact = fact?.let { CoachFact(it.kicker, it.figure, it.because) },
        source = source,
        actionLabel = target?.let { "Open ${it.title}" },
        action = target,
        chart = if (screen == "charts") charts else null,
        limit = limit,
    )
}
