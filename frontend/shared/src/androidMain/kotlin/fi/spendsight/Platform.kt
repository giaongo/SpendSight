package fi.spendsight

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

fun ComponentActivity.setSpendSightContent() {
    setContent { App() }
}

@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
