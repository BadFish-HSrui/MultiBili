package tv.hsrui.bolo.ui.common.player.danmaku

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.koinInject
import tv.hsrui.bolo.player.settings.BoloPlayerSettings
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.login.storage.LoginStorage

@Composable
fun PlayerDanmakuPage(
    viewModelStoreOwner: ViewModelStoreOwner,
    modifier: Modifier = Modifier,
) {
    val settings: BoloPlayerSettings = koinInject()
    val loginStorage: LoginStorage = koinInject()
    val snackbarManager: SnackbarManager = koinInject()
    val viewModel = viewModel(viewModelStoreOwner = viewModelStoreOwner) {
        DanmakuFilterViewModel(
            readFilters = { settings.danmaku.filterList },
            writeFilters = { settings.danmaku.filterList = it },
            isLoggedIn = { loginStorage.isLoggedIn },
            showMessage = { snackbarManager.showMessage(it) },
        )
    }
    DanmakuFilterPage(viewModel = viewModel, modifier = modifier)
}
