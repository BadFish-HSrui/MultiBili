package tv.hsrui.bolo.player.base

import kotlinx.coroutines.flow.StateFlow

/** 输出宿主只依赖渲染生命周期，不参与点播或直播的播放策略。 */
abstract class BoloPlayerOutputController {
    internal abstract val backend: StateFlow<BoloMpvBackend?>
    internal abstract val outputFrameRate: StateFlow<Float>
    internal abstract fun outputAttached()
    internal abstract fun outputDetached(output: Any)
    abstract fun rebuild()
    abstract fun release()
}
