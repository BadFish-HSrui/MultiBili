package tv.hsrui.bolo.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
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
            SearchBarDefaults.InputField(
                state = textFieldState,
                onSearch = {},
                expanded = false,
                onExpandedChange = onExpandedChange,
                readOnly = true,
                placeholder = { Text("搜索") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .searchInputFieldHeight(SearchBarDefaults.inputFieldShape)
            )
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
) {
    val searchBarContainerColor = SearchBarDefaults.colors().containerColor
    val inputFieldColors = SearchBarDefaults.inputFieldColors(
        focusedContainerColor = searchBarContainerColor,
        unfocusedContainerColor = searchBarContainerColor,
        disabledContainerColor = searchBarContainerColor
    )
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            SearchBarDefaults.InputField(
                state = textFieldState,
                onSearch = { onSearch() },
                expanded = true,
                onExpandedChange = {},
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = {
                    Text(
                        text = "搜索",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (textFieldState.text.isNotEmpty()) {
                        IconButton(onClick = textFieldState::clearText) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "清除搜索内容"
                            )
                        }
                    }
                },
                colors = inputFieldColors,
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
                    .searchInputFieldHeight(SearchBarDefaults.inputFieldShape)
                    .focusRequester(focusRequester)
            )
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
