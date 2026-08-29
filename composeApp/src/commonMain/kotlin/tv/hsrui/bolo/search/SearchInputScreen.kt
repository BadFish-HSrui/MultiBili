package tv.hsrui.bolo.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.Navigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchInputScreen(modifier: Modifier = Modifier) {
    val textFieldState = rememberTextFieldState()
    val navigator: Navigator = koinInject()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchBarContainerColor = SearchBarDefaults.colors().containerColor
    val inputFieldColors = SearchBarDefaults.inputFieldColors(
        focusedContainerColor = searchBarContainerColor,
        unfocusedContainerColor = searchBarContainerColor,
        disabledContainerColor = searchBarContainerColor
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    SearchBarDefaults.InputField(
                        state = textFieldState,
                        onSearch = {},
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
                            .fillMaxWidth()
                            .searchInputFieldHeight(SearchBarDefaults.inputFieldShape)
                            .focusRequester(focusRequester)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigator::goBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
