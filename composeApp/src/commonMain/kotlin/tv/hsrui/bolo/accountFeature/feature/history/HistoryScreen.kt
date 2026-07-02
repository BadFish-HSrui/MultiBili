package tv.hsrui.bolo.accountFeature.feature.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.isExpanded

@Composable
fun HistoryScreen(modifier: Modifier = Modifier, isEntryFromList: Boolean = true) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isEntryFromList || !isExpanded()) {
                ShowTopBarWithNavigationButton(title = { Text("历史记录") })
            }
        }
    ) { innerPadding ->
        HistoryGridContent(modifier = Modifier.padding(innerPadding.calculateWithoutBottom()))
    }
}