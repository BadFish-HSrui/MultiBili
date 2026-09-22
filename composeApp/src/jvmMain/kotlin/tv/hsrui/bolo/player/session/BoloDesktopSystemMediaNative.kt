package tv.hsrui.bolo.player.session

import java.awt.Window
import java.util.concurrent.ConcurrentHashMap
import tv.hsrui.bolo.player.DesktopPlayerFullscreenWindow
import tv.hsrui.bolo.player.base.loadBoloMpvLibrary

/** JNI 只传输快照和命令；所有播放业务返回会话主线程执行。 */
internal object BoloDesktopSystemMediaNative {
    private val sessions = ConcurrentHashMap<Long, BoloPlaybackSession>()

    fun connect(session: BoloPlaybackSession): BoloSystemMediaSession {
        loadBoloMpvLibrary()
        if (System.getProperty("os.name").startsWith("Windows")) System.loadLibrary("jawt")
        val handle = create(DesktopPlayerFullscreenWindow.window)
        check(handle != 0L) { "无法创建系统媒体会话" }
        sessions[handle] = session
        return object : BoloSystemMediaSession {
            private var closed = false
            private var lastArtwork: ByteArray? = null
            override fun publish(state: BoloSystemMediaState) {
                if (closed) return
                val commands = (if (state.canPlay) 1 else 0) or (if (state.canPause) 2 else 0) or
                    (if (state.canSeek) 4 else 0) or (if (state.canPrevious) 8 else 0) or (if (state.canNext) 16 else 0)
                val artworkChanged = lastArtwork !== state.artwork
                update(handle, state.metadata.mediaId, state.metadata.title, state.metadata.artist, state.metadata.album,
                    if (artworkChanged) state.artwork else null, artworkChanged,
                    state.positionMs, state.durationMs, state.playbackSpeed, state.status.ordinal, commands)
                lastArtwork = state.artwork
            }
            override fun close() {
                if (closed) return
                closed = true
                sessions.remove(handle)
                destroy(handle)
            }
        }
    }

    @Suppress("unused") // Native event handler; must retain this JVM signature.
    fun onCommand(handle: Long, action: Int, mediaId: String, positionMs: Long, value: Double) {
        val kind = BoloSystemMediaAction.entries.getOrNull(action) ?: return
        sessions[handle]?.dispatch(BoloSystemMediaCommand(kind, mediaId, positionMs, value))
    }

    private external fun create(window: Window?): Long
    private external fun update(handle: Long, mediaId: String, title: String, artist: String, album: String,
        artwork: ByteArray?, artworkChanged: Boolean, positionMs: Long, durationMs: Long,
        speed: Double, status: Int, commands: Int)
    private external fun destroy(handle: Long)
}
