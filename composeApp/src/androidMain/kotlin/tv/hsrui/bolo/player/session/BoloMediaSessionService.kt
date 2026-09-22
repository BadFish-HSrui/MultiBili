package tv.hsrui.bolo.player.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import tv.hsrui.bolo.utils.url.AppContext

@UnstableApi
class BoloMediaSessionService : MediaSessionService() {
    private var owner: BoloPlaybackSession? = null
    private var mediaSession: MediaSession? = null
    private var player: BoloMedia3Player? = null
    private lateinit var audioManager: AudioManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private var focusOwner: Any? = null
    private var hasFocus = false
    private var transientLoss = false
    private var receiverRegistered = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                transientLoss = false
                owner?.resumeAudioAfterInterruption(true)
                if (owner?.state?.value?.playWhenReady != true) abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasFocus = false
                transientLoss = true
                owner?.interruptAudio(true)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                transientLoss = false
                owner?.interruptAudio(false)
                abandonFocus()
            }
        }
    }
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper())).build()
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) owner?.interruptAudio(false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(AudioManager::class.java)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bolo:playback")
            .apply { setReferenceCounted(false) }
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "播放", NotificationManager.IMPORTANCE_LOW),
        )
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED)
        else registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        receiverRegistered = true
        desired?.let { install(it) }
        // startForegroundService 的首个通知也带 MediaSession token，不依赖普通通知权限。
        promote()
        ready.complete(this)
        if (owner == null) stopSelf()
    }

    private fun install(session: BoloPlaybackSession) {
        if (owner === session) return
        releaseSession()
        owner = session
        player = BoloMedia3Player(session)
        val builder = MediaSession.Builder(this, checkNotNull(player))
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            builder.setSessionActivity(PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        mediaSession = builder.build().also { addSession(it) }
        publishState(session.state.value)
    }

    private fun promote() {
        val builder = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this))
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(owner?.state?.value?.metadata?.title?.ifEmpty { "Bolo" } ?: "Bolo")
            .setCategory(Notification.CATEGORY_TRANSPORT).setOnlyAlertOnce(true)
        mediaSession?.let { builder.setStyle(Notification.MediaStyle().setMediaSession(it.platformToken)) }
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, builder.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        desired?.let { install(it) } ?: stopSelf()
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        owner?.close()
        stopSelf()
    }

    private fun publishState(state: BoloSystemMediaState) {
        player?.publish(state)
        val active = state.playWhenReady && state.status in setOf(BoloSystemMediaPlaybackStatus.Playing, BoloSystemMediaPlaybackStatus.Buffering)
        if (active) wakeLock.acquire(30_000) else if (wakeLock.isHeld) wakeLock.release()
    }

    private fun audioActive(token: Any, active: Boolean): Boolean {
        if (!active) {
            if (focusOwner === token && !transientLoss) abandonFocus()
            return true
        }
        if (hasFocus) { focusOwner = token; return true }
        // Android 15+ 后台请求焦点前必须已处于 mediaPlayback 前台服务状态。
        promote()
        focusOwner = token
        val result = if (Build.VERSION.SDK_INT >= 26) audioManager.requestAudioFocus(focusRequest)
            else audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (hasFocus) transientLoss = false
        return hasFocus
    }

    private fun abandonFocus() {
        if (focusOwner != null) {
            if (Build.VERSION.SDK_INT >= 26) audioManager.abandonAudioFocusRequest(focusRequest)
            else audioManager.abandonAudioFocus(focusListener)
        }
        focusOwner = null
        hasFocus = false
        transientLoss = false
    }

    private fun releaseSession() {
        abandonFocus()
        mediaSession?.let { removeSession(it); it.release() }
        mediaSession = null
        player?.release()
        player = null
        owner = null
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
    }

    override fun onDestroy() {
        val previous = owner
        releaseSession()
        if (receiverRegistered) unregisterReceiver(noisyReceiver)
        if (instance === this) { instance = null; ready = CompletableDeferred() }
        if (desired === previous) { desired = null; previous?.close() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "bolo_playback"
        // 与 Media3 默认通知 ID 一致，初始前台通知可原位替换。
        private const val NOTIFICATION_ID = 1001
        private var instance: BoloMediaSessionService? = null
        private var desired: BoloPlaybackSession? = null
        private var ready = CompletableDeferred<BoloMediaSessionService>()

        internal fun attach(session: BoloPlaybackSession) {
            desired = session
            instance?.install(session)
            val intent = Intent(AppContext.instance, BoloMediaSessionService::class.java)
            if (Build.VERSION.SDK_INT >= 26) AppContext.instance.startForegroundService(intent)
            else AppContext.instance.startService(intent)
        }

        internal fun publish(session: BoloPlaybackSession, state: BoloSystemMediaState) {
            instance?.takeIf { it.owner === session }?.publishState(state)
        }

        internal fun detach(session: BoloPlaybackSession) {
            if (desired !== session) return
            desired = null
            instance?.let { it.releaseSession(); it.stopForeground(STOP_FOREGROUND_REMOVE); it.stopSelf() }
        }

        internal suspend fun setAudioActive(owner: Any, active: Boolean): Boolean {
            if (!active) return instance?.audioActive(owner, false) ?: true
            if (desired == null) return false
            val service = instance ?: withTimeoutOrNull(5_000) { ready.await() } ?: return false
            return service.audioActive(owner, true)
        }
    }
}
