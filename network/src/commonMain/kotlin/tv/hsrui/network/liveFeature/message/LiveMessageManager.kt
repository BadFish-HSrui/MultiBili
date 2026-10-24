@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package tv.hsrui.network.liveFeature.message

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.constant.BOLO_UA
import tv.hsrui.network.login.storage.LoginStorage
import kotlin.concurrent.atomics.AtomicLong
import kotlin.random.Random
import kotlin.time.TimeSource

/** 导航页面持有；所有订阅者共享连接，媒体输出的重建不影响信息流。 */
class LiveMessageManager(
    parentScope: CoroutineScope,
    private val infoRequest: suspend (Long) -> LiveMessageInfoData = ::fetchLiveMessageInfo,
    private val accountChanges: Flow<Long> = getKoin().get<LoginStorage>().currentUserMidFlow,
    private val client: HttpClient = HttpClient {
        install(WebSockets) { maxFrameSize = LiveMessageProtocol.MAX_FRAME.toLong() }
    },
    private val heartbeatMs: Long = 30_000,
    private val heartbeatTimeoutMs: Long = 75_000,
    private val retryDelaysMs: List<Long> = listOf(5_000, 10_000, 20_000, 30_000),
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]) + Dispatchers.Default)
    private val serial = AtomicLong(0)
    private val requested = MutableStateFlow<Pair<Long?, Long>>(null to 0L)
    private val mutableConnection = MutableStateFlow(LiveMessageConnectionState())
    val connectionState = mutableConnection.asStateFlow()
    private val mutableRoomStatus = MutableStateFlow<LiveRoomStatusData?>(null)
    val roomStatus = mutableRoomStatus.asStateFlow()
    private val mutableMessages = MutableSharedFlow<LiveMessageData>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages = mutableMessages.asSharedFlow()
    private var closed = false

    init {
        require(heartbeatMs > 0 && heartbeatTimeoutMs > heartbeatMs && retryDelaysMs.isNotEmpty())
        scope.launch {
            requested.collectLatest { request ->
                val roomId = request.first ?: return@collectLatest
                accountChanges.distinctUntilChanged().collectLatest { userId ->
                    var attempt = 0
                    while (isActive && requested.value == request) {
                        try {
                            publishConnection(LiveMessageConnectionState(
                                if (attempt == 0) LiveMessageConnectionStatus.CONNECTING else LiveMessageConnectionStatus.RECONNECTING,
                                roomId, request.second,
                            ))
                            val info = infoRequest(roomId)
                            check(info.userId == userId) { "直播信息流身份已变化" }
                            for (address in info.urls) {
                                if (requested.value != request) break
                                try {
                                    runConnection(roomId, address, info, request.second) { attempt = 0 }
                                } catch (error: Exception) {
                                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                                    if (requested.value != request) break
                                    publishConnection(LiveMessageConnectionState(LiveMessageConnectionStatus.RECONNECTING, roomId, request.second))
                                    delay(retryDelaysMs[attempt.coerceAtMost(retryDelaysMs.lastIndex)] + Random.nextLong(0, 1001))
                                    attempt++
                                    if (error is IllegalArgumentException) break
                                }
                            }
                            check(info.urls.isNotEmpty()) { "直播信息流节点为空" }
                        } catch (error: Exception) {
                            if (error is CancellationException && error !is TimeoutCancellationException) throw error
                            if (requested.value != request) break
                            publishConnection(LiveMessageConnectionState(LiveMessageConnectionStatus.RECONNECTING, roomId, request.second))
                            delay(retryDelaysMs[attempt.coerceAtMost(retryDelaysMs.lastIndex)] + Random.nextLong(0, 1001))
                            attempt++
                        }
                    }
                }
            }
        }
    }

    fun start(roomId: Long) {
        require(roomId > 0)
        if (closed || requested.value.first == roomId) return
        val generation = serial.addAndFetch(1)
        requested.value = roomId to generation
        mutableRoomStatus.update { it?.takeIf { status -> status.generation == generation } }
    }

    fun stop() {
        if (closed) return
        requested.value = null to serial.addAndFetch(1)
        mutableRoomStatus.value = null
        mutableConnection.value = LiveMessageConnectionState()
    }

    fun close() {
        if (closed) return
        stop()
        closed = true
        scope.cancel()
        client.close()
        mutableConnection.value = LiveMessageConnectionState(LiveMessageConnectionStatus.CLOSED)
    }

    fun isCurrent(roomId: Long, generation: Long): Boolean = !closed && requested.value == (roomId to generation)

    private fun publishConnection(value: LiveMessageConnectionState) {
        mutableConnection.update { if (requested.value == (value.roomId to value.generation)) value else it }
    }

    private suspend fun runConnection(roomId: Long, address: String, info: LiveMessageInfoData, generation: Long, onConnected: () -> Unit) = coroutineScope {
        val session = withTimeout(10_000) {
            client.webSocketSession {
                url(address)
                header("User-Agent", BOLO_UA)
                header("Referer", "https://live.bilibili.com/$roomId")
            }
        }
        val authenticated = CompletableDeferred<Unit>()
        val protocol = LiveMessageProtocol()
        val connectionId = serial.addAndFetch(1)
        val heartbeat = MutableStateFlow(TimeSource.Monotonic.markNow())
        try {
            session.send(Frame.Binary(true, LiveMessageProtocol.packet(7, buildJsonObject {
                put("uid", info.userId)
                put("roomid", roomId)
                put("protover", 3)
                put("platform", "web")
                put("type", 2)
                put("key", info.token)
                put("buvid", info.buvid)
                put("support_ack", false)
            }.toString())))
            val receiver = launch {
                for (frame in session.incoming) {
                    if (requested.value != (roomId to generation)) return@launch
                    if (frame !is Frame.Binary) continue
                    protocol.receive(frame.data) { operation, body ->
                        if (requested.value != (roomId to generation)) return@receive
                        when (operation) {
                            8 -> {
                                require(body?.get("code")?.jsonPrimitive?.intOrNull == 0) { "直播信息流鉴权失败" }
                                authenticated.complete(Unit)
                            }
                            3 -> heartbeat.value = TimeSource.Monotonic.markNow()
                            5 -> if (authenticated.isCompleted && body != null) {
                                val command = body["cmd"]?.jsonPrimitive?.contentOrNull ?: return@receive
                                val live = when (command) {
                                    "LIVE" -> true
                                    "PREPARING", "END", "CLOSE", "BLOCK", "ROUND" -> false
                                    else -> null
                                }
                                if (live != null) {
                                    val data = body["data"] as? JsonObject
                                    val start = (body["live_time"] ?: data?.get("live_time"))?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
                                    val revision = serial.addAndFetch(1)
                                    mutableRoomStatus.update { previous ->
                                        if (!isCurrent(roomId, generation)) previous else LiveRoomStatusData(
                                            roomId, live, revision, generation, start,
                                            // 最新状态即使合并了短暂关播，也保留新场次标记。
                                            if (live && previous?.isLive == false) revision else previous?.broadcastRevision ?: 0,
                                        )
                                    }
                                }
                                if (isCurrent(roomId, generation)) mutableMessages.tryEmit(LiveMessageData(roomId, command, body, generation))
                            }
                        }
                    }
                }
                error("直播信息流连接已关闭")
            }
            withTimeout(10_000) { authenticated.await() }
            if (requested.value != (roomId to generation)) return@coroutineScope
            publishConnection(LiveMessageConnectionState(LiveMessageConnectionStatus.CONNECTED, roomId, generation, connectionId))
            onConnected()
            val keepAlive = launch {
                while (isActive) {
                    session.send(Frame.Binary(true, LiveMessageProtocol.packet(2)))
                    delay(heartbeatMs)
                }
            }
            val watchdog = launch {
                while (isActive) {
                    delay(minOf(5_000, heartbeatMs))
                    check(heartbeat.value.elapsedNow().inWholeMilliseconds < heartbeatTimeoutMs) { "直播信息流心跳超时" }
                }
            }
            try { receiver.join() } finally { keepAlive.cancel(); watchdog.cancel() }
        } finally {
            session.cancel()
        }
    }
}
