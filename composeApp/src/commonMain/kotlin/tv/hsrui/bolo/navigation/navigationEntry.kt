package tv.hsrui.bolo.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import tv.hsrui.bolo.TestScreen
import tv.hsrui.bolo.accountFeature.AccountFeaturesScreen
import tv.hsrui.bolo.accountFeature.feature.history.HistoryScreen
import tv.hsrui.bolo.boloSetting.BoloSettingsScreen
import tv.hsrui.bolo.boloSetting.setting.about.AboutScreen
import tv.hsrui.bolo.login.LoginScreen
import tv.hsrui.bolo.login.LoginWebView
import tv.hsrui.bolo.main.MainScreen
import tv.hsrui.bolo.view.video.VideoScreen
import tv.hsrui.bolo.view.video.VideoViewModel


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun navigationEntry(route: BoloRoute): NavEntry<BoloRoute> =
    when (route) {
        is BoloRoute.Test -> NavEntry(key = route) { TestScreen() }
        is BoloRoute.Main -> NavEntry(key = route) { MainScreen() }

        is BoloRoute.Login.Screen -> NavEntry(key = route) { LoginScreen() }
        is BoloRoute.Login.Webview -> NavEntry(key = route) { LoginWebView() }

        is BoloRoute.AccountFeature -> {
            when (route) {
                is BoloRoute.AccountFeature.List -> NavEntry(
                    key = route,
                    metadata = ListDetailSceneStrategy.listPane()
                ) { AccountFeaturesScreen() }

                is BoloRoute.AccountFeature.History -> NavEntry(
                    key = route,
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { HistoryScreen(isEntryFromList = true) }

                is BoloRoute.AccountFeature.Favorite -> NavEntry(
                    key = route,
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {}

                is BoloRoute.AccountFeature.WatchLater -> NavEntry(
                    key = route,
                    metadata = ListDetailSceneStrategy.detailPane()
                ) {}
            }
        }

        is BoloRoute.BoloSetting -> when (route) {
            is BoloRoute.BoloSetting.List -> NavEntry(
                key = route,
                metadata = ListDetailSceneStrategy.listPane()
            ) { BoloSettingsScreen() }

            is BoloRoute.BoloSetting.About -> NavEntry(
                key = route,
                metadata = ListDetailSceneStrategy.detailPane()
            ) { AboutScreen() }
        }

        is BoloRoute.View -> when (route) {
            is BoloRoute.View.Video -> NavEntry(key = route) {
                VideoScreen(viewModel {
                    VideoViewModel(
                        route.avid
                    )
                })
            }
        }
    }