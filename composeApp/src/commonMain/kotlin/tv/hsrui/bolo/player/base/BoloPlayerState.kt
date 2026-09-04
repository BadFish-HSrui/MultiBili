package tv.hsrui.bolo.player.base

/**
 * 播放器实时状态快照（通过[BoloPlayerController.state] StateFlow 订阅）
 */
data class BoloPlayerState(
    /** 是否正在播放 */
    val isPlaying: Boolean = false,
    /** 是否正在缓冲 */
    val isBuffering: Boolean = false,
    /** VLC 已确认的实际播放位置（毫秒） */
    val currentPositionMs: Long = 0L,
    /** 视频总时长（毫秒），0 表示尚未获取 */
    val durationMs: Long = 0L,
    /** 尚未由 VLC 时间观测确认的最新跳转目标（毫秒） */
    val pendingSeekPositionMs: Long? = null,
    /** 当前媒体是否支持跳转 */
    val isSeekable: Boolean = false,
    /** 当前播放速度 */
    val playbackSpeed: BoloPlayerSpeed = BoloPlayerSpeed.default,
    /** 视频编码格式（如 AVC、HEVC、AV1） */
    val videoCodec: String = "",
    /** 音频编码格式（如 AAC、OPUS、MP3） */
    val audioCodec: String = "",
    /** 视频宽度（像素） */
    val videoWidth: Int = 0,
    /** 视频高度（像素） */
    val videoHeight: Int = 0,
    /** 视频码率（bps），获取不到时为 0L */
    val videoBitrate: Long = 0L,
    /** 音频码率（bps），获取不到时为 0L */
    val audioBitrate: Long = 0L,
    /** 实时传输速度（bps），获取不到时为 0L */
    val transferSpeed: Long = 0L
) {
    /** 是否存在等待 VLC 确认的跳转请求 */
    val isSeeking: Boolean get() = pendingSeekPositionMs != null

    /** UI 和媒体恢复应展示/保留的位置（毫秒） */
    val displayPositionMs: Long get() = pendingSeekPositionMs ?: currentPositionMs
}
