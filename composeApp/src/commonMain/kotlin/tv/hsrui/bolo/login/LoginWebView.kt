package tv.hsrui.bolo.login

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.multiplatform.webview.cookie.WebViewCookieManager
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import kotlinx.coroutines.flow.filter

@Composable
fun LoginWebView(
    onLoginSuccess: (Map<String, String>) -> Unit
) {
    val webViewState = rememberWebViewState("https://passport.bilibili.com/login")
    val navigator = rememberWebViewNavigator()
    val cookieManager = WebViewCookieManager()

    LaunchedEffect(Unit) {
        cookieManager.removeAllCookies()
    }

    LaunchedEffect(webViewState) {
        snapshotFlow { webViewState.loadingState }
            .filter { it is LoadingState.Finished }
            .collect {
                val currentUrl = webViewState.lastLoadedUrl ?: ""
                if (currentUrl != "https://passport.bilibili.com/login") {
                    val cookies = cookieManager.getCookies("https://www.bilibili.com/")
                    val cookieMap = cookies.associate { it.name to it.value }
                    onLoginSuccess(cookieMap)
                }
            }
    }

    WebView(
        state = webViewState,
        modifier = Modifier.fillMaxSize(),
        navigator = navigator
    )
}