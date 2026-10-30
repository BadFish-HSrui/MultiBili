package tv.hsrui.bolo.livePlayer.session

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLineStrict
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tv.hsrui.bolo.player.base.BoloMpvStreamReader
import tv.hsrui.bolo.player.base.BoloMpvStreamSource
import kotlin.random.Random

/** 一个读取代次的本机 HLS 视图；媒体正文始终由原采集会话拥有。 */
internal class LiveHlsInput(
    private val cache: LivePlaybackCache,
    private val first: LiveCachedSegment,
    generation: Long,
    private val format: String,
    targetDurationMs: Long = 0,
    private val onMissing: () -> Unit,
) : BoloMpvStreamSource {
    val startMs = first.media.startMs
    private val epoch = cache.snapshot.value.epoch
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val route = "/live/$generation/${Random.nextLong().toULong().toString(16)}"
    private var server: ServerSocket? = null
    private var lease: Long? = null
    private val targetDuration = (maxOf(targetDurationMs, cache.snapshot.value.segments.maxOfOrNull {
        duration(it)
    } ?: duration(first)) + 999) / 1000

    suspend fun start(): String = withContext(Dispatchers.IO) {
        job.ensureActive()
        lease = cache.retainSeek(first.id, epoch)
        val selector = SelectorManager(Dispatchers.IO)
        try {
            val socket = aSocket(selector).tcp().bind("127.0.0.1", 0)
            server = socket
            job.ensureActive()
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    while (isActive) {
                        val connection = socket.accept()
                        launch { serve(connection) }
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { if (isActive) onMissing() }
                finally {
                    socket.close()
                    selector.close()
                    withContext(NonCancellable) { confirm() }
                }
            }
            "http://127.0.0.1:${(socket.localAddress as InetSocketAddress).port}$route/index.m3u8"
        } catch (error: Throwable) {
            server?.close()
            selector.close()
            withContext(NonCancellable) { confirm() }
            throw error
        }
    }

    suspend fun confirm() {
        lease?.let { cache.releaseSeek(it) }
    }

    suspend fun awaitClosed() { job.join() }
    override suspend fun open(uri: String): BoloMpvStreamReader? = null
    override fun cancel() { job.cancel(); server?.close() }

    private suspend fun serve(socket: Socket) {
        try {
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = true)
            var method = ""
            var path = ""
            var range: String? = null
            withTimeout(5_000) {
                val request = input.readLineStrict(8_192)?.split(' ') ?: return@withTimeout
                if (request.size == 3) { method = request[0]; path = request[1] }
                var total = 0
                while (true) {
                    val line = input.readLineStrict(8_192) ?: break
                    if (line.isEmpty()) break
                    total += line.length
                    check(total <= 16_384)
                    if (line.substringBefore(':').equals("Range", ignoreCase = true)) range = line.substringAfter(':').trim()
                }
            }
            if (method != "GET" && method != "HEAD") {
                headers(output, "405 Method Not Allowed", 0)
                return
            }
            if (!path.startsWith("$route/")) {
                headers(output, "404 Not Found", 0)
                return
            }
            if (path == "$route/index.m3u8") {
                val bytes = playlist().encodeToByteArray()
                headers(output, "200 OK", bytes.size, "application/vnd.apple.mpegurl")
                if (method == "GET") output.writeFully(bytes)
                return
            }
            val resource = path.removePrefix("$route/")
            val init = resource.startsWith("init/")
            val media = resource.startsWith("segment/")
            val id = resource.substringAfter('/').substringBefore('.').toLongOrNull()
            if ((!init && !media) || id == null) {
                headers(output, "404 Not Found", 0)
                return
            }
            val body = cache.acquire(id, init, epoch)
            try {
                val size = body.size
                var start = 0
                var end = size - 1
                if (range != null) {
                    val bounds = range.removePrefix("bytes=").split('-')
                    val left = bounds.getOrNull(0)
                    val right = bounds.getOrNull(1)
                    if (!range.startsWith("bytes=") || bounds.size != 2 ||
                        left?.toLongOrNull()?.let { it !in 0 until size.toLong() } == true ||
                        left?.isNotEmpty() == true && left.toLongOrNull() == null ||
                        right?.isNotEmpty() == true && right.toLongOrNull() == null ||
                        left.isNullOrEmpty() && (right?.toLongOrNull() ?: 0) <= 0
                    ) {
                        headers(output, "416 Range Not Satisfiable", 0, extra = "Content-Range: bytes */$size\r\n")
                        return
                    }
                    if (left.isNullOrEmpty()) start = (size.toLong() - checkNotNull(right).toLong()).coerceAtLeast(0).toInt()
                    else {
                        start = left.toInt()
                        end = right?.toLongOrNull()?.coerceAtMost(end.toLong())?.toInt() ?: end
                    }
                    if (end < start) {
                        headers(output, "416 Range Not Satisfiable", 0, extra = "Content-Range: bytes */$size\r\n")
                        return
                    }
                }
                headers(output, if (range == null) "200 OK" else "206 Partial Content", end - start + 1,
                    if (format == "ts" && !init) "video/mp2t" else "video/mp4",
                    "Accept-Ranges: bytes\r\n" + if (range == null) "" else "Content-Range: bytes $start-$end/$size\r\n")
                if (method == "GET") {
                    val bytes = ByteArray(64 * 1024)
                    var offset = start
                    while (offset <= end) {
                        val count = minOf(cache.read(body, offset, bytes), end - offset + 1)
                        check(count > 0)
                        output.writeFully(bytes, 0, count)
                        offset += count
                    }
                    output.flush()
                    if (!init && end == size - 1) cache.consume(id - 1)
                }
            } finally { withContext(NonCancellable) { cache.release(body) } }
        } catch (error: CancellationException) { throw error }
        catch (_: LivePlaybackCache.Missing) { if (job.isActive) onMissing() }
        catch (_: Exception) { /* 单连接关闭不改变采集会话；读取超时由播放器处理。 */ }
        finally { socket.close() }
    }

    private suspend fun playlist(): String {
        val snapshot = cache.snapshot.first { value ->
            value.epoch != epoch || value.ended || value.segments.any { it.id >= first.id }
        }
        if (snapshot.epoch != epoch) throw LivePlaybackCache.Missing(null)
        val segments = snapshot.segments.filter { it.id >= first.id }
        if (segments.any { it.continuity != first.continuity || it.initializationId != first.initializationId } ||
            segments.zipWithNext().any { (a, b) -> a.sequence + 1 != b.sequence }) throw LivePlaybackCache.Missing(null)
        if (segments.isEmpty()) throw LivePlaybackCache.Missing(null)
        val durations = segments.map(::duration)
        if ((durations.max() + 999) / 1000 > targetDuration) throw LivePlaybackCache.Missing(null)
        return buildString {
            append("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:")
            append(targetDuration)
            append("\n#EXT-X-MEDIA-SEQUENCE:${segments.first().sequence}\n")
            first.initializationId?.let { append("#EXT-X-MAP:URI=\"$route/init/$it.mp4\"\n") }
            for ((index, segment) in segments.withIndex()) {
                val duration = durations[index]
                append("#EXTINF:${duration / 1000}.${(duration % 1000).toString().padStart(3, '0')},\n")
                append("$route/segment/${segment.id}.${if (format == "ts") "ts" else "m4s"}\n")
            }
            if (snapshot.ended) append("#EXT-X-ENDLIST\n")
        }
    }

    private fun duration(segment: LiveCachedSegment): Long {
        val start = segment.media.decodeStartMs
        val end = segment.media.decodeEndMs
        return if (start != null && end != null) (end - start).coerceAtLeast(1) else segment.media.durationMs.coerceAtLeast(1)
    }

    private suspend fun headers(output: ByteWriteChannel, status: String, size: Int,
        type: String = "application/octet-stream", extra: String = "") {
        output.writeStringUtf8("HTTP/1.1 $status\r\nContent-Length: $size\r\nContent-Type: $type\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n$extra\r\n")
    }
}
