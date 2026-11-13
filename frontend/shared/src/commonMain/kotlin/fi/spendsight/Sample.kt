package fi.spendsight

/**
 * Aino’s sample, copied from the design. The Python service owns the live figures.
 * These values are the published sample the phone shows until that service is connected.
 * List totals on Spending are the sum of the rows on screen, so a refile updates that list.
 */
object Sample {
    const val balance = 118_420
    const val salary = 284_000
    const val safeSpend = 102_830
    const val billsBefore = 7_590
    const val buffer = 8_000
    const val leftUntilDecember = 263_830
    const val slippedSinceFriday = 560
    const val jacket = 8_900
    const val jacketRoom = 93_930
    const val jacketGain = 285
    const val jacketLoss = 8_615
    const val jacketWithSgroup = 30_300
    const val octoberDining = 18_600
    const val diningFifth = 3_720
    const val diningOnce = 8_268
    const val diningTwice = 16_536
    const val diningMeal = 2_067
    const val diningOrders = 9
    const val woltOctober = 11_800
    const val rossoOctober = 6_800
    const val subsMonth = 4_990
    const val subsYear = 59_880
    const val fressi = 2_990
    const val netflix = 800
    const val spotify = 1_200
    const val runwayDays = 38
    const val runwayPerDay = 3_049
    const val runwayVariable = 39_640
    const val runwayRent = 82_000
    const val runwayCount = 15
    const val impulsePeakCount = 3
    const val impulsePeakAmount = 5_140
    const val impulseCount = 9
    const val aspSaved = 640_000
    const val aspGoal = 2_200_000
    const val aspMonthsDone = 14
    const val aspMonthsNeed = 20
    const val aspMin = 5_000
    const val aspMax = 150_000
    const val homeEuro = 220_000
    const val rentOctober = 82_000
    const val octoberBonus = 2_282
    const val novemberSgroup = 21_400
    const val novemberBonus = 321
    const val bonusAtNext = 600
    const val bonusGap = 8_600
    const val bonusGain = 279
    const val bonusLoss = 8_321
    const val visaOnGap = 43
    const val weekShop = 7_000
    const val coffeePlan = 1_600
    const val groceryPlan = 28_000
    const val monthAt = 51_000
    const val monthBonus = 1_530
    const val foodOnly = 49_400
    const val foodOnlyBonus = 1_235
    const val octoberSgroup = 65_200
    const val octoberNextGap = 4_800
    const val octoberNextBonus = 2_800
    const val otherGrocers = 11_100
    const val octoberMust = 103_790
    const val octoberSlip = 35_600
    const val repair = 16_500
    const val hsl = 7_400

    val slipCategories = setOf("Dining out", "Coffee & drinks", "Taxis", "Pharmacy", "Shopping")
    val impulseCategories = setOf("Dining out", "Coffee & drinks", "Taxis", "Shopping")

    val efforts = listOf(
        Effort(EffortId.Small, "Small", 5_000, 8_307, 2, 312, mapOf(1 to 60_000, 3 to 180_000, 5 to 300_000), 593),
        Effort(EffortId.Steady, "Steady", 40_000, 6_400, 14, 39, mapOf(1 to 480_000, 3 to 1_440_000, 5 to 2_400_000), 2_500),
        Effort(EffortId.Big, "Big", 80_000, 4_000, 28, 20, mapOf(1 to 960_000, 3 to 2_880_000, 5 to 4_800_000), 4_900),
    )

    val holds = listOf(
        Hold("December rent", "Leaves 2 Dec. The next salary is 15 Dec.", 82_000),
        Hold("Elisa", "18 Nov, same as October", 3_000),
        Hold("Helen", "14 Nov, the same day it left in October. Due before payday.", 4_600, beforePayday = true),
        Hold("HSL season ticket", "20 Nov, after payday", 7_400),
        Hold("If insurance", "Monthly, not posted yet. October left on 20 Oct.", 1_800),
        Hold("Netflix", "Spotify already left on 5 Nov. No date before payday.", 800),
        Hold("Fressi", "October left on 2 Oct. November has not left, so it is already due.", 2_990, beforePayday = true),
        Hold("Groceries still to buy", "Food for the rest of November. A plan, not a bill.", 28_000),
        Hold("Cushion", "The buffer for a repair. October’s repair was €165.", 8_000, buffer = true),
    )

    val charts = listOf(
        ChartRow("Groceries", 27_733, 23_400),
        ChartRow("Coffee & drinks", 3_727, 3_600),
        ChartRow("Dining out", 8_060, 8_840),
        ChartRow("Taxis", 1_950, 2_600),
    )

