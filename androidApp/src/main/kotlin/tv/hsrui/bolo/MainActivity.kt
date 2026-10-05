package tv.hsrui.bolo

import android.content.pm.ActivityInfo
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tv.hsrui.bolo.utils.url.AppContext
import tv.hsrui.bolo.player.session.BoloPlaybackSession
import tv.hsrui.bolo.navigation.ExternalLinkHandler
import tv.hsrui.bolo.download.DownloadFiles

class MainActivity : ComponentActivity() {
    private fun receiveExternalLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val url = intent.dataString ?: return
        // 消费宿主 Intent；配置重建不会再次将同一入口入队。
        intent.data = null
        ExternalLinkHandler.receiveSystemLink(url)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveExternalLink(intent)
    }

    override fun onStart() {
        super.onStart()
        BoloPlaybackSession.current?.setForeground(true)
    }

    override fun onStop() {
        if (!isChangingConfigurations) BoloPlaybackSession.current?.setForeground(false)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) BoloPlaybackSession.current?.close()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppContext.instance = application
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DownloadFiles.attach(this)
        receiveExternalLink(intent)

        requestedOrientation = if (getPlatform().isPhone) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
