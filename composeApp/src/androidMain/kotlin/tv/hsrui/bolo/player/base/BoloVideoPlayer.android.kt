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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

@Composable
actual fun BoloVideoPlayer(controller: BoloPlayerController, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val host = remember { TextureView(context) }
    val backend by controller.backend.collectAsState()
    DisposableEffect(controller, owner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { controller.setForeground(true) }
            override fun onStop(owner: LifecycleOwner) { controller.setForeground(false) }
        }
        owner.lifecycle.addObserver(observer)
        controller.outputAttached()
        onDispose { owner.lifecycle.removeObserver(observer); controller.release() }
    }
    LaunchedEffect(backend, host) { backend?.bind(host) }
    AndroidView(factory = { host }, modifier = modifier)
}