    val filings = listOf(
        "Rent" to Basket.Regular,
        "Bills" to Basket.Regular,
        "Transport pass" to Basket.Regular,
        "Repair" to Basket.Irregular,
        "Health" to Basket.Irregular,
        "Groceries" to Basket.Everyday,
        "Dining out" to Basket.Everyday,
        "Coffee & drinks" to Basket.Everyday,
        "Taxis" to Basket.Everyday,
        "Pharmacy" to Basket.Everyday,
        "Shopping" to Basket.Everyday,
    )

    val accounts = listOf(
        AccountCopy("tili", "S-Tili", "Everyday. Bonus lands here.", "Everyday account. Salary, rent, and Bonus land here. The safe-to-spend number is counted from this account."),
        AccountCopy("asp", "ASP account", "The home. Plan to leave it there.", "For a first home. A month is €50 to €1,500, and the loan wants at least 20 months. Pauses are allowed, so a missed month is not a failure. S-Pankki pays 1% while you save. Another 4% is paid when you buy the home, for the year you started and up to five calendar years after that. Both are tax-free. The extra interest is paid at the purchase. It is not a monthly forecast, and it is not paid on money you took out before then. Opening the account happens with S-Pankki. This screen does not open one."),
        AccountCopy("tuotto", "S-Tuottotili", "Savings you can take.", "Savings you can take out when you need them. The cushion for a broken machine belongs here, not on the ASP account. Interest depends on the balance. This design does not quote a rate."),
        AccountCopy("maara", "S-Määräaikaistili", "Stays until a date.", "A fixed date. The money stays until that date. Use it only for an amount you already know you will not need."),
        AccountCopy("saastaja", "Säästäjä", "Bonus, card rounding, or a monthly sum. A fund can fall.", "Four ways to save. Send Bonus to a fund. Set aside €0.50 to €5 on each debit-card payment. Move a monthly sum. Add extra when you want. The funds can fall. This design does not predict them. October’s Bonus stays on S-Tili unless you send the next one."),
    )

    val bonusLadder = listOf(
        5.0 to 90_000,
        4.5 to 80_000,
        4.0 to 70_000,
        3.5 to 60_000,
        3.0 to 50_000,
        2.5 to 40_000,
        2.0 to 30_000,
        1.5 to 20_000,
        1.0 to 5_000,
    )

    fun effort(id: EffortId) = efforts.first { it.id == id }

    /** Payments at a snapped home price and rate. Rates are 2.0% to 6.0% by 0.5. Prices are €10,000 steps. */
    fun loanPaymentCents(priceEuro: Int, rateTenths: Int): Int {
        val price = priceEuro.coerceIn(150_000, 400_000)
        val snappedPrice = (price / 10_000) * 10_000
        val rate = rateTenths.coerceIn(20, 60)
        val index = ((rate - 20) / 5).coerceIn(0, 8)
        return loanPays.getValue(snappedPrice)[index]
    }

    fun loanStressCents(priceEuro: Int): Int {
        val snappedPrice = (priceEuro.coerceIn(150_000, 400_000) / 10_000) * 10_000
        return loanPays.getValue(snappedPrice).last()
    }
}

private fun p(
    id: Int,
    month: Month,
    place: String,
    cents: Int,
    basket: Basket,
    category: String,
    bonus: Boolean,
    whenLabel: String,
    key: String,
    detail: String,
) = Payment(id, month, place, cents, category, basket, bonus, whenLabel, detail, key)

