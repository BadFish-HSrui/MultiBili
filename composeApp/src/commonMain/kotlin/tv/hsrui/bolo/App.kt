package tv.hsrui.bolo

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.KoinConfiguration
import org.koin.dsl.koinConfiguration
import tv.hsrui.bolo.accountFeature.AccountFeaturesScreen
import tv.hsrui.bolo.login.LoginScreen
import tv.hsrui.bolo.login.LoginWebView
import tv.hsrui.bolo.main.MainScreen
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.navigation.NavigatorModule
import tv.hsrui.bolo.storage.kSafe.KSafeModule
import tv.hsrui.bolo.ui.theme.AppTheme
import tv.hsrui.network.feature.account.myinfo.MyAccountInfoModule
import tv.hsrui.network.login.storage.LoginStorageModule

fun koinConfig(): KoinConfiguration {
    return koinConfiguration {
        modules(KSafeModule, LoginStorageModule, MyAccountInfoModule, NavigatorModule)
    }
}

@Composable
fun App() {
    AppTheme {
        KoinApplication(configuration = koinConfig()) {
            val navigator: Navigator = koinInject()
            val layoutDirection = LocalLayoutDirection.current
            Scaffold { innerPadding ->
                NavDisplay(
                    backStack = navigator.backStack,
                    onBack = { navigator.goBack() },
                    entryProvider = { route ->
                        when (route) {
                            is BoloRoute.Test -> NavEntry(key = route) { TestScreen() }
                            is BoloRoute.Main -> NavEntry(key = route) { MainScreen() }

                            is BoloRoute.Login.Screen -> NavEntry(key = route) { LoginScreen() }
                            is BoloRoute.Login.Webview -> NavEntry(key = route) { LoginWebView() }

                            is BoloRoute.AccountFeature.List -> NavEntry(key = route) { AccountFeaturesScreen() }
                            is BoloRoute.AccountFeature.WatchLater -> NavEntry(key = route) {}
                            is BoloRoute.AccountFeature.History -> NavEntry(key = route) {}
                            is BoloRoute.AccountFeature.Favorite -> NavEntry(key = route) {}
                        }
                    },
                    modifier = Modifier.padding(
                        top = innerPadding.calculateTopPadding(),
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection)
                    )
                )
            }
        }
    }
}
