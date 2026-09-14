package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlaybackReportLifecycleEffect(onForegroundChanged: (Boolean) -> Unit) = Unit

internal actual suspend fun withPlaybackReportBackgroundExecution(block: suspend () -> Unit) = block()
