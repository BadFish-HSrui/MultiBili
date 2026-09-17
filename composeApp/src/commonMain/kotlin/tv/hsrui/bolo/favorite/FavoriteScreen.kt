package tv.hsrui.bolo.favorite

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.login.storage.LoginStorage

@Composable
fun FavoriteScreen(
    modifier: Modifier = Modifier,
    isEntryFromList: Boolean = true,
    viewModel: FavoriteFoldersViewModel = viewModel { FavoriteFoldersViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigator: Navigator = koinInject()
    val loginStorage: LoginStorage = koinInject()
    val currentUserMid by loginStorage.currentUserMidFlow.collectAsState(
        initial = if (loginStorage.isLoggedIn) loginStorage.cookies.dedeUserID else 0L,
    )
    val currentRoute = navigator.backStack.lastOrNull()
    var loadedUserMid by remember(viewModel) { mutableStateOf(currentUserMid) }

    LaunchedEffect(currentRoute, currentUserMid) {
        val identityChanged = loadedUserMid != currentUserMid
        loadedUserMid = currentUserMid
        if (
            identityChanged ||
            (currentRoute == BoloRoute.Favorite.List && uiState !is FavoriteFoldersUiState.Loading)
        ) {
            viewModel.refreshFolders()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isEntryFromList || !isExpanded()) {
                ShowTopBarWithNavigationButton(title = { Text("我的收藏") })
            }
        }
    ) { innerPadding ->
        FavoriteFoldersContent(
            uiState = uiState,
            onRefresh = viewModel::refreshFolders,
            canManage = currentUserMid > 0,
            canManageNow = { loginStorage.isLoggedIn },
            modifier = Modifier.padding(innerPadding.calculateWithoutBottom())
        )
    }
}
