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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    CompositionLocalProvider(LocalLang provides store.language) {
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
                        t(R.string.T_reading_the_ledger),
                        t(R.string.T_spendsight_is_asking_the_service_for_every_figure),
                    )
                    LoadPhase.Failed -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Hero(t(R.string.T_the_service_did_not_answer_2), 32)
                        Lede(store.error)
                        PrimaryButton(t(R.string.T_try_again)) { store.load() }
                        Note(t(R.string.T_this_build_calls, baseUrl))
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
}

@Composable
private fun LanguageSwitch(store: SpendSightStore) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Fill).padding(2.dp),
    ) {
        Lang.entries.forEach { lang ->
            val on = store.language == lang
            Text(
                lang.short,
                color = if (on) Color.White else Secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Green else Color.Transparent)
                    .clickable { store.chooseLanguage(lang) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
            store.current.heading(),
            modifier = Modifier.weight(1f),
            color = Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        LanguageSwitch(store)
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
                Text(tab.heading(), color = if (on) Green else Tertiary, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
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
                            t(R.string.T_the_service_has_not_answered_yet)
                        } else {
                            t(R.string.T_postal_code_age_account, session.postalCode, session.age, session.accountId)
                        },
                    )
                    Lede(
                        session?.note?.let { knownNote(it) } ?: t(R.string.T_today_spending_and_save_come_from_the_service_the),
                    )
                    Lede(t(R.string.T_bonus_follows_the_s_group_steps_one_percentage_cov))
                    Lede(t(R.string.T_the_asp_figures_use_s_pankki_s_published_terms_50))
                    TextLink(t(R.string.T_your_data)) { store.sheet = Sheet.Privacy }
                }
                Sheet.Privacy -> {
                    Text(t(R.string.T_your_data), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Lede(t(R.string.T_spendsight_reads_your_payments_for_october_and_1_1))
                    Lede(t(R.string.T_answers_can_be_wrong_each_figure_names_the_categor))
                    Lede(t(R.string.T_a_question_is_sent_with_this_customer_s_figures_so))
                    TextLink(t(R.string.T_delete_this_thread)) { store.deleteThread() }
                    Lede(t(R.string.T_if_you_refile_a_payment_the_next_list_uses_your_fi))
                    TextLink(t(R.string.T_book_a_person)) { store.sheet = Sheet.Book }
                }
                Sheet.Book -> {
                    Text(t(R.string.T_book_a_person), color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Lede(t(R.string.T_s_pankki_and_prisma_already_book_customer_service))
                }
                is Sheet.Account -> {
                    val account = accounts(store.language).first { it.id == sheet.id }
                    Text(account.title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Lede(account.body)
                }
                is Sheet.PaymentSheet -> {
                    val row = store.payment(sheet.id)
                    if (row == null) {
                        Note(t(R.string.T_that_payment_is_not_loaded))
                    } else {
                        Text(row.place, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(row.shownAmount(), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Note("${row.whenLabel} · ${row.detail}")
                        Lede(categoryLabel(row.category), strong = true)
                        Note(row.basket.heading())
                        Lede(
                            if (row.earnsBonus) {
                                t(R.string.T_earns_bonus_counts_toward_this_month_s_s_group_ste)
                            } else {
                                t(R.string.T_no_bonus_at_this_place_s_group_shops_and_rosso_pic)
                            },
                        )
                        Lede(row.reason)
                        Lede(
                            (if (row.month == Month.Oct) t(R.string.T_october_is_one_line_per_place_so_a_new_filing_cove) else "") +
                                t(R.string.T_a_new_filing_changes_the_basket_it_does_not_change),
                        )
                        store.filingChoices().forEach { (category, basket) ->
                            ChevronRow(categoryLabel(category), basket.heading()) { store.refile(row.id, category) }
                        }
                    }
                }
            }
            TextLink(t(R.string.T_done)) { store.sheet = null }
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
