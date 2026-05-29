package tv.hsrui.bolo.utils.url

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

actual suspend fun openUrl(url: String): Boolean {
    return try {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        suspendCancellableCoroutine { continuation ->
            UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>()) { success ->
                continuation.resume(success)
            }
        }
    } catch (e: Exception) {
        false
    }
}