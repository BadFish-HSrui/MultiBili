@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package tv.hsrui.bolo.player.session

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.*
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.create
import platform.MediaPlayer.*
import platform.UIKit.*
import platform.darwin.NSObjectProtocol

internal actual fun createBoloSystemMediaSession(session: BoloPlaybackSession): BoloSystemMediaSession =
    object : BoloSystemMediaSession {
        private val center = MPRemoteCommandCenter.sharedCommandCenter()
        private val info = MPNowPlayingInfoCenter.defaultCenter()
        private val notifications = NSNotificationCenter.defaultCenter
        private val targets = mutableListOf<Pair<MPRemoteCommand, Any>>()
        private val observers = mutableListOf<NSObjectProtocol>()
        private var snapshot = BoloSystemMediaState()
        private var lastArtwork: ByteArray? = null
        private var artwork: MPMediaItemArtwork? = null
        private var closed = false

        init {
            bind(center.playCommand, BoloSystemMediaAction.Play)
            bind(center.pauseCommand, BoloSystemMediaAction.Pause)
            bind(center.togglePlayPauseCommand, BoloSystemMediaAction.TogglePlayPause)
            bind(center.previousTrackCommand, BoloSystemMediaAction.Previous)
            bind(center.nextTrackCommand, BoloSystemMediaAction.Next)
            bind(center.changePlaybackPositionCommand, BoloSystemMediaAction.SeekTo)
            bind(center.stopCommand, BoloSystemMediaAction.Stop)
            center.skipBackwardCommand.enabled = false
            center.skipForwardCommand.enabled = false
            observe(UIApplicationWillResignActiveNotification) {
                session.player.controller.backend.value?.suspendOutput()
            }
            observe(UIApplicationDidEnterBackgroundNotification) {
                session.player.controller.backend.value?.suspendOutput(background = true)
                session.setForeground(false)
            }
            observe(UIApplicationDidBecomeActiveNotification) {
                session.player.controller.backend.value?.resumeRendering()
                session.setForeground(true)
                session.player.controller.setVideoOutputActive(true)
            }
            observe(UIApplicationDidReceiveMemoryWarningNotification) { session.player.controller.releaseBackgroundResources() }
            observe(UIApplicationWillTerminateNotification) { session.close() }
            observers += notifications.addObserverForName(AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue) { note ->
                val data = note?.userInfo
                val type = (data?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.longValue
                if (type == 1L) session.interruptAudio(mayResume = true)
                else if (type == 0L) {
                    val options = (data?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)?.longValue ?: 0L
                    session.resumeAudioAfterInterruption(options and 1L != 0L)
                }
            }
            observers += notifications.addObserverForName(AVAudioSessionRouteChangeNotification, null, NSOperationQueue.mainQueue) { note ->
                // OldDeviceUnavailable: 断开耳机后不切换为扬声器继续播放。
                if ((note?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.longValue == 2L)
                    session.interruptAudio(mayResume = false)
            }
            observe(AVAudioSessionMediaServicesWereResetNotification) { session.player.controller.rebuild() }
            // 会话可能在系统面板展开或后台自动切集时创建，先同步当前门禁。
            val applicationState = UIApplication.sharedApplication.applicationState
            session.player.controller.setVideoOutputActive(applicationState == UIApplicationState.UIApplicationStateActive)
            session.setForeground(applicationState != UIApplicationState.UIApplicationStateBackground)
        }

        private fun observe(name: String?, action: () -> Unit) {
            if (name == null) return
            observers += notifications.addObserverForName(name, null, NSOperationQueue.mainQueue) { if (!closed) action() }
        }

        private fun bind(command: MPRemoteCommand, action: BoloSystemMediaAction) {
            command.enabled = false
            val token = command.addTargetWithHandler { event ->
                val value = session.state.value
                if (closed || BoloPlaybackSession.current !== session || value.metadata.mediaId.isEmpty()) {
                    MPRemoteCommandHandlerStatusNoSuchContent
                } else if (!command.enabled) {
                    MPRemoteCommandHandlerStatusCommandFailed
                } else {
                    val position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime
                    session.dispatch(BoloSystemMediaCommand(action, value.metadata.mediaId,
                        positionMs = ((position ?: 0.0) * 1000).toLong()))
                    MPRemoteCommandHandlerStatusSuccess
                }
            }
            targets += command to token
        }

        override fun publish(state: BoloSystemMediaState) {
            if (closed || BoloPlaybackSession.current !== session) return
            snapshot = state
            center.playCommand.enabled = state.canPlay
            center.pauseCommand.enabled = state.canPause
            center.togglePlayPauseCommand.enabled = state.canPlay || state.canPause
            center.previousTrackCommand.enabled = state.canPrevious
            center.nextTrackCommand.enabled = state.canNext
            center.changePlaybackPositionCommand.enabled = state.canSeek
            center.stopCommand.enabled = state.canPause
            if (lastArtwork !== state.artwork) {
                lastArtwork = state.artwork
                val image = state.artwork?.takeIf { it.isNotEmpty() }?.usePinned {
                    UIImage.imageWithData(NSData.create(bytes = it.addressOf(0), length = state.artwork.size.toULong()))
                }
                artwork = image?.let { MPMediaItemArtwork(boundsSize = it.size, requestHandler = { _ -> it }) }
            }
            if (state.metadata.mediaId.isEmpty()) { info.nowPlayingInfo = null; return }
            val values = mutableMapOf<Any?, Any?>(
                MPMediaItemPropertyTitle to state.metadata.title,
                MPMediaItemPropertyPlaybackDuration to NSNumber(double = state.durationMs / 1000.0),
                MPNowPlayingInfoPropertyElapsedPlaybackTime to NSNumber(double = state.positionMs / 1000.0),
                MPNowPlayingInfoPropertyPlaybackRate to NSNumber(double =
                    if (state.status == BoloSystemMediaPlaybackStatus.Playing) state.playbackSpeed else 0.0),
                MPNowPlayingInfoPropertyDefaultPlaybackRate to NSNumber(double = state.playbackSpeed),
                MPNowPlayingInfoPropertyExternalContentIdentifier to state.metadata.mediaId,
            )
            state.metadata.artist.takeIf { it.isNotBlank() }?.let { values[MPMediaItemPropertyArtist] = it }
            state.metadata.album.takeIf { it.isNotBlank() }?.let { values[MPMediaItemPropertyAlbumTitle] = it }
            artwork?.let { values[MPMediaItemPropertyArtwork] = it }
            info.nowPlayingInfo = values
        }

        override fun close() {
            if (closed) return
            closed = true
            observers.forEach(notifications::removeObserver)
            observers.clear()
            targets.forEach { (command, target) -> command.removeTarget(target) }
            targets.clear()
            info.nowPlayingInfo = null
            artwork = null
        }
    }
