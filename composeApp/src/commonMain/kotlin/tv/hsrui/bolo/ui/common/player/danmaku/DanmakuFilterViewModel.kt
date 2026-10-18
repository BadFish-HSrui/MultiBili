package tv.hsrui.bolo.ui.common.player.danmaku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.danmaku.DanmakuFilterAddResponse
import tv.hsrui.network.feature.danmaku.DanmakuFilterData
import tv.hsrui.network.feature.danmaku.DanmakuFilterListResponse
import tv.hsrui.network.feature.danmaku.DanmakuFilterType
import tv.hsrui.network.feature.danmaku.addDanmakuFilter
import tv.hsrui.network.feature.danmaku.deleteDanmakuFilter
import tv.hsrui.network.feature.danmaku.fetchDanmakuFilters
import tv.hsrui.network.model.BaseResponse

class DanmakuFilterViewModel(
    private val readFilters: () -> List<DanmakuFilterData>,
    private val writeFilters: (List<DanmakuFilterData>) -> Unit,
    private val isLoggedIn: () -> Boolean,
    private val showMessage: (String) -> Unit,
    private val fetchRequest: suspend () -> DanmakuFilterListResponse = ::fetchDanmakuFilters,
    private val addRequest: suspend (DanmakuFilterData) -> DanmakuFilterAddResponse = ::addDanmakuFilter,
    private val deleteRequest: suspend (Long) -> BaseResponse = ::deleteDanmakuFilter,
) : ViewModel() {
    private val _isWorking = MutableStateFlow(false)
    val isWorking = _isWorking.asStateFlow()
    val filters: List<DanmakuFilterData> get() = readFilters()

    fun syncFilters(): Job? = runOperation("同步失败") {
        check(isLoggedIn()) { "请先登录" }
        val response = fetchRequest()
        currentCoroutineContext().ensureActive()
        check(response.isSuccess) { response.message.ifBlank { "错误码：${response.code}" } }

        val cloudFilters = response.filters.associateByTo(linkedMapOf()) { it.type to it.content }
        val localFilters = readFilters()
        val localKeys = localFilters.mapTo(mutableSetOf()) { it.type to it.content }
        val updatedLocalFilters = localFilters.map { local ->
            local.copy(id = cloudFilters[local.type to local.content]?.id ?: 0L)
        }
        val cloudOnlyFilters = cloudFilters.filterKeys { it !in localKeys }.values.map {
            it.copy(isOpened = true)
        }
        writeFilters(updatedLocalFilters + cloudOnlyFilters)
        showMessage("屏蔽规则已同步")
    }

    fun addFilter(
        content: String,
        type: DanmakuFilterType,
        upload: Boolean,
        onAdded: (DanmakuFilterType) -> Unit = {},
    ): Job? {
        if (_isWorking.value) return null
        if (content.isBlank()) {
            showMessage("屏蔽内容不能为空白")
            return null
        }
        if (type == DanmakuFilterType.User) return null
        if (type == DanmakuFilterType.Regex) {
            danmakuFilterRegexError(content)?.let {
                showMessage(it)
                return null
            }
        }
        if (readFilters().any { it.type == type && it.content == content }) {
            showMessage("规则已存在")
            return null
        }
        val filter = DanmakuFilterData(type = type, content = content)
        writeFilters(readFilters() + filter)
        if (!upload) {
            onAdded(type)
            return null
        }
        return uploadFilter(filter, onFinished = { onAdded(type) })
    }

    fun uploadFilter(
        filter: DanmakuFilterData,
        onSuccess: () -> Unit = {},
        onFinished: () -> Unit = {},
    ): Job? = runOperation("上传失败，规则已保留在本地", onFinished) {
        val local = findFilter(filter) ?: return@runOperation
        if (local.id > 0L) return@runOperation
        if (local.type == DanmakuFilterType.Regex) {
            danmakuFilterRegexError(local.content)?.let { error(it) }
        }
        check(isLoggedIn()) { "请先登录" }
        val response = addRequest(local)
        currentCoroutineContext().ensureActive()
        check(response.isSuccess) { response.message.ifBlank { "错误码：${response.code}" } }
        val id = response.filter?.id
        check(id != null && id > 0L) { "云端未返回有效的规则 ID" }
        updateFilter(local) { it.copy(id = id) }
        onSuccess()
    }

    fun removeFilterFromCloud(
        filter: DanmakuFilterData,
        onSuccess: () -> Unit = {},
    ): Job? = runOperation("云端删除失败") {
        val local = findFilter(filter) ?: return@runOperation
        if (local.id > 0L) {
            check(isLoggedIn()) { "请先登录" }
            val response = deleteRequest(local.id)
            currentCoroutineContext().ensureActive()
            check(response.isSuccess) { response.message.ifBlank { "错误码：${response.code}" } }
            updateFilter(local) { it.copy(id = 0L) }
        }
        onSuccess()
    }

    fun deleteFilter(
        filter: DanmakuFilterData,
        onSuccess: () -> Unit = {},
    ): Job? = runOperation("删除失败") {
        val local = findFilter(filter) ?: return@runOperation
        if (local.id > 0L) {
            check(isLoggedIn()) { "请先登录" }
            val response = deleteRequest(local.id)
            currentCoroutineContext().ensureActive()
            check(response.isSuccess) { response.message.ifBlank { "错误码：${response.code}" } }
        }
        writeFilters(readFilters().filterNot { it.type == local.type && it.content == local.content })
        onSuccess()
    }

    fun toggleFilter(filter: DanmakuFilterData) {
        if (_isWorking.value) return
        updateFilter(filter) { it.copy(isOpened = !it.isOpened) }
    }

    private fun findFilter(filter: DanmakuFilterData): DanmakuFilterData? =
        readFilters().firstOrNull { it.type == filter.type && it.content == filter.content }

    private fun updateFilter(filter: DanmakuFilterData, transform: (DanmakuFilterData) -> DanmakuFilterData) {
        val updatedFilters = readFilters().map { local ->
            if (local.type == filter.type && local.content == filter.content) transform(local) else local
        }
        writeFilters(updatedFilters)
    }

    private fun runOperation(
        failureMessage: String,
        onFinished: () -> Unit = {},
        operation: suspend () -> Unit,
    ): Job? {
        if (!_isWorking.compareAndSet(expect = false, update = true)) return null
        return viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                currentCoroutineContext().ensureActive()
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("$failureMessage：${e.message ?: "未知错误"}")
            } finally {
                _isWorking.value = false
                onFinished()
            }
        }
    }
}

internal fun danmakuFilterRegexError(content: String): String? = try {
    Regex(content)
    null
} catch (e: IllegalArgumentException) {
    // Native 的异常类型未公开；从两端的消息格式中只保留 description。
    val description = e.message?.lineSequence()?.firstOrNull()?.trim()
        ?.replace(Regex(""" near index:?\s+\d+$"""), "")
        ?.takeIf { it.isNotBlank() }
    "语法错误: ${description ?: "未知原因"}"
}
