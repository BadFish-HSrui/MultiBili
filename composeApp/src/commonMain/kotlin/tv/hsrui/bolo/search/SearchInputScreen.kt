package tv.hsrui.bolo.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.storage.appData.AppDataStorage
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog

@Composable
fun SearchInputScreen(modifier: Modifier = Modifier) {
    val textFieldState = rememberTextFieldState()
    val navigator: Navigator = koinInject()
    val appDataStorage: AppDataStorage = koinInject()
    val historyItems by appDataStorage.searchHistory.items.collectAsState()
    var deleteHistoryKeyword by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    val historyScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitSearch = {
        val keyword = textFieldState.text.toString().trim()
        if (keyword.isNotEmpty()) {
            navigator.navigateTo(BoloRoute.Search.Results(keyword))
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ShowSearchInputTopBar(
                textFieldState = textFieldState,
                onSearch = { submitSearch() },
                onBack = navigator::goBack,
                focusRequester = focusRequester,
            )
        }
    ) { innerPadding ->
        ShowSearchHistory(
            items = historyItems,
            onClick = { keyword ->
                navigator.navigateTo(BoloRoute.Search.Results(keyword))
            },
            onLongClick = { keyword ->
                deleteHistoryKeyword = keyword
            },
            onClear = { showClearHistoryDialog = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(historyScrollState)
                .padding(16.dp)
        )
    }

    deleteHistoryKeyword?.let { keyword ->
        ShowConfirmDialog(
            title = { Text("删除搜索记录？") },
            text = keyword,
            onCancel = { deleteHistoryKeyword = null },
            onConfirm = {
                appDataStorage.searchHistory.remove(keyword)
                deleteHistoryKeyword = null
            }
        )
    }

    if (showClearHistoryDialog) {
        ShowConfirmDialog(
            title = { Text("清空搜索记录？") },
            text = "确认清空全部搜索记录吗？",
            onCancel = { showClearHistoryDialog = false },
            onConfirm = {
                appDataStorage.searchHistory.clear()
                showClearHistoryDialog = false
            }
        )
    }
}
