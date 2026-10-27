package tv.hsrui.network.liveFeature.message.fansMedal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuFansMedalData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuMedalResourcesData
import tv.hsrui.network.liveFeature.danmaku.fetchLiveDanmakuMedalResources
import tv.hsrui.network.liveFeature.danmaku.toLiveDanmakuFansMedal
import tv.hsrui.network.liveFeature.message.LiveMessageData

/** 信息流管理器持有；请求在独立任务中执行，状态变更按收到的顺序串行处理。 */
class LiveFansMedalManager(
    parentScope: CoroutineScope,
    private val pageRequest: suspend (Long, Long, Int) -> LiveFansMedalPageData = ::fetchLiveFansMedalPage,
    private val currentRequest: suspend (Long, Long, Long) -> LiveDanmakuFansMedalData? = ::fetchLiveCurrentFansMedal,
    private val wearRequest: suspend (Long, Long, Long) -> LiveFansMedalOperationData = ::wearLiveFansMedal,
    private val takeOffRequest: suspend (Long, Long) -> LiveFansMedalOperationData = ::takeOffLiveFansMedal,
    private val resourcesRequest: suspend () -> LiveDanmakuMedalResourcesData = ::fetchLiveDanmakuMedalResources,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val commands = Channel<() -> Unit>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(LiveFansMedalState())
    val state = mutableState.asStateFlow()
    private var context: Triple<Long, Long, Long>? = null
    private var generation = 0L
    private var session = 0L
    private var listRequestRevision = 0L
    private var currentRequestRevision = 0L
    private var listJob: Job? = null
    private var currentJob: Job? = null
    private var operationJob: Job? = null
    private var resourcesJob: Job? = null
    private var pendingOperation: LiveFansMedalOperationData? = null

    init {
        scope.launch { for (command in commands) command() }
    }

    internal fun start(roomId: Long, anchorUid: Long, userId: Long, generation: Long) = dispatch {
        val next = Triple(roomId, anchorUid, userId)
        if (context == next && this.generation == generation) return@dispatch
        cancelRequests()
        session++
        context = next
        this.generation = generation
        update { LiveFansMedalState(resources = it.resources, isCurrentLoaded = userId <= 0) }
        if (roomId <= 0 || anchorUid <= 0 || userId <= 0) return@dispatch
        refresh()
        val token = session
        resourcesJob = scope.launch {
            try {
                val resources = resourcesRequest()
                ensureActive()
                dispatch { if (token == session) update { it.copy(resources = resources) } }
            } catch (error: CancellationException) {
                if (error !is TimeoutCancellationException) throw error
            } catch (_: Exception) {
                // 图标不可用时仍显示勋章文字。
            }
        }
    }

    internal fun onConnected(generation: Long) = dispatch {
        if (this.generation == generation) refresh()
    }

    internal fun onMessage(message: LiveMessageData) {
        val command = message.command.substringBefore(':')
        if (command != "MESSAGEBOX_USER_GAIN_MEDAL" && command != "MESSAGEBOX_USER_MEDAL_CHANGE") return
        val data = message.payload["data"] as? JsonObject ?: return
        val uid = (data["uid"] as? JsonPrimitive)?.longOrNull ?: return
        val anchorUid = (data["up_uid"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return
        val medal = (data["uinfo_medal"] as? JsonObject)?.toLiveDanmakuFansMedal() ?: return
        if (medal.anchorUid != anchorUid) return
        dispatch {
            val (roomId, _, userId) = context ?: return@dispatch
            if (roomId != message.roomId || generation != message.generation || uid <= 0 || uid != userId) return@dispatch
            val reloadList = command == "MESSAGEBOX_USER_GAIN_MEDAL" || mutableState.value.currentPage == 0 || pendingOperation != null
            invalidateReads()
            update { previous ->
                previous.copy(
                    items = previous.items.map { if (it.anchorUid == anchorUid) it.copy(medal = medal) else it },
                    currentMedal = if (previous.currentMedal?.anchorUid == anchorUid) medal else previous.currentMedal,
                )
            }
            if (reloadList) refresh() else refreshCurrent()
        }
    }

    fun refresh() = dispatch {
        val (roomId, anchorUid, userId) = context ?: return@dispatch
        if (userId <= 0 || anchorUid <= 0) return@dispatch
        if (operationJob != null) return@dispatch
        refreshCurrent()
        if (listJob != null && !mutableState.value.isLoadingMore) return@dispatch
        listJob?.cancel()
        val revision = ++listRequestRevision
        val token = session
        update { it.copy(isLoadingList = true, isLoadingMore = false, listError = null, loadMoreError = null) }
        listJob = scope.launch {
            try {
                val page = pageRequest(roomId, anchorUid, 1)
                ensureActive()
                dispatch {
                    if (token != session || revision != listRequestRevision) return@dispatch
                    listJob = null
                    update {
                        it.copy(
                            items = page.items.distinctBy { item -> item.medalId },
                            currentPage = page.currentPage,
                            totalPages = page.totalPages,
                            totalCount = page.totalCount,
                            isLoadingList = false,
                            listError = null,
                            listRevision = it.listRevision + 1,
                        )
                    }
                    finishOperation()
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                dispatch {
                    if (token != session || revision != listRequestRevision) return@dispatch
                    listJob = null
                    update { it.copy(isLoadingList = false, listError = error.message ?: "勋章列表加载失败") }
                    finishOperation()
                }
            }
        }
    }

    fun refreshCurrent() = dispatch {
        val (roomId, anchorUid, userId) = context ?: return@dispatch
        if (userId <= 0 || anchorUid <= 0) return@dispatch
        if (operationJob != null || currentJob != null) return@dispatch
        val revision = ++currentRequestRevision
        val token = session
        update { it.copy(isLoadingCurrent = true, currentError = null) }
        currentJob = scope.launch {
            try {
                val medal = currentRequest(roomId, anchorUid, userId)
                ensureActive()
                dispatch {
                    if (token != session || revision != currentRequestRevision) return@dispatch
                    currentJob = null
                    update { it.copy(currentMedal = medal, isCurrentLoaded = true, isLoadingCurrent = false, currentError = null) }
                    finishOperation()
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                dispatch {
                    if (token != session || revision != currentRequestRevision) return@dispatch
                    currentJob = null
                    update { it.copy(isLoadingCurrent = false, currentError = error.message ?: "当前勋章加载失败") }
                    finishOperation()
                }
            }
        }
    }

    fun loadMore() = dispatch {
        val (roomId, anchorUid, userId) = context ?: return@dispatch
        if (userId <= 0 || anchorUid <= 0) return@dispatch
        val previous = mutableState.value
        if (listJob != null || previous.isChanging || !previous.hasMore) return@dispatch
        val pageNumber = previous.currentPage + 1
        val revision = ++listRequestRevision
        val token = session
        update { it.copy(isLoadingMore = true, loadMoreError = null) }
        listJob = scope.launch {
            try {
                val page = pageRequest(roomId, anchorUid, pageNumber)
                ensureActive()
                dispatch {
                    if (token != session || revision != listRequestRevision) return@dispatch
                    listJob = null
                    update {
                        it.copy(
                            items = (it.items + page.items).distinctBy { item -> item.medalId },
                            currentPage = page.currentPage,
                            totalPages = page.totalPages,
                            totalCount = page.totalCount,
                            isLoadingMore = false,
                            loadMoreError = null,
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                dispatch {
                    if (token != session || revision != listRequestRevision) return@dispatch
                    listJob = null
                    update { it.copy(isLoadingMore = false, loadMoreError = error.message ?: "更多勋章加载失败") }
                }
            }
        }
    }

    fun wear(medalId: Long?) = dispatch {
        val (roomId, anchorUid, userId) = context ?: return@dispatch
        if (userId <= 0 || anchorUid <= 0) return@dispatch
        val current = mutableState.value
        if (current.isChanging || (medalId != null && current.items.none { it.medalId == medalId })) return@dispatch
        invalidateReads()
        pendingOperation = null
        update { it.copy(isChanging = true, operationError = null) }
        val token = session
        operationJob = scope.launch {
            val result = try {
                if (medalId == null) takeOffRequest(roomId, anchorUid)
                else wearRequest(roomId, anchorUid, medalId)
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                LiveFansMedalOperationData(-1, "勋章操作结果未确认，正在查询当前状态，请勿重复操作")
            }
            ensureActive()
            dispatch {
                if (token != session) return@dispatch
                operationJob = null
                if (result.isSuccess || result.code == 1500008 || result.code == -1) {
                    pendingOperation = result
                    val message = if (result.isSuccess) null else result.message.ifBlank { "勋章操作未完成（${result.code}）" }
                    update { it.copy(operationError = message) }
                    refresh()
                } else {
                    val message = result.message.ifBlank { "勋章操作失败（${result.code}）" }
                    update { it.copy(isChanging = false, operationError = message) }
                }
            }
        }
    }

    fun takeOff() { wear(null) }

    internal fun stop(generation: Long? = null) = dispatch {
        if (generation != null && this.generation != generation) return@dispatch
        cancelRequests()
        session++
        context = null
        update { it.copy(isLoadingList = false, isLoadingMore = false, isLoadingCurrent = false, isChanging = false) }
    }

    internal fun close() {
        scope.cancel()
        commands.cancel()
        mutableState.value = LiveFansMedalState()
    }

    private fun finishOperation() {
        val result = pendingOperation ?: return
        val current = mutableState.value
        if (current.isLoadingList || current.isLoadingCurrent) return
        val isSynchronized = current.listError == null && current.currentError == null
        if (isSynchronized) pendingOperation = null
        val message = when {
            result.isSuccess && !isSynchronized -> "勋章操作已成功，状态刷新失败，请重试刷新"
            result.isSuccess -> null
            result.code == -1 && isSynchronized -> "勋章操作结果未确认，已重新查询状态，请核对佩戴标记"
            result.code == -1 -> "勋章操作结果未确认，状态尚未同步，请重试刷新"
            else -> result.message.ifBlank { "勋章操作未完成（${result.code}）" }
        }
        update {
            it.copy(
                isChanging = false,
                operationRevision = it.operationRevision + if (result.isSuccess && isSynchronized) 1 else 0,
                operationError = message,
            )
        }
    }

    private fun invalidateReads() {
        listRequestRevision++
        currentRequestRevision++
        listJob?.cancel()
        currentJob?.cancel()
        listJob = null
        currentJob = null
        update { it.copy(isLoadingList = false, isLoadingMore = false, isLoadingCurrent = false) }
    }

    private fun cancelRequests() {
        invalidateReads()
        operationJob?.cancel()
        resourcesJob?.cancel()
        operationJob = null
        resourcesJob = null
        pendingOperation = null
    }

    private fun dispatch(command: () -> Unit) {
        if (scope.isActive) commands.trySend(command)
    }

    private fun update(transform: (LiveFansMedalState) -> LiveFansMedalState) {
        mutableState.update { if (scope.isActive) transform(it) else it }
    }
}
