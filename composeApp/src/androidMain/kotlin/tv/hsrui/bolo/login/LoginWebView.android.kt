package tv.hsrui.bolo.login

import com.multiplatform.webview.web.NativeWebView

actual suspend fun clearWebView() = Unit

actual fun setupWebViewInterceptor(webView: NativeWebView) = Unit
