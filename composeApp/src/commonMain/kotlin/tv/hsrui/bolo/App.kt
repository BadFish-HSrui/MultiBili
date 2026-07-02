package tv.hsrui.bolo

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.KoinConfiguration
import org.koin.dsl.koinConfiguration
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.navigation.NavigatorModule
import tv.hsrui.bolo.navigation.navigationEntry
import tv.hsrui.bolo.storage.kSafe.KSafeModule
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.common.snackbar.SnackbarModule
import tv.hsrui.bolo.ui.theme.AppTheme
import tv.hsrui.network.feature.account.myinfo.MyAccountInfoModule
import tv.hsrui.network.login.storage.LoginStorageModule
import tv.hsrui.network.wbi.WbiManagerModule
import kotlin.time.Duration.Companion.milliseconds

fun koinConfig(): KoinConfiguration {
    return koinConfiguration {
        modules(
            KSafeModule,
            LoginStorageModule,
            MyAccountInfoModule,
            NavigatorModule,
            SnackbarModule,
            WbiManagerModule
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun App() {
    AppTheme {
        KoinApplication(configuration = koinConfig()) {
            val navigator: Navigator = koinInject()
            val snackbarManager: SnackbarManager = koinInject()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                snackbarManager.messages.collect { (message, duration) ->
                    withTimeoutOrNull(duration.milliseconds) {
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Indefinite
                        )
                    }
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) {
                val rawDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
                val isIPhone = getPlatform().deviceCode.contains("iPhone")
                val listDetailStrategy = rememberListDetailSceneStrategy<BoloRoute>(
                    directive = rawDirective.copy(
                        maxHorizontalPartitions = if (isIPhone) 1 else rawDirective.maxHorizontalPartitions,
                        horizontalPartitionSpacerSize = 0.dp
                    )
                )
                NavDisplay(
                    backStack = navigator.backStack,
                    onBack = { navigator.goBack() },
                    sceneStrategies = listOf(listDetailStrategy),
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = { navigationEntry(it) }
                )
            }
        }
    }
}
