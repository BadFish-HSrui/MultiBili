package tv.hsrui.bolo.player.base

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
actual fun BoloVideoPlayer(controller: BoloPlayerOutputController, modifier: Modifier) {
    val context = LocalContext.current
    val host = remember { SurfaceView(context) }
    val backend by controller.backend.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, host) {
        onDispose { controller.outputDetached(host) }
    }
    DisposableEffect(backend, host, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            backend?.setOutputForeground(host, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(backend, host) {
        val engine = backend
        engine?.bind(host)
        engine?.setOutputForeground(host, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        controller.outputAttached()
        if (engine == null) return@LaunchedEffect
        controller.outputFrameRate.collect { engine.requestFrameRate(host, it) }
    }
    AndroidView(factory = { host }, modifier = modifier)
}
