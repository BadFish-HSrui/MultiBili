package tv.hsrui.bolo.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIBackgroundTaskInvalid

@Composable
internal actual fun PlaybackReportLifecycleEffect(onForegroundChanged: (Boolean) -> Unit) {
    val latestCallback by rememberUpdatedState(onForegroundChanged)
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        latestCallback(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive)
        val observers = listOf(
            center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
                latestCallback(false)
            },
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
                latestCallback(true)
            },
        )
        onDispose { observers.forEach(center::removeObserver) }
    }
}

internal actual suspend fun withPlaybackReportBackgroundExecution(block: suspend () -> Unit) {
    withContext(Dispatchers.Main.immediate) {
        coroutineScope {
            val application = UIApplication.sharedApplication
            val task = application.beginBackgroundTaskWithName("Playback report") {
                cancel()
            }
            try {
                block()
            } finally {
                if (task != UIBackgroundTaskInvalid) application.endBackgroundTask(task)
            }
        }
    }
}
