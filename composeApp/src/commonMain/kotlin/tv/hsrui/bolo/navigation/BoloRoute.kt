package tv.hsrui.bolo.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface BoloRoute {
    @Serializable
    data object Main : BoloRoute

    @Serializable
    sealed interface Login : BoloRoute {
        @Serializable
        data object Screen : Login
        @Serializable
        data object Webview : Login
    }
}