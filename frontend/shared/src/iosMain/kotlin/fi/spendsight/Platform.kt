package fi.spendsight

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App() }

@Composable
actual fun SystemBack(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun SystemBars(onDark: Boolean) = Unit
