package tv.hsrui.bolo.utils

import android.content.ClipData
import androidx.compose.ui.platform.Clipboard

actual suspend fun Clipboard.setText(text: String) {
    nativeClipboard.setPrimaryClip(ClipData.newPlainText(null, text))
}

actual suspend fun Clipboard.getText(): String? {
    return if (nativeClipboard.primaryClip != null) {
        val clip = nativeClipboard.primaryClip
        if (clip?.itemCount == 0) null
        else clip?.getItemAt(0)?.text?.toString()
    } else {
        null
    }
}