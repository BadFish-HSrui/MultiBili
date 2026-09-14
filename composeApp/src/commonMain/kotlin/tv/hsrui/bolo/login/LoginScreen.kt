package tv.hsrui.bolo.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.awaitCancellation
import org.koin.compose.koinInject
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.network.login.storage.LoginStorage

@Preview
@Composable
fun LoginScreen(modifier: Modifier = Modifier) {
    if (getPlatform().type != PlatformType.Desktop) {
        Box(modifier = modifier.fillMaxSize()) {
            LoginWebView()
        }
        return
    }

    val navigator: Navigator = koinInject()
    val loginStorage: LoginStorage = koinInject()
    val viewModel = viewModel { LoginViewModel(loginStorage) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                viewModel.startQrCodeLogin()
                awaitCancellation()
            } finally {
                viewModel.stopQrCodeLogin()
            }
        }
    }
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) navigator.goHome()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ShowTopBarWithNavigationButton(title = { Text("登录") }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            LoginQrCodeContent(
                state = uiState.qrCode,
                onRefresh = viewModel::refreshQrCode,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            )
        }
    }
}
