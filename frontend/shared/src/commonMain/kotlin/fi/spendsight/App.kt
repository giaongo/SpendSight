package fi.spendsight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalEditorGate = staticCompositionLocalOf { EditorGate() }

@Composable
fun App(store: SpendSightStore = remember { SpendSightStore() }) {
    LaunchedEffect(store) { store.load() }
    SystemBack(enabled = store.dialog != null || store.sheet != null || store.canBack) {
        store.onBack()
    }
    val onDark = store.phase == LoadPhase.Ready
    SystemBars(onDark)
    val gate = remember { EditorGate() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    CompositionLocalProvider(LocalLang provides store.language, LocalEditorGate provides gate) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .pointerInput(gate) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    gate.down = false
                    waitForUpOrCancellation(pass = PointerEventPass.Final)
                    if (!gate.down) {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            val rooted = store.phase == LoadPhase.Ready && store.current.isRoot()
            if (!rooted) {
                if (store.phase == LoadPhase.Ready) {
                    SlimBar(store)
                } else {
                    Spacer(Modifier.statusBarsPadding())
                }
            }
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
            if (store.phase == LoadPhase.Ready && store.current.showsComposer()) {
                Composer(store)
            }
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

/** The short green bar on a drill-in screen: Back, the title, and the language menu. */
@Composable
private fun SlimBar(store: SpendSightStore) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Forest)
            .statusBarsPadding()
            .heightIn(min = 52.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            store.current.heading(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 96.dp),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (store.canBack) {
                Row(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = store::pop)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(Icon.Back, Color.White, 22.dp, stroke = 2.4f)
                    Spacer(Modifier.width(2.dp))
                    Text(t(R.string.T_back), color = Color.White, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            LanguageMenu(store, onDark = true)
        }
    }
}

/** Zone 3: the floating tab bar. */
@Composable
private fun TabBar(store: SpendSightStore) {
    val selected = store.current.tab()
    val hide = hideKeyboard()
    Box(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(14.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                .clip(RoundedCornerShape(30.dp))
                .background(Card.copy(alpha = 0.96f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (on) Green.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable {
                            hide()
                            store.jump(tab.root)
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    TabGlyph(tab, on)
                    Text(
                        tab.heading(),
                        color = if (on) Green else Graphite,
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabGlyph(tab: Tab, on: Boolean) {
    val icon = when (tab) {
        Tab.Home -> Icon.Home
        Tab.SpendSight -> Icon.Sight
        Tab.Savings -> Icon.Sprout
        Tab.Offers -> Icon.Ticket
    }
    Glyph(
        icon,
        if (on) Green else Graphite,
        24.dp,
        stroke = if (on) 2.1f else 1.8f,
        fill = if (on) Green.copy(alpha = 0.18f) else null,
    )
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

@Composable
expect fun SystemBars(onDark: Boolean)
