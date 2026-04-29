package tv.hsrui.bolo.utils

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch


@Composable
fun LazyGridState.ShowGridScrollToTopButton(
    buffer: Int = 4,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val needToShowButton by remember {
        derivedStateOf {
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            totalItems != 0 && lastVisibleItemIndex >= buffer
        }
    }

    if (needToShowButton){
        FilledIconButton(
            onClick = { scope.launch{ animateScrollToItem(0)} },
            modifier = modifier
        ) {
            Icon(imageVector = Icons.Filled.VerticalAlignTop, contentDescription = "回到顶部", modifier = Modifier.size(32.dp))
        }
    }
}