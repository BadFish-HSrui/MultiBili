package tv.hsrui.bolo.utils.url

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri

object AppContext {
    lateinit var instance: Application
}

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AppContext.instance.startActivity(intent)
}