package tv.hsrui.bolo.search

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.storage.appData.AppDataStorage
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.utils.isCompact

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchInputScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchSuggestionsViewModel = viewModel { SearchSuggestionsViewModel() },
    trendingViewModel: SearchTrendingViewModel = viewModel { SearchTrendingViewModel() },
) {
    val textFieldState = rememberTextFieldState()
    val navigator: Navigator = koinInject()
    val appDataStorage: AppDataStorage = koinInject()
    val settings: BoloSettings = koinInject()
    val searchSuggestionsEnabled = settings.general.searchSuggestionsEnabled
    val historyItems by appDataStorage.searchHistory.items.collectAsState()
    var deleteHistoryKeyword by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    val historyScrollState = rememberScrollState()
    val trendingScrollState = rememberScrollState()
    val compactScrollState = rememberScrollState()
    val compact = isCompact()
    val trendingState by trendingViewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val suggestionsState by viewModel.uiState.collectAsState()
    val suggestionRowHeight = 32.dp
    val suggestionContentPadding = 16.dp
    val suggestionDividerThickness = 1.dp
    val suggestionErrorHeight = 56.dp
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var inputCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var searchBarCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var inputBounds by remember { mutableStateOf<IntRect?>(null) }
    val searchBounds = inputBounds?.takeIf { it.width > 0 }
    val isInputReady = searchBounds != null
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isActive = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var isSubmitting by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val suggestionsExpanded = searchSuggestionsEnabled && isActive && isEditing && !isSubmitting &&
        when (val state = suggestionsState) {
            SearchSuggestionsUiState.Idle -> false
            is SearchSuggestionsUiState.Success -> state.keywords.isNotEmpty()
            is SearchSuggestionsUiState.Error -> true
        }
    val suggestionContentHeight = with(density) {
        val height = when (val state = suggestionsState) {
            SearchSuggestionsUiState.Idle -> 0
            is SearchSuggestionsUiState.Success -> {
                state.keywords.size * suggestionRowHeight.roundToPx() +
                    (state.keywords.size - 1).coerceAtLeast(0) * suggestionDividerThickness.roundToPx()
            }
            is SearchSuggestionsUiState.Error -> suggestionErrorHeight.roundToPx()
        }
        height.toDp()
    }
    var lastExpandedContentHeight by remember { mutableStateOf(0.dp) }
    SideEffect {
        if (suggestionsExpanded) lastExpandedContentHeight = suggestionContentHeight
    }
    val searchBarMaxHeight = with(density) {
        // 收起时沿用最近的内容上限，让取消请求不截断容器退出动画。
        val contentHeight = if (suggestionsExpanded) suggestionContentHeight else lastExpandedContentHeight
        (SearchInputFieldHeight.roundToPx() + DividerDefaults.Thickness.roundToPx() + contentHeight.roundToPx()).toDp()
    }

    fun updateInputBounds() {
        val root = rootCoordinates?.takeIf { it.isAttached } ?: return
        val input = inputCoordinates?.takeIf { it.isAttached } ?: return
        inputBounds = IntRect(
            offset = root.localPositionOf(input, Offset.Zero).round(),
            size = input.size,
        )
    }

    val dismissSuggestions: () -> Unit = {
        // 先结束编辑，避免清焦点提交组合文本后重新展开建议。
        isEditing = false
        viewModel.clearSuggestions()
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    val editInput: () -> Unit = {
        if (isActive && !isSubmitting) {
            isEditing = true
            focusRequester.requestFocus()
            keyboardController?.show()
            val keyword = textFieldState.text.toString().trim()
            if (keyword.isEmpty() || !settings.general.searchSuggestionsEnabled) {
                viewModel.clearSuggestions()
            } else {
                viewModel.loadSuggestions(keyword)
            }
        }
    }
    val submitSearch: (String) -> Unit = { value ->
        val keyword = value.trim()
        if (keyword.isNotEmpty() && !isSubmitting) {
            isSubmitting = true
            dismissSuggestions()
            navigator.navigateTo(BoloRoute.Search.Results(keyword))
        }
    }

    LaunchedEffect(isActive, isInputReady) {
        if (isActive) {
            isSubmitting = false
            if (isInputReady) {
                isEditing = true
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        } else {
            dismissSuggestions()
        }
    }

    LaunchedEffect(isActive, isInputReady, searchSuggestionsEnabled, viewModel) {
        if (!searchSuggestionsEnabled) viewModel.clearSuggestions()
        if (!isActive || !isInputReady) return@LaunchedEffect
        var previousText = textFieldState.text.toString()
        snapshotFlow { textFieldState.text.toString() }
            .collect { text ->
                val textChanged = text != previousText
                previousText = text
                if (isSubmitting || !isEditing) return@collect
                val keyword = text.trim()
                if (keyword.isEmpty()) {
                    viewModel.clearSuggestions()
                    if (textChanged) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                } else if (settings.general.searchSuggestionsEnabled) {
                    viewModel.loadSuggestions(keyword)
                }
            }
    }

    DisposableEffect(viewModel, focusManager, keyboardController) {
        onDispose {
            dismissSuggestions()
        }
    }

    val onPagePress by rememberUpdatedState<(Offset) -> Unit> { position ->
        if (isActive && !isSubmitting) {
            val root = rootCoordinates?.takeIf { it.isAttached }
            val searchBar = searchBarCoordinates?.takeIf { it.isAttached }
            val input = inputCoordinates?.takeIf { it.isAttached }
            if (root != null && searchBar != null) {
                if (!root.localBoundingBoxOf(searchBar, clipBounds = false).contains(position)) {
                    dismissSuggestions()
                } else if (input != null && root.localBoundingBoxOf(input, clipBounds = false).contains(position)) {
                    isEditing = true
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                rootCoordinates = it
                updateInputBounds()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // 只观察按下，不消费事件，页面按钮继续处理同一次点击。
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    onPagePress(down.position)
                }
            },
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                ShowSearchInputTopBar(
                    textFieldState = textFieldState,
                    onSearch = { submitSearch(textFieldState.text.toString()) },
                    onBack = navigator::goBack,
                    focusRequester = focusRequester,
                    inputFieldModifier = Modifier.onGloballyPositioned {
                        inputCoordinates = it
                        updateInputBounds()
                    },
                    inputField = { fieldModifier ->
                        Box(fieldModifier.fillMaxWidth().height(SearchInputFieldHeight))
                    },
                )
            }
        ) { innerPadding ->
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                        .verticalScroll(compactScrollState).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    ShowSearchTrending(
                        state = trendingState,
                        compact = true,
                        onClick = submitSearch,
                        onRetry = trendingViewModel::loadTrending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ShowSearchHistory(
                        items = historyItems,
                        onClick = submitSearch,
                        onLongClick = { deleteHistoryKeyword = it },
                        onClear = { showClearHistoryDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(Modifier.fillMaxSize().padding(innerPadding)) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .verticalScroll(trendingScrollState).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        ShowSearchTrending(
                            state = trendingState,
                            compact = false,
                            onClick = submitSearch,
                            onRetry = trendingViewModel::loadTrending,
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        )
                    }
                    VerticalDivider(Modifier.fillMaxHeight())
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .verticalScroll(historyScrollState).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        ShowSearchHistory(
                            items = historyItems,
                            onClick = submitSearch,
                            onLongClick = { deleteHistoryKeyword = it },
                            onClear = { showClearHistoryDialog = true },
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }

        searchBounds?.let { bounds ->
            // 搜索框始终位于同一页面层级，建议展开不占用历史区域的布局空间。
            DockedSearchBar(
                expanded = suggestionsExpanded,
                shape = RoundedCornerShape(SearchInputFieldHeight / 2),
                onExpandedChange = { expanded ->
                    if (expanded) editInput() else dismissSuggestions()
                },
                modifier = Modifier
                    .absoluteOffset { bounds.topLeft }
                    .width(with(density) { bounds.width.toDp() })
                    // 以内容高度约束建议区，消除少量内容时的最小高度留白。
                    .heightIn(max = searchBarMaxHeight)
                    .onGloballyPositioned { searchBarCoordinates = it },
                inputField = {
                    ShowSearchInputField(
                        textFieldState = textFieldState,
                        onSearch = { submitSearch(textFieldState.text.toString()) },
                        modifier = Modifier.focusRequester(focusRequester),
                        onExpandedChange = { expanded -> if (expanded) editInput() },
                    )
                },
            ) {
                when (val state = suggestionsState) {
                    SearchSuggestionsUiState.Idle -> Unit
                    is SearchSuggestionsUiState.Success -> {
                        LazyColumn {
                            itemsIndexed(state.keywords) { index, keyword ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = suggestionContentPadding),
                                        thickness = suggestionDividerThickness,
                                    )
                                }
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                                    ListItem(
                                        onClick = {
                                            textFieldState.setTextAndPlaceCursorAtEnd(keyword)
                                            submitSearch(keyword)
                                        },
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    textFieldState.setTextAndPlaceCursorAtEnd(keyword)
                                                    editInput()
                                                },
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.NorthWest,
                                                    contentDescription = "填入搜索框",
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = suggestionContentPadding),
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier.height(suggestionRowHeight).alpha(0.75f),
                                    ) {
                                        Text(keyword, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    is SearchSuggestionsUiState.Error -> ListItem(
                        headlineContent = { Text(state.message) },
                        trailingContent = {
                            TextButton(modifier = Modifier.height(32.dp), onClick = {
                                if (settings.general.searchSuggestionsEnabled) {
                                    viewModel.loadSuggestions(textFieldState.text.toString(), immediately = true)
                                }
                            }) { Text("重试") }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.height(suggestionErrorHeight),
                    )
                }
            }
        }
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
