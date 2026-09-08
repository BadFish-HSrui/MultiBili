package tv.hsrui.bolo.player.base

import kotlinx.coroutines.CancellationException
import tv.hsrui.network.feature.player.BiliDashObject
import tv.hsrui.network.feature.player.fetchDashIndex

internal class DashSegment(val start: Long, val endInclusive: Long, val time: Long, val duration: Long)

internal class DashIndex(
    val url: String,
    val resourceLength: Long,
    val timescale: Long,
    val firstTime: Long,
    val segments: List<DashSegment>,
) {
    val durationMs: Long
        get() {
            val ticks = segments.last().let { it.time + it.duration } - firstTime
            require(ticks / timescale <= Long.MAX_VALUE / 1_000L) { "DASH 时长溢出" }
            return ticks / timescale * 1_000L + (ticks % timescale * 1_000L + timescale - 1L) / timescale
        }
}

internal fun parseDashRange(value: String): LongRange {
    val match = Regex("(\\d+)-(\\d+)").matchEntire(value)
    require(match != null) { "DASH 字节范围格式无效" }
    val start = match.groupValues[1].toLongOrNull()
    val end = match.groupValues[2].toLongOrNull()
    require(start != null && end != null && start >= 0 && end >= start) { "DASH 字节范围无效" }
    return start..end
}

internal suspend fun resolveDashIndex(dash: BiliDashObject): DashIndex {
    val range = parseDashRange(dash.segmentBase?.resolvedIndexRange.orEmpty())
    val urls = (listOf(dash.baseUrl) + dash.backupUrl).filter { it.isNotBlank() }.distinct()
    require(urls.isNotEmpty()) { "DASH URL 为空" }
    var failure: Exception? = null
    for (url in urls) {
        try {
            val response = fetchDashIndex(url, range.first, range.last, videoPlayHeaders)
            return parseDashIndex(response.bytes, range.first, response.resourceLength, url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
        }
    }
    throw IllegalStateException("DASH 索引加载失败：${failure?.message}", failure)
}

internal fun parseDashIndex(bytes: ByteArray, fileOffset: Long, resourceLength: Long, url: String): DashIndex {
    val reader = DashIndexReader(bytes)
    while (reader.position < bytes.size) {
        val boxStart = reader.position
        val size32 = reader.uint32()
        val type = reader.uint32()
        val size = when (size32) {
            0L -> (bytes.size - boxStart).toLong()
            1L -> reader.uint64()
            else -> size32
        }
        require(size >= reader.position - boxStart && size <= bytes.size - boxStart) { "DASH 索引 box 长度无效" }
        val boxEnd = boxStart + size.toInt()
        if (type != 0x73696478L) {
            reader.position = boxEnd
            continue
        }
        reader.limit = boxEnd
        val version = reader.uint32() ushr 24
        require(version == 0L || version == 1L) { "不支持的 SIDX 版本" }
        reader.uint32() // reference_ID
        val timescale = reader.uint32()
        require(timescale > 0L) { "SIDX timescale 为零" }
        val firstTime = if (version == 0L) reader.uint32() else reader.uint64()
        val firstOffset = if (version == 0L) reader.uint32() else reader.uint64()
        val count = (reader.uint32() and 0xffffL).toInt()
        require(count > 0 && count.toLong() * 12L == boxEnd - reader.position.toLong()) { "SIDX 分片表长度无效" }
        var offset = checkedDashAdd(checkedDashAdd(fileOffset, boxEnd.toLong()), firstOffset)
        var time = firstTime
        val segments = List(count) {
            val reference = reader.uint32()
            require(reference ushr 31 == 0L) { "不支持嵌套 SIDX 索引" }
            val length = reference and 0x7fffffffL
            val duration = reader.uint32()
            reader.uint32() // SAP flags
            require(length > 0 && duration > 0) { "SIDX 分片大小或时长为零" }
            val end = checkedDashAdd(offset, length)
            require(end <= resourceLength) { "SIDX 分片范围超出媒体长度" }
            val segment = DashSegment(offset, end - 1L, time, duration)
            offset = end
            time = checkedDashAdd(time, duration)
            segment
        }
        return DashIndex(url, resourceLength, timescale, firstTime, segments)
    }
    throw IllegalArgumentException("DASH 索引中没有 SIDX")
}

private fun checkedDashAdd(left: Long, right: Long): Long {
    require(left >= 0 && right >= 0 && left <= Long.MAX_VALUE - right) { "SIDX 数值溢出" }
    return left + right
}

private class DashIndexReader(private val bytes: ByteArray) {
    var position = 0
    var limit = bytes.size

    fun uint32(): Long {
        require(position <= limit - 4) { "SIDX 数据不完整" }
        var value = 0L
        repeat(4) { value = (value shl 8) or (bytes[position++].toLong() and 0xffL) }
        return value
    }

    fun uint64(): Long {
        val high = uint32()
        val low = uint32()
        require(high <= 0x7fffffffL) { "SIDX 数值超过 Long 范围" }
        return (high shl 32) or low
    }
}
