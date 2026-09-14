package tv.hsrui.network.feature.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaConditionsResponse(
    private val code: Int = -1,
    @SerialName("message") private val responseMessage: String = "",
    private val data: MediaConditionsData? = null,
) {
    val isSuccess: Boolean get() = code == 0 && data != null
    val conditions: MediaConditionsData? get() = data
    val message: String
        get() = if (code == 0 && data == null) "影视番剧响应缺少筛选条件" else responseMessage
}

@Serializable
data class MediaConditionsData(
    @SerialName("filter") val filters: List<MediaFilterGroup> = emptyList(),
    @SerialName("order") val sortOptions: List<MediaSortOption> = emptyList(),
)

@Serializable
data class MediaFilterGroup(
    @SerialName("field") val parameterName: String,
    @SerialName("name") val title: String,
    @SerialName("values") val options: List<MediaFilterOption> = emptyList(),
)

@Serializable
data class MediaFilterOption(
    @SerialName("keyword") val value: String,
    @SerialName("name") val title: String,
) {
    val isAll: Boolean get() = value == "-1"
}

@Serializable
data class MediaSortOption(
    @SerialName("field") val value: String,
    @SerialName("name") private val name: String,
    @SerialName("sort") private val directions: String = "",
) {
    val title: String get() = if (name == "播放数量") "播放次数" else name
    val supportsDescending: Boolean get() = "0" in directions.split(',')
    val supportsAscending: Boolean get() = "1" in directions.split(',')
}

@Serializable
data class MediaFilterSelection(
    val values: Map<String, String> = emptyMap(),
    val order: String? = null,
    val ascending: Boolean = false,
) {
    val queryParameters: Map<String, String>
        get() = buildMap {
            this@MediaFilterSelection.values.forEach { (field, value) ->
                if (field !in setOf("season_type", "type", "page", "pagesize", "order", "sort") &&
                    value.isNotBlank() && value != "-1"
                ) {
                    put(field, value)
                }
            }
            if (!order.isNullOrBlank()) {
                put("order", order)
                put("sort", if (ascending) "1" else "0")
            }
        }

    fun normalized(conditions: MediaConditionsData): MediaFilterSelection {
        val selectedOrder = conditions.sortOptions.firstOrNull { it.value == order }
        return MediaFilterSelection(
            values = conditions.filters.mapNotNull { group ->
                group.options.firstOrNull {
                    it.value == values[group.parameterName] && !it.isAll
                }?.let { group.parameterName to it.value }
            }.toMap(),
            order = selectedOrder?.value,
            ascending = selectedOrder?.supportsAscending == true &&
                (ascending || !selectedOrder.supportsDescending),
        )
    }
}
