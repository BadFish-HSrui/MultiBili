package tv.hsrui.bolo.login

import com.multiplatform.webview.web.NativeWebView
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSOperationQueue
import platform.Foundation.distantPast
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWebView
import platform.darwin.NSObject
import kotlin.coroutines.resume

private var retainedDelegate: NSObject? = null

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

actual fun setupWebViewInterceptor(webView: NativeWebView) {
    val wkWebView = webView as WKWebView

    val delegate = object : NSObject(), WKNavigationDelegateProtocol {
        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationAction: WKNavigationAction,
            decisionHandler: (WKNavigationActionPolicy) -> Unit
        ) {
            val urlString = decidePolicyForNavigationAction.request.URL?.absoluteString ?: ""

            if (!urlString.startsWith("https://passport.bili") && urlString.isNotEmpty()) {
                LoginWebViewInterceptor.interceptedFlag = true
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                return
            }

            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }
    }

    retainedDelegate = delegate

    NSOperationQueue.mainQueue.addOperationWithBlock {
        wkWebView.navigationDelegate = delegate
    }
}
