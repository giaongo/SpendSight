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
    val onDark = store.phase == LoadPhase.Ready && (store.current.isRoot() || store.current == Screen.Coach)
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
                Column(Modifier.statusBarsPadding()) {
                    if (store.phase == LoadPhase.Ready) SlimBar(store)
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
                Composer(store, compact = store.current != Screen.Coach)
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

@Composable
private fun SlimBar(store: SpendSightStore) {
    val coach = store.current == Screen.Coach
    val fg = if (coach) Color.White else Ink
    val back = if (coach) Color.White else Green
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (coach) Forest else Bg)
            .height(40.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (store.canBack) {
                Text(
                    "‹",
                    color = back,
                    fontSize = 26.sp,
                    modifier = Modifier.clickable(onClick = store::pop),
                )
            }
        }
        Text(
            store.current.heading(),
            modifier = Modifier.weight(1f),
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        LanguageMenu(store, onDark = coach)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun TabBar(store: SpendSightStore) {
    val selected = store.current.tab()
    val hide = hideKeyboard()
    Row(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .windowInsetsPadding(WindowInsets(0))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Card)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) Mint else Color.Transparent)
                    .clickable {
                        hide()
                        store.jump(tab.root)
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TabGlyph(tab, if (on) Forest else Tertiary)
                Text(
                    tab.heading(),
                    color = if (on) Forest else Tertiary,
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TabGlyph(tab: Tab, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension / 24f
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.7f * s,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        fun path(block: androidx.compose.ui.graphics.Path.() -> Unit) {
            drawPath(androidx.compose.ui.graphics.Path().apply(block), color, style = stroke)
        }
        fun androidx.compose.ui.graphics.Path.m(x: Float, y: Float) = moveTo(x * s, y * s)
        fun androidx.compose.ui.graphics.Path.l(x: Float, y: Float) = lineTo(x * s, y * s)
        fun androidx.compose.ui.graphics.Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
            cubicTo(x1 * s, y1 * s, x2 * s, y2 * s, x3 * s, y3 * s)
        when (tab) {
            Tab.Home -> path {
                m(4.5f, 11f); l(12f, 4.2f); l(19.5f, 11f)
                m(7.2f, 10f); l(7.2f, 19.5f); l(16.8f, 19.5f); l(16.8f, 10f)
                m(10.2f, 19.5f); l(10.2f, 14.2f); l(13.8f, 14.2f); l(13.8f, 19.5f)
            }
            Tab.SpendSight -> {
                // Three bars, so the tab reads as a spending split rather than another eye.
                val cap = androidx.compose.ui.graphics.StrokeCap.Round
                fun column(x: Float, top: Float) {
                    drawLine(
                        color,
                        start = androidx.compose.ui.geometry.Offset(x * s, 19.2f * s),
                        end = androidx.compose.ui.geometry.Offset(x * s, top * s),
                        strokeWidth = 2.15f * s,
                        cap = cap,
                    )
                }
                column(6.4f, 12.4f)
                column(12f, 5.2f)
                column(17.6f, 8.8f)
            }
            Tab.Savings -> path {
                m(12f, 20.5f); l(12f, 12f)
                m(12f, 15.5f)
                c(8f, 15.5f, 5f, 12f, 6.2f, 8.6f)
                c(9f, 10f, 11f, 13f, 12f, 15.5f)
                m(12f, 13f)
                c(16f, 12.2f, 19.2f, 8f, 17.2f, 5.4f)
                c(14.6f, 8.2f, 13f, 11f, 12f, 13f)
            }
            Tab.Offers -> path {
                m(6f, 7.5f); l(18f, 7.5f); l(18f, 16.5f); l(6f, 16.5f); close()
                m(6f, 12f); l(8.2f, 12f)
                m(10.4f, 12f); l(13.6f, 12f)
                m(15.8f, 12f); l(18f, 12f)
            }
        }
    }
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