fun samplePayments(): List<Payment> = listOf(
    p(0, Month.Oct, "Vuokra", 82_000, Basket.Regular, "Rent", false, "2 Oct", "rent", "Rent"),
    p(1, Month.Oct, "Elisa", 3_000, Basket.Regular, "Bills", false, "28 Oct", "elisa", "Phone"),
    p(2, Month.Oct, "Helen", 4_600, Basket.Regular, "Bills", false, "14 Oct", "helen", "Electricity"),
    p(3, Month.Oct, "If", 1_800, Basket.Regular, "Bills", false, "20 Oct", "if", "Insurance"),
    p(4, Month.Oct, "Spotify and Netflix", 2_000, Basket.Regular, "Bills", false, "5 Oct", "stream", "Streaming"),
    p(5, Month.Oct, "Fressi", 2_990, Basket.Regular, "Bills", false, "2 Oct", "gym", "Gym"),
    p(6, Month.Oct, "HSL", 7_400, Basket.Regular, "Transport pass", false, "15 Oct", "hsl", "Season ticket"),
    p(7, Month.Oct, "Kodinkonehuolto", 16_500, Basket.Irregular, "Repair", false, "8 Oct", "repair", "Washing machine"),
    p(8, Month.Oct, "Hammaslääkäri", 4_500, Basket.Irregular, "Health", false, "26 Oct", "dentist", "Dentist"),
    p(9, Month.Oct, "Prisma Herttoniemi", 40_000, Basket.Everyday, "Groceries", true, "October", "prisma", "9 visits"),
    p(10, Month.Oct, "S-market", 12_900, Basket.Everyday, "Groceries", true, "October", "smarket", "6 visits"),
    p(11, Month.Oct, "K-Market Kamppi", 6_400, Basket.Everyday, "Groceries", false, "October", "kmarket", "4 visits"),
    p(12, Month.Oct, "Lidl", 4_700, Basket.Everyday, "Groceries", false, "October", "lidl", "3 visits"),
    p(13, Month.Oct, "Wolt", 11_800, Basket.Everyday, "Dining out", false, "October", "wolt", "7 orders"),
    p(14, Month.Oct, "Rosso", 6_800, Basket.Everyday, "Dining out", true, "October", "rosso", "2 visits"),
    p(15, Month.Oct, "Robert's Coffee", 4_600, Basket.Everyday, "Coffee & drinks", false, "October", "roberts", "11 visits"),
    p(16, Month.Oct, "Picnic", 2_400, Basket.Everyday, "Coffee & drinks", true, "October", "picnic", "5 visits"),
    p(17, Month.Oct, "ABC", 1_600, Basket.Everyday, "Coffee & drinks", true, "October", "abc", "2 visits"),
    p(18, Month.Oct, "Bolt", 4_500, Basket.Everyday, "Taxis", false, "October", "bolt", "2 rides"),
    p(19, Month.Oct, "Yliopiston Apteekki", 2_400, Basket.Everyday, "Pharmacy", false, "27 Oct", "pharmacy", "1 visit"),
    p(20, Month.Oct, "Sokos", 1_500, Basket.Everyday, "Shopping", true, "27 Oct", "sokos", "1 visit"),
    p(21, Month.Nov, "Vuokra", 82_000, Basket.Regular, "Rent", false, "3 Nov", "rent", "Rent"),
    p(22, Month.Nov, "K-Market Kamppi", 3_800, Basket.Everyday, "Groceries", false, "4 Nov", "kmarket", "Groceries"),
    p(23, Month.Nov, "Bolt", 1_420, Basket.Everyday, "Taxis", false, "5 Nov", "bolt", "Taxi"),
    p(24, Month.Nov, "Spotify", 1_200, Basket.Regular, "Bills", false, "5 Nov", "stream", "Streaming"),
    p(25, Month.Nov, "Prisma Herttoniemi", 7_200, Basket.Everyday, "Groceries", true, "6 Nov", "prisma", "Groceries"),
    p(26, Month.Nov, "Wolt", 3_240, Basket.Everyday, "Dining out", false, "7 Nov", "wolt", "Dinner"),
    p(27, Month.Nov, "S-market", 4_600, Basket.Everyday, "Groceries", true, "8 Nov", "smarket", "Groceries"),
    p(28, Month.Nov, "Robert's Coffee", 1_240, Basket.Everyday, "Coffee & drinks", false, "8 Nov", "roberts", "Coffee"),
    p(29, Month.Nov, "Lidl", 2_200, Basket.Everyday, "Groceries", false, "9 Nov", "lidl", "Groceries"),
    p(30, Month.Nov, "Rosso", 2_800, Basket.Everyday, "Dining out", true, "10 Nov", "rosso", "Dinner"),
    p(31, Month.Nov, "Prisma Herttoniemi", 5_600, Basket.Everyday, "Groceries", true, "11 Nov", "prisma", "Groceries"),
    p(32, Month.Nov, "Picnic", 640, Basket.Everyday, "Coffee & drinks", true, "11 Nov", "picnic", "Coffee"),
    p(33, Month.Nov, "Wolt", 2_800, Basket.Everyday, "Dining out", false, "12 Nov", "wolt", "Dinner"),
    p(34, Month.Nov, "Bolt", 1_180, Basket.Everyday, "Taxis", false, "12 Nov", "bolt", "Taxi"),
    p(35, Month.Nov, "Robert's Coffee", 1_160, Basket.Everyday, "Coffee & drinks", false, "12 Nov", "roberts", "Coffee"),
    p(36, Month.Nov, "Picnic", 560, Basket.Everyday, "Coffee & drinks", true, "13 Nov", "picnic", "Coffee"),
)

