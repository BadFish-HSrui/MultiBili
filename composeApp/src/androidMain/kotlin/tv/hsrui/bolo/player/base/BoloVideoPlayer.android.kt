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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
actual fun BoloVideoPlayer(controller: BoloPlayerController, modifier: Modifier) {
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
        combine(controller.state, controller.info) { playback, info ->
            val active = playback.playWhenReady && !playback.isEnded && !playback.isPlaybackSuspended && !playback.isRebuilding
            (info.video.fps?.times(playback.playbackSpeed)?.takeIf { active && it.isFinite() && it > 0 } ?: 0.0).toFloat()
        }.distinctUntilChanged().collect { engine.requestFrameRate(host, it) }
    }
    AndroidView(factory = { host }, modifier = modifier)
}
