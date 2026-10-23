package tv.hsrui.bolo.boloSetting.setting.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded

@Composable
fun LiveSettingsScreen(modifier: Modifier = Modifier) = LookaheadScope {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isExpanded()) ShowTopBarWithNavigationButton(title = { Text("直播设置") })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding.calculateWithoutBottom()),
            contentAlignment = Alignment.TopCenter,
        ) {
        }
    }
}
