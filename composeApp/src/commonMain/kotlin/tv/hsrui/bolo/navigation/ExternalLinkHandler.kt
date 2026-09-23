package tv.hsrui.bolo.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.link.fetchExternalLinkRedirect
import tv.hsrui.network.feature.link.isBilibiliShortLink
import tv.hsrui.network.utils.toMD5

class ExternalLinkHandler internal constructor(
    private val scope: CoroutineScope,
    private val clipboardEnabled: () -> Boolean,
    private val systemLinksEnabled: () -> Boolean,
    private val readClipboard: suspend () -> String?,
    private val isCurrentRoute: (BoloRoute) -> Boolean,
    private val navigate: (BoloRoute) -> Unit,
    private val showMessage: (String) -> Unit,
    private val fetchRedirect: suspend (String) -> String? = ::fetchExternalLinkRedirect,
) {
    internal var clipboardLink by mutableStateOf<String?>(null)
        private set
    internal var resolvingClipboard by mutableStateOf(false)
        private set

    private var interactive = false
    private var activationStarted = false
    private var clipboardCheckPending = false
    private var suppressNextActivation = false
    private var pendingSystemLink: Pair<Long, String>? = null
    private var clipboardReadJob: Job? = null
    private var clipboardOpenJob: Job? = null
    private var systemOpenJob: Job? = null

    internal fun onActivation() {
        activationStarted = true
        clipboardCheckPending = !suppressNextActivation && systemLinks.value == null &&
            pendingSystemLink == null && systemOpenJob?.isActive != true && clipboardLink == null && clipboardEnabled()
        suppressNextActivation = false
        processPending()
    }

    internal fun onBackground() {
        activationStarted = false
        interactive = false
        clipboardCheckPending = false
        clipboardReadJob?.cancel()
    }

    internal fun setInteractive(value: Boolean) {
        interactive = value
        processPending()
    }

    internal fun onSettingsChanged() {
        if (!clipboardEnabled()) {
            clipboardCheckPending = false
            clipboardReadJob?.cancel()
            dismissClipboard()
        }
        if (!systemLinksEnabled()) {
            systemOpenJob?.cancel()
            pendingSystemLink?.let(::acknowledgeSystemLink)
            pendingSystemLink = null
        }
    }

    internal fun handleSystemLink(event: Pair<Long, String>) {
        clipboardReadJob?.cancel()
        clipboardCheckPending = false
        dismissClipboard()
        systemOpenJob?.cancel()
        suppressNextActivation = !activationStarted
        pendingSystemLink = event
        if (!systemLinksEnabled()) {
            acknowledgeSystemLink(event)
            pendingSystemLink = null
        }
        processPending()
    }

    private fun processPending() {
        if (!interactive) return
        val event = pendingSystemLink
        if (event != null) {
            if (systemOpenJob?.isActive == true) return
            systemOpenJob = scope.launch {
                try {
                    if (!systemLinksEnabled() || systemLinks.value != event) return@launch
                    val link = event.second
                    val route = if (isBilibiliShortLink(link)) {
                        val target = fetchRedirect(link)
                        ensureActive()
                        if (!systemLinksEnabled() || systemLinks.value != event) return@launch
                        if (target == null) {
                            showMessage("链接解析失败")
                            return@launch
                        }
                        parseExternalLink(target)
                    } else parseExternalLink(link)
                    ensureActive()
                    if (!systemLinksEnabled() || systemLinks.value != event) return@launch
                    if (route == null) showMessage("暂不支持此链接")
                    else if (!isCurrentRoute(route)) navigate(route)
                    acknowledgeSystemLink(event)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ensureActive()
                    if (systemLinks.value == event) showMessage("链接解析失败")
                } finally {
                    // 重建宿主时保留未完成入口；正常完成和失败只消费自己的事件。
                    if (coroutineContext[Job]?.isActive == true) acknowledgeSystemLink(event)
                    if (pendingSystemLink == event) pendingSystemLink = null
                }
            }
            return
        }
        if (!clipboardCheckPending || clipboardLink != null || systemLinks.value != null) return
        clipboardCheckPending = false
        if (!clipboardEnabled()) return
        val ingressId = nextSystemLinkId
        clipboardReadJob?.cancel()
        clipboardReadJob = scope.launch {
            try {
                if (!clipboardEnabled() || ingressId != nextSystemLinkId || systemLinks.value != null) return@launch
                val text = readClipboard() ?: return@launch
                ensureActive()
                if (!clipboardEnabled() || ingressId != nextSystemLinkId || systemLinks.value != null) return@launch
                val candidate = extractExternalLink(text) ?: return@launch
                if (!seenClipboard.add(text.toMD5())) return@launch
                val route = parseExternalLink(candidate)
                if (route == null || !isCurrentRoute(route)) clipboardLink = candidate
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 剪贴板权限被拒绝或暂时被占用时结束本次检测。
            }
        }
    }

    internal fun dismissClipboard() {
        clipboardOpenJob?.cancel()
        clipboardLink = null
        resolvingClipboard = false
    }

    internal fun openClipboard() {
        val candidate = clipboardLink ?: return
        if (resolvingClipboard || !clipboardEnabled()) return
        resolvingClipboard = true
        clipboardOpenJob = scope.launch {
            try {
                val route = if (isBilibiliShortLink(candidate)) {
                    val target = fetchRedirect(candidate)
                    ensureActive()
                    if (target == null) {
                        showMessage("链接解析失败")
                        return@launch
                    }
                    parseExternalLink(target)
                } else parseExternalLink(candidate)
                ensureActive()
                if (!clipboardEnabled() || clipboardLink != candidate || systemLinks.value != null) return@launch
                if (route == null) {
                    showMessage("暂不支持此链接")
                } else {
                    clipboardLink = null
                    if (!isCurrentRoute(route)) navigate(route)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                showMessage("链接解析失败")
            } finally {
                if (clipboardLink == candidate || clipboardLink == null) resolvingClipboard = false
            }
        }
    }

    internal fun close() {
        clipboardReadJob?.cancel()
        clipboardOpenJob?.cancel()
        systemOpenJob?.cancel()
    }

    companion object {
        // 宿主可能早于 Koin/Compose 就绪。未消费事件在进程内跨宿主重建保留。
        internal val systemLinks = MutableStateFlow<Pair<Long, String>?>(null)
        private var nextSystemLinkId = 0L
        private val seenClipboard = mutableSetOf<String>()

        fun receiveSystemLink(url: String) {
            systemLinks.value = ++nextSystemLinkId to url
        }

        private fun acknowledgeSystemLink(event: Pair<Long, String>) {
            systemLinks.compareAndSet(event, null)
        }
    }
}
