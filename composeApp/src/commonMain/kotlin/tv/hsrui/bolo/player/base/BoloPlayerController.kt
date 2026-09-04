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
 * controller.load(video = videoDashObject, audio = audioDashObject, startPositionMs = 0L)
 *
 * BoloVideoPlayer(controller = controller, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
 *
 * val state by controller.state.collectAsState()
 * ```
 *
 * ## 平台底层实现
 * | 平台    | 底层技术                              |
 * |---------|---------------------------------------|
 * | Android | VLC (LibVLC Android 3.7.5)            |
 * | iOS     | VLC (MobileVLCKit ~>3.7, CocoaPods)   |
 * | Desktop | VLC (vlcj 4.12.1)                     |
 */
expect class BoloPlayerController(
    autoPlay: Boolean = true,
    onError: (BoloPlayerError) -> Unit = {}
) {
    /** 当前播放器状态（StateFlow，可在 Composable 中 collectAsState） */
    val state: StateFlow<BoloPlayerState>

    /** 加载已在 commonMain 合成好的 DASH MPD。业务层应调用同包扩展函数 load(video, audio, startPositionMs)。 */
    internal fun load(mpd: BoloDashMpd, startPositionMs: Long = 0L)

    /** commonMain 扩展函数在 MPD 合成失败时通过平台控制器上报错误。 */
    internal fun reportLoadError(error: BoloPlayerError)

    /** 仅供被 git 忽略的 PlaybackSeekDebugContent 注入下一次 seek 的失败分支。 */
    internal fun injectSeekFailureForDebug(
        nativeSubmissionFailure: Boolean,
        timeout: Boolean,
        notSeekable: Boolean
    )

    /** 开始/恢复播放 */
    fun play()

    /** 暂停 */
    fun pause()

    /**
     * 跳转到指定位置
     * @param positionMs 目标位置（毫秒）
     */
    fun seekToMs(positionMs: Long)

    /**
     * 调整音量增益。
     * @param gain 增益值，0 = 静音，100 = 原始音量，200 = 200% 增益。
     *             所有平台 VLC volume 范围均为 0-200。
     */
    fun setVolumeGain(gain: Int)

    /**
     * 调整播放速度。
     * @param speed 倍速枚举，包含展示标题和 VLC 实际速率值。
     */
    fun setPlaybackSpeed(speed: BoloPlayerSpeed)

    /** 临时释放底层播放器资源，保留恢复播放所需状态。 */
    fun release()

    /**
     * 永久释放控制器持有的全部资源。
     * 该方法是幂等的，调用后不可再使用该控制器进行播放。
     */
    fun dispose()
}
