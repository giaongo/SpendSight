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
        assertEquals(0, daysSinceEpoch(1970, 1, 1))
        assertEquals("Tuesday 1 October", formatAsOf("2024-10-01", Lang.En))
        assertEquals("Wednesday 30 September", formatAsOf("2026-09-30", Lang.En))
        assertEquals("Keskiviikko 30.9.", formatAsOf("2026-09-30", Lang.Fi))
        assertEquals("Onsdag 30 september", formatAsOf("2026-09-30", Lang.Sv))
        val cats = listOf(
            SpendCategoryDto("Housing", 1, -10_000, ""),
            SpendCategoryDto("Restaurants", 2, -5_000, ""),
            SpendCategoryDto("Salary", 1, 20_000, ""),
            SpendCategoryDto("Invest Deposit", 1, -3_000, ""),
        )
        assertEquals(10_000, essentialsCents(cats))
        assertEquals(5_000, everydayCents(cats))
        val look = worthALook(cats, listOf(SpendCategoryDto("Restaurants", 1, -1_000, "")))
        assertEquals("Housing", look?.name)
        assertEquals(10_000, look?.deltaCents)
    }

    @Test
    fun formatsCentsTheWayTheServiceDoes() {
        assertEquals("1\u00A0028,30\u00A0€", eur(102_830, Lang.En))
        assertEquals("1\u00A0045,12\u00A0€", eur(104_512, Lang.En))
        assertEquals("−5,60\u00A0€", eur(-560, Lang.En))
        assertEquals("1\u00A0028,30\u00A0€", eur(102_830, Lang.Fi))
        assertEquals("−5,60\u00A0€", eur(-560, Lang.Fi))
        assertEquals("1\u00A0028,30\u00A0€", eur(102_830, Lang.Sv))
        assertEquals("150\u00A0000\u00A0€", euros(150_000, Lang.En))
        assertEquals("150\u00A0000\u00A0€", euros(150_000, Lang.Fi))
        assertEquals("4\u00A0723,58\u00A0€", presentMoney("€4,723.58", Lang.En))
        assertEquals("4\u00A0723,58\u00A0€", presentMoney("€4,723.58", Lang.Fi))
        assertEquals("−24,14\u00A0€", presentMoney("−€24.14", Lang.Sv))
        assertEquals("The balance is 4\u00A0723,58\u00A0€, and −5,60\u00A0€ left.", presentMoneyIn("The balance is €4,723.58, and −€5.60 left."))
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
        assertNull(message.card)
        assertNull(message.homeAsk)
    }

    @Test
    fun decodesTheFixedCoachCard() {
        val reply = ApiJson.decodeFromString<CustomerCoachResponseDto>(
            """
            {
              "customer_id": "CUST00007",
              "answer": "You spent it.",
              "model": "test",
              "source": "verda",
              "uses_customer_data": true,
              "disclaimer": "It can be wrong.",
              "category": "personal_finance",
              "lead": "Money went out in September.",
              "detail": "Restaurants were the largest part.",
              "caption": "AI answer · numbers come from your transactions",
              "follow_ups": [{"text": "How much have I saved?", "category": "personal_finance"}],
              "card": {
                "kind": "spending_breakdown",
                "title": "Where did your money go?",
                "context": "September 2026",
                "headline": {"label": "Money out", "amount_cents": 330417, "amount": "€3,304.17"},
                "items": [{
                  "key": "Restaurants",
                  "label": "Restaurants",
                  "amount_cents": 119859,
                  "amount": "€1,198.59",
                  "detail": "36% · 10 payments",
                  "highlight": true,
                  "bar_pct": 100
                }],
                "profile": {
                  "key": "big_buys",
                  "window": "Jul 2026 – Sep 2026",
                  "big_buys": 6,
                  "threshold_cents": 10000,
                  "flex_payments": 20,
                  "months_with_essentials": 3,
                  "months": 3
                },
                "cut": {
                  "category": "Restaurants",
                  "visits": 10,
                  "visit_cents": 11986,
                  "selected": 2,
                  "options": [{"skip": 2, "month_cents": 23972, "year_cents": 287664}]
                }
              }
            }
            """.trimIndent(),
        )
        val message = reply.toMessage(HomeAsk.Where)
        assertEquals(HomeAsk.Where, message.homeAsk)
        assertEquals(330417, message.card?.headline?.amountCents)
        assertEquals(119859, message.card?.items?.single()?.amountCents)
        assertEquals(6, message.card?.profile?.bigBuys)
        assertEquals(23972, message.card?.cut?.options?.single()?.monthCents)
        assertTrue(message.paragraphs.isEmpty())
        assertEquals("Money went out in September.", message.lead)
        assertEquals(HomeAsk.Household, homeAsk("Miten säästöni vertautuvat ikäisiini?"))
        assertEquals(HomeAsk.Save, homeAsk("Hur skulle jag kunna spara mer?"))
        assertEquals(HomeAsk.Week, homeAsk("What can I safely spend this week?"))
        assertNull(homeAsk("What is my balance?"))
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
        assertEquals("5,60 €", row.shownAmount(Lang.En))
        assertEquals("5,60 €", row.shownAmount(Lang.Fi))
        assertEquals("5,60 €", row.shownAmount(Lang.Sv))

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
    fun decodesTheRossoCouponAndTheCarouselHighlights() {
        val rosso = ApiJson.decodeFromString<CouponDto>(
            """
            {
              "coupon_id": "CPN-ROSSO",
              "brand": "Rosso",
              "title": "Rosso coupon",
              "line": "−15 % on your next 4 visits · renews monthly",
              "tag": "Fits your everyday spending",
              "benefit": "−15 %",
              "uses_per_month": 4,
              "used_this_month": 0,
              "renews_on": "2026-10-01",
              "activated": true,
              "activate": "/customers/CUST00007/coupons/CPN-ROSSO/activate",
              "deactivate": "/customers/CUST00007/coupons/CPN-ROSSO/deactivate"
            }
            """.trimIndent(),
        )
        assertEquals("Rosso coupon", rosso.title)
        assertEquals(4, rosso.usesPerMonth)
        assertEquals("/customers/CUST00007/coupons/CPN-ROSSO/deactivate", rosso.deactivate)
        assertEquals("1 Oct", formatDayMonth(rosso.renewsOn!!, Lang.En))
        assertEquals("1.10.", formatDayMonth(rosso.renewsOn!!, Lang.Fi))

        val highlights = ApiJson.decodeFromString<HighlightsDto>(
            """
            {
              "spending_change": {"category": "Restaurants", "month": "2026-09", "prior_month": "2026-08",
                "count": 10, "amount_cents": 119859, "prior_count": 10, "prior_cents": 28940, "delta_cents": 90919},
              "friday_evenings": {"category": "Restaurants", "month": "2026-09", "count": 4, "amount_cents": 51073}
            }
            """.trimIndent(),
        )
        assertEquals("1 198,59 €", eur(highlights.spendingChange!!.amountCents, Lang.En))
        assertEquals("909,19 €", eur(highlights.spendingChange!!.deltaCents, Lang.En))
        assertEquals("510,73 €", eur(highlights.fridayEvenings!!.amountCents, Lang.En))
    }

    @Test
    fun decodesCouponsAndCampaigns() {
        val coupons = ApiJson.decodeFromString<CouponsDto>(
            """
            {
              "customer_id": "CUST00001",
              "count": 1,
              "activated_count": 0,
              "note": "Sample offers for the demo. They are not real S-Pankki offers.",
              "coupons": [{
                "coupon_id": "CPN-GROCERY-5",
                "title": "€5 back on groceries",
                "description": "Pay €200 or more for groceries.",
                "benefit": "€5 back",
                "value_cents": 500,
                "value": "€5.00",
                "min_spend_cents": 20000,
                "min_spend": "€200.00",
                "percent": null,
                "days_left": 47,
                "reason": "Offered because you paid for groceries.",
                "activated": false,
                "activate": "/customers/CUST00001/coupons/CPN-GROCERY-5/activate"
              }]
            }
            """.trimIndent(),
        )
        val coupon = coupons.coupons.single()
        assertEquals(500, coupon.valueCents)
        assertEquals("€5 back", coupon.benefit)
        assertEquals("/customers/CUST00001/coupons/CPN-GROCERY-5/activate", coupon.activate)
        assertFalse(coupon.activated)
        val campaigns = ApiJson.decodeFromString<CampaignsDto>(
            """{"count":1,"campaigns":[{"campaign_id":"CMP-WINTER-2026","title":"Winter","description":"Now","days_left":47,"coupon_count":3}]}""",
        )
        assertEquals(3, campaigns.campaigns.single().couponCount)
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
    fun rendersCoachMarkdownWithoutTouchingPlainMoney() {
        val plain = parseMarkdown("The balance is €4,723.58.")
        val paragraph = plain.single() as MdBlock.Paragraph
        assertEquals(listOf(MdInline.Text("The balance is €4,723.58.")), paragraph.inlines)

        val rich = parseMarkdown(
            """
            Left the account was **€2,114.38**.

            - Groceries were **€640.10**
            - Restaurants were *less*
            1. Salary landed on 15 Sep
            See [the note](https://example.invalid/secret).
            """.trimIndent(),
        )
        assertTrue(rich[0] is MdBlock.Paragraph)
        val first = (rich[0] as MdBlock.Paragraph).inlines
        assertEquals(MdInline.Bold("€2,114.38"), first[1])
        assertTrue(rich[1] is MdBlock.Bullet)
        assertEquals(MdInline.Bold("€640.10"), (rich[1] as MdBlock.Bullet).inlines[1])
        assertEquals(MdInline.Italic("less"), (rich[2] as MdBlock.Bullet).inlines[1])
        val step = rich[3] as MdBlock.Numbered
        assertEquals(1, step.number)
        assertEquals("Salary landed on 15 Sep", (step.inlines.single() as MdInline.Text).value)
        val note = (rich[4] as MdBlock.Paragraph).inlines.single() as MdInline.Text
        assertEquals("See the note.", note.value)
        assertFalse(note.value.contains("example.invalid"))
    }

    @Test
    fun deployedUrlIsEmptyOrHttpsWithoutTrailingSlash() {
        val url = DEPLOYED_BASE_URL
        assertTrue(url.isEmpty() || (url.startsWith("https://") && !url.endsWith("/")), url)
    }
}
