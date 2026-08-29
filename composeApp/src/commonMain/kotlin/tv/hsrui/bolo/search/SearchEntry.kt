package tv.hsrui.bolo.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal val SearchInputFieldHeight = 48.dp

internal fun Modifier.searchInputFieldHeight(shape: Shape): Modifier =
    height(SearchInputFieldHeight)
        .clip(shape)
        .wrapContentHeight(unbounded = true)

@Composable
fun ShowSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
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
