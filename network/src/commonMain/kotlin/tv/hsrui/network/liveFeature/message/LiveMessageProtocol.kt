package tv.hsrui.network.liveFeature.message

import com.ensody.kompressor.brotli.AsyncBrotliDecompressor
import com.ensody.kompressor.core.ByteArraySlice
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal class LiveMessageProtocol {
    private var pending = ByteArray(0)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun receive(bytes: ByteArray, onPacket: suspend (Int, JsonObject?) -> Unit) {
        require(bytes.size <= MAX_FRAME && pending.size + bytes.size <= MAX_FRAME * 2) { "直播信息流数据过大" }
        val input = if (pending.isEmpty()) bytes else pending + bytes
        pending = ByteArray(0)
        var inflated = 0
        suspend fun parse(input: ByteArray, depth: Int): Int {
            require(depth <= 4) { "直播信息流嵌套过深" }
            var offset = 0
            while (input.size - offset >= 16) {
                currentCoroutineContext().ensureActive()
                val size = readInt(input, offset)
                val header = readShort(input, offset + 4)
                val version = readShort(input, offset + 6)
                val operation = readInt(input, offset + 8)
                require(size in 16..MAX_FRAME && header in 16..size) { "直播信息流包头无效" }
                if (size > input.size - offset) break
                val body = input.copyOfRange(offset + header, offset + size)
                when {
                    version == 3 -> {
                        val decoder = AsyncBrotliDecompressor()
                        val source = ByteArraySlice(body)
                        val chunks = mutableListOf<ByteArray>()
                        var count = 0
                        do {
                            currentCoroutineContext().ensureActive()
                            val output = ByteArraySlice(64 * 1024)
                            val before = source.readStart
                            decoder.transform(source, output, finish = true)
                            require(!source.insufficient) { "直播信息流压缩包不完整" }
                            count += output.writeStart
                            inflated += output.writeStart
                            require(inflated <= MAX_INFLATED) { "直播信息流解压结果过大" }
                            chunks += output.data.copyOf(output.writeStart)
                            require(source.readStart != before || output.writeStart > 0 || !output.insufficient) { "直播信息流解压停滞" }
                        } while (source.remainingRead > 0 || output.insufficient)
                        val unpacked = ByteArray(count)
                        var cursor = 0
                        for (chunk in chunks) { chunk.copyInto(unpacked, cursor); cursor += chunk.size }
                        require(parse(unpacked, depth + 1) == unpacked.size) { "直播信息流内嵌包不完整" }
                    }
                    version == 0 || version == 1 -> {
                        when (operation) {
                            3 -> onPacket(operation, null)
                            5, 8 -> onPacket(operation, json.parseToJsonElement(body.decodeToString()) as? JsonObject
                                ?: error("直播信息流正文无效"))
                        }
                    }
                    else -> error("不支持的直播信息流协议（$version）")
                }
                offset += size
            }
            return offset
        }
        val consumed = parse(input, 0)
        if (consumed < input.size) {
            pending = input.copyOfRange(consumed, input.size)
            require(pending.size <= MAX_FRAME) { "直播信息流未完成包过大" }
        }
    }

    companion object {
        const val MAX_FRAME = 4 * 1024 * 1024
        const val MAX_INFLATED = 16 * 1024 * 1024

        fun packet(operation: Int, body: String = ""): ByteArray {
            val content = body.encodeToByteArray()
            val bytes = ByteArray(16 + content.size)
            fun put(offset: Int, value: Int) { repeat(4) { bytes[offset + it] = (value ushr (24 - it * 8)).toByte() } }
            put(0, bytes.size)
            bytes[5] = 16
            bytes[7] = 1
            put(8, operation)
            put(12, 1)
            content.copyInto(bytes, 16)
            return bytes
        }

        private fun readInt(bytes: ByteArray, offset: Int): Int =
            (0..3).fold(0) { value, index -> (value shl 8) or (bytes[offset + index].toInt() and 255) }
        private fun readShort(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
    }
}
