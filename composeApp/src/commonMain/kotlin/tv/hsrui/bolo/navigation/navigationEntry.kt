package tv.hsrui.bolo.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.NavEntry
import tv.hsrui.bolo.accountFeature.AccountFeaturesScreen
import tv.hsrui.bolo.favorite.FavoriteScreen
import tv.hsrui.bolo.favorite.videos.FavoriteVideosScreen
import tv.hsrui.bolo.accountFeature.feature.history.HistoryScreen
import tv.hsrui.bolo.accountFeature.feature.history.HistorySearchScreen
import tv.hsrui.bolo.favorite.videos.FavoriteSearchScreen
import tv.hsrui.bolo.accountFeature.feature.watchLater.WatchLaterScreen
import tv.hsrui.bolo.boloSetting.BoloSettingsScreen
import tv.hsrui.bolo.boloSetting.setting.about.AboutScreen
import tv.hsrui.bolo.boloSetting.setting.playback.PlaybackSettingsScreen
import tv.hsrui.bolo.debug.DebugScreen
import tv.hsrui.bolo.login.LoginScreen
import tv.hsrui.bolo.login.LoginWebView
import tv.hsrui.bolo.userSpace.UserSpaceScreen
import tv.hsrui.bolo.userSpace.UserUploadsSearchScreen
import tv.hsrui.bolo.userSpace.collection.UserCollectionScreen
import tv.hsrui.bolo.userSpace.series.UserSeriesScreen
import tv.hsrui.bolo.main.MainScreen
import tv.hsrui.bolo.search.SearchInputScreen
import tv.hsrui.bolo.search.SearchResultsScreen
import tv.hsrui.bolo.view.video.VideoScreen
import tv.hsrui.bolo.view.video.VideoPlaybackRequest
import tv.hsrui.bolo.view.media.MediaPlaybackScreen


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun navigationEntry(route: BoloRoute): NavEntry<BoloRoute> =
    when (route) {
        is BoloRoute.Debug -> NavEntry(key = route) { DebugScreen() }
        is BoloRoute.Main -> NavEntry(key = route) { MainScreen() }

        is BoloRoute.Search -> when (route) {
            is BoloRoute.Search.Input -> NavEntry(key = route) { SearchInputScreen() }
            is BoloRoute.Search.Results -> NavEntry(key = route) {
                SearchResultsScreen(keyword = route.keyword)
            }
        }

        is BoloRoute.Login.Screen -> NavEntry(key = route) { LoginScreen() }
        is BoloRoute.Login.Webview -> NavEntry(key = route) { LoginWebView() }

        is BoloRoute.AccountFeature -> {
            when (route) {
                is BoloRoute.AccountFeature.HistorySearch -> NavEntry(key = route) { HistorySearchScreen() }
                is BoloRoute.AccountFeature.List -> NavEntry(
                    key = route,
                    contentKey = "AccountFeature.List",
                    metadata = ListDetailSceneStrategy.listPane()
                ) { AccountFeaturesScreen() }

                is BoloRoute.AccountFeature.History -> NavEntry(
                    key = route,
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { HistoryScreen(isEntryFromList = true) }

                is BoloRoute.AccountFeature.WatchLater -> NavEntry(
                    key = route,
                    metadata = ListDetailSceneStrategy.detailPane()
                ) { WatchLaterScreen(isEntryFromList = true) }
            }
        }

        is BoloRoute.User -> when (route) {
            is BoloRoute.User.Space -> NavEntry(key = route) { UserSpaceScreen(mid = route.mid) }
            is BoloRoute.User.UploadsSearch -> NavEntry(key = route) { UserUploadsSearchScreen(mid = route.mid) }
            is BoloRoute.User.Collection -> NavEntry(key = route) {
                UserCollectionScreen(mid = route.mid, seasonId = route.seasonId)
            }
            is BoloRoute.User.Series -> NavEntry(key = route) {
                UserSeriesScreen(mid = route.mid, seriesId = route.seriesId)
            }
        }
        is BoloRoute.Favorite -> when (route) {
            is BoloRoute.Favorite.Search -> NavEntry(key = route) { FavoriteSearchScreen(mediaId = route.mediaId) }
            is BoloRoute.Favorite.List -> NavEntry(
                key = route,
                metadata = ListDetailSceneStrategy.detailPane(),
            ) { FavoriteScreen(isEntryFromList = true) }

            is BoloRoute.Favorite.Folder -> NavEntry(key = route) {
                FavoriteVideosScreen(mediaId = route.mediaId)
            }
        }

        is BoloRoute.BoloSetting -> when (route) {
            is BoloRoute.BoloSetting.List -> NavEntry(
                key = route,
                contentKey = "BoloSetting.List",
                metadata = ListDetailSceneStrategy.listPane()
            ) { BoloSettingsScreen() }

            is BoloRoute.BoloSetting.Playback -> NavEntry(
                key = route,
                contentKey = "BoloSetting.Playback",
                metadata = ListDetailSceneStrategy.detailPane()
            ) { PlaybackSettingsScreen() }

            is BoloRoute.BoloSetting.About -> NavEntry(
                key = route,
                metadata = ListDetailSceneStrategy.detailPane()
            ) { AboutScreen() }
        }

        is BoloRoute.View -> when (route) {
            is BoloRoute.View.Video -> NavEntry(key = route) {
                VideoScreen(request = VideoPlaybackRequest.Single(route.vid))
            }
            is BoloRoute.View.VideoList -> NavEntry(key = route) {
                VideoScreen(request = route.request)
            }
            is BoloRoute.View.Media -> NavEntry(key = route) {
                MediaPlaybackScreen(seasonId = route.seasonId, episodeId = route.episodeId)
            }
        }
    }
