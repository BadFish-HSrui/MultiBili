package tv.hsrui.bolo.accountFeature.feature.favorite

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
            modifier = Modifier.padding(innerPadding.calculateWithoutBottom())
        )
    }
}
