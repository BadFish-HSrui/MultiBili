package tv.hsrui.bolo.utils

import androidx.compose.ui.platform.Clipboard
import platform.UIKit.UIPasteboard

actual suspend fun Clipboard.setText(text: String) {
    UIPasteboard.generalPasteboard.string = text
}

actual suspend fun Clipboard.getText(): String? {
    return UIPasteboard.generalPasteboard.string
}