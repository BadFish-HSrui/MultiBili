package tv.hsrui.bolo.player.base

/**
 * 播放器实时状态快照（通过[BoloPlayerController.state] StateFlow 订阅）
 */
data class BoloPlayerState(
    /** 是否正在播放 */
    val isPlaying: Boolean = false,
    /** 是否正在缓冲 */
    val isBuffering: Boolean = false,
    /** 当前播放位置（毫秒） */
    val currentPositionMs: Long = 0L,
    /** 视频总时长（毫秒），0 表示尚未获取 */
    val durationMs: Long = 0L,
    /** 视频编码格式（如 AVC、HEVC、AV1） */
    val videoCodec: String = "",
    /** 音频编码格式（如 AAC、OPUS、MP3） */
    val audioCodec: String = "",
    /** 视频宽度（像素） */
    val videoWidth: Int = 0,
    /** 视频高度（像素） */
    val videoHeight: Int = 0,
    /** 视频码率（bps），容器声明值，平台不支持时为 null */
    val videoBitrate: Long? = null,
    /** 音频码率（bps），容器声明值，平台不支持时为 null */
    val audioBitrate: Long? = null,
    /** 实时传输速度（bps），平台不支持时为 null */
    val transferSpeed: Long? = null
)
