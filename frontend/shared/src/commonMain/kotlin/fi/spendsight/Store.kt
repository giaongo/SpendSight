package fi.spendsight

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LoadPhase { Loading, Ready, Failed }

data class LedgerSnapshot(
    val session: CustomerSessionDto,
    val customerToday: CustomerTodayDto,
    val spending: SpendingDto,
    val customerSave: CustomerSaveDto,
    val effort: EffortSettingsDto,
    val payments: List<Payment>,
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

class SpendSightStore(
    private val api: SpendSightApi = SpendSightApi(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    var phase by mutableStateOf(LoadPhase.Loading)
    var error by mutableStateOf("")
    var notice by mutableStateOf<String?>(null)
    var disclaimer by mutableStateOf("")
    var asking by mutableStateOf(false)
    var pendingEffort by mutableStateOf<String?>(null)

    var month by mutableStateOf(Month.Oct)
    var years by mutableStateOf(5)
    var transferPrepared by mutableStateOf(false)
    var bonusPreview by mutableStateOf(false)
    var saver by mutableStateOf(false)
    var purchaseChoice by mutableStateOf("open")
    var language by mutableStateOf(Lang.En)
        private set
    var draft by mutableStateOf("")
    var balanceHidden by mutableStateOf(false)
    var coupons by mutableStateOf<CouponsDto?>(null)
    var campaigns by mutableStateOf<CampaignsDto?>(null)
    var offersError by mutableStateOf<String?>(null)
    var offersLoading by mutableStateOf(false)
    var activatingId by mutableStateOf<String?>(null)
    var priorSpending by mutableStateOf<SpendingDto?>(null)
    var cutPercent by mutableStateOf(20)
    var homeEuro by mutableStateOf(220_000)
    var rateTenths by mutableStateOf(40)
    val subs = mutableStateMapOf("gym" to "open", "netflix" to "open", "spotify" to "open")
    val watch = mutableStateMapOf("bolt" to "open", "refund" to "open")
    val messages = mutableStateListOf<CoachMessage>()
    var stack by mutableStateOf(listOf<Screen>(Screen.Today))
    var sheet by mutableStateOf<Sheet?>(null)
    var dialog by mutableStateOf<Dialog?>(null)

    var ledger by mutableStateOf<LedgerSnapshot?>(null)
        private set

    private var loadJob: Job? = null
    private var loanJob: Job? = null
    private var loanToken = 0
    private var priorToken = 0
    private var pendingFiling = false

    private val book: LedgerSnapshot get() = checkNotNull(ledger)

    val current: Screen get() = stack.last()
    val canBack: Boolean get() = stack.size > 1
    val customer: CustomerTodayDto get() = book.customerToday
    val spendingView: SpendingDto get() = book.spending
    val customerSave: CustomerSaveDto get() = book.customerSave
    val settings: EffortSettingsDto get() = book.effort
    var newestSpendMonth by mutableStateOf<String?>(null)
    val effortRow: EffortRowDto
        get() {
            val id = pendingEffort ?: book.effort.selected
            return book.effort.efforts.first { it.id == id }
        }

    val loanPriceEuro: Int
        get() = (homeEuro.coerceIn(150_000, 400_000) / 10_000) * 10_000

    val loanRateTenths: Int
        get() {
            val raw = rateTenths.coerceIn(20, 60)
            return ((raw - 20) / 5) * 5 + 20
        }

    val loanRateQuery: String
        get() {
            val tenths = loanRateTenths
            return "${tenths / 10}.${tenths % 10}"
        }

    fun loanMatches(): Boolean {
        val loan = ledger?.loan ?: return false
        val expected = loanRateTenths / 10.0
        return loan.priceEur == loanPriceEuro && abs(loan.ratePercent - expected) < 0.001
    }

    fun pileCents(): Int = effortRow.pilesCents[years.toString()] ?: 0

    fun rows(month: Month = this.month) = book.payments.filter { it.month == month }
    fun total(month: Month = this.month) = rows(month).sumOf { it.amountCents }
    fun catSum(month: Month, category: String) = rows(month).filter { it.category == category }.sumOf { it.amountCents }
    fun basketSum(month: Month, basket: Basket) = rows(month).filter { it.basket == basket }.sumOf { it.amountCents }
    fun slip(month: Month) = rows(month).filter { it.inSlip }.sumOf { it.amountCents }

    fun categories(month: Month = this.month): List<CategoryTotal> =
        rows(month).groupBy { it.category }.map { (name, list) ->
            CategoryTotal(name, list.maxBy { it.amountCents }.basket, list.sumOf { it.amountCents })
        }.sortedByDescending { it.amountCents }

    fun payment(id: Int): Payment? = ledger?.payments?.firstOrNull { it.id == id }

    fun filingChoices(): List<Pair<String, Basket>> {
        val present = ledger?.payments?.associate { it.category to it.basket } ?: return emptyList()
        val known = filingOrder.filter { it in present }.map { it to present.getValue(it) }
        val extra = present.filterKeys { it !in filingOrder }.map { it.key to it.value }
        return known + extra
    }

    fun load() {
        loadJob?.cancel()
        loadJob = scope.launch {
            phase = LoadPhase.Loading
            notice = null
            try {
                replaceLedger(preserveLoan = false)
                phase = LoadPhase.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: t(language, R.string.T_the_service_did_not_answer)
                phase = LoadPhase.Failed
            }
        }
    }

    fun ask(text: String) {
        val question = text.trim().take(280)
        val customerId = ledger?.session?.customerId
        if (question.isEmpty() || asking || customerId == null) return
        if (current != Screen.Coach) push(Screen.Coach)
        draft = ""
        messages.add(CoachMessage(role = "user", text = question))
        asking = true
        scope.launch {
            try {
                val reply = api.ask(customerId, question)
                disclaimer = reply.disclaimer
                messages.add(reply.toMessage(homeAsk(question)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.add(CoachMessage(role = "coach", paragraphs = listOf(e.message ?: t(language, R.string.T_i_could_not_reach_the_service))))
            } finally {
                asking = false
            }
        }
    }

    fun selectEffort(id: String) {
        val current = ledger?.effort?.selected ?: return
        if (id == current || pendingEffort != null) return
        pendingEffort = id
        scope.launch {
            try {
                api.putEffort(id)
                replaceLedger(preserveLoan = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message ?: t(language, R.string.T_the_save_setting_did_not_update)
            } finally {
                pendingEffort = null
            }
        }
    }

    fun refile(id: Int, category: String) {
        if (pendingFiling) return
        pendingFiling = true
        scope.launch {
            try {
                api.refile(id, category)
                replaceLedger(preserveLoan = true)
                sheet = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message ?: t(language, R.string.T_the_filing_did_not_save)
            } finally {
                pendingFiling = false
            }
        }
    }

    fun scheduleLoan() {
        val price = loanPriceEuro
        val rate = loanRateQuery
        val token = ++loanToken
        loanJob?.cancel()
        loanJob = scope.launch {
            delay(150)
            try {
                val view = api.loan(price, rate)
                if (token != loanToken) return@launch
                if (view.priceEur != price || abs(view.ratePercent - loanRateTenths / 10.0) >= 0.001) return@launch
                ledger = ledger?.copy(loan = view)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == loanToken) notice = e.message ?: t(language, R.string.T_the_loan_sketch_did_not_update)
            }
        }
    }

    fun chooseLanguage(next: Lang) {
        if (next == language) return
        language = next
        val first = messages.firstOrNull()
        if (first != null && first.paragraphs.singleOrNull() in coastOpenings) {
            messages[0] = CoachMessage(role = "coach", paragraphs = listOf(coastOpening(next)))
        }
    }

    fun deleteThread() {
        messages.clear()
        disclaimer = ""
        messages.add(CoachMessage(role = "coach", paragraphs = listOf(coastOpening(language))))
        sheet = null
    }

    fun jump(screen: Screen) {
        stack = listOf(screen)
        closeOverlays()
    }

    fun push(screen: Screen) {
        if (screen == current) return
        stack = stack + screen
        closeOverlays()
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    fun onBack() {
        when {
            dialog != null -> dialog = null
            sheet != null -> sheet = null
            canBack -> pop()
        }
    }

    fun confirmDialog() {
        when (dialog?.confirm) {
            Confirm.PrepareTransfer -> transferPrepared = true
            Confirm.CancelTransfer -> transferPrepared = false
            Confirm.SendBonus -> saver = true
            Confirm.KeepBonus -> saver = false
            null -> Unit
        }
        dialog = null
    }

    fun prepareTransfer() {
        val amount = eur(effortRow.saveCents, language)
        dialog = if (transferPrepared) {
            Dialog(
                t(language, R.string.T_cancel_the_prepared_move),
                t(language, R.string.T_the_stays_on_s_tili_nothing_has_moved, amount),
                t(language, R.string.T_keep_it_prepared),
                t(language, R.string.T_cancel_the_move),
                Confirm.CancelTransfer,
            )
        } else {
            Dialog(
                t(language, R.string.T_prepare_the_monthly_move),
                t(language, R.string.T_a_move_of_would_leave_s_tili_for_the_asp_account_t, amount),
                t(language, R.string.T_not_now),
                t(language, R.string.T_prepare_it),
                Confirm.PrepareTransfer,
            )
        }
    }

    fun toggleSaver() {
        val october = eur(book.bonus.octoberBonusCents, language)
        dialog = if (saver) {
            Dialog(
                t(language, R.string.T_keep_bonus_on_s_tili),
                t(language, R.string.T_the_next_bonus_stays_on_s_tili_where_it_can_be_spe),
                t(language, R.string.T_cancel),
                t(language, R.string.T_keep_it_on_s_tili),
                Confirm.KeepBonus,
            )
        } else {
            Dialog(
                t(language, R.string.T_send_the_next_bonus_to_s_st_j),
                t(language, R.string.T_bonus_would_go_to_s_st_j_instead_of_s_tili_october, october),
                t(language, R.string.T_not_now),
                t(language, R.string.T_send_next_bonus),
                Confirm.SendBonus,
            )
        }
    }

    private suspend fun replaceLedger(preserveLoan: Boolean) {
        val kept = if (preserveLoan) ledger?.loan else null
        val fresh = api.loadBundle().toSnapshot()
        ledger = if (kept != null) fresh.copy(loan = kept) else fresh
        newestSpendMonth = fresh.spending.month
        loadPriorSpending()
        if (messages.isEmpty()) {
            messages.add(CoachMessage(role = "coach", paragraphs = listOf(coastOpening(language))))
        }
    }

    fun loadSpending(month: String) {
        scope.launch {
            try {
                val view = api.spending(book.session.reads.spending, month)
                ledger = ledger?.copy(spending = view)
                loadPriorSpending()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message ?: t(language, R.string.T_that_month_is_not_in_the_service)
            }
        }
    }

    fun loadCustomerSave(years: Int) {
        this.years = years
        scope.launch {
            try {
                val view = api.save(book.session.reads.save, years)
                ledger = ledger?.copy(customerSave = view)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message ?: t(language, R.string.T_the_save_reading_did_not_update)
            }
        }
    }

    fun loadOffers() {
        val id = ledger?.session?.customerId ?: return
        if (offersLoading) return
        offersLoading = true
        offersError = null
        scope.launch {
            try {
                coupons = api.coupons(id)
                campaigns = api.campaigns()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                offersError = e.message ?: t(language, R.string.T_offers_failed)
            } finally {
                offersLoading = false
            }
        }
    }

    fun activateCoupon(coupon: CouponDto) {
        if (coupon.activated || coupon.activate.isBlank() || activatingId != null) return
        activatingId = coupon.couponId
        scope.launch {
            try {
                val updated = api.activateCoupon(coupon.activate)
                val current = coupons ?: return@launch
                val rows = current.coupons.map { if (it.couponId == updated.couponId) updated else it }
                coupons = current.copy(coupons = rows, activatedCount = rows.count { it.activated })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message ?: t(language, R.string.T_offers_failed)
            } finally {
                activatingId = null
            }
        }
    }

    fun loadPriorSpending() {
        val spending = ledger?.spending ?: return
        val path = ledger?.session?.reads?.spending ?: return
        val prior = shiftMonth(spending.month, -1)
        val token = ++priorToken
        priorSpending = null
        scope.launch {
            try {
                val view = api.spending(path, prior)
                if (token == priorToken && view.month == prior) priorSpending = view
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (token == priorToken) priorSpending = null
            }
        }
    }

    private fun closeOverlays() {
        sheet = null
        dialog = null
    }
}

private fun WireBundle.toSnapshot() = LedgerSnapshot(
    session = session,
    customerToday = customerToday,
    spending = spending,
    customerSave = customerSave,
    effort = effort,
    payments = payments.map { it.toPayment() },
    summary = summary,
    week = week,
    spender = spender,
    dining = dining,
    impulse = impulse,
    runway = runway,
    subs = subs,
    charts = charts,
    bonus = bonus,
    purchase = purchase,
    watch = watch,
    loan = loan,
)
