package tv.hsrui.bolo.player.base

/**
 * 播放器实时状态快照（通过[BoloPlayerController.state] StateFlow 订阅）
 */
data class BoloPlayerState(
    /** 是否正在播放 */
    val isPlaying: Boolean = false,
    /** 是否正在缓冲 */
    val isBuffering: Boolean = false,
    /** 当前播放位置（秒） */
    val currentPosition: Int = 0,
    /** 视频总时长（秒），0 表示尚未获取 */
    val duration: Int = 0,
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
)
