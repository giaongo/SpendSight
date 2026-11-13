package fi.spendsight

/**
 * Offline stand-in for POST /coach/ask. The sentences are the design’s sample replies.
 * The service will replace this when it is connected. The phone does not recompute the figures.
 */
fun sampleReply(raw: String, effort: Effort): CoachMessage {
    val q = raw.lowercase()
    fun stop(paragraphs: List<String>, label: String? = null, screen: Screen? = null) = CoachMessage(
        role = "coach",
        refused = true,
        paragraphs = paragraphs,
        actionLabel = label,
        action = screen,
    )
    if (Regex("""hide money|launder|fake (income|salary)|lie (on|to) the|evade tax|tax evasion|don't tell the bank|dont tell the bank|cheat the bonus|false application""").containsMatchIn(q)) {
        return stop(
            listOf("I can’t help with hiding money, a false application, or a Bonus that was not earned. If a charge is not yours, that belongs in Watch."),
            "Open Watch",
            Screen.Watch,
        )
    }
    if (Regex("""ignore (your|the) (rules|instructions)|jailbreak|developer mode|pretend you are|you are now a""").containsMatchIn(q)) {
        return stop(listOf("I only answer questions about your own spending. I can’t switch roles or ignore that limit."))
    }
    if (Regex("""pay off|payoff|pay the loan|early repayment|get out of (the |my )?loan""").containsMatchIn(q)) {
        return stop(listOf("I don’t answer how to pay a loan off, or how to shape an application. A later screen can sketch a monthly payment. It is not an offer."))
    }
    if (Regex("""peer|people like me|same class|other people|customers like|average person|neighbour|neighbor|what do people""").containsMatchIn(q)) {
        return stop(listOf("Comparing you with other households is later. It would use anonymised open figures, such as Statistics Finland. I will not invent an average."))
    }
    if (Regex("""boyfriend|girlfriend|pregnant|diagnos|depress|politic|religion|password|pin code|henkilötunnus|another person|my (mom|dad|wife|husband|friend)'s""").containsMatchIn(q)) {
        return stop(listOf("I only answer questions about your payments. I don’t answer about health, relationships, beliefs, secrets, or another person’s money."))
    }
    if (Regex("""impulse|when do i tend|spur of the moment|unplanned""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            fact = CoachFact(
                "12 Nov · busiest dated day",
                "${Sample.impulsePeakCount} payments",
                "Wolt, Bolt, Robert's Coffee, ${eur(Sample.impulsePeakAmount)}. October’s coffee is 18 visits rolled up by place, so that month has no weekday.",
            ),
            paragraphs = listOf("Groceries and rent are not counted as impulse. No payment here has a time of day, so I will not guess evening or payday."),
            source = counted("1–13 Nov", "Dining, coffee, and taxis", Sample.impulseCount),
            actionLabel = "See the days",
            action = Screen.Impulse,
        )
    }
    if (Regex("""run out|days until|money last|current pace""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            fact = CoachFact(
                "Rent left out of the pace",
                "${Sample.runwayDays} days",
                "${eur(Sample.balance)} divided by ${eur(Sample.runwayPerDay)} a day. ${eur(Sample.runwayVariable)} left in 13 days, not counting rent that already left.",
            ),
            paragraphs = listOf("Bills after Thursday are not subtracted here. Safe to spend until payday is the number that does that."),
            source = counted("1–13 Nov", "All payments except rent", Sample.runwayCount),
            actionLabel = "See the division",
            action = Screen.Runway,
        )
    }
    if (Regex("""top 3|least-used|least used|cancel my""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            fact = CoachFact(
                "If all three stop",
                eur(Sample.subsMonth),
                "${eur(Sample.subsYear)} across a year. Fressi has a charge and no visit. Netflix and Spotify have no use count, so this is not a measured ranking of use.",
            ),
            paragraphs = listOf("Fressi ${eur(Sample.fressi)}, Netflix ${eur(Sample.netflix)}, Spotify ${eur(Sample.spotify)}. Spotify already left on 5 Nov, so a cancel starts next time. Nothing here cancels a charge."),
            source = counted("Oct–Nov", "Fressi, Netflix, Spotify", 2, "charges") + " and 1 hold",
            actionLabel = "Open subscriptions",
            action = Screen.Subs,
        )
    }
    if (Regex("""buy|jacket|afford this|purchase""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            paragraphs = listOf(
                "The Sokos jacket is ${eur(Sample.jacket)}. This week’s line is ${eur(effort.slipCents)}, so it is ${eur(effort.jacketOverCents)} over.",
                "Sokos earns Bonus. As new spending, Bonus rises ${eur(Sample.jacketGain)} and you are ${eur(Sample.jacketLoss)} poorer. The grocery shop you already need is the payment that raises the step.",
                "Thursday’s salary is ${eur(Sample.salary)}. Waiting is the calm choice. You can still take it from the slip line.",
            ),
            source = counted("13 Nov", "S-group purchases plus this jacket", 7),
            actionLabel = "Open the pause",
            action = Screen.Purchase,
        )
    }
    if (Regex("""home plan|first home|how is the home|asp account|save up for a house|saving for a house""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            fact = CoachFact(
                "This month, not a loan decision",
                eur(effort.saveCents),
                "That is the save that matches your ${effort.name.lowercase()} setting. The multi-year home screen is later.",
            ),
            paragraphs = listOf("The later plan has ${eur(Sample.aspSaved)} of ${eur(Sample.aspGoal)}, and ${Sample.aspMonthsDone} of ${Sample.aspMonthsNeed} months. I will not say whether a bank should lend, and I will not answer how to pay a loan off."),
            source = "Your save setting · ${effort.name} · not a count of payments",
            actionLabel = "Open the later plan",
            action = Screen.Home,
        )
    }
    if (Regex("""safe to save|how much should i save|how much is safe to save|saving too much|save too much|asp""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            fact = CoachFact(
                "${effort.name} save",
                eur(effort.saveCents),
                "${effort.salaryPercent}% of Thursday’s salary. Safe to spend until payday is ${eur(Sample.safeSpend)}. €1,500 would still be one ASP month.",
            ),
            paragraphs = listOf("The cushion is ${eur(Sample.buffer)}, and it is already the buffer in the safe-to-spend number. Keep anything above this save where you can take it."),
            source = "Your save setting · ${effort.name} · not a count of payments",
            actionLabel = "Open Save",
            action = Screen.Save,
        )
    }
    if (Regex("""subscri|gym|fressi|netflix|forgot|recurring""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            paragraphs = listOf(
                "Spotify, ${eur(Sample.spotify)}, already left on 5 Nov.",
                "Netflix, ${eur(Sample.netflix)}, has not left yet. Fressi was ${eur(Sample.fressi)} on 2 Oct and is due again. The ledger has the charge and no visit, so I ask before treating it as wanted.",
            ),
            source = counted("Oct–Nov", "Fressi, Netflix, Spotify", 2, "charges") + " and 1 hold",
            actionLabel = "Open subscriptions",
            action = Screen.Subs,
        )
    }
    if (Regex("""refund|fraud|odd|isn't mine|isnt mine|billing|second bolt""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            paragraphs = listOf(
                "A second Bolt of ${eur(1_180)} sits four minutes after the ride on 12 Nov. It is not in the spending total.",
                "Prisma still owes ${eur(420)} from 11 Nov. It is not on S-Tili.",
            ),
            source = "12 Nov and 11 Nov · a second Bolt and a refund · 2 items kept out of the total",
            actionLabel = "Open Watch",
            action = Screen.Watch,
        )
    }
    if (Regex("""chart|show me|against october|compare""").containsMatchIn(q)) {
        val rows = when {
            Regex("""coffee|drink""").containsMatchIn(q) -> Sample.charts.filter { it.label == "Coffee & drinks" }
            Regex("""din|wolt|restaurant""").containsMatchIn(q) -> Sample.charts.filter { it.label == "Dining out" }
            Regex("""taxi|bolt""").containsMatchIn(q) -> Sample.charts.filter { it.label == "Taxis" }
            Regex("""grocer|food""").containsMatchIn(q) -> Sample.charts.filter { it.label == "Groceries" }
            else -> Sample.charts
        }
        val line = if (rows.size == 1) {
            val row = rows.first()
            "${row.label}. Grey is October’s pace for 13 days, ${eur(row.octoberCents)}. Green is 1–13 November, ${eur(row.novemberCents)}."
        } else {
            "Grey is October’s pace for 13 days. Green is what has left so far in November."
        }
        val category = if (rows.size == 1) rows.first().label else "Groceries, coffee, dining, taxis"
        val noun = if (rows.size == 1) "category" else "categories"
        return CoachMessage(
            role = "coach",
            paragraphs = listOf(line),
            chart = rows,
            source = counted("1–13 Nov", category, rows.size, noun),
            actionLabel = "See the charts",
            action = Screen.Charts,
        )
    }
    if (Regex("""loan|mortgage|afford a home|house price""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            paragraphs = listOf(
                "At ${eur(Sample.homeEuro * 100)} with 10% down, a 25-year sketch at 4.0% is ${eur(104_512)} a month. That is 36.8% of salary. Rent today is ${eur(Sample.rentOctober)}.",
                "This is arithmetic, not an offer, and it is not how much the bank will lend. Drag the price if the payment is too large a share.",
            ),
            source = "A price and a rate you set · 25 years · not your payments",
            actionLabel = "Open the sketch",
            action = Screen.Loan,
        )
    }
    if (Regex("""emergency|cushion""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            paragraphs = listOf(
                "The cushion is ${eur(Sample.buffer)}. One quiet month of must-pay in October was ${eur(Sample.octoberMust)}.",
                "Keep the cushion where you can take it. The ASP account is for the home.",
            ),
            source = counted("October", "Must, every month", 7, "lines"),
            actionLabel = "Open the home plan",
            action = Screen.Home,
        )
    }
    if (Regex("""taxi|bolt|uber""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            paragraphs = listOf(
                "Taxis in October were ${eur(4_500)}, two Bolt rides.",
                "The HSL season ticket was ${eur(Sample.hsl)}. That one is the plan. The rides are the part that slips through.",
            ),
            source = counted("October", "Taxis", 2, "rides"),
            actionLabel = "Open taxis",
            action = Screen.Category("Taxis"),
        )
    }
    if (Regex("""spender|kind of spender""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            fact = CoachFact(
                "October, the last full month",
                "A regular payer.",
                "${eur(Sample.octoberMust)} left on a schedule. ${eur(Sample.octoberSlip)} slipped through in small payments. Robert’s was 18 visits. Wolt was 7 orders.",
            ),
            paragraphs = listOf("Groceries were ${eur(64_000)}, mostly Prisma and S-market. The repair and the dentist were ${eur(21_000)}, and the day was not yours to pick. October’s small payments are rolled up by place, so that month has no weekday."),
            source = counted("October", "Three baskets", 21, "lines"),
            actionLabel = "Open the portrait",
            action = Screen.Spender,
        )
    }
    if (Regex("""once a week|twice a week""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            fact = CoachFact(
                "Once a week, not twice",
                eur(Sample.diningOnce),
                "October had ${Sample.diningOrders} dining payments, about two a week. The average was ${eur(Sample.diningMeal)}. One fewer each week, for four weeks, is ${eur(Sample.diningOnce)} a month, ${eur(Sample.diningOnce * 12)} a year.",
            ),
            paragraphs = listOf("Two meals a week less would be ${eur(Sample.diningTwice)} a month. October only had ${Sample.diningOrders} payments, so about one meal would remain. A 20% cut is smaller, ${eur(Sample.diningFifth)} a month, ${eur(Sample.diningFifth * 12)} a year, and it can come from Wolt. Rosso, ${eur(Sample.rossoOctober)}, earns Bonus."),
            source = counted("October", "Dining out", Sample.diningOrders),
            actionLabel = "Move the percentage",
            action = Screen.Cut,
        )
    }
    if (Regex("""safe|this week|pocket""").containsMatchIn(q) && !q.contains("save")) {
        return CoachMessage(
            role = "coach",
            fact = CoachFact(
                "Balance − bills before payday − buffer",
                eur(Sample.safeSpend),
                "${eur(Sample.balance)} on S-Tili, minus Helen ${eur(4_600)} and Fressi ${eur(Sample.fressi)}, minus a ${eur(Sample.buffer)} buffer.",
            ),
            paragraphs = listOf("Elisa, the season ticket, insurance, Netflix, and December rent are after Thursday, so they are not in this number. Coffee, taxis, and meals have a separate line of ${eur(effort.slipCents)}. Passing that line sends one note. The card is not blocked."),
            source = counted("13 Nov", "Helen and Fressi", 2, "bills") + ", plus the buffer",
            actionLabel = "See the arithmetic",
            action = Screen.Week,
        )
    }
    if (Regex("""dining|wolt|restaurant|20%|20 percent|cut""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            fact = CoachFact(
                "Dining out, minus 20%",
                eur(Sample.diningFifth),
                "A month, from October’s ${eur(Sample.octoberDining)}. Across a year that is ${eur(Sample.diningFifth * 12)}. It can come from Wolt, ${eur(Sample.woltOctober)}. Rosso stays.",
            ),
            paragraphs = listOf("Once a week instead of twice would save ${eur(Sample.diningOnce)} a month, ${eur(Sample.diningOnce * 12)} a year. Two meals a week less would save ${eur(Sample.diningTwice)} a month, and about one of the ${Sample.diningOrders} October meals would remain. Rosso, ${eur(Sample.rossoOctober)}, earns Bonus."),
            source = counted("October", "Dining out", Sample.diningOrders),
            actionLabel = "Move the percentage",
            action = Screen.Cut,
        )
    }
    if (Regex("""bonus|tier|green card|säästä|saasta|prisma""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            paragraphs = listOf(
                "This month S-group purchases are ${eur(Sample.novemberSgroup)}, so the step is 1.5% and Bonus so far is ${eur(Sample.novemberBonus)}.",
                "The next step is 2% at ${eur(30_000)}. That is ${eur(Sample.bonusGap)} away, and that ${eur(Sample.bonusGap)} changes the rate on the whole amount. Bonus becomes ${eur(Sample.bonusAtNext)}.",
                "Use the green card for the grocery shop and two café visits you are going to buy anyway. If the whole food plan stays there, November reaches ${eur(Sample.monthAt)} and Bonus is ${eur(Sample.monthBonus)}.",
            ),
            source = counted("1–13 Nov", "S-group places", 6),
            actionLabel = "Open Bonus",
            action = Screen.Bonus,
        )
    }
    if (Regex("""coffee|drink|robert|picnic""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            paragraphs = listOf(
                "Coffee and drinks in October were ${eur(8_600)}. Robert’s Coffee was ${eur(4_600)}. Picnic was ${eur(2_400)} and ABC was ${eur(1_600)}. Picnic and ABC earn Bonus.",
                "Two café visits this week, ${eur(Sample.coffeePlan)}, are part of the move to the next step.",
            ),
            source = counted("October", "Coffee and drinks", 18, "visits"),
            actionLabel = "Open coffee and drinks",
            action = Screen.Category("Coffee & drinks"),
        )
    }
    if (Regex("""grocer|food|lidl|k-market|eat""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            paragraphs = listOf("Groceries in October were ${eur(64_000)}. Prisma and S-market were ${eur(52_900)}. K-Market and Lidl were ${eur(Sample.otherGrocers)}, and that part earned no Bonus."),
            source = counted("October", "Groceries", 4, "lines"),
            actionLabel = "Open groceries",
            action = Screen.Category("Groceries"),
        )
    }
    if (Regex("""rent|bill|hsl|salary""").containsMatchIn(q)) {
        return CoachMessage(
            role = "coach",
            paragraphs = listOf(
                "The regular basket in October was ${eur(Sample.octoberMust)}: rent ${eur(Sample.rentOctober)}, bills ${eur(14_390)}, and the season ticket ${eur(Sample.hsl)}.",
                "November’s rent has already left. Bills due before Thursday are in the safe-to-spend number. The later ones are not.",
            ),
            source = counted("October", "Must, every month", 7, "lines"),
            actionLabel = "Open the baskets",
            action = Screen.Baskets,
        )
    }
    return CoachMessage(
        role = "coach",
        paragraphs = listOf("I can answer what kind of spender you are, what is safe this week, a smaller dining habit, when impulse payments cluster, how many days the current pace lasts, and what cancelling three subscriptions would keep. I stop for other people’s money, for paying a loan off, and for anything that is not a payment."),
    )
}

val openingMessage = CoachMessage(
    role = "coach",
    paragraphs = listOf("I answer from your payments only. The figures are counted, and I can still be wrong. Check anything that would move money. I am not a person at the bank, and I do not move money."),
)

val coachChips = listOf(
    "What kind of spender" to "What kind of spender am I?",
    "Safe this week" to "What is safe for me to spend this week?",
    "Dining minus 20%" to "How much could I save if I reduced dining out by 20%?",
    "Once a week" to "What if I dine out once a week instead of twice?",
    "Impulse" to "When do I tend to impulse spend?",
    "Days left" to "How many days until my money runs out at my current pace?",
    "Cancel 3 subs" to "What happens if I cancel my top 3 least-used subscriptions?",
    "People like me" to "What do people in the same class save?",
)
