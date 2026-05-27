package tv.hsrui.network.feature.watchLater

import kotlinx.serialization.Serializable

@Serializable
data class ModifyWatchLaterResponse(
    val code: Int = -1,
    val message: String = "-1"
){
    val isSuccess get() = (code == 0)
}