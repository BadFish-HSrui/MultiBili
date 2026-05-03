package tv.hsrui.bolo.utils

import androidx.compose.ui.platform.Clipboard

expect suspend fun Clipboard.setText(text: String)

expect suspend fun Clipboard.getText(): String?