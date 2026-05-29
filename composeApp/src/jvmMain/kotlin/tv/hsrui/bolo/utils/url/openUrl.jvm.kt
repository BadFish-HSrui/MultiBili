package tv.hsrui.bolo.utils.url

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

actual suspend fun openUrl(url: String): Boolean {
    return try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            withContext(Dispatchers.IO) {
                Desktop.getDesktop().browse(URI(url))
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }
}