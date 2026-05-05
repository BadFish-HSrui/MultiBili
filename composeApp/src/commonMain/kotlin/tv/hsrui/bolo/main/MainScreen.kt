package tv.hsrui.bolo.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.main.home.HomeScreen
import tv.hsrui.bolo.main.region.RegionsScreen
import tv.hsrui.bolo.utils.AppWindowSize
import tv.hsrui.bolo.utils.getNowWindowSize

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val onClick: (MainTab) -> Unit = { selectedTab = it }

    when (getNowWindowSize()) {
        AppWindowSize.MEDIUM, AppWindowSize.EXPANDED -> MediumMainScreen(
            selectedTab,
            saveableStateHolder,
            onClick,
            modifier
        )

        AppWindowSize.COMPACT -> CompactMainScreen(
            selectedTab,
            saveableStateHolder,
            onClick,
            modifier
        )
    }
}

@Composable
private fun MediumMainScreen(
    selectedTab: MainTab,
    saveableStateHolder: SaveableStateHolder,
    onClick: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight()
        ) {
            Spacer(Modifier.weight(1F))
            MainTab.entries.forEach { tab ->
                NavigationRailItem(
                    selected = selectedTab == tab,
                    onClick = { onClick(tab) },
                    icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                    label = { Text(tab.title) }
                )
            }
            Spacer(Modifier.weight(1F))
        }

        saveableStateHolder.SaveableStateProvider(selectedTab.name) {
            MainContent(selectedTab)
        }
    }
}
@Composable
private fun CompactMainScreen(
    selectedTab: MainTab,
    saveableStateHolder: SaveableStateHolder,
    onClick: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {

    Scaffold(
        modifier = modifier,
        topBar = {},
        bottomBar = {
            NavigationBar(windowInsets = WindowInsets(bottom = 24)) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { onClick(tab) },
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