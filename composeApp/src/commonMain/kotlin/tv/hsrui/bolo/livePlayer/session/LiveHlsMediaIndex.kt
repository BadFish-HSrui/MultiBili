package tv.hsrui.bolo.livePlayer.session

import tv.hsrui.network.liveFeature.player.LiveHlsSegmentData
import kotlin.math.abs

internal data class LiveHlsMediaIndexData(
    val startMs: Long?,
    val durationMs: Long,
    val independent: Boolean,
    val programDateTimeMs: Long?,
    val configurationId: Long? = null,
    val dependenciesKnown: Boolean = true,
    val decodeStartMs: Long? = null,
    val decodeEndMs: Long? = null,
) {
    val endMs: Long? = startMs?.takeIf { it >= 0 && durationMs > 0 && it <= Long.MAX_VALUE - durationMs }?.plus(durationMs)
}

/** 只解析容器头和随机访问标志；不能核实的依赖不公布为定位起点。 */
internal class LiveHlsMediaIndex(private val format: String) {
    private class Box(val type: String, val start: Int, val end: Int)
    private var videoTrack = -1L
    private var timescale = 0L
    private var defaultFlags: Long? = null
    private var defaultDuration = 0L
    private var decodeStartMs: Long? = null
    private var decodeEndMs: Long? = null
    private var lastTs: Long? = null
    private var lastDate: Pair<Long, Long>? = null
    private var configurationId: Long? = null
    private var transportDependenciesKnown = false
    private var transportCodec: Int? = null

    fun discontinuity() {
        lastTs = null
        lastDate = null
        transportCodec = null
    }

    fun initialize(body: LivePlaybackCache.Body) {
        videoTrack = -1
        timescale = 0
        defaultFlags = null
        defaultDuration = 0
        val moov = boxes(body, 0, body.size).firstOrNull { it.type == "moov" } ?: return
        for (track in boxes(body, moov.start, moov.end).filter { it.type == "trak" }) {
            val children = boxes(body, track.start, track.end)
            val mdia = children.firstOrNull { it.type == "mdia" } ?: continue
            val media = boxes(body, mdia.start, mdia.end)
            val handler = media.firstOrNull { it.type == "hdlr" } ?: continue
            if (text(body, handler.start + 8, 4) != "vide") continue
            val tkhd = children.firstOrNull { it.type == "tkhd" } ?: continue
            val mdhd = media.firstOrNull { it.type == "mdhd" } ?: continue
            videoTrack = uint(body, tkhd.start + if (body.byteAt(tkhd.start) == 1) 20 else 12, 4)
            timescale = uint(body, mdhd.start + if (body.byteAt(mdhd.start) == 1) 20 else 12, 4)
        }
        val mvex = boxes(body, moov.start, moov.end).firstOrNull { it.type == "mvex" } ?: return
        for (trex in boxes(body, mvex.start, mvex.end).filter { it.type == "trex" }) {
            if (uint(body, trex.start + 4, 4) == videoTrack) {
                defaultDuration = uint(body, trex.start + 12, 4)
                defaultFlags = uint(body, trex.start + 20, 4)
            }
        }
    }

    fun inspect(body: LivePlaybackCache.Body, segment: LiveHlsSegmentData, independent: Boolean): LiveHlsMediaIndexData {
        configurationId = null
        transportDependenciesKnown = false
        decodeStartMs = null
        decodeEndMs = null
        val parsed = runCatching { if (format == "fmp4") fragmentedMp4(body) else transportStream(body)?.let { Triple(it.first, it.second, segment.durationMs) } }.getOrNull()
        val start = parsed?.first
        val labelMatches = segment.mediaStartTimeMs?.let { start != null && abs(it - start) <= 250 } ?: true
        val keyframe = parsed?.second == true && labelMatches && segment.startsWithKeyframe != false
        // 独立分片声明仍需要可解析的媒体时间，且不能覆盖明确的非随机访问标志。
        val canStart = start != null && labelMatches && (keyframe || independent && parsed.second != false)
        var date = segment.programDateTimeMs
        val previous = lastDate
        if (date != null && start != null) {
            lastDate = start to date
            if (previous == null || abs((date - previous.second) - (start - previous.first)) > 1_000) date = null
        }
        return LiveHlsMediaIndexData(
            start,
            parsed?.third ?: segment.durationMs,
            canStart,
            date,
            configurationId,
            canStart || labelMatches && if (format == "ts") transportDependenciesKnown else parsed?.second != null,
            decodeStartMs,
            decodeEndMs,
        )
    }

    private fun fragmentedMp4(body: LivePlaybackCache.Body): Triple<Long, Boolean?, Long>? {
        if (videoTrack < 0 || timescale <= 0) return null
        var first: Long? = null
        var firstDecode: Long? = null
        var endDecode: Long? = null
        var sync: Boolean? = null
        var seenSample = false
        for (moof in boxes(body, 0, body.size).filter { it.type == "moof" }) {
            for (traf in boxes(body, moof.start, moof.end).filter { it.type == "traf" }) {
                val children = boxes(body, traf.start, traf.end)
                val tfhd = children.firstOrNull { it.type == "tfhd" } ?: continue
                if (uint(body, tfhd.start + 4, 4) != videoTrack) continue
                val flags = uint(body, tfhd.start + 1, 3).toInt()
                var position = tfhd.start + 8
                if (flags and 1 != 0) position += 8
                if (flags and 2 != 0) position += 4
                val duration = if (flags and 8 != 0) uint(body, position, 4).also { position += 4 } else defaultDuration
                if (flags and 16 != 0) position += 4
                val defaults = if (flags and 32 != 0) uint(body, position, 4) else defaultFlags
                val tfdt = children.firstOrNull { it.type == "tfdt" } ?: continue
                var decode = uint(body, tfdt.start + 4, if (body.byteAt(tfdt.start) == 1) 8 else 4)
                for (trun in children.filter { it.type == "trun" }) {
                    val trunFlags = uint(body, trun.start + 1, 3).toInt()
                    val count = uint(body, trun.start + 4, 4)
                    check(count <= 1_000_000)
                    position = trun.start + 8
                    if (trunFlags and 1 != 0) position += 4
                    val firstFlags = if (trunFlags and 4 != 0) uint(body, position, 4).also { position += 4 } else defaults
                    repeat(count.toInt()) { sample ->
                        val length = if (trunFlags and 0x100 != 0) uint(body, position, 4).also { position += 4 } else duration
                        if (trunFlags and 0x200 != 0) position += 4
                        val sampleFlags = if (trunFlags and 0x400 != 0) uint(body, position, 4).also { position += 4 }
                            else if (sample == 0) firstFlags else defaults
                        val composition = if (trunFlags and 0x800 != 0) {
                            val value = uint(body, position, 4)
                            position += 4
                            if (body.byteAt(trun.start) == 1) value.toInt().toLong() else value
                        } else 0L
                        check(
                            position <= trun.end && length > 0 && decode >= 0 &&
                                decode <= Long.MAX_VALUE / 1000 - length - kotlin.math.abs(composition)
                        )
                        val pts = decode + composition
                        check(pts >= 0)
                        if (first == null) first = pts
                        firstDecode = minOf(firstDecode ?: decode, decode)
                        endDecode = maxOf(endDecode ?: 0, decode + length)
                        if (!seenSample) {
                            sync = sampleFlags?.let { it and 0x10000 == 0L && (it shr 24 and 3) != 1L }
                            seenSample = true
                        }
                        decode += length
                    }
                }
            }
        }
        val start = first ?: return null
        // B 帧的呈现时间可跨分片重排，连续性必须使用解码时间，不能把 PTS 重叠当成缺口。
        decodeStartMs = checkNotNull(firstDecode) * 1000 / timescale
        decodeEndMs = checkNotNull(endDecode) * 1000 / timescale
        return Triple(start * 1000 / timescale, sync, decodeEndMs!! - decodeStartMs!!)
    }

    private fun transportStream(body: LivePlaybackCache.Body): Pair<Long, Boolean?>? {
        var pmt = -1
        var video = -1
        var codec = -1
        var timestamp: Long? = null
        var patSeen = false
        var pmtSeen = false
        var parametersAtStart = false
        val counters = mutableMapOf<Int, Int>()
        var sps = false
        var pps = false
        var vps = false
        var firstVcl: Boolean? = null
        var zeros = 0
        var nalPending = false
        var parameterType: Int? = null
        var parameterHash = -3750763034362895579L
        val parameters = mutableMapOf<Int, Long>()
        var configuration: Long? = null
        fun finishParameter() {
            parameterType?.let { parameters[it] = parameterHash }
            parameterType = null
        }
        var packet = 0
        val packetBytes = ByteArray(188)
        fun byteAt(position: Int): Int = packetBytes[position - packet].toInt() and 255
        while (packet + 188 <= body.size) {
            body.copy(packet, packetBytes, 188)
            if (byteAt(packet) != 0x47) return null
            val b1 = byteAt(packet + 1)
            if (b1 and 0x80 != 0) return null
            val pid = (b1 and 31) * 256 + byteAt(packet + 2)
            val header = byteAt(packet + 3)
            if (header and 0xc0 != 0) return null
            val adaptation = header shr 4 and 3
            var start = packet + 4
            if (adaptation and 2 != 0) {
                val length = byteAt(start)
                start += length + 1
            }
            val end = packet + 188
            if (adaptation and 1 != 0 && start < end) {
                val counter = header and 15
                val previousCounter = counters.put(pid, counter)
                if (previousCounter != null && counter != (previousCounter + 1) % 16) return null
                val begins = b1 and 0x40 != 0
                if ((pid == 0 || pid == pmt) && begins) {
                    val section = start + 1 + byteAt(start)
                    if (section + 12 < end) {
                        val sectionEnd = minOf(end, section + 3 + (uint(body, section + 1, 2).toInt() and 0xfff) - 4)
                        if (pid == 0 && byteAt(section) == 0) {
                            patSeen = true
                            var item = section + 8
                            while (item + 4 <= sectionEnd) {
                                if (uint(body, item, 2) != 0L) pmt = uint(body, item + 2, 2).toInt() and 0x1fff
                                item += 4
                            }
                        } else if (pid == pmt && byteAt(section) == 2) {
                            pmtSeen = true
                            var item = section + 12 + (uint(body, section + 10, 2).toInt() and 0xfff)
                            while (item + 5 <= sectionEnd) {
                                val type = byteAt(item)
                                if (type == 0x1b || type == 0x24) {
                                    codec = type
                                    video = uint(body, item + 1, 2).toInt() and 0x1fff
                                }
                                item += 5 + (uint(body, item + 3, 2).toInt() and 0xfff)
                            }
                        }
                    }
                }
                if (pid == video) {
                    if (begins && start + 14 <= end && uint(body, start, 3) == 1L) {
                        if (byteAt(start + 7) and 0x80 != 0 && timestamp == null) {
                            val p = start + 9
                            val pts = ((byteAt(p).toLong() shr 1 and 7) shl 30) or
                                (byteAt(p + 1).toLong() shl 22) or ((byteAt(p + 2).toLong() shr 1) shl 15) or
                                (byteAt(p + 3).toLong() shl 7) or (byteAt(p + 4).toLong() shr 1)
                            timestamp = unwrap(pts)
                        }
                        start += 9 + byteAt(start + 8)
                    }
                    for (i in start until end) {
                        val value = byteAt(i)
                        if (nalPending) {
                            val nal = if (codec == 0x24) value shr 1 and 63 else value and 31
                            if (codec == 0x24) {
                                if (nal == 32) vps = true
                                if (nal == 33) sps = true
                                if (nal == 34) pps = true
                                if (nal <= 31 && firstVcl == null) {
                                    firstVcl = nal in 16..20
                                    parametersAtStart = vps && sps && pps
                                }
                            } else {
                                if (nal == 7) sps = true
                                if (nal == 8) pps = true
                                if (nal in 1..5 && firstVcl == null) {
                                    firstVcl = nal == 5
                                    parametersAtStart = sps && pps
                                }
                            }
                            parameterType = nal.takeIf { if (codec == 0x24) it in 32..34 else it == 7 || it == 8 }
                            parameterHash = -3750763034362895579L
                            if (firstVcl != null && configuration == null && parametersAtStart) {
                                configuration = parameters.entries.sortedBy { it.key }.fold(codec.toLong()) { hash, entry ->
                                    ((hash xor entry.key.toLong()) * 1099511628211L xor entry.value) * 1099511628211L
                                }
                            }
                            nalPending = false
                        }
                        if (value == 1 && zeros >= 2) {
                            finishParameter()
                            nalPending = true
                            zeros = 0
                        } else if (value == 0) {
                            zeros++
                        } else {
                            if (parameterType != null) {
                                repeat(zeros) { parameterHash *= 1099511628211L }
                                parameterHash = (parameterHash xor value.toLong()) * 1099511628211L
                            }
                            zeros = 0
                        }
                    }
                }
            }
            packet += 188
        }
        configurationId = configuration
        transportDependenciesKnown = patSeen && pmtSeen && firstVcl != null &&
            (transportCodec == null || transportCodec == codec || configuration != null)
        transportCodec = codec.takeIf { it >= 0 }
        return timestamp?.let { it / 90 to (patSeen && pmtSeen && parametersAtStart && firstVcl == true) }
    }

    private fun unwrap(value: Long): Long {
        val previous = lastTs
        val wrap = 1L shl 33
        var result = value
        if (previous != null) {
            result += ((previous - value + wrap / 2) / wrap) * wrap
            if (result - previous > wrap / 2) result -= wrap
            if (previous - result > wrap / 2) result += wrap
        }
        lastTs = result
        return result
    }

    private fun boxes(body: LivePlaybackCache.Body, from: Int, to: Int): List<Box> {
        val result = mutableListOf<Box>()
        var position = from
        while (position + 8 <= to) {
            val size32 = uint(body, position, 4)
            val header = if (size32 == 1L) 16 else 8
            val size = when (size32) { 0L -> (to - position).toLong(); 1L -> uint(body, position + 8, 8); else -> size32 }
            check(size >= header && size <= to - position)
            result += Box(text(body, position + 4, 4), position + header, position + size.toInt())
            position += size.toInt()
        }
        return result
    }
    private fun uint(body: LivePlaybackCache.Body, offset: Int, length: Int): Long {
        check(offset >= 0 && offset + length <= body.size)
        var value = 0L
        repeat(length) { value = (value shl 8) or body.byteAt(offset + it).toLong() }
        check(value >= 0)
        return value
    }
    private fun text(body: LivePlaybackCache.Body, offset: Int, length: Int): String =
        (0 until length).map { body.byteAt(offset + it).toChar() }.joinToString("")
}
