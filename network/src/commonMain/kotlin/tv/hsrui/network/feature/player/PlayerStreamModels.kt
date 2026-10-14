package tv.hsrui.network.feature.player

/** 字节范围使用半开区间；总长度在服务端未声明且尚未读到 EOF 时未知。 */
data class PlayerStreamResponseData(
    val statusCode: Int,
    val start: Long,
    val endExclusive: Long?,
    val totalBytes: Long?,
    val etag: String?,
    val lastModified: String?,
)

class PlayerStreamException(message: String, val retryable: Boolean, cause: Throwable? = null) :
    Exception(message, cause)
