package tv.hsrui.bolo.player

import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
internal actual fun PlaybackReportLifecycleEffect(onForegroundChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val latestCallback by rememberUpdatedState(onForegroundChanged)
    DisposableEffect(context) {
        // 使用宿主 Activity，避免把 Navigation3 条目的生命周期变化当成退后台。
        val owner = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<LifecycleOwner>().firstOrNull()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> latestCallback(false)
                Lifecycle.Event.ON_RESUME -> latestCallback(true)
                else -> Unit
            }
        }
        owner?.lifecycle?.let {
            latestCallback(it.currentState.isAtLeast(Lifecycle.State.RESUMED))
            it.addObserver(observer)
        }
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
}

internal actual suspend fun withPlaybackReportBackgroundExecution(block: suspend () -> Unit) = block()
