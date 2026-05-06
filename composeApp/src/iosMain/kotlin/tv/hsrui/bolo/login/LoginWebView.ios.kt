package tv.hsrui.bolo.login

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.distantPast
import platform.WebKit.WKWebsiteDataStore
import kotlin.coroutines.resume

actual suspend fun clearWebView() {
    val dataStore = WKWebsiteDataStore.defaultDataStore()
    val dataTypes = WKWebsiteDataStore.allWebsiteDataTypes()

    suspendCancellableCoroutine { continuation ->
        dataStore.httpCookieStore.getAllCookies { cookies ->
            cookies?.forEach { cookie ->
                (cookie as? NSHTTPCookie)?.let {
                    dataStore.httpCookieStore.deleteCookie(it, completionHandler = {})
                }
            }

            dataStore.removeDataOfTypes(
                dataTypes = dataTypes,
                modifiedSince = NSDate.distantPast,
            ) {
                continuation.resume(Unit)
            }
        }
    }
}
