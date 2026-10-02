package tv.hsrui.bolo.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.feature.download.downloadVideoStream
import tv.hsrui.network.feature.player.VideoSource
import tv.hsrui.network.feature.player.fetchVideoPlayInfo
import tv.hsrui.network.login.storage.LoginStorage
import kotlin.time.Clock
import kotlin.uuid.Uuid

class DownloadManager private constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val downloads = Semaphore(3)
    private val merges = Semaphore(1)
    private val jobs = mutableMapOf<String, Job>()
    private val files = DownloadFiles()
    private val storage by lazy { DownloadStorage(getKoin().get(named("appData"))) }
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks = _tasks.asStateFlow()
    private val _initializationError = MutableStateFlow<String?>(null)
    val initializationError = _initializationError.asStateFlow()

    private val initialization = scope.async(start = CoroutineStart.LAZY) {
        try {
            val previous = storage.load()
            val restored = previous.map {
                if (it.status.isTerminal) it else it.copy(status = DownloadStatus.Failed,
                    error = "应用退出，下载已中断，请重试", updatedAt = now())
            }
            files.clearTemporaryFiles()
            if (restored != previous) storage.save(restored)
            _tasks.value = restored.sortedByDescending { it.createdAt }
        } catch (error: Exception) {
            _initializationError.value = safeError(error)
            throw error
        }
    }

    suspend fun initialize() { initialization.await() }

    suspend fun enqueue(request: DownloadRequest, source: VideoSource): Boolean {
        initialize()
        check(getKoin().get<LoginStorage>().isLoggedIn) { "请先登录" }
        require(request.avid > 0 && request.cid > 0) { "视频标识无效" }
        validateSource(request, source)
        if (mutex.withLock { _tasks.value.any { it.request.key == request.key } }) {
            notify("已存在相同下载任务")
            return false
        }
        check(files.requestPermission()) { "未获得下载目录写入权限" }
        // 持久化及任务启动属于管理器；弹窗在此后被销毁也不能丢失已接受任务。
        return withContext(Dispatchers.Default + NonCancellable) {
            mutex.withLock {
                check(getKoin().get<LoginStorage>().isLoggedIn) { "请先登录" }
                if (_tasks.value.any { it.request.key == request.key }) {
                    notify("已存在相同下载任务")
                    return@withLock false
                }
                val queued = _tasks.value.count { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading } >= 3
                val task = DownloadTask(Uuid.random().toString(), request, now(), now())
                commit(listOf(task) + _tasks.value)
                launchTask(task.id, source)
                if (queued) notify("已加入下载队列")
                true
            }
        }
    }

    fun retry(id: String) = perform {
        initialize()
        check(getKoin().get<LoginStorage>().isLoggedIn) { "请先登录" }
        check(files.requestPermission()) { "未获得下载目录写入权限" }
        mutex.withLock {
            val task = _tasks.value.firstOrNull { it.id == id } ?: return@withLock
            if ((task.status != DownloadStatus.Failed && task.status != DownloadStatus.Canceled) || id in jobs) return@withLock
            val queued = _tasks.value.count { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading } >= 3
            commit(_tasks.value.map { if (it.id == id) task.copy(status = DownloadStatus.Queued,
                downloadedBytes = 0, totalBytes = null, mergeProgress = 0f, error = null, output = null, updatedAt = now()) else it })
            launchTask(id, null)
            if (queued) notify("已加入下载队列")
        }
    }

    fun cancel(id: String) = perform {
        initialize()
        val job = mutex.withLock { jobs[id]?.also { it.cancel() } }
        job?.join()
        mutex.withLock {
            if (jobs[id] != null && jobs[id] !== job) return@withLock
            if (jobs[id] === job) jobs.remove(id)
            val task = _tasks.value.firstOrNull { it.id == id } ?: return@withLock
            if (!task.status.isTerminal || (job == null && task.status == DownloadStatus.Failed)) {
                commit(_tasks.value.map { if (it.id == id) it.copy(status = DownloadStatus.Canceled, error = null, updatedAt = now()) else it })
            }
        }
    }

    fun remove(id: String) = perform {
        initialize()
        val job = mutex.withLock {
            if (_tasks.value.firstOrNull { it.id == id }?.status?.isTerminal != true) return@perform
            jobs[id]
        }
        job?.join()
        mutex.withLock {
            val task = _tasks.value.firstOrNull { it.id == id } ?: return@withLock
            if (task.status.isTerminal && id !in jobs) commit(_tasks.value.filterNot { it.id == id })
        }
    }

    private fun launchTask(id: String, source: VideoSource?) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var failure: String? = null
            var canceled = false
            try {
                downloads.withPermit {
                    executeDownload(id, source)
                    change(id) { it.copy(status = DownloadStatus.WaitingForMerge) }
                }
                merges.withPermit {
                    change(id) { it.copy(status = DownloadStatus.Merging) }
                    val task = task(id)
                    DownloadMuxer().merge(files.path(id, "video.m4s"),
                        task.request.spec.audioQuality?.let { files.path(id, "audio.m4s") }, files.path(id, "output.mp4")) { progress ->
                        change(id, persist = false) { if (it.status == DownloadStatus.Merging) it.copy(mergeProgress = progress) else it }
                    }
                    change(id) { it.copy(status = DownloadStatus.Saving) }
                    files.publish(id, task.request.fileName) { output ->
                        change(id) { it.copy(status = DownloadStatus.Completed, output = output, mergeProgress = 1f) }
                    }
                }
            } catch (_: CancellationException) {
                canceled = true
            } catch (error: Throwable) {
                failure = safeError(error)
            } finally {
                withContext(NonCancellable) {
                    try { files.clean(id) } catch (error: Exception) {
                        failure = listOfNotNull(failure, safeError(error)).joinToString("；")
                    }
                    mutex.withLock {
                        jobs.remove(id)
                        val current = _tasks.value.firstOrNull { it.id == id }
                        if (current != null && current.status != DownloadStatus.Completed) {
                            val terminal = current.copy(status = if (canceled && failure == null) DownloadStatus.Canceled else DownloadStatus.Failed,
                                error = failure, updatedAt = now())
                            try { commit(_tasks.value.map { if (it.id == id) terminal else it }) }
                            catch (error: Exception) {
                                _tasks.value = _tasks.value.map { if (it.id == id) terminal.copy(status = DownloadStatus.Failed,
                                    error = "任务记录保存失败：${safeError(error)}") else it }
                                notify("下载任务记录保存失败")
                            }
                        }
                    }
                }
            }
        }
        jobs[id] = job
        job.start()
    }

    private suspend fun executeDownload(id: String, initialSource: VideoSource?) {
        val request = task(id).request
        val source = initialSource ?: try {
            check(getKoin().get<LoginStorage>().isLoggedIn) { "请先登录" }
            withTimeout(15_000) { fetchVideoPlayInfo(request.avid, request.cid) }
        } catch (_: TimeoutCancellationException) { error("播放信息获取超时，请重试") }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { error("播放信息获取失败，请重试") }
        validateSource(request, source)
        files.clean(id)
        files.prepare(id)
        change(id) { it.copy(status = DownloadStatus.Downloading) }
        notify("开始下载：${request.title}")
        val video = checkNotNull(source.getExactVideo(request.spec.videoQuality, request.spec.videoCodec))
        val audio = request.spec.audioQuality?.let { checkNotNull(source.getExactAudio(it)) }
        val streams = listOfNotNull(video, audio)
        val receivedBytes = LongArray(streams.size)
        val totalBytes = arrayOfNulls<Long>(streams.size)
        val progressMutex = Mutex()
        // 两条轨道共享一个任务名额；任一失败或取消时，等待另一条停止后统一清理。
        coroutineScope {
            streams.forEachIndexed { index, stream ->
                launch {
                    files.writeStream(id, if (index == 0) "video.m4s" else "audio.m4s") { write ->
                        downloadVideoStream(stream.getUrls(sortCDN = true).first(), write) { count, total ->
                            progressMutex.withLock {
                                receivedBytes[index] = count
                                totalBytes[index] = total
                                val downloaded = receivedBytes.sum()
                                val size = if (totalBytes.all { it != null }) totalBytes.sumOf { checkNotNull(it) } else null
                                change(id, persist = false) { it.copy(downloadedBytes = downloaded, totalBytes = size) }
                            }
                        }
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
    }

    private fun validateSource(request: DownloadRequest, source: VideoSource) {
        check(source.isSuccess) { source.message }
        check(!source.isPreview) { "试看内容不支持完整下载" }
        check(source.getExactVideo(request.spec.videoQuality, request.spec.videoCodec) != null) { "所选视频规格不可用" }
        check(if (request.spec.audioQuality == null) source.audioQualities.isEmpty()
            else source.getExactAudio(request.spec.audioQuality!!) != null) { "所选音质不可用" }
    }
    private suspend fun task(id: String): DownloadTask = mutex.withLock { _tasks.value.first { it.id == id } }
    private suspend fun change(id: String, persist: Boolean = true, transform: (DownloadTask) -> DownloadTask) = mutex.withLock {
        val changed = _tasks.value.map { if (it.id == id) transform(it).let { value -> if (persist) value.copy(updatedAt = now()) else value } else it }
        if (persist) commit(changed) else _tasks.value = changed
    }
    private suspend fun commit(value: List<DownloadTask>) {
        storage.save(value)
        _tasks.value = value
    }
    private fun perform(block: suspend () -> Unit) { scope.launch {
        try { block() } catch (error: CancellationException) { throw error }
        catch (error: Exception) { notify(safeError(error)) }
    } }
    private fun notify(message: String) { runCatching { getKoin().get<SnackbarManager>().showMessage(message) } }
    private fun safeError(error: Throwable): String = error.message?.replace(Regex("https?://\\S+"), "[网络地址]")
        ?.take(300)?.takeIf(String::isNotBlank) ?: "下载失败，请重试"
    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    companion object {
        val instance: DownloadManager by lazy { DownloadManager() }
    }
}
