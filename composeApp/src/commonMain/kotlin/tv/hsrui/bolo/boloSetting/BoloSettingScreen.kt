package tv.hsrui.bolo.boloSetting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.setting.BoloSetting
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded

@Composable
fun BoloSettingsScreen(modifier: Modifier = Modifier) {
    Box {
        Scaffold(
            topBar = {
                ShowTopBarWithNavigationButton(
                    goBackBefore = BoloRoute.BoloSetting.List,
                    title = { Text("应用设置") }
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            val settings = BoloSetting.entries
            val navigator: Navigator = koinInject()
            val selectedSetting = settings.firstOrNull { it.route == navigator.backStack.lastOrNull() }

            val isExpanded = isExpanded()
            LaunchedEffect(Unit) {
                if (isExpanded && navigator.backStack.last() == BoloRoute.BoloSetting.List)
                    navigator.navigateTo(settings.first().route)
            }

            LazyColumn(
                modifier = Modifier.padding(innerPadding.calculateWithoutBottom())
            ) {
                items(settings) { setting ->
                    Surface(
                        onClick = { navigator.navigateTo(setting.route) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 12.dp),
                        shape = CardDefaults.shape,
                        color = if (isExpanded && selectedSetting == setting) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = setting.icon,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                            )
                            Text(
                                text = setting.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
        if (isExpanded()) VerticalDivider(Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}