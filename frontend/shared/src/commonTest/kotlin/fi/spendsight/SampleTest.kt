package fi.spendsight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SampleTest {
    private val rows = samplePayments()

    @Test
    fun ledgerMatchesTheDesign() {
        fun sum(month: Month, pred: (Payment) -> Boolean = { true }) =
            rows.filter { it.month == month && pred(it) }.sumOf { it.amountCents }
        assertEquals(224_390, sum(Month.Oct))
        assertEquals(103_790, sum(Month.Oct) { it.basket == Basket.Regular })
        assertEquals(21_000, sum(Month.Oct) { it.basket == Basket.Irregular })
        assertEquals(99_600, sum(Month.Oct) { it.basket == Basket.Everyday })
        assertEquals(2_990, sum(Month.Oct) { it.key == "gym" })
        assertEquals(35_600, sum(Month.Oct) { it.category in Sample.slipCategories })
        assertEquals(65_200, sum(Month.Oct) { it.earnsBonus })
        assertEquals(121_640, sum(Month.Nov))
        assertEquals(21_400, sum(Month.Nov) { it.earnsBonus })
        assertEquals(102_830, Sample.safeSpend)
        assertEquals(7_590, Sample.billsBefore)
        assertEquals(2_400_000, Sample.effort(EffortId.Steady).piles.getValue(5))
        assertEquals(6_400, Sample.effort(EffortId.Steady).slipCents)
    }

    @Test
    fun loanSketchMatchesTheDesign() {
        assertEquals(104_512, Sample.loanPaymentCents(220_000, 40))
        assertEquals(127_572, Sample.loanStressCents(220_000))
        assertEquals("€1,028.30", eur(Sample.safeSpend))
        assertEquals("€1,045.12", eur(Sample.loanPaymentCents(220_000, 40)))
    }

    @Test
    fun coachStopsBeforeALedgerRead() {
        val reply = sampleReply("What do people in the same class save?", Sample.effort(EffortId.Steady))
        assertTrue(reply.refused)
        assertNull(reply.fact)
        assertEquals("Outside what I answer", if (reply.refused) "Outside what I answer" else "")
    }

    @Test
    fun coachAnswersTheWeekFromTheSample() {
        val reply = sampleReply("What is safe for me to spend this week?", Sample.effort(EffortId.Steady))
        assertEquals(false, reply.refused)
        assertEquals("€1,028.30", reply.fact?.figure)
        assertEquals(Screen.Week, reply.action)
    }
}
