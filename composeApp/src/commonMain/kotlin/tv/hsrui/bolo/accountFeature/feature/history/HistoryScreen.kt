package tv.hsrui.bolo.accountFeature.feature.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            ShowTopBarWithNavigationButton(title = { Text("历史记录") })
        }
    ) { innerPadding ->
        HistoryGridContent(modifier = Modifier.padding(innerPadding))
    }
}