package tv.hsrui.network.feature.history

import kotlinx.serialization.Serializable

@Serializable
data class DeleteHistoryResponse(
    val code: Int = -1,
    val message: String = "",
) {
    val isSuccess get() = (code == 0)
}
