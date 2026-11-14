package fi.spendsight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App(store: SpendSightStore = remember { SpendSightStore() }) {
    LaunchedEffect(store) { store.load() }
    SystemBack(enabled = store.dialog != null || store.sheet != null || store.canBack) {
        store.onBack()
    }
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(store)
            store.notice?.let { text ->
                Text(
                    text,
                    color = Slip,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { store.notice = null }.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (store.phase) {
                    LoadPhase.Loading -> StatusPane(
                        "Reading the ledger",
                        "SpendSight is asking the service for every figure.",
                    )
                    LoadPhase.Failed -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Hero("The service did not answer", 32)
                        Lede(store.error)
                        PrimaryButton("Try again") { store.load() }
                        Note("This build calls $baseUrl.")
                    }
                    LoadPhase.Ready -> ScreenBody(store, Modifier.fillMaxSize())
                }
                if (store.phase == LoadPhase.Ready && store.canBack && store.sheet == null) {
                    var dragged = 0f
                    Box(
                        Modifier
                            .width(22.dp)
                            .fillMaxHeight()
                            .pointerInput(store.stack) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragged = 0f },
                                    onHorizontalDrag = { _, amount -> dragged += amount },
                                    onDragEnd = {
                                        if (dragged > 72f) store.pop()
                                        dragged = 0f
                                    },
                                )
                            },
                    )
                }
            }
            if (store.phase == LoadPhase.Ready && store.current == Screen.Coach) Composer(store)
            if (store.phase == LoadPhase.Ready) TabBar(store)
        }
        store.sheet?.let { SheetHost(store, it) }
        store.dialog?.let { dialog ->
            AlertDialog(
                onDismissRequest = { store.dialog = null },
                containerColor = Card,
                titleContentColor = Ink,
                textContentColor = Secondary,
                title = { Text(dialog.title, fontWeight = FontWeight.SemiBold) },
                text = { Text(dialog.body) },
                dismissButton = {
                    TextButton(onClick = { store.dialog = null }) { Text(dialog.dismissLabel, color = Secondary) }
                },
                confirmButton = {
                    TextButton(onClick = store::confirmDialog) { Text(dialog.confirmLabel, color = Green) }
                },
            )
        }
    }
}

@Composable
private fun TopBar(store: SpendSightStore) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (store.canBack) {
                Text(
                    "‹",
                    color = Green,
                    fontSize = 32.sp,
                    modifier = Modifier.clickable(onClick = store::pop),
                )
            }
        }
        Text(
            store.current.title,
            modifier = Modifier.weight(1f),
            color = Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Box(
            Modifier
                .padding(end = 6.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Deep)
                .clickable { store.sheet = Sheet.About },
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TabBar(store: SpendSightStore) {
    val selected = store.current.tab()
    Row(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .windowInsetsPadding(WindowInsets(0))
            .navigationBarsPadding()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            Column(
                Modifier.weight(1f).clickable { store.jump(tab.root) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TabGlyph(tab, if (on) Green else Tertiary)
                Spacer(Modifier.height(2.dp))
                Text(tab.label, color = if (on) Green else Tertiary, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TabGlyph(tab: Tab, color: Color) {
    val label = when (tab) {
        Tab.Today -> "⌂"
        Tab.Spending -> "≡"
        Tab.Save -> "+"
        Tab.Coach -> "◌"
    }
    Text(label, color = color, fontSize = 18.sp, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetHost(store: SpendSightStore, sheet: Sheet) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { store.sheet = null },
        sheetState = state,
        containerColor = Card,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (sheet) {
                Sheet.About -> {
                    val session = store.ledger?.session
                    val name = session?.customerId ?: "SpendSight"
                    Text(name, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Lede(
                        if (session == null) {
                            "The service has not answered yet."
                        } else {
                            "Postal code ${session.postalCode}. Age ${session.age}. Account ${session.accountId}."
                        },
                    )
                    Lede(session?.note ?: "Today, Spending, and Save come from the service. The coach answers separately, from the design sample. It does not move money, and an answer can be wrong.")
                    Lede("Bonus follows the S-group steps. One percentage covers the whole month once a step is reached. Under €50 in a month, Bonus is €0. The steps run from 1% at €50 to 5% at €900.")
                    Lede("The ASP figures use S-Pankki’s published terms: €50–€1,500 a month, 20 months, 10% of the home, 1% while saving, and 4% more when the home is bought. The loan sketch is arithmetic, not an offer.")
                    TextLink("Your data") { store.sheet = Sheet.Privacy }
                }
                Sheet.Privacy -> {
                    Text("Your data", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Lede("SpendSight reads your payments for October and 1–13 November, the holds, and the save setting you pick, and only so it can answer you. It does not read other customers. The reading stays inside the bank.")
                    Lede("Answers can be wrong. Each figure names the category, the period, and how many payments it used. Check anything that would move money. This is an automated assistant, not a person at S-Pankki.")
                    Lede("A question is sent with this customer's figures so the answer can be written. The service does not store the question. Clearing the chat removes it from this screen. It does not change the payments.")
                    TextLink("Delete this thread") { store.deleteThread() }
                    Lede("If you refile a payment, the next list uses your filing. A move between accounts is prepared only after you confirm. Booking a person uses the existing S-Pankki and Prisma customer service. This screen does not book a time.")
                    TextLink("Book a person") { store.sheet = Sheet.Book }
                }
                Sheet.Book -> {
                    Text("Book a person", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Lede("S-Pankki and Prisma already book customer service. This screen does not reserve a time, and it does not send what you asked the coach.")
                }
                is Sheet.Account -> {
                    val account = accountCopy.first { it.id == sheet.id }
                    Text(account.title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Lede(account.body)
                }
                is Sheet.PaymentSheet -> {
                    val row = store.payment(sheet.id)
                    if (row == null) {
                        Note("That payment is not loaded.")
                    } else {
                        Text(row.place, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(row.shownAmount(), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Note("${row.whenLabel} · ${row.detail}")
                        Lede(row.category, strong = true)
                        Note(row.basket.title)
                        Lede(if (row.earnsBonus) "Earns Bonus. Counts toward this month’s S-group step." else "No Bonus at this place. S-group shops and Rosso, Picnic, ABC, and Sokos do.")
                        Lede(row.reason)
                        Lede(
                            (if (row.month == Month.Oct) "October is one line per place, so a new filing covers that whole line. " else "") +
                                "A new filing changes the basket. It does not change whether this place earns Bonus.",
                        )
                        store.filingChoices().forEach { (category, basket) ->
                            ChevronRow(category, basket.title) { store.refile(row.id, category) }
                        }
                    }
                }
            }
            TextLink("Done") { store.sheet = null }
        }
    }
}

@Composable
private fun StatusPane(title: String?, body: String?) {
    if (title == null) return
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hero(title, 32)
        if (body != null) Lede(body)
    }
}

@Composable
expect fun SystemBack(enabled: Boolean, onBack: () -> Unit)
