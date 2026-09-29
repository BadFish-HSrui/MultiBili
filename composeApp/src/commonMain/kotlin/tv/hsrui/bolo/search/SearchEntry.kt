package tv.hsrui.bolo.search

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal val SearchInputFieldHeight = 42.dp

internal fun Modifier.searchInputFieldHeight(shape: Shape): Modifier =
    height(SearchInputFieldHeight)
        .clip(shape)
        .wrapContentHeight(unbounded = true)

@Composable
fun ShowSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "搜索"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowSearchPlaceholder(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit
) {
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberSearchBarState()
    val onExpandedChange: (Boolean) -> Unit = { expanded ->
        if (expanded) onClick()
    }

    AppBarWithSearch(
        state = searchBarState,
        inputField = {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides SearchInputFieldHeight) {
                SearchBarDefaults.InputField(
                    state = textFieldState,
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = onExpandedChange,
                    readOnly = true,
                    placeholder = { Text("搜索") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier.offset(x = (-4).dp).size(SearchInputFieldHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .searchInputFieldHeight(SearchBarDefaults.inputFieldShape)
                )
            }
        },
        navigationIcon = navigationIcon,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowSearchInputTopBar(
    textFieldState: TextFieldState,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    inputFieldModifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit = {},
    inputField: (@Composable (Modifier) -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            val fieldModifier = Modifier.widthIn(max = 500.dp).then(inputFieldModifier)
            if (inputField == null) {
                ShowSearchInputField(
                    textFieldState = textFieldState,
                    onSearch = onSearch,
                    onExpandedChange = onExpandedChange,
                    modifier = fieldModifier.focusRequester(focusRequester),
                )
            } else {
                inputField(fieldModifier)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                    contentDescription = "返回"
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowSearchInputField(
    textFieldState: TextFieldState,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    val containerColor = SearchBarDefaults.colors().containerColor
    val inputFieldColors = SearchBarDefaults.inputFieldColors(
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
        disabledContainerColor = containerColor,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) currentOnExpandedChange(true)
        }
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides SearchInputFieldHeight) {
        SearchBarDefaults.InputField(
            state = textFieldState,
            onSearch = { onSearch() },
            // 可编辑输入框始终支持输入，焦点和建议区域由页面管理。
            expanded = true,
            onExpandedChange = {},
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text("搜索", style = MaterialTheme.typography.bodyLarge) },
            leadingIcon = {
                // 抵消官方搜索框对两侧图标施加的 4.dp 向内偏移。
                Box(
                    modifier = Modifier.offset(x = (-4).dp).size(SearchInputFieldHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(
                        onClick = textFieldState::clearText,
                        modifier = Modifier.offset(x = 4.dp).size(SearchInputFieldHeight),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "清除搜索内容", modifier = Modifier.size(24.dp))
                    }
                }
            },
            colors = inputFieldColors,
            interactionSource = interactionSource,
            modifier = modifier
                .fillMaxWidth()
                .searchInputFieldHeight(SearchBarDefaults.inputFieldShape),
        )
    }
}
