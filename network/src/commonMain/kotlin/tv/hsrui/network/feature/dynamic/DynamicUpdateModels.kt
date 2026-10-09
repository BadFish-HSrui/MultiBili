package tv.hsrui.network.feature.dynamic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DynamicUpdateResponse(
    private val code: Int = -1,
    val message: String = "",
    private val data: DynamicUpdateData? = null,
) {
    val isSuccess: Boolean get() = code == 0 && data?.updateCount != null
    val isLoginExpired: Boolean get() = code == -101
    val hasUpdates: Boolean get() = (data?.updateCount ?: 0) > 0
}

@Serializable
data class DynamicUpdateData(
    @SerialName("update_num") val updateCount: Int? = null,
)
