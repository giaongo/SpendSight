package fi.spendsight

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiContractTest {
    @Test
    fun switchesBetweenEnglishFinnishAndSwedish() {
        assertEquals("Today", t(Lang.En, R.string.T_today))
        assertEquals("Tänään", t(Lang.Fi, R.string.T_today))
        assertEquals("Idag", t(Lang.Sv, R.string.T_today))
        assertEquals(listOf("FI", "EN", "SW"), Lang.entries.map { it.short })
        assertEquals("EN", Lang.En.short)
        assertEquals("FI", Lang.Fi.short)
        assertEquals("SW", Lang.Sv.short)
        assertEquals("Svenska", Lang.Sv.endonym)
        assertTrue(coastOpening(Lang.Fi).contains("asiakkaan"))
        assertTrue(coastOpening(Lang.En).contains("customer's"))
        assertTrue(coastOpening(Lang.Sv).contains("kundens"))
        assertFalse(coastOpening(Lang.En).contains('\\'))
        assertTrue(coastOpening(Lang.En) in coastOpenings)
        assertTrue(coastOpening(Lang.Sv) in coastOpenings)
        assertEquals("Coffee & drinks", t(Lang.En, R.string.T_coffee_drinks))
        assertEquals("Kahvi ja juomat", t(Lang.Fi, R.string.T_coffee_drinks))
        assertEquals("Kaffe och dryck", t(Lang.Sv, R.string.T_coffee_drinks))
        assertEquals("Housing", categoryLabel(Lang.En, "Housing"))
        assertEquals("Asuminen", categoryLabel(Lang.Fi, "Housing"))
        assertEquals("Boende", categoryLabel(Lang.Sv, "Housing"))
        assertEquals("Yleiskulut", categoryLabel(Lang.Fi, "Utilities"))
        assertEquals("Hushållsräkningar", categoryLabel(Lang.Sv, "Utilities"))
        assertEquals("Asumismaksu", typeLabel(Lang.Fi, "housing_payment"))
        assertEquals("Bostadsbetalning", typeLabel(Lang.Sv, "housing_payment"))
        assertEquals("Mystery", categoryLabel(Lang.Sv, "Mystery"))
        assertEquals(
            "Saldo on viimeisimmän tapahtuman jälkeen.",
            knownNote(Lang.Fi, "The balance is after their latest transaction."),
        )
        assertEquals("Verda said this.", knownNote(Lang.Sv, "Verda said this."))
        assertEquals("From 1 to 2. 3 transactions.", t(Lang.En, R.string.T_from_to_transactions, "1", "2", "3"))
        assertEquals("Ajalta 1–2. 3 tapahtumaa.", t(Lang.Fi, R.string.T_from_to_transactions, "1", "2", "3"))
        assertEquals("Från 1 till 2. 3 transaktioner.", t(Lang.Sv, R.string.T_from_to_transactions, "1", "2", "3"))
        assertTrue(t(Lang.En, R.string.T_bonus_follows_the_s_group_steps_one_percentage_cov).contains("1%"))
        assertTrue(t(Lang.Fi, R.string.T_bonus_follows_the_s_group_steps_one_percentage_cov).contains("1 %"))
        assertTrue(t(Lang.Sv, R.string.T_bonus_follows_the_s_group_steps_one_percentage_cov).contains("1 %"))
        assertEquals("b a", formatString("%2\$s %1\$s", arrayOf("a", "b")))
        assertEquals("5%", formatString("5%%", emptyArray()))
    }

    @Test
    fun formatsCentsTheWayTheServiceDoes() {
        assertEquals("€1,028.30", eur(102_830, Lang.En))
        assertEquals("€1,045.12", eur(104_512, Lang.En))
        assertEquals("−€5.60", eur(-560, Lang.En))
        assertEquals("€1\u00A0028,30", eur(102_830, Lang.Fi))
        assertEquals("€1\u00A0045,12", eur(104_512, Lang.Fi))
        assertEquals("−€5,60", eur(-560, Lang.Fi))
        assertEquals("€1\u00A0028,30", eur(102_830, Lang.Sv))
        assertEquals("€1\u00A0045,12", eur(104_512, Lang.Sv))
        assertEquals("−€5,60", eur(-560, Lang.Sv))
        assertEquals("€150,000", euros(150_000, Lang.En))
        assertEquals("€150\u00A0000", euros(150_000, Lang.Fi))
        assertEquals("€150\u00A0000", euros(150_000, Lang.Sv))
        assertEquals("€4\u00A0723,58", presentMoney("€4,723.58", Lang.Fi))
        assertEquals("−€24,14", presentMoney("−€24.14", Lang.Fi))
        assertEquals("€4\u00A0723,58", presentMoney("€4,723.58", Lang.Sv))
        assertEquals("−€24,14", presentMoney("−€24.14", Lang.Sv))
        assertEquals("€4,723.58", presentMoney("€4,723.58", Lang.En))
        assertEquals("1", formatPct(1.0, Lang.En))
        assertEquals("1.5", formatPct(1.5, Lang.En))
        assertEquals("1,5", formatPct(1.5, Lang.Fi))
        assertEquals("3,5", formatPct(3.5, Lang.Fi))
        assertEquals("1,5", formatPct(1.5, Lang.Sv))
        assertEquals("3,5", formatPct(3.5, Lang.Sv))
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
    fun decodesTheCustomerChat() {
        val reply = ApiJson.decodeFromString<CustomerCoachResponseDto>(
            """
            {
              "customer_id": "CUST00001",
              "answer": "The balance is €4,723.58.",
              "model": "Qwen/Qwen2.5-1.5B-Instruct",
              "source": "verda",
              "disclaimer": "This answer is generated from that customer's transactions. It can be wrong. It does not move money."
            }
            """.trimIndent(),
        )
        val message = reply.toMessage()
        assertEquals("CUST00001", reply.customerId)
        assertEquals("Qwen/Qwen2.5-1.5B-Instruct", reply.model)
        assertEquals("The balance is €4,723.58.", message.paragraphs.single())
        assertNull(message.source)
        assertFalse(message.refused)
        assertNull(message.fact)
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
        assertEquals("€5.60", row.shownAmount(Lang.En))
        assertEquals("€5,60", row.shownAmount(Lang.Fi))
        assertEquals("€5,60", row.shownAmount(Lang.Sv))

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
