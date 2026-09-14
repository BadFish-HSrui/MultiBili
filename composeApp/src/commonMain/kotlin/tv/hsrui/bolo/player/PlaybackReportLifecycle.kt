package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlaybackReportLifecycleEffect(onForegroundChanged: (Boolean) -> Unit)

internal expect suspend fun withPlaybackReportBackgroundExecution(block: suspend () -> Unit)