fun paymentNote(row: Payment): String = when {
    row.basket == Basket.Irregular -> "This had to be paid, and the day was a surprise. The cushion on This week is for the next one."
    row.category == "Taxis" -> "The season ticket is the plan. This ride is the part that slips through."
    row.key == "wolt" -> "Dinner that does not earn Bonus. Rosso, in the same category, does."
    row.key == "rosso" -> "Dinner that earns Bonus and counts toward the step."
    row.earnsBonus && row.category == "Groceries" -> "This shop counts toward the Bonus step."
    !row.earnsBonus && (row.category == "Groceries" || row.category == "Coffee & drinks") ->
        "This place pays no Bonus. The same kind of purchase at Prisma, S-market, Picnic, or ABC would."
    row.basket == Basket.Regular -> "This leaves on its own schedule. A bill due before payday is subtracted in the safe-to-spend number. A later one is not."
    row.earnsBonus -> "This earns Bonus."
    else -> "This is everyday spending, chosen one payment at a time."
}

fun visitCount(detail: String): Int {
    val match = Regex("""(\d+)\s+(visits|orders|rides)""").find(detail)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: 1
}

private val loanPays: Map<Int, IntArray> = mapOf(
    150000 to intArrayOf(57220, 60563, 64019, 67584, 71258, 75037, 78920, 82902, 86981),
    160000 to intArrayOf(61035, 64601, 68286, 72090, 76009, 80040, 84181, 88429, 92779),
    170000 to intArrayOf(64850, 68638, 72554, 76595, 80759, 85042, 89442, 93955, 98578),
    180000 to intArrayOf(68664, 72676, 76822, 81101, 85510, 90045, 94704, 99482, 104377),
    190000 to intArrayOf(72479, 76713, 81090, 85607, 90260, 95047, 99965, 105009, 110176),
    200000 to intArrayOf(76294, 80751, 85358, 90112, 95011, 100050, 105226, 110536, 115974),
    210000 to intArrayOf(80108, 84789, 89626, 94618, 99761, 105052, 110488, 116063, 121773),
    220000 to intArrayOf(83923, 88826, 93894, 99123, 104512, 110055, 115749, 121589, 127572),
    230000 to intArrayOf(87738, 92864, 98162, 103629, 109262, 115057, 121010, 127116, 133370),
    240000 to intArrayOf(91553, 96901, 102430, 108135, 114013, 120060, 126271, 132643, 139169),
    250000 to intArrayOf(95367, 100939, 106698, 112640, 118763, 125062, 131533, 138170, 144968),
    260000 to intArrayOf(99182, 104976, 110965, 117146, 123514, 130065, 136794, 143696, 150767),
    270000 to intArrayOf(102997, 109014, 115233, 121652, 128264, 135067, 142055, 149223, 156565),
    280000 to intArrayOf(106811, 113051, 119501, 126157, 133015, 140070, 147317, 154750, 162364),
    290000 to intArrayOf(110626, 117089, 123769, 130663, 137765, 145072, 152578, 160277, 168163),
    300000 to intArrayOf(114441, 121127, 128037, 135168, 142516, 150075, 157839, 165804, 173961),
    310000 to intArrayOf(118255, 125164, 132305, 139674, 147266, 155077, 163101, 171330, 179760),
    320000 to intArrayOf(122070, 129202, 136573, 144180, 152017, 160080, 168362, 176857, 185559),
    330000 to intArrayOf(125885, 133239, 140841, 148685, 156768, 165082, 173623, 182384, 191358),
    340000 to intArrayOf(129699, 137277, 145109, 153191, 161518, 170085, 178885, 187911, 197156),
    350000 to intArrayOf(133514, 141314, 149377, 157696, 166269, 175087, 184146, 193438, 202955),
    360000 to intArrayOf(137329, 145352, 153644, 162202, 171019, 180090, 189407, 198964, 208754),
    370000 to intArrayOf(141143, 149389, 157912, 166708, 175770, 185092, 194668, 204491, 214552),
    380000 to intArrayOf(144958, 153427, 162180, 171213, 180520, 190095, 199930, 210018, 220351),
    390000 to intArrayOf(148773, 157464, 166448, 175719, 185271, 195097, 205191, 215545, 226150),
    400000 to intArrayOf(152588, 161502, 170716, 180224, 190021, 200100, 210452, 221071, 231949),
)
