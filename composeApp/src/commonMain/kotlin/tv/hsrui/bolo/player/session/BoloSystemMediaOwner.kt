package tv.hsrui.bolo.player.session

import kotlinx.coroutines.flow.StateFlow
import tv.hsrui.bolo.player.base.BoloPlayerOutputController

/** 当前播放内容的唯一所有者；导航、宿主与系统媒体命令共用同一生命周期。仅在 Main 使用。 */
abstract class BoloSystemMediaOwner {
    abstract val state: StateFlow<BoloSystemMediaState>
    abstract val isClosed: Boolean
    val isCurrent: Boolean get() = current === this && !isClosed
    internal abstract val outputController: BoloPlayerOutputController

    abstract fun activate()
    abstract fun dispatch(command: BoloSystemMediaCommand)
    abstract fun setForeground(active: Boolean)
    abstract fun interruptAudio(mayResume: Boolean)
    abstract fun resumeAudioAfterInterruption(allowed: Boolean)
    abstract fun close()
    internal abstract fun setVideoOutputActive(active: Boolean)
    internal abstract fun releaseBackgroundResources()
    internal abstract suspend fun pauseAndObserve()

    companion object {
        var current: BoloSystemMediaOwner? = null
            private set

        internal fun acquire(owner: BoloSystemMediaOwner) {
            if (owner.isClosed || current === owner) return
            current?.close()
            current = owner
        }

        internal fun release(owner: BoloSystemMediaOwner) {
            if (current === owner) current = null
        }
    }
}
