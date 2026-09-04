package tv.hsrui.bolo.player.base

/**
 * 播放器错误类型，通过[BoloPlayerController]的 onError 回调传出。
 */
sealed class BoloPlayerError(
    val message: String,
    val cause: Throwable? = null
) {
    /** 网络错误（超时、DNS 失败、HTTP 4xx/5xx 等） */
    class NetworkError(message: String, cause: Throwable? = null)
        : BoloPlayerError(message, cause)

    /** 格式不支持（编解码器缺失、容器格式未知等） */
    class FormatNotSupported(message: String) : BoloPlayerError(message)

    /** 解码错误 */
    class DecoderError(message: String, cause: Throwable? = null)
        : BoloPlayerError(message, cause)

    /** 跳转失败；不会终止当前媒体播放 */
    class SeekError(message: String, cause: Throwable? = null)
        : BoloPlayerError(message, cause)

    /** 其他未知错误 */
    class UnknownError(message: String, cause: Throwable? = null)
        : BoloPlayerError(message, cause)
}
