package tv.hsrui.bolo.login

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import org.koin.compose.koinInject
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.network.login.storage.LoginStorage

object LoginWebViewInterceptor {
    var interceptedFlag by mutableStateOf(false)

    fun reset() {
        interceptedFlag = false
    }
}

@Composable
actual fun LoginWebView() {
    val webViewState = rememberWebViewState("https://passport.bilibili.com/login")
    val webNavigator = rememberWebViewNavigator()
    val cookieManager = remember { WebViewCookieManager() }
    val scope = rememberCoroutineScope()

    val navigator: Navigator = koinInject()
    val loginStorage: LoginStorage = koinInject()

    val isLoginRedirect: (String) -> Boolean = { url ->
        !url.startsWith("https://passport.bili") && url.isNotEmpty()
    }

    val onLoginDetected: suspend () -> Unit = {
        val cookies = cookieManager.getCookies("https://www.bilibili.com/")
        val cookieMap = cookies.associate { it.name to it.value }
        loginStorage.saveCookie(cookieMap)
        navigator.goHome()
    }

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { ShowTopBarWithNavigationButton(title = { Text("登录") }) },
    ) { innerPadding ->
        WebView(
            state = webViewState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            navigator = webNavigator,
            onCreated = { nativeWebView ->
                setupWebViewInterceptor(nativeWebView)
            }
        )
    }
}

expect suspend fun clearWebView()

expect fun setupWebViewInterceptor(webView: NativeWebView)
