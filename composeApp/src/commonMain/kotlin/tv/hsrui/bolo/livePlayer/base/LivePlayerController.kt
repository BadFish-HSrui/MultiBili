package tv.hsrui.bolo.livePlayer.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tv.hsrui.bolo.player.base.BoloMpvBackend
import tv.hsrui.bolo.player.base.BoloMpvEvent
import tv.hsrui.bolo.player.base.BoloPlayerOutputController
import tv.hsrui.bolo.player.base.boloMpvDispatcher
import tv.hsrui.network.constant.BOLO_UA
import tv.hsrui.network.liveFeature.player.LivePlayStreamData

/** 直播只打开实时地址；不复用点播缓存、进度恢复或 seek 策略。公开操作归 Main 所有。 */
class LivePlayerController : BoloPlayerOutputController() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(LivePlayerState())
    val state: StateFlow<LivePlayerState> = mutableState.asStateFlow()
    private val mutableBackend = MutableStateFlow<BoloMpvBackend?>(null)
    internal override val backend: StateFlow<BoloMpvBackend?> = mutableBackend.asStateFlow()
    private val mutableFrameRate = MutableStateFlow(0f)
    internal override val outputFrameRate: StateFlow<Float> = mutableFrameRate.asStateFlow()
    private var stream: LivePlayStreamData? = null
    private var roomId = 0L
    private var urls = emptyList<String>()
    private var urlIndex = 0
    private var generation = 0L
    private var ready = false
    private var foreground = true
    private var videoOutputActive = true
    private var disposed = false
    private var volume = 100
    private var sourceFrameRate = 0f
    private var loadJob: Job? = null
    private var eventsJob: Job? = null
    private var intentJob: Job? = null
    private var volumeJob: Job? = null
    private var outputJob: Job? = null
    private var detachJob: Job? = null
    private val commandJobs = mutableSetOf<Job>()
    private var releaseJob: Job? = null
    var playIntentRevision: Long = 0L
        private set

    fun load(stream: LivePlayStreamData, roomId: Long, playWhenReady: Boolean) {
        if (disposed) return
        playIntentRevision++
        this.stream = stream
        this.roomId = roomId
        urls = stream.urls.filter { it.isNotBlank() }.distinct()
        urlIndex = 0
        mutableState.value = LivePlayerState(playWhenReady = playWhenReady && foreground)
        if (roomId <= 0L || urls.isEmpty()) {
            fail("直播播放地址为空")
            return
        }
        if (!foreground) {
            closeEngine(suspended = true)
            return
        }
        startLoad()
    }

    private fun startLoad() {
        if (disposed || urls.isEmpty()) return
        val expected = ++generation
        val previousLoad = loadJob
        previousLoad?.cancel()
        val previousCommands = commandJobs.toList()
        previousCommands.forEach { it.cancel() }
        intentJob = null
        ready = false
        sourceFrameRate = 0f
        mutableFrameRate.value = 0f
        mutableState.value = state.value.copy(isPlaying = false, isBuffering = true,
            isPlaybackSuspended = !foreground, isEnded = false, error = null)
        val closing = releaseJob
        loadJob = scope.launch {
            try {
                previousLoad?.join()
                previousCommands.forEach { it.join() }
                closing?.join()
                if (!isCurrent(expected)) return@launch
                var engine = mutableBackend.value
                if (engine == null) {
                    // 构造完成后必须交接或销毁，取消不能遗失 native handle。
                    engine = withContext(NonCancellable) {
                        val created = withContext(boloMpvDispatcher) { BoloMpvBackend() }
                        if (!isCurrent(expected)) {
                            withContext(boloMpvDispatcher) { created.destroy() }
                            null
                        } else {
                            mutableBackend.value = created
                            startEvents(created)
                            created
                        }
                    } ?: return@launch
                }
                val current = engine
                if (foreground && videoOutputActive) withTimeout(8_000) { current.awaitOutput() }
                if (!isCurrent(expected, current)) return@launch
                check(current.setAudioActive(state.value.playWhenReady)) { "音频会话激活失败" }
                if (!isCurrent(expected, current)) return@launch
                val address = urls[urlIndex]
                val referrer = "https://live.bilibili.com/$roomId"
                val gain = volume.toDouble()
                val visible = foreground && videoOutputActive
                val result = withContext(boloMpvDispatcher) {
                    if (!isCurrent(expected, current)) return@withContext 0
                    check(current.videoEnabled(visible) >= 0) { "视频轨配置失败" }
                    check(current.volume(gain) >= 0) { "音量配置失败" }
                    current.loadLive(address, expected, BOLO_UA, referrer)
                }
                if (!isCurrent(expected, current)) return@launch
                if (result < 0) retrySource("直播流打开失败（$result）")
                else {
                    delay(20_000)
                    if (isCurrent(expected, current) && !ready) retrySource("直播流加载超时")
                }
            } catch (error: TimeoutCancellationException) {
                if (isCurrent(expected)) fail("直播画面准备超时")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(expected)) retrySource("直播播放失败：${error.message ?: "未知错误"}")
            }
        }
    }

    private fun isCurrent(expected: Long, engine: BoloMpvBackend? = mutableBackend.value): Boolean =
        !disposed && generation == expected && mutableBackend.value === engine

    private fun startEvents(engine: BoloMpvBackend) {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            var samples = 0
            while (isActive && !disposed && mutableBackend.value === engine) {
                val events = withContext(boloMpvDispatcher) {
                    buildList {
                        repeat(128) { add(engine.poll() ?: return@buildList) }
                    }
                }
                if (mutableBackend.value !== engine || disposed) break
                for (event in events) {
                    if (event.generation == generation && mutableBackend.value === engine) handleEvent(event)
                }
                if (++samples >= 50 && ready && mutableBackend.value === engine) {
                    samples = 0
                    val snapshot = withContext(boloMpvDispatcher) { engine.info(false) }
                    if (snapshot != null && isCurrent(snapshot.generation, engine)) {
                        sourceFrameRate = snapshot.info.video.fps?.takeIf { it.isFinite() && it > 0 }
                            ?.toFloat() ?: 0f
                        updateFrameRate()
                    }
                }
                delay(20)
            }
        }
    }

    private fun handleEvent(event: BoloMpvEvent) {
        when (event.type) {
            BoloMpvEvent.Loaded -> {
                ready = true
                loadJob?.cancel()
                loadJob = null
                mutableState.value = state.value.copy(isBuffering = state.value.playWhenReady)
                applyPlayIntent()
            }
            BoloMpvEvent.Paused -> if (ready) {
                mutableState.value = state.value.copy(isPlaying = event.value == 0.0 && state.value.playWhenReady &&
                    !state.value.isBuffering && !state.value.isPlaybackSuspended)
            }
            BoloMpvEvent.Buffering -> if (ready) {
                val buffering = event.value != 0.0 && state.value.playWhenReady
                mutableState.value = state.value.copy(isBuffering = buffering,
                    isPlaying = state.value.playWhenReady && !buffering && !state.value.isPlaybackSuspended)
            }
            BoloMpvEvent.Restart -> if (ready && state.value.playWhenReady) {
                mutableState.value = state.value.copy(isBuffering = false,
                    isPlaying = !state.value.isPlaybackSuspended)
            }
            BoloMpvEvent.Eof -> if (ready && event.value != 0.0) retrySource("直播流已结束", ended = true)
            BoloMpvEvent.Error -> retrySource("直播流播放失败（${event.error}）")
            BoloMpvEvent.Overflow -> fail("播放器事件队列溢出，请重新加载")
        }
        updateFrameRate()
    }

    private fun retrySource(message: String, ended: Boolean = false) {
        if (disposed) return
        if (urlIndex + 1 < urls.size) {
            urlIndex++
            startLoad()
        } else fail(message, ended)
    }

    private fun fail(message: String, ended: Boolean = false) {
        mutableState.value = state.value.copy(playWhenReady = false, isPlaying = false,
            isBuffering = false, isEnded = ended, error = message)
        closeEngine(suspended = false)
    }

    fun pause() {
        if (disposed) return
        playIntentRevision++
        mutableState.value = state.value.copy(playWhenReady = false, isPlaying = false, isBuffering = false)
        updateFrameRate()
        applyPlayIntent()
    }

    private fun applyPlayIntent() {
        intentJob?.cancel()
        val engine = mutableBackend.value ?: return
        val expected = generation
        val intent = playIntentRevision
        intentJob = launchCommand {
            val playing = state.value.playWhenReady && foreground
            try {
                if (!isCurrent(expected, engine) || playIntentRevision != intent) return@launchCommand
                if (!playing) withContext(boloMpvDispatcher) {
                    if (isCurrent(expected, engine) && playIntentRevision == intent) engine.pause(true)
                }
                if (!isCurrent(expected, engine) || playIntentRevision != intent) return@launchCommand
                val active = engine.setAudioActive(playing)
                if (!isCurrent(expected, engine) || playIntentRevision != intent) return@launchCommand
                if (!active) {
                    fail("音频会话激活失败")
                    return@launchCommand
                }
                if (playing && ready) {
                    val result = withContext(boloMpvDispatcher) {
                        if (isCurrent(expected, engine) && playIntentRevision == intent) engine.pause(false) else 0
                    }
                    if (isCurrent(expected, engine) && result < 0) retrySource("直播播放命令失败（$result）")
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (isCurrent(expected, engine)) fail("直播播放失败：${error.message ?: "未知错误"}")
            }
        }
    }

    fun setVolume(percent: Int) {
        if (disposed) return
        volume = percent.coerceIn(0, 200)
        val engine = mutableBackend.value ?: return
        volumeJob?.cancel()
        val expected = generation
        volumeJob = launchCommand {
            withContext(boloMpvDispatcher) {
                if (isCurrent(expected, engine)) engine.volume(volume.toDouble())
            }
        }
    }

    fun setForeground(active: Boolean) {
        if (disposed || foreground == active) return
        foreground = active
        if (!active) {
            pause()
            if (!ready) closeEngine(suspended = true)
        }
        mutableState.value = state.value.copy(isPlaybackSuspended = !active || mutableBackend.value == null)
        updateFrameRate()
        applyVideoOutput()
    }

    fun setVideoOutputActive(active: Boolean) {
        if (disposed) return
        videoOutputActive = active
        updateFrameRate()
        applyVideoOutput()
    }

    private fun applyVideoOutput() {
        outputJob?.cancel()
        val engine = mutableBackend.value ?: return
        val expected = generation
        outputJob = launchCommand {
            try {
                if (foreground && videoOutputActive) withTimeout(8_000) { engine.awaitOutput() }
                withContext(boloMpvDispatcher) {
                    if (isCurrent(expected, engine)) engine.videoEnabled(foreground && videoOutputActive)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* 输出错误由加载或平台宿主报告。 */ }
        }
    }

    private fun updateFrameRate() {
        mutableFrameRate.value = sourceFrameRate.takeIf {
            state.value.playWhenReady && !state.value.isEnded && !state.value.isPlaybackSuspended &&
                foreground && videoOutputActive
        } ?: 0f
    }

    private fun launchCommand(block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { block() }
            finally { coroutineContext[Job]?.let(commandJobs::remove) }
        }
        commandJobs.add(job)
        job.start()
        return job
    }

    internal override fun outputAttached() = setVideoOutputActive(true)

    internal override fun outputDetached(output: Any) {
        val engine = mutableBackend.value ?: return
        val previous = detachJob
        detachJob = scope.launch { previous?.join(); engine.detachOutput(output) }
    }

    override fun rebuild() {
        if (disposed || stream == null) return
        closeEngine(suspended = true)
        if (foreground) startLoad()
    }

    fun releaseBackgroundResources() {
        if (!disposed && !foreground && !state.value.playWhenReady) closeEngine(suspended = true)
    }

    override fun release() {
        if (!disposed) closeEngine(suspended = true)
    }

    private fun closeEngine(suspended: Boolean) {
        generation++
        ready = false
        mutableFrameRate.value = 0f
        val loading = loadJob
        loadJob = null
        loading?.cancel()
        val events = eventsJob
        eventsJob = null
        events?.cancel()
        val commands = commandJobs.toList()
        commands.forEach { it.cancel() }
        intentJob = null
        volumeJob = null
        outputJob = null
        val detach = detachJob
        detachJob = null
        val engine = mutableBackend.value
        mutableBackend.value = null
        mutableState.value = state.value.copy(isPlaying = false, isBuffering = false, isPlaybackSuspended = suspended)
        val closing = releaseJob
        releaseJob = scope.launch {
            try {
                withContext(NonCancellable) {
                    loading?.join()
                    events?.join()
                    commands.forEach { it.join() }
                    detach?.join()
                    closing?.join()
                    if (engine != null) {
                        try {
                            withContext(boloMpvDispatcher) { engine.stop() }
                        } finally {
                            try { engine.unbind() }
                            finally {
                                try { withContext(boloMpvDispatcher) { engine.destroy() } }
                                finally { engine.setAudioActive(false) }
                            }
                        }
                    }
                }
            } finally {
                if (disposed) scope.cancel()
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        mutableState.value = state.value.copy(playWhenReady = false)
        closeEngine(suspended = false)
    }
}
