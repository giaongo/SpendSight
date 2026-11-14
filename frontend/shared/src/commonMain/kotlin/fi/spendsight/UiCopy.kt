package fi.spendsight

/** Account explanations. These are not ledger figures. */
val accountCopy = listOf(
    AccountCopy(
        "tili",
        "S-Tili",
        "Everyday. Bonus lands here.",
        "Everyday account. Salary, rent, and Bonus land here. The safe-to-spend number is counted from this account.",
    ),
    AccountCopy(
        "asp",
        "ASP account",
        "The home. Plan to leave it there.",
        "For a first home. A month is €50 to €1,500, and the loan wants at least 20 months. Pauses are allowed, so a missed month is not a failure. S-Pankki pays 1% while you save. Another 4% is paid when you buy the home, for the year you started and up to five calendar years after that. Both are tax-free. The extra interest is paid at the purchase. It is not a monthly forecast, and it is not paid on money you took out before then. Opening the account happens with S-Pankki. This screen does not open one.",
    ),
    AccountCopy(
        "tuotto",
        "S-Tuottotili",
        "Savings you can take.",
        "Savings you can take out when you need them. The cushion for a broken machine belongs here, not on the ASP account. Interest depends on the balance. This design does not quote a rate.",
    ),
    AccountCopy(
        "maara",
        "S-Määräaikaistili",
        "Stays until a date.",
        "A fixed date. The money stays until that date. Use it only for an amount you already know you will not need.",
    ),
    AccountCopy(
        "saastaja",
        "Säästäjä",
        "Bonus, card rounding, or a monthly sum. A fund can fall.",
        "Four ways to save. Send Bonus to a fund. Set aside €0.50 to €5 on each debit-card payment. Move a monthly sum. Add extra when you want. The funds can fall. This design does not predict them. October’s Bonus stays on S-Tili unless you send the next one.",
    ),
)

/** Shown before the first coast answer. The service writes the disclaimer after that. */
const val coastOpening =
    "Ask about this customer's transactions. The service writes the answer. It can be wrong, and it does not move money."

/** Questions the chat chips send to this customer's coast endpoint. The service writes the answer. */
val coachChips = listOf(
    "Balance" to "What is my balance?",
    "Last salary" to "When was my latest salary, and how much was it?",
    "Last month" to "How much left my account last month?",
    "Biggest category" to "What did I spend the most on?",
    "Recent months" to "How did money in and money out change over the recent months?",
    "Mortgage" to "Is there a mortgage decision on this customer?",
)

/** Categories the filing route accepts. Unknown names are rejected. */
val filingOrder = listOf(
    "Rent",
    "Bills",
    "Transport pass",
    "Repair",
    "Health",
    "Groceries",
    "Dining out",
    "Coffee & drinks",
    "Taxis",
    "Pharmacy",
    "Shopping",
)
