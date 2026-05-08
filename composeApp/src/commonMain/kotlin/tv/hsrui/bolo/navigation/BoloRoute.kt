package tv.hsrui.bolo.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface BoloRoute {
    @Serializable
    data object Main : BoloRoute
    @Serializable
    data object Login : BoloRoute

}