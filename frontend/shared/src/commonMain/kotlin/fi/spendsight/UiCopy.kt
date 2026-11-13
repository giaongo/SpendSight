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

/** Questions the coach chips send. The service writes the answer. */
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
