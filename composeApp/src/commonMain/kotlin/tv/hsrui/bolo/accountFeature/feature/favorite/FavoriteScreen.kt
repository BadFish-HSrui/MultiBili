package tv.hsrui.bolo.accountFeature.feature.favorite

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded

@Composable
fun FavoriteScreen(
    modifier: Modifier = Modifier,
    isEntryFromList: Boolean = true,
    viewModel: FavoriteFoldersViewModel = viewModel { FavoriteFoldersViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigator: Navigator = koinInject()
    val currentRoute = navigator.backStack.lastOrNull()

    LaunchedEffect(currentRoute) {
        if (
            currentRoute == BoloRoute.AccountFeature.Favorite &&
            uiState !is FavoriteFoldersUiState.Loading
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
            onFolderDeleted = viewModel::removeItem,
            modifier = Modifier.padding(innerPadding.calculateWithoutBottom())
        )
    }
}
