package tv.hsrui.bolo.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.storage.appData.AppDataStorage
import tv.hsrui.network.feature.search.SearchCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    keyword: String,
    modifier: Modifier = Modifier,
) {
    var categoryName by rememberSaveable(keyword) { mutableStateOf(SearchCategory.Video.name) }
    val category = SearchCategory.valueOf(categoryName)
    val categoryStates = rememberSaveableStateHolder()
    val navigator: Navigator = koinInject()
    val appDataStorage: AppDataStorage = koinInject()
    val textFieldState = rememberTextFieldState(initialText = keyword)
    val searchBarContainerColor = SearchBarDefaults.colors().containerColor
    val inputFieldColors = SearchBarDefaults.inputFieldColors(
        focusedContainerColor = searchBarContainerColor,
        unfocusedContainerColor = searchBarContainerColor,
        disabledContainerColor = searchBarContainerColor
    )
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(keyword) {
        appDataStorage.searchHistory.add(keyword)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
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
                            .widthIn(max = 500.dp)
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
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            Spacer(Modifier.height(4.dp))
            PrimaryTabRow(
                selectedTabIndex = SearchCategory.entries.indexOf(category),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                SearchCategory.entries.forEach { item ->
                    Tab(
                        selected = category == item,
                        onClick = { categoryName = item.name },
                        text = { Text(item.title, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.height(32.dp),
                    )
                }
            }
            if (category == SearchCategory.Video || category == SearchCategory.User) Spacer(Modifier.height(4.dp))
            categoryStates.SaveableStateProvider(category.name) {
                SearchResultsPage(
                    keyword = keyword,
                    category = category,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}
