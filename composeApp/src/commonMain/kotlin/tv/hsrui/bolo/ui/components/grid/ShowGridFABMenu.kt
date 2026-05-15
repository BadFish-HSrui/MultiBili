package tv.hsrui.bolo.ui.components.grid

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingActionButtonMenuScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShowGridFABMenu(
    onBackToTop: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    otherButton: @Composable FloatingActionButtonMenuScope.() -> Unit = {}
) {
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    FloatingActionButtonMenu(
        expanded = fabMenuExpanded,
        button = {
            ToggleFloatingActionButton(
                checked = fabMenuExpanded,
                onCheckedChange = { fabMenuExpanded = it },
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "额外操作")
            }
        },
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        otherButton()
        FloatingActionButtonMenuItem(
            onClick = onBackToTop,
            text = { Text("回到顶部") },
            icon = { Icon(imageVector = Icons.Default.VerticalAlignTop, contentDescription = null) }
        )
        FloatingActionButtonMenuItem(
            onClick = onRefresh,
            text = { Text("刷新列表") },
            icon = { Icon(imageVector = Icons.Default.Refresh, contentDescription = null) }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
private fun FABMenuPreview() {
    ShowGridFABMenu({}, {})
}
