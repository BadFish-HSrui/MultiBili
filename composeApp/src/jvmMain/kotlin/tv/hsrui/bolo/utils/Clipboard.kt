package tv.hsrui.bolo.utils

import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual suspend fun Clipboard.setText(text: String) {
    withContext(Dispatchers.IO) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }
}

actual suspend fun Clipboard.getText(): String? {
    return withContext(Dispatchers.IO) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } else null
    }
}