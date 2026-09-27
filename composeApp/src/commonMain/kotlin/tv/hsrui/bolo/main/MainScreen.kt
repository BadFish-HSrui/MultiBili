package tv.hsrui.bolo.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import multibili.composeapp.generated.resources.AppIconSquare
import multibili.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import tv.hsrui.bolo.main.home.HomeScreen
import tv.hsrui.bolo.main.media.MediaScreen
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.main.region.RegionsScreen
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.search.ShowSearchButton
import tv.hsrui.bolo.search.ShowSearchPlaceholder
import tv.hsrui.bolo.utils.isCompact
import tv.hsrui.network.feature.account.myinfo.MyAccountInfoManager
import tv.hsrui.network.login.storage.LoginStorage

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val eventTab = selectedTab
    val reselectEvents = remember(eventTab) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val onClick: (MainTab) -> Unit = { tab ->
        if (selectedTab != tab) {
            selectedTab = tab
        } else if (eventTab == tab) {
            reselectEvents.tryEmit(Unit)
        }
    }
    val saveableStateHolder = rememberSaveableStateHolder()
    val isVerticalLayout = isCompact()
    val navigator: Navigator = koinInject()
    val onSearchClick = { navigator.navigateTo(BoloRoute.Search.Input) }

    saveableStateHolder.SaveableStateProvider(key = selectedTab) {
        Scaffold(
            modifier = modifier,
            topBar = {
                if (isVerticalLayout) {
                    ShowSearchPlaceholder(
                        onClick = onSearchClick,
                        navigationIcon = { LoginOrAvatarImage(Modifier.size(42.dp)) }
                    )
                }
            },
            bottomBar = {
                if (isVerticalLayout) {
                    NavigationBar(windowInsets = WindowInsets(bottom = 24)) {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { onClick(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = { Text(tab.title) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding.calculateWithoutBottom())
            ) {
                if (!isVerticalLayout) {
                    Box(modifier = Modifier.fillMaxHeight()) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            windowInsets = WindowInsets()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                MainTab.entries.forEach { tab ->
                                    NavigationRailItem(
                                        selected = selectedTab == tab,
                                        onClick = { onClick(tab) },
                                        icon = {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.title
                                            )
                                        },
                                        label = { Text(tab.title) }
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LoginOrAvatarImage(modifier = Modifier.size(48.dp))
                            Spacer(Modifier.size(8.dp))
                            ShowSearchButton(
                                onClick = onSearchClick,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
                MainContent(selectedTab, Modifier.weight(1F).fillMaxHeight(), reselectEvents)
            }
        }
    }
}

@Composable
private fun MainContent(
    tab: MainTab,
    modifier: Modifier = Modifier,
    reselectEvents: Flow<Unit>? = null,
) {
    when (tab) {
        MainTab.HOME -> HomeScreen(modifier, reselectEvents)
        MainTab.REGION -> RegionsScreen(modifier, reselectEvents)
        MainTab.MEDIA -> MediaScreen(modifier, reselectEvents)
    }
}

@Composable
fun LoginOrAvatarImage(modifier: Modifier = Modifier) {
    val onClick: () -> Unit
    val loginStorage: LoginStorage = koinInject()
    val navigator: Navigator = koinInject()
    val isLoggedIn by loginStorage.isLoggedInFlow.collectAsState(initial = loginStorage.isLoggedIn)

    onClick = if (isLoggedIn) {
        { navigator.navigateTo(BoloRoute.AccountFeature.List) }
    } else {
        { navigator.navigateTo(BoloRoute.Login.Screen) }
    }

    IconButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier
    ) {
        if (isLoggedIn) {
            val myAccountInfoManager: MyAccountInfoManager = koinInject()
            val myAccountInfo by myAccountInfoManager.info.collectAsState()

            LaunchedEffect(Unit) { myAccountInfoManager.loadInfo() }

            AsyncImage(
                model = myAccountInfo.face,
                contentDescription = "个人主页",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
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
