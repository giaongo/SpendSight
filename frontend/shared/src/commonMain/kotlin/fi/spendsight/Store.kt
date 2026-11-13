package fi.spendsight

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class SpendSightStore {
    var month by mutableStateOf(Month.Oct)
    var effortId by mutableStateOf(EffortId.Steady)
    var years by mutableStateOf(5)
    var transferPrepared by mutableStateOf(false)
    var bonusPreview by mutableStateOf(false)
    var saver by mutableStateOf(false)
    var purchase by mutableStateOf("open")
    var cutPercent by mutableStateOf(20)
    var homeEuro by mutableStateOf(Sample.homeEuro)
    var rateTenths by mutableStateOf(40)
    val subs = mutableStateMapOf("gym" to "open", "netflix" to "open", "spotify" to "open")
    val watch = mutableStateMapOf("bolt" to "open", "refund" to "open")
    val payments = mutableStateListOf<Payment>().apply { addAll(samplePayments()) }
    val messages = mutableStateListOf(openingMessage)
    var stack by mutableStateOf(listOf<Screen>(Screen.Coach))
    var sheet by mutableStateOf<Sheet?>(null)
    var dialog by mutableStateOf<Dialog?>(null)

    val current: Screen get() = stack.last()
    val effort: Effort get() = Sample.effort(effortId)
    val canBack: Boolean get() = stack.size > 1

    fun rows(month: Month = this.month) = payments.filter { it.month == month }
    fun total(month: Month = this.month) = rows(month).sumOf { it.amountCents }
    fun catSum(month: Month, category: String) = rows(month).filter { it.category == category }.sumOf { it.amountCents }
    fun basketSum(month: Month, basket: Basket) = rows(month).filter { it.basket == basket }.sumOf { it.amountCents }
    fun keySum(month: Month, key: String) = rows(month).filter { it.key == key }.sumOf { it.amountCents }
    fun slip(month: Month) = rows(month).filter { it.category in Sample.slipCategories }.sumOf { it.amountCents }

    fun categories(month: Month = this.month): List<CategoryTotal> =
        rows(month).groupBy { it.category }.map { (name, list) ->
            CategoryTotal(name, list.maxBy { it.amountCents }.basket, list.sumOf { it.amountCents })
        }.sortedByDescending { it.amountCents }

    fun impulseDays(): List<ImpulseDay> =
        rows(Month.Nov).filter { it.category in Sample.impulseCategories }
            .groupBy { it.whenLabel }
            .map { (whenLabel, list) ->
                ImpulseDay(whenLabel, list.size, list.map { it.place }, list.sumOf { it.amountCents })
            }
            .sortedBy { it.whenLabel.substringBefore(" ").toIntOrNull() ?: 0 }

    fun coffeeVisits(): Int =
        rows(Month.Oct).filter { it.category == "Coffee & drinks" }.sumOf { visitCount(it.detail) }

    fun woltOrders(): Int =
        rows(Month.Oct).find { it.key == "wolt" }?.let { visitCount(it.detail) } ?: 0

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

    fun ask(text: String) {
        val question = text.trim().take(280)
        if (question.isEmpty()) return
        messages.add(CoachMessage(role = "user", text = question))
        messages.add(sampleReply(question, effort))
    }

    fun refile(id: Int, category: String, basket: Basket) {
        val index = payments.indexOfFirst { it.id == id }
        if (index < 0) return
        payments[index] = payments[index].copy(category = category, basket = basket, filedByCustomer = true)
        sheet = null
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
        val amount = eur(effort.saveCents)
        dialog = if (transferPrepared) {
            Dialog(
                "Cancel the prepared move",
                "The $amount stays on S-Tili. Nothing has moved.",
                "Keep it prepared",
                "Cancel the move",
                Confirm.CancelTransfer,
            )
        } else {
            Dialog(
                "Prepare the monthly move",
                "After Thursday’s salary, $amount would move from S-Tili to the ASP account. The coach prepared this. It does not run until you confirm, and it does not open a new account. You can cancel it before Thursday.",
                "Not now",
                "Prepare it",
                Confirm.PrepareTransfer,
            )
        }
    }

    fun toggleSaver() {
        dialog = if (saver) {
            Dialog(
                "Keep Bonus on S-Tili",
                "The next Bonus stays on S-Tili, where it can be spent. Nothing in this design moves money.",
                "Cancel",
                "Keep it on S-Tili",
                Confirm.KeepBonus,
            )
        } else {
            Dialog(
                "Send the next Bonus to Säästäjä",
                "From 10 Dec, Bonus would go to Säästäjä instead of S-Tili. October’s Bonus is already on S-Tili. You can change this later. This preview does not move money.",
                "Not now",
                "Send next Bonus",
                Confirm.SendBonus,
            )
        }
    }

    private fun closeOverlays() {
        sheet = null
        dialog = null
    }
}

/** Share of salary, in tenths of a percent, for a published monthly payment. */
fun salaryShareTenths(payCents: Int): Int =
    (((payCents * 1000L) + Sample.salary / 2) / Sample.salary).toInt()

fun shareLabel(payCents: Int): String {
    val tenths = salaryShareTenths(payCents)
    return "${tenths / 10}.${tenths % 10}%"
}
