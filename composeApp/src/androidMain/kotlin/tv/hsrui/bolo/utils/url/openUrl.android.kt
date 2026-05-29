package tv.hsrui.bolo.utils.url

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri

object AppContext {
    lateinit var instance: Application
}

actual suspend fun openUrl(url: String): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        AppContext.instance.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}