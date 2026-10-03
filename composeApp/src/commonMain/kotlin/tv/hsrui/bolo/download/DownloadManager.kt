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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.feature.download.downloadVideoStream
import tv.hsrui.network.feature.player.VideoSource
import tv.hsrui.network.feature.player.fetchMediaPlayInfo
import tv.hsrui.network.feature.player.fetchVideoPlayInfo
import tv.hsrui.network.login.storage.LoginStorage
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

class DownloadManager private constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val downloads = Semaphore(3)
    private val merges = Semaphore(1)
    private val jobs = mutableMapOf<String, Job>()
    private val removing = mutableSetOf<String>()
    private val retrying = mutableSetOf<String>()
    private val files = DownloadFiles()
    private val storage by lazy { DownloadStorage(getKoin().get(named("appData"))) }
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks = _tasks.asStateFlow()
    private val _initializationError = MutableStateFlow<String?>(null)
    val initializationError = _initializationError.asStateFlow()

    private val initialization = scope.async(start = CoroutineStart.LAZY) {
        try {
            val settings = getKoin().get<BoloSettings>().general
            if (settings.downloadDirectory.isEmpty()) {
                val directory = files.directoryDisplayName("")
                withContext(Dispatchers.Main.immediate) {
                    if (settings.downloadDirectory.isEmpty()) settings.downloadDirectory = directory
                }
            }
            val previous = storage.load()
            val restored = previous.map {
                if (it.status.isTerminal) it else it.copy(status = DownloadStatus.Failed,
                    error = "应用退出，下载已中断，请重试", updatedAt = now())
            }
            files.clearTemporaryFiles(previous.mapNotNull { it.output })
            if (restored != previous) storage.save(restored)
            _tasks.value = restored.sortedByDescending { it.createdAt }
        } catch (error: Exception) {
            _initializationError.value = safeError(error)
            throw error
        }
    }

    suspend fun initialize() { initialization.await() }

    suspend fun checkFile(task: DownloadTask): Boolean? {
        val output = task.output ?: return null
        if (task.status != DownloadStatus.Completed) return null
        // 保留任务快照身份，避免同一任务重试后用旧检查结果覆盖新成品。
        fun isCurrent(): Boolean = task.id !in removing && task.id !in retrying && _tasks.value.any { it === task }
        var failurePrefix = "无法检查下载文件"
        return try {
            initialize()
            val job = mutex.withLock {
                if (!isCurrent()) return null
                jobs[task.id]
            }
            job?.join()
            if (!mutex.withLock { isCurrent() && task.id !in jobs }) return null
            val exists = files.exists(output)
            withContext(NonCancellable) {
                mutex.withLock {
                    if (!isCurrent() || task.id in jobs) return@withLock null
                    if (!exists) {
                        failurePrefix = "下载任务记录保存失败"
                        commit(_tasks.value.map {
                            if (it === task) it.copy(status = DownloadStatus.Failed, error = "文件被移动或删除",
                                downloadedBytes = 0, totalBytes = null, mergeProgress = 0f, bytesPerSecond = 0,
                                updatedAt = now()) else it
                        })
                    }
                    exists
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (mutex.withLock { isCurrent() }) notify("$failurePrefix：${safeError(error)}")
            null
        }
    }

    suspend fun enqueue(request: DownloadRequest, source: VideoSource): Boolean {
        require(request.id > 0 && (request.type != DownloadType.Video || request.cid > 0)) { "视频标识无效" }
        resolveDownloadStreams(source, request.spec)
        val task = DownloadTask(Uuid.random().toString(), request, now(), now())
        return enqueueTasks(listOf(task), mapOf(task.id to source)) > 0
    }

    suspend fun enqueueBatch(group: DownloadGroup, mainTitle: String, targets: List<DownloadTarget>, spec: DownloadSpec): Int {
        require(group.id > 0 && targets.isNotEmpty()) { "请选择下载内容" }
        require(targets.all { it.id > 0 && it.cid > 0 }) { "视频标识无效" }
        val createdAt = now()
        val candidates = targets.sortedBy { it.number }.map { target ->
            val title = listOf(mainTitle, target.subtitle).filter(String::isNotBlank).joinToString(" ")
            DownloadTask(
                id = Uuid.random().toString(),
                request = DownloadRequest(target.id, target.cid, title, spec, group.type),
                createdAt = createdAt,
                updatedAt = createdAt,
                mainTitle = mainTitle,
                subtitle = target.subtitle,
                group = group,
                episodeNumber = target.number,
            )
        }
        return enqueueTasks(candidates)
    }

    private suspend fun enqueueTasks(candidates: List<DownloadTask>, sources: Map<String, VideoSource> = emptyMap()): Int {
        initialize()
        val login = getKoin().get<LoginStorage>()
        check(login.isLoggedIn) { "请先登录" }
        val session = login.cookies.sessData
        if (mutex.withLock { candidates.all { candidate -> _tasks.value.any { it.request.key == candidate.request.key } } }) {
            notify("已存在相同下载任务")
            return 0
        }
        val directory = getKoin().get<BoloSettings>().general.downloadDirectory
        check(files.requestPermission(directory)) { "未获得下载目录写入权限" }
        // 持久化及任务启动属于管理器；弹窗在此后被销毁也不能丢失已接受任务。
        return withContext(Dispatchers.Default + NonCancellable) {
            mutex.withLock {
                check(login.isLoggedIn && session == login.cookies.sessData) { "登录状态已变化，请重试" }
                val keys = _tasks.value.mapTo(mutableSetOf()) { it.request.key }
                val accepted = candidates.filter { keys.add(it.request.key) }.map { it.copy(downloadDirectory = directory) }
                if (accepted.isEmpty()) {
                    notify("已存在相同下载任务")
                    return@withLock 0
                }
                val queued = _tasks.value.count { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading } + accepted.size > 3
                commit(accepted + _tasks.value)
                accepted.forEach { launchTask(it.id, sources[it.id]) }
                val skipped = candidates.size - accepted.size
                when {
                    skipped > 0 -> notify("已加入 ${accepted.size} 个下载任务，跳过 $skipped 个重复任务")
                    queued -> notify("已加入下载队列")
                }
                accepted.size
            }
        }
    }

    fun retry(id: String) = perform {
        initialize()
        check(getKoin().get<LoginStorage>().isLoggedIn) { "请先登录" }
        val directory = getKoin().get<BoloSettings>().general.downloadDirectory
        check(files.requestPermission(directory)) { "未获得下载目录写入权限" }
        val task = mutex.withLock {
            val current = _tasks.value.firstOrNull { it.id == id } ?: return@perform
            if ((current.status != DownloadStatus.Failed && current.status != DownloadStatus.Canceled) ||
                id in jobs || id in removing || !retrying.add(id)) return@perform
            current
        }
        try {
            // 先移除旧发布标记，再丢弃输出引用；清理失败时保留原任务和成品保护。
            if (task.output != null) files.clean(id, task.output)
            mutex.withLock {
                if (_tasks.value.none { it === task }) return@withLock
                val queued = _tasks.value.count { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Downloading } >= 3
                commit(_tasks.value.map { if (it === task) task.copy(status = DownloadStatus.Queued,
                    downloadedBytes = 0, totalBytes = null, mergeProgress = 0f, error = null, output = null,
                    actualSpec = null, downloadDirectory = directory, bytesPerSecond = 0, updatedAt = now()) else it })
                launchTask(id, null)
                if (queued) notify("已加入下载队列")
            }
        } finally {
            withContext(NonCancellable) { mutex.withLock { retrying.remove(id) } }
        }
    }

    fun cancel(id: String) = perform {
        initialize()
        val job = mutex.withLock {
            if (id in removing || id in retrying) return@perform
            jobs[id]?.also { it.cancel() }
        }
        job?.join()
        mutex.withLock {
            if (id in removing || id in retrying) return@withLock
            if (jobs[id] != null && jobs[id] !== job) return@withLock
            if (jobs[id] === job) jobs.remove(id)
            val task = _tasks.value.firstOrNull { it.id == id } ?: return@withLock
            if (!task.status.isTerminal || (job == null && task.status == DownloadStatus.Failed)) {
                commit(_tasks.value.map { if (it.id == id) it.copy(status = DownloadStatus.Canceled, error = null, bytesPerSecond = 0, updatedAt = now()) else it })
            }
        }
    }

    suspend fun remove(id: String, deleteFile: Boolean = false): Boolean = scope.async {
        var reserved = false
        var failurePrefix = "移除下载记录失败"
        try {
            initialize()
            val job = mutex.withLock {
                val task = _tasks.value.firstOrNull { it.id == id } ?: return@async true
                check(task.status.isTerminal) { "任务尚未结束" }
                check(id !in retrying) { "任务正在重试" }
                check(removing.add(id)) { "任务正在移除" }
                reserved = true
                jobs[id]
            }
            job?.join()
            val task = mutex.withLock {
                check(id !in jobs) { "任务尚未结束" }
                _tasks.value.first { it.id == id }.also { check(it.status.isTerminal) { "任务尚未结束" } }
            }
            if (deleteFile) {
                failurePrefix = "清理本地文件失败"
                files.clean(id, task.output)
                task.output?.let { files.delete(it) }
            }
            failurePrefix = "移除下载记录失败"
            mutex.withLock { commit(_tasks.value.filterNot { it.id == id }) }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            notify("$failurePrefix：${safeError(error)}")
            false
        } finally {
            if (reserved) withContext(NonCancellable) { mutex.withLock { removing.remove(id) } }
        }
    }.await()

    private fun launchTask(id: String, source: VideoSource?) {
        // 调用方持有 mutex；先按入队顺序领取或等待名额，读取任务时挂起，登记 job 后才继续。
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
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
                        checkNotNull(task.actualSpec).audioQuality?.let { files.path(id, "audio.m4s") }, files.path(id, "output.mp4")) { progress ->
                        change(id, persist = false) { if (it.status == DownloadStatus.Merging) it.copy(mergeProgress = progress) else it }
                    }
                    change(id) { it.copy(status = DownloadStatus.Saving) }
                    files.publish(id, task.fileName, task.downloadDirectory) { output ->
                        change(id) { it.copy(status = DownloadStatus.Completed, output = output, mergeProgress = 1f) }
                    }
                }
            } catch (_: CancellationException) {
                canceled = true
            } catch (error: Throwable) {
                failure = safeError(error)
            } finally {
                withContext(NonCancellable) {
                    try { files.clean(id, task(id).output) } catch (error: Exception) {
                        failure = listOfNotNull(failure, safeError(error)).joinToString("；")
                    }
                    mutex.withLock {
                        jobs.remove(id)
                        val current = _tasks.value.firstOrNull { it.id == id }
                        if (current != null && current.status != DownloadStatus.Completed) {
                            val terminal = current.copy(status = if (canceled && failure == null) DownloadStatus.Canceled else DownloadStatus.Failed,
                                error = failure, bytesPerSecond = 0, updatedAt = now())
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
    }

    private suspend fun executeDownload(id: String, initialSource: VideoSource?) {
        val request = task(id).request
        val source = initialSource ?: try {
            check(getKoin().get<LoginStorage>().isLoggedIn) { "请先登录" }
            withTimeout(15_000) {
                when (request.type) {
                    DownloadType.Video -> fetchVideoPlayInfo(request.id, request.cid)
                    DownloadType.Media -> fetchMediaPlayInfo(request.id)
                }
            }
        } catch (_: TimeoutCancellationException) { error("播放信息获取超时，请重试") }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { error("播放信息获取失败，请重试") }
        val selected = resolveDownloadStreams(source, request.spec)
        files.clean(id)
        files.prepare(id)
        change(id) { it.copy(status = DownloadStatus.Downloading, actualSpec = selected.spec) }
        notify("开始下载：${task(id).title}")
        val streams = listOfNotNull(selected.video, selected.audio)
        val receivedBytes = LongArray(streams.size)
        val totalBytes = arrayOfNulls<Long>(streams.size)
        val progressMutex = Mutex()
        // 两条轨道共享一个任务名额；任一失败或取消时，等待另一条停止后统一清理。
        coroutineScope {
            val sampling = launch {
                var previousBytes = 0L
                var previousTime = TimeSource.Monotonic.markNow()
                while (isActive) {
                    delay(1_000)
                    val sampledAt = TimeSource.Monotonic.markNow()
                    val bytes = task(id).downloadedBytes
                    val seconds = (sampledAt - previousTime).inWholeNanoseconds / 1_000_000_000.0
                    val speed = if (seconds > 0) ((bytes - previousBytes).coerceAtLeast(0) / seconds).toLong() else 0L
                    change(id, persist = false) { it.copy(bytesPerSecond = speed) }
                    previousBytes = bytes
                    previousTime = sampledAt
                }
            }
            try {
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
            } finally {
                sampling.cancel()
            }
        }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun task(id: String): DownloadTask = mutex.withLock { _tasks.value.first { it.id == id } }
    private suspend fun change(id: String, persist: Boolean = true, transform: (DownloadTask) -> DownloadTask) = mutex.withLock {
        val changed = _tasks.value.map { if (it.id == id) transform(it).let { value ->
            value.copy(updatedAt = if (persist) now() else value.updatedAt,
                bytesPerSecond = if (value.status == DownloadStatus.Downloading) value.bytesPerSecond else 0)
        } else it }
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
