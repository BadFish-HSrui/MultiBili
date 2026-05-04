package tv.hsrui.bolo.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.main.home.HomeScreen
import tv.hsrui.bolo.main.region.RegionsScreen
import tv.hsrui.bolo.utils.AppWindowSize
import tv.hsrui.bolo.utils.getNowWindowSize

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    when (getNowWindowSize()) {
        AppWindowSize.EXPANDED -> ExpandedMainScreen(modifier)
        AppWindowSize.MEDIUM -> MediumMainScreen(modifier)
        AppWindowSize.COMPACT -> CompactMainScreen(modifier)
    }
}

@Composable
private fun ExpandedMainScreen(modifier: Modifier = Modifier) {

}

@Composable
private fun MediumMainScreen(modifier: Modifier = Modifier) {

}

@Composable
private fun CompactMainScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        modifier = modifier,
        topBar = {},
        bottomBar = {
            NavigationBar(windowInsets = WindowInsets(bottom = 24)) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) {
        saveableStateHolder.SaveableStateProvider(selectedTab.name) { MainContent(selectedTab) }
    }
}

@Composable
private fun MainContent(tab: MainTab, modifier: Modifier = Modifier) {
    when (tab) {
        MainTab.HOME -> HomeScreen(modifier)
        MainTab.REGION -> RegionsScreen(modifier)
    }
}