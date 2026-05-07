package tv.hsrui.bolo.login

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.multiplatform.webview.cookie.WebViewCookieManager
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform

object LoginWebViewInterceptor {
    var interceptedFlag by mutableStateOf(false)

    fun reset() {
        interceptedFlag = false
    }
}

@Composable
fun LoginWebView(
    onLoginSuccess: (Map<String, String>) -> Unit
) {
    val webViewState = rememberWebViewState("https://passport.bilibili.com/login")
    val navigator = rememberWebViewNavigator()
    val cookieManager = remember { WebViewCookieManager() }
    val scope = rememberCoroutineScope()

    val isLoginRedirect: (String) -> Boolean = { url ->
        !url.startsWith("https://passport.bili") && url.isNotEmpty()
    }

    val onLoginDetected: suspend () -> Unit = {
        val cookies = cookieManager.getCookies("https://www.bilibili.com/")
        val cookieMap = cookies.associate { it.name to it.value }
        onLoginSuccess(cookieMap)
    }

    //测试时注释掉这一段可以不用重复登录
    LaunchedEffect(Unit) {
        if (getPlatform().type == PlatformType.Ios) {
            clearWebView()
        } else {
            cookieManager.removeAllCookies()
        }
    }

    LaunchedEffect(webViewState) {
        snapshotFlow { webViewState.loadingState }
            .filter { it is LoadingState.Finished }
            .collect {
                val currentUrl = webViewState.lastLoadedUrl ?: ""
                if (isLoginRedirect(currentUrl)) {
                    scope.launch { onLoginDetected() }
                }
            }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { LoginWebViewInterceptor.interceptedFlag }
            .filter { it }
            .collect {
                LoginWebViewInterceptor.reset()
                scope.launch { onLoginDetected() }
            }
    }

    WebView(
        state = webViewState,
        modifier = Modifier.fillMaxSize(),
        navigator = navigator,
        onCreated = { nativeWebView ->
            setupWebViewInterceptor(nativeWebView)
        }
    )
}

expect suspend fun clearWebView()

expect fun setupWebViewInterceptor(webView: NativeWebView)
