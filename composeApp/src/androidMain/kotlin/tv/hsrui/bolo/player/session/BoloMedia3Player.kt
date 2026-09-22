package tv.hsrui.bolo.player.session

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Media3 只负责系统会话及通知，播放、seek 和切集仍由现有控制器执行。 */
@UnstableApi
internal class BoloMedia3Player(private val session: BoloPlaybackSession) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var snapshot = session.state.value
    private var released = false

    fun publish(value: BoloSystemMediaState) {
        if (released) return
        snapshot = value
        invalidateState()
    }

    override fun getState(): State {
        val value = snapshot
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_TIMELINE, Player.COMMAND_STOP, Player.COMMAND_RELEASE,
        ).apply {
            if (value.canPlay || value.canPause) add(Player.COMMAND_PLAY_PAUSE)
            if (value.canSeek) addAll(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            if (value.canPrevious) add(Player.COMMAND_SEEK_TO_PREVIOUS)
            if (value.canNext) add(Player.COMMAND_SEEK_TO_NEXT)
            if (value.canPlay) add(Player.COMMAND_SET_SPEED_AND_PITCH)
        }.build()
        val metadata = MediaMetadata.Builder().setTitle(value.metadata.title).setArtist(value.metadata.artist)
            .setAlbumTitle(value.metadata.album).setArtworkData(value.artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
        val playlist = if (value.metadata.mediaId.isEmpty()) emptyList() else listOf(
            MediaItemData.Builder(value.metadata.mediaId)
                .setMediaItem(MediaItem.Builder().setMediaId(value.metadata.mediaId).setMediaMetadata(metadata).build())
                .setDurationUs(if (value.durationMs > 0) value.durationMs * 1_000 else C.TIME_UNSET)
                .setIsSeekable(value.canSeek).build(),
        )
        return State.Builder().setAvailableCommands(commands).setPlaylist(playlist)
            .setPlayWhenReady(value.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(when {
                playlist.isEmpty() -> Player.STATE_IDLE
                value.status == BoloSystemMediaPlaybackStatus.Buffering -> Player.STATE_BUFFERING
                value.status == BoloSystemMediaPlaybackStatus.Ended -> Player.STATE_ENDED
                value.status == BoloSystemMediaPlaybackStatus.Stopped || value.status == BoloSystemMediaPlaybackStatus.Error -> Player.STATE_IDLE
                else -> Player.STATE_READY
            })
            .setContentPositionMs(value.positionMs)
            .setContentBufferedPositionMs(PositionSupplier.getConstant(value.positionMs))
            .setPlaybackParameters(PlaybackParameters(value.playbackSpeed.toFloat()))
            .build()
    }

    private fun command(action: BoloSystemMediaAction, position: Long = 0, value: Double = 0.0): ListenableFuture<*> {
        session.dispatch(BoloSystemMediaCommand(action, snapshot.metadata.mediaId, position, value))
        // dispatch 在 Main.immediate 执行，同步业务状态已更新；mpv 确认随后由 publish 回传。
        snapshot = session.state.value
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean) =
        command(if (playWhenReady) BoloSystemMediaAction.Play else BoloSystemMediaAction.Pause)

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int) = when (seekCommand) {
        // 即便单项 timeline 算出 INDEX_UNSET，SimpleBasePlayer 仍会转发原始 command。
        Player.COMMAND_SEEK_TO_PREVIOUS -> command(BoloSystemMediaAction.Previous)
        Player.COMMAND_SEEK_TO_NEXT -> command(BoloSystemMediaAction.Next)
        else -> command(BoloSystemMediaAction.SeekTo, if (positionMs == C.TIME_UNSET) 0 else positionMs)
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters) =
        command(BoloSystemMediaAction.SetRate, value = playbackParameters.speed.toDouble())

    override fun handleStop() = command(BoloSystemMediaAction.Stop)
    override fun handleRelease(): ListenableFuture<*> {
        released = true
        return Futures.immediateVoidFuture()
    }
}
