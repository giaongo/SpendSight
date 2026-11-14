package fi.spendsight

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiContractTest {
    @Test
    fun formatsCentsTheWayTheServiceDoes() {
        assertEquals("€1,028.30", eur(102_830))
        assertEquals("€1,045.12", eur(104_512))
        assertEquals("−€5.60", eur(-560))
        assertEquals("1", formatPct(1.0))
        assertEquals("1.5", formatPct(1.5))
        assertEquals("3.5", formatPct(3.5))
    }

    @Test
    fun diningTwentyUsesTheServiceFifth() {
        assertEquals(3_720, diningWhatIf(18_600, 3_720, 20))
        assertEquals(0, diningWhatIf(18_600, 3_720, 0))
        assertEquals(9_300, diningWhatIf(18_600, 3_720, 50))
    }

    @Test
    fun decodesTheCoachContract() {
        val week = ApiJson.decodeFromString<CoachResponseDto>(
            """
            {
              "refused": false,
              "disclaimer": "Answers can be wrong. Check a decision before you act. This is not a person at the bank.",
              "source": "13 Nov · Helen and Fressi · 2 bills, plus the buffer",
              "fact": {
                "kicker": "Balance − bills before payday − buffer",
                "figure": "€1,028.30",
                "because": "Counted by the service."
              },
              "next": "Bills after payday stay out.",
              "screen": "week",
              "limit": null,
              "intent": "allowed",
              "tool": "safeWeek"
            }
            """.trimIndent(),
        )
        val message = week.toMessage(charts = null)
        assertFalse(message.refused)
        assertEquals("€1,028.30", message.fact?.figure)
        assertEquals(Screen.Week, message.action)
        assertEquals("Open This week", message.actionLabel)
        assertNull(message.chart)

        val refused = ApiJson.decodeFromString<CoachResponseDto>(
            """
            {
              "refused": true,
              "disclaimer": "Answers can be wrong.",
              "source": null,
              "fact": null,
              "next": "Comparing you with other households is later.",
              "screen": null,
              "limit": "Outside what I answer"
            }
            """.trimIndent(),
        )
        assertTrue(refused.refused)
        assertNull(refused.fact)
        assertEquals("Outside what I answer", refused.toMessage(null).limit)
        assertNull(refused.toMessage(null).action)
    }

    @Test
    fun decodesPaymentWeekAndLoanShapes() {
        val payment = ApiJson.decodeFromString<PaymentDto>(
            """
            {
              "id": 36,
              "month": "nov",
              "place": "Picnic",
              "amount_cents": 560,
              "amount": "€5.60",
              "category": "Coffee & drinks",
              "basket": "everyday",
              "basket_name": "Everyday",
              "earns_bonus": true,
              "when": "13 Nov",
              "detail": "Coffee",
              "key": "picnic",
              "filed_by_customer": false,
              "recurring": false,
              "in_slip": true,
              "reason": "Filed as Coffee & drinks."
            }
            """.trimIndent(),
        )
        val row = payment.toPayment()
        assertEquals(Month.Nov, row.month)
        assertEquals(Basket.Everyday, row.basket)
        assertEquals("13 Nov", row.whenLabel)
        assertTrue(row.inSlip)
        assertEquals("€5.60", row.shownAmount())

        val loan = ApiJson.decodeFromString<LoanDto>(
            """
            {
              "price_eur": 220000,
              "down_cents": 2200000,
              "loan_cents": 19800000,
              "years": 25,
              "rate_percent": 4.0,
              "monthly_cents": 104512,
              "monthly": "€1,045.12",
              "stress_6pct_cents": 127572,
              "stress_6pct": "€1,275.72",
              "offer": null,
              "decision": null,
              "note": "Arithmetic on the price and rate you set."
            }
            """.trimIndent(),
        )
        assertEquals(220_000, loan.priceEur)
        assertEquals("€1,045.12", loan.monthly)
        assertEquals("€1,275.72", loan.stress6pct)
        assertNull(loan.offer)
        assertNull(loan.decision)

        val effort = ApiJson.decodeFromString<EffortSettingsDto>(
            """
            {
              "selected": "steady",
              "efforts": [
                {
                  "id": "steady",
                  "name": "Steady",
                  "save_cents": 40000,
                  "save": "€400.00",
                  "slip_cents": 6400,
                  "slip": "€64.00",
                  "salary_percent": 14,
                  "selected": true,
                  "piles_cents": {"1": 480000, "3": 1440000, "5": 2400000}
                }
              ],
              "asp_min_cents": 5000,
              "asp_max_cents": 150000,
              "note": "The pile is the monthly amount times twelve times the years."
            }
            """.trimIndent(),
        )
        assertEquals(2_400_000, effort.efforts.single().pilesCents["5"])
        assertNull(effort.today)
    }

    @Test
    fun decodesTheCustomerRead() {
        val today = ApiJson.decodeFromString<CustomerTodayDto>(
            """
            {
              "kind": "read",
              "screen": "today",
              "customer_id": "CUST00001",
              "postal_code": "00990",
              "age": 63,
              "account_id": "ACC000001",
              "mortgage_customer_id": null,
              "mortgage_decision": null,
              "as_of": "2026-09-30",
              "balance_cents": 472358,
              "balance": "€4,723.58",
              "latest_salary_cents": 244781,
              "latest_salary_date": "2026-09-15",
              "month": "2026-09",
              "month_in_cents": 244781,
              "month_out_cents": 211438,
              "transaction_count": 581,
              "first_date": "2024-10-03",
              "note": "First customer in the database."
            }
            """.trimIndent(),
        )
        assertEquals("CUST00001", today.customerId)
        assertEquals("€4,723.58", today.balance)
        assertEquals(211_438, today.monthOutCents)
        assertNull(today.mortgageCustomerId)
        assertEquals("2026-08", shiftMonth("2026-09", -1))
        assertEquals("2026-01", shiftMonth("2025-12", 1))

        val tx = ApiJson.decodeFromString<SpendTxDto>(
            """
            {"tx_date":"2026-09-01","tx_timestamp":"01SEP26:15:18:10","category":"Transportation","amount_cents":-2414,"amount":"−€24.14","balance_cents":436601,"transaction_type":"purchase","merchant_id":null,"mcc_code":null}
            """.trimIndent(),
        )
        assertNull(tx.merchantId)
        assertEquals("−€24.14", tx.amount)
    }

    @Test
    fun mapsCoachScreensAndReadsFastApiDetail() {
        assertEquals(Screen.Spender, screenFor("spender"))
        assertEquals(Screen.Cut, screenFor("cut"))
        assertEquals(Screen.Charts, screenFor("charts"))
        assertEquals(Screen.Watch, screenFor("watch"))
        assertNull(screenFor("peer"))
        assertEquals("month is oct or nov", apiDetail("""{"detail":"month is oct or nov"}"""))
        assertEquals(
            "text is empty",
            apiDetail("""{"detail":[{"loc":["body","text"],"msg":"text is empty","type":"value_error"}]}"""),
        )
    }

    @Test
    fun trustsOnlyLocalDevHosts() {
        assertTrue(isLocalDevHost("127.0.0.1"))
        assertTrue(isLocalDevHost("localhost"))
        assertTrue(isLocalDevHost("10.0.2.2"))
        assertTrue(isLocalDevHost("192.168.1.20"))
        assertFalse(isLocalDevHost("example.com"))
        assertFalse(isLocalDevHost("1.1.1.1"))
    }

    @Test
    fun deployedUrlIsEmptyOrHttpsWithoutTrailingSlash() {
        val url = DEPLOYED_BASE_URL
        assertTrue(url.isEmpty() || (url.startsWith("https://") && !url.endsWith("/")), url)
    }
}
