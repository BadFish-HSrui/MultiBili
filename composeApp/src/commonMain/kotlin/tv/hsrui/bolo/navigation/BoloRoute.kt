package tv.hsrui.bolo.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface BoloRoute {
    @Serializable data object Test : BoloRoute

    @Serializable data object Main : BoloRoute

    @Serializable
    sealed interface Login : BoloRoute {
        @Serializable data object Screen : Login
        @Serializable data object Webview : Login
    }

    @Serializable
    sealed interface AccountFeature : BoloRoute {
        @Serializable data object List : AccountFeature
        @Serializable data object History : AccountFeature
        @Serializable data object WatchLater : AccountFeature
        @Serializable data object Favorite : AccountFeature
    }

    @Serializable
    sealed interface BoloSetting : BoloRoute {
        @Serializable data object List : BoloSetting
        @Serializable data object About : BoloSetting
    }

    @Serializable
    sealed interface View : BoloRoute {
        @Serializable data class VideoBV(val bvid: String) : View
    }
}