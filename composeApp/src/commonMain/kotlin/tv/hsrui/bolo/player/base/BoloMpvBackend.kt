package tv.hsrui.bolo.player.base

/** 普通 native 调用由串行 dispatcher 执行；GL 只在平台渲染上下文执行。 */
internal expect class BoloMpvBackend() {
    suspend fun bind(output: Any)
    suspend fun unbind()
    suspend fun awaitOutput()
    fun load(video: String, audio: String?, startSeconds: Double, generation: Long, userAgent: String, referrer: String): Int
    fun pause(paused: Boolean): Int
    fun speed(speed: Double): Int
    fun volume(volume: Double): Int
    fun seek(seconds: Double, request: Long): Int
    fun poll(): BoloMpvEvent?
    fun info(): BoloMpvInfoSnapshot?
    fun stop(): Int
    fun destroy()
    suspend fun setAudioActive(active: Boolean): Boolean
}

internal class BoloMpvEvent(
    val type: Int,
    val generation: Long,
    val request: Long,
    val error: Int,
    val value: Double,
) {
    companion object {
        const val Loaded = 1
        const val Position = 2
        const val Duration = 3
        const val Seekable = 4
        const val Paused = 5
        const val Buffering = 6
        const val Seeking = 7
        const val Eof = 8
        const val Error = 9
        const val SeekReply = 10
        const val Restart = 11
        const val Overflow = 12
    }
}
