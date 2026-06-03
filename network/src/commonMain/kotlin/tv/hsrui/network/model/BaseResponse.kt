package tv.hsrui.network.model

import kotlinx.serialization.Serializable

@Serializable
data class BaseResponse(
    val code: Int = -1,
    val message: String = ""
){
    val isSuccess get() = (code == 0)
}
