package tv.hsrui.bolo.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    keyword: String,
    modifier: Modifier = Modifier,
    viewModel: SearchVideosViewModel = viewModel(key = "search_$keyword") {
        SearchVideosViewModel(keyword)
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigator: Navigator = koinInject()
    val textFieldState = rememberTextFieldState(initialText = keyword)
    val searchBarContainerColor = SearchBarDefaults.colors().containerColor
    val inputFieldColors = SearchBarDefaults.inputFieldColors(
        focusedContainerColor = searchBarContainerColor,
        unfocusedContainerColor = searchBarContainerColor,
        disabledContainerColor = searchBarContainerColor
    )
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    SearchBarDefaults.InputField(
                        state = textFieldState,
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = { expanded ->
                            if (expanded) navigator.goBack()
                        },
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null
                            )
                        },
                        colors = inputFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .searchInputFieldHeight(SearchBarDefaults.inputFieldShape)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigator::goBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        VideosGridPage(
            uiState = uiState,
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding),
            emptyMessage = "未找到相关视频"
        )
    }
}
