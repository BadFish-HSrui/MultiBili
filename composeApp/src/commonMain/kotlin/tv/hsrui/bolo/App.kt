package tv.hsrui.bolo

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import tv.hsrui.bolo.login.LoginScreen
import tv.hsrui.bolo.login.LoginWebView
import tv.hsrui.bolo.main.MainScreen
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.LocalNavigator
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.theme.AppTheme

@Composable
fun App() {
    AppTheme {

        val globalNavigator = remember { Navigator() }

        val layoutDirection = LocalLayoutDirection.current

        CompositionLocalProvider(
            LocalNavigator provides globalNavigator
        ) {
            Scaffold { innerPadding ->
                NavDisplay(
                    backStack = globalNavigator.backStack,
                    onBack = { globalNavigator.goBack() },
                    entryProvider = { route ->
                        when (route) {
                            is BoloRoute.Main -> NavEntry(key = route) { MainScreen() }
                            is BoloRoute.Login -> NavEntry(key = route) { LoginScreen(Modifier.fillMaxSize()) }
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