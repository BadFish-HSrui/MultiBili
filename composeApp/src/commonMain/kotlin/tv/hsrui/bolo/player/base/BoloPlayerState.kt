package tv.hsrui.bolo.player.base

/**
 * 播放器实时状态快照（通过[BoloPlayerController.state] StateFlow 订阅）
 */
data class BoloPlayerState(
    /** 后台暂停或恢复准备中，禁止普通播放请求启动媒体。 */
    val isPlaybackSuspended: Boolean = false,
    /** 手动重建实例并恢复当前媒体期间。 */
    val isRebuilding: Boolean = false,
    /** 是否正在播放 */
    val isPlaying: Boolean = false,
    /** 是否正在缓冲 */
    val isBuffering: Boolean = false,
    /** 当前媒体已加载，并接受过原生实际位置观测；加载目标和恢复缓存不算确认。 */
    val hasConfirmedPosition: Boolean = false,
    /** 原生播放器已确认的实际播放位置（毫秒） */
    val currentPositionMs: Long = 0L,
    /** 视频总时长（毫秒），0 表示尚未获取 */
    val durationMs: Long = 0L,
    /** 尚未由原生时间观测确认的最新跳转目标（毫秒） */
    val pendingSeekPositionMs: Long? = null,
    /** 当前媒体是否支持跳转 */
    val isSeekable: Boolean = false,
    /** 当前播放速度 */
    val playbackSpeed: Float = 1f,
    /** 原生 EOF 已确认；暂停或命令提交不能推导此状态。 */
    val isEnded: Boolean = false,
) {
    /** 是否存在等待原生播放器确认的跳转请求 */
    val isSeeking: Boolean get() = pendingSeekPositionMs != null

    /** UI 和媒体恢复应展示/保留的位置（毫秒） */
    val displayPositionMs: Long get() = pendingSeekPositionMs ?: currentPositionMs
}
