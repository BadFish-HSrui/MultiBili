package tv.hsrui.bolo.player.base

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun BoloVideoPlayer(controller: BoloPlayerController, modifier: Modifier) {
    val context = LocalContext.current
    val host = remember { TextureView(context) }
    val backend by controller.backend.collectAsState()
    DisposableEffect(controller, host) {
        onDispose { controller.outputDetached(host) }
    }
    LaunchedEffect(backend, host) { backend?.bind(host); controller.outputAttached() }
    AndroidView(factory = { host }, modifier = modifier)
}
