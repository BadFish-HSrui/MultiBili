package tv.hsrui.bolo.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import multibili.composeapp.generated.resources.AppIconSquare
import multibili.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import tv.hsrui.bolo.main.home.HomeScreen
import tv.hsrui.bolo.main.region.RegionsScreen
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.LocalNavigator
import tv.hsrui.bolo.utils.AppWindowSize
import tv.hsrui.bolo.utils.getNowWindowSize
import tv.hsrui.network.login.storage.LoginStorage

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
            windowInsets = WindowInsets(),
            modifier = Modifier.fillMaxHeight()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoginOrAvatarImage(modifier = Modifier.size(48.dp))
                Spacer(Modifier.weight(0.8F))
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
        topBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp)) {
                LoginOrAvatarImage(Modifier.size(42.dp))
            }
        },
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
    ) { innerPadding ->
        saveableStateHolder.SaveableStateProvider(selectedTab.name) {
            MainContent(
                tab = selectedTab,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun MainContent(tab: MainTab, modifier: Modifier = Modifier) {
    when (tab) {
        MainTab.HOME -> HomeScreen(modifier)
        MainTab.REGION -> RegionsScreen(modifier)
    }
}

@Composable
fun LoginOrAvatarImage(modifier: Modifier = Modifier) {
    val onClick: () -> Unit
    val loginStorage: LoginStorage = koinInject()
    val navigator = LocalNavigator.current

    onClick = if (loginStorage.isLoggedIn) {
        {}
    } else {
        { navigator.navigateTo(BoloRoute.Login.Screen) }
    }

    IconButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier
    ) {
        if (loginStorage.isLoggedIn) {
            Text("CCB")
        } else {
            Image(
                painter = painterResource(Res.drawable.AppIconSquare),
                contentDescription = "登录按钮",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}