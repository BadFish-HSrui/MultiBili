package tv.hsrui.bolo.player.session

import java.awt.EventQueue
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.TypeRef
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.annotations.DBusProperty.Access
import org.freedesktop.dbus.annotations.PropertiesEmitsChangedSignal.EmitChangeSignal
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant
import tv.hsrui.bolo.player.DesktopPlayerFullscreenWindow

@DBusInterfaceName("org.mpris.MediaPlayer2")
@DBusProperty(name = "CanQuit", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanRaise", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "HasTrackList", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "Identity", type = String::class, access = Access.READ)
@DBusProperty(name = "SupportedUriSchemes", type = Array<String>::class, access = Access.READ)
@DBusProperty(name = "SupportedMimeTypes", type = Array<String>::class, access = Access.READ)
internal interface BoloMprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
@DBusProperty(name = "PlaybackStatus", type = String::class, access = Access.READ)
@DBusProperty(name = "Rate", type = Double::class, access = Access.READ_WRITE)
@DBusProperty(name = "Metadata", type = BoloMprisPlayer.MetadataType::class, access = Access.READ)
@DBusProperty(name = "Volume", type = Double::class, access = Access.READ_WRITE)
@DBusProperty(name = "Position", type = Long::class, access = Access.READ, emitChangeSignal = EmitChangeSignal.FALSE)
@DBusProperty(name = "MinimumRate", type = Double::class, access = Access.READ)
@DBusProperty(name = "MaximumRate", type = Double::class, access = Access.READ)
@DBusProperty(name = "CanGoNext", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanGoPrevious", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanPlay", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanPause", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanSeek", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanControl", type = Boolean::class, access = Access.READ, emitChangeSignal = EmitChangeSignal.FALSE)
internal interface BoloMprisPlayer : DBusInterface {
    class MetadataType : TypeRef<Map<String, Variant<*>>>
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)
    class Seeked(path: String, val position: Long) : DBusSignal(path, position)
}

internal class BoloMprisSession(private val session: BoloPlaybackSession) :
    BoloSystemMediaSession, BoloMprisRoot, BoloMprisPlayer, Properties {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val path = "/org/mpris/MediaPlayer2"
    private val root = "org.mpris.MediaPlayer2"
    private val player = "$root.Player"
    private val busName = "$root.Bolo.instance${ProcessHandle.current().pid()}_${System.nanoTime().toULong()}"
    @Volatile private var connection: DBusConnection? = null
    @Volatile private var snapshot = BoloSystemMediaState()
    @Volatile private var closed = false
    @Volatile private var artworkUri = ""
    @Volatile private var artworkMediaId = ""
    private var artworkFile: Path? = null
    private var lastArtwork: ByteArray? = null
    private var lastPublished = BoloSystemMediaState()

    init {
        scope.launch {
            mutex.withLock {
                if (closed) return@withLock
                var bus: DBusConnection? = null
                try {
                    bus = DBusConnectionBuilder.forSessionBus().withShared(false).build()
                    bus.exportObject(path, this@BoloMprisSession)
                    bus.requestBusName(busName)
                    connection = bus
                } catch (error: Exception) {
                    runCatching { bus?.close() }
                    println("MPRIS 不可用：${error.message}")
                }
            }
        }
    }

    override fun publish(state: BoloSystemMediaState) {
        if (closed) return
        snapshot = state
        scope.launch {
            mutex.withLock {
                if (closed || snapshot !== state) return@withLock
                try {
                    if (lastArtwork !== state.artwork) {
                        artworkFile?.let { Files.deleteIfExists(it) }
                        artworkFile = null
                        artworkUri = ""
                        state.artwork?.let { bytes ->
                            val file = Files.createTempFile("bolo-media-artwork-", ".img")
                            Files.write(file, bytes)
                            artworkFile = file
                            artworkUri = file.toUri().toString()
                        }
                        lastArtwork = state.artwork
                        artworkMediaId = state.metadata.mediaId
                    }
                    val changed = GetAll(player).toMutableMap()
                    changed.remove("Position")
                    connection?.sendMessage(Properties.PropertiesChanged(path, player, changed, emptyList()))
                    if (state.seekRevision != lastPublished.seekRevision && state.metadata.mediaId == lastPublished.metadata.mediaId)
                        connection?.sendMessage(BoloMprisPlayer.Seeked(path, microseconds(state.positionMs)))
                    lastPublished = state
                } catch (error: Exception) { println("MPRIS 更新失败：${error.message}") }
            }
        }
    }

    private fun trackId(value: BoloSystemMediaState) = DBusPath(
        "/org/mpris/MediaPlayer2/track/" + value.metadata.mediaId.map { if (it.isLetterOrDigit()) it else '_' }.joinToString(""),
    )
    private fun microseconds(value: Long) = value.coerceIn(0L, Long.MAX_VALUE / 1_000) * 1_000
    private fun command(action: BoloSystemMediaAction, position: Long = 0, value: Double = 0.0) {
        if (!closed) session.dispatch(BoloSystemMediaCommand(action, snapshot.metadata.mediaId, position, value))
    }
    override fun getObjectPath() = path
    override fun Raise() = EventQueue.invokeLater {
        DesktopPlayerFullscreenWindow.window?.let { it.isVisible = true; it.toFront(); it.requestFocus() }
    }
    override fun Quit() = Unit
    override fun Next() = command(BoloSystemMediaAction.Next)
    override fun Previous() = command(BoloSystemMediaAction.Previous)
    override fun Pause() = command(BoloSystemMediaAction.Pause)
    override fun PlayPause() = command(BoloSystemMediaAction.TogglePlayPause)
    override fun Stop() = command(BoloSystemMediaAction.Stop)
    override fun Play() = command(BoloSystemMediaAction.Play)
    override fun Seek(offset: Long) = command(BoloSystemMediaAction.SeekBy, offset / 1_000)
    override fun SetPosition(trackId: DBusPath, position: Long) {
        val state = snapshot
        if (trackId != trackId(state) || position < 0 || position > microseconds(state.durationMs)) return
        command(BoloSystemMediaAction.SeekTo, position / 1_000)
    }
    override fun OpenUri(uri: String) = Unit

    @Suppress("UNCHECKED_CAST")
    override fun <A> Get(interfaceName: String, propertyName: String): A =
        (GetAll(interfaceName)[propertyName]?.value ?: error("未知媒体属性：$propertyName")) as A

    override fun <A> Set(interfaceName: String, propertyName: String, value: A) {
        if (interfaceName != player) return
        val number = ((if (value is Variant<*>) value.value else value) as? Number)?.toDouble() ?: return
        when (propertyName) {
            "Rate" -> if (number == 0.0) Pause() else command(BoloSystemMediaAction.SetRate, value = number)
            "Volume" -> command(BoloSystemMediaAction.SetVolume, value = number.coerceAtLeast(0.0))
        }
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> {
        val state = snapshot
        return when (interfaceName) {
            root -> mapOf(
                "CanQuit" to Variant(false), "CanRaise" to Variant(true), "HasTrackList" to Variant(false),
                "Identity" to Variant("Multi Bili"), "SupportedUriSchemes" to Variant(emptyArray<String>(), "as"),
                "SupportedMimeTypes" to Variant(emptyArray<String>(), "as"),
            )
            player -> {
                val metadata = mutableMapOf<String, Variant<*>>()
                if (state.metadata.mediaId.isNotEmpty()) {
                    metadata["mpris:trackid"] = Variant(trackId(state))
                    metadata["mpris:length"] = Variant(microseconds(state.durationMs))
                    metadata["xesam:title"] = Variant(state.metadata.title)
                    metadata["xesam:artist"] = Variant(arrayOf(state.metadata.artist))
                    metadata["xesam:album"] = Variant(state.metadata.album)
                    if (artworkUri.isNotEmpty() && artworkMediaId == state.metadata.mediaId)
                        metadata["mpris:artUrl"] = Variant(artworkUri)
                }
                mapOf(
                    "PlaybackStatus" to Variant(when (state.status) {
                        BoloSystemMediaPlaybackStatus.Playing -> "Playing"
                        BoloSystemMediaPlaybackStatus.Stopped, BoloSystemMediaPlaybackStatus.Ended,
                        BoloSystemMediaPlaybackStatus.Error -> "Stopped"
                        else -> "Paused"
                    }),
                    "Rate" to Variant(state.playbackSpeed), "Volume" to Variant(state.volume),
                    "MinimumRate" to Variant(0.25), "MaximumRate" to Variant(3.0),
                    "Metadata" to Variant(metadata, "a{sv}"), "Position" to Variant(microseconds(state.positionMs)),
                    "CanGoNext" to Variant(state.canNext), "CanGoPrevious" to Variant(state.canPrevious),
                    "CanPlay" to Variant(state.canPlay), "CanPause" to Variant(state.canPause),
                    "CanSeek" to Variant(state.canSeek), "CanControl" to Variant(true),
                )
            }
            else -> emptyMap()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        scope.launch {
            mutex.withLock {
                runCatching { connection?.unExportObject(path) }
                runCatching { connection?.releaseBusName(busName) }
                runCatching { connection?.close() }
                connection = null
                artworkFile?.let { runCatching { Files.deleteIfExists(it) } }
            }
            scope.cancel()
        }
    }
}
