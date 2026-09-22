package tv.hsrui.bolo

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tv.hsrui.bolo.utils.url.AppContext
import tv.hsrui.bolo.player.session.BoloPlaybackSession
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
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

        val widthDp = resources.configuration.screenWidthDp
        val heightDP = resources.configuration.screenHeightDp
        val maxScreenDp = max(widthDp, heightDP)
        val minScreenDp = min(widthDp, heightDP)
        val isPhone = (minScreenDp < 600 && maxScreenDp < 840)

        if (isPhone)
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

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