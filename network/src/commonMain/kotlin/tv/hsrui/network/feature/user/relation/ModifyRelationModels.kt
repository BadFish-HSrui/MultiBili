package tv.hsrui.network.feature.user.relation

import kotlinx.serialization.Serializable

@Serializable
data class ModifyRelationResponse(
    val code: Int = -1,
    val message: String = ""
) {
    val isSuccess get() = (code == 0)
}