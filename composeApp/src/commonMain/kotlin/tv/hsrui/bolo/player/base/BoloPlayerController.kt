package tv.hsrui.bolo.player.base

import kotlinx.coroutines.flow.StateFlow

/**
 * 播放控制器 — 通过 expect/actual 在各平台有对应底层实现。
 *
 * ## 使用方法
 * ```kotlin
 * val controller = remember {
 *     BoloPlayerController(
 *         autoPlay = true,
 *         onError = { error ->
 *             when (error) {
 *                 is BoloPlayerError.NetworkError -> println("网络错误: ${error.message}")
 *                 else -> println("未知错误: ${error.message}")
 *             }
 *         }
 *     )
 * }
 *
 * DisposableEffect(controller) { onDispose { controller.release() } }
 *
 * controller.load(videoUrl = "...", audioUrl = "...")
 *
 * BoloVideoPlayer(controller = controller, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
 *
 * val state by controller.state.collectAsState()
 * ```
 *
 * ## 平台底层实现
 * | 平台    | 底层技术                              |
 * |---------|---------------------------------------|
 * | Android | ExoPlayer (Media3) + OkHttp DataSource |
 * | iOS     | AVPlayer + AVMutableComposition        |
 * | Desktop | VLC (vlcj)                            |
 */
expect class BoloPlayerController(
    autoPlay: Boolean = true,
    onError: (BoloPlayerError) -> Unit = {}
) {
    /** 当前播放器状态（StateFlow，可在 Composable 中 collectAsState） */
    val state: StateFlow<BoloPlayerState>

    /**
     * 加载 DASH 视频源并开始播放（如 autoPlay=true）。
     * 可随时调用以切换视频。
     *
     * @param videoUrl 视频流 URL（仅视频轨道，Bilibili m4s）
     * @param audioUrl 音频流 URL（仅音频轨道，Bilibili m4s）
     */
    fun load(videoUrl: String, audioUrl: String? = null)

    /** 开始/恢复播放 */
    fun play()

    /** 暂停 */
    fun pause()

    /**
     * 跳转到指定位置
     * @param positionMs 目标位置（毫秒）
     */
    fun seekTo(positionMs: Long)

    /**
     * 调整音量增益。
     * @param gain 增益值，0 = 静音，100 = 原始音量（默认），200 = 200% 增益
     */
    fun setVolumeGain(gain: Int)

    /**
     * 释放底层播放器资源。
     * 必须在 DisposableEffect 的 onDispose 中调用，防止内存泄漏。
     */
    fun release()
}
