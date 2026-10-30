package tv.hsrui.network.feature.player

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class PlayerStreamByteRangeData(val start: Long, val endExclusive: Long)

data class PlayerStreamIndexData(
    val initialization: PlayerStreamByteRangeData?,
    val index: PlayerStreamByteRangeData?,
) {
    companion object {
        internal fun fromJson(value: JsonElement?): PlayerStreamIndexData? {
            val fields = value as? JsonObject ?: return null
            fun range(vararg names: String): PlayerStreamByteRangeData? = names.firstNotNullOfOrNull { name ->
                val text = (fields[name] as? JsonPrimitive)?.contentOrNull ?: return@firstNotNullOfOrNull null
                val parts = text.split('-')
                if (parts.size != 2) return@firstNotNullOfOrNull null
                val start = parts[0].toLongOrNull() ?: return@firstNotNullOfOrNull null
                val end = parts[1].toLongOrNull() ?: return@firstNotNullOfOrNull null
                if (start < 0 || end < start || end == Long.MAX_VALUE) null
                else PlayerStreamByteRangeData(start, end + 1)
            }
            val initialization = range("initialization", "Initialization")
            val index = range("index_range", "indexRange")
            return if (initialization == null && index == null) null else PlayerStreamIndexData(initialization, index)
        }
    }
}

/** 字节范围使用半开区间；总长度在服务端未声明且尚未读到 EOF 时未知。 */
data class PlayerStreamResponseData(
    val statusCode: Int,
    val start: Long,
    val endExclusive: Long?,
    val totalBytes: Long?,
    val etag: String?,
    val lastModified: String?,
)

class PlayerStreamException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
    val statusCode: Int? = null,
    val resourceChanged: Boolean = false,
) : Exception(message, cause)
