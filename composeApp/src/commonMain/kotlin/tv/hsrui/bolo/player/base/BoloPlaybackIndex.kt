package tv.hsrui.bolo.player.base

import tv.hsrui.network.feature.player.PlayerStreamIndexData

/** 播放时间轴上的半开区间，单位为毫秒。 */
data class BoloPlaybackTimeRange(val startMs: Long, val endMs: Long)

internal data class BoloPlaybackSegment(
    val startByte: Long,
    val endByte: Long,
    val startMs: Long,
    val endMs: Long,
)

internal class BoloPlaybackIndex private constructor(val segments: List<BoloPlaybackSegment>) {
    fun bufferedRanges(cached: List<BoloPlaybackByteRange>): List<BoloPlaybackTimeRange> {
        val result = mutableListOf<BoloPlaybackTimeRange>()
        var cursor = 0
        for (segment in segments) {
            while (cursor < cached.size && cached[cursor].endExclusive <= segment.startByte) cursor++
            val range = cached.getOrNull(cursor) ?: break
            if (range.start <= segment.startByte && range.endExclusive >= segment.endByte) {
                val previous = result.lastOrNull()
                if (previous != null && previous.endMs == segment.startMs) {
                    result[result.lastIndex] = previous.copy(endMs = segment.endMs)
                } else if (segment.endMs > segment.startMs) {
                    result.add(BoloPlaybackTimeRange(segment.startMs, segment.endMs))
                }
            }
        }
        return result
    }

    companion object {
        const val ScanLimit = 4L * 1024 * 1024
        const val ReadLimit = 1024 * 1024

        fun intersect(
            video: List<BoloPlaybackTimeRange>,
            audio: List<BoloPlaybackTimeRange>?,
            durationMs: Long,
        ): List<BoloPlaybackTimeRange> {
            if (durationMs <= 0) return emptyList()
            val result = mutableListOf<BoloPlaybackTimeRange>()
            fun add(start: Long, end: Long) {
                val first = start.coerceIn(0, durationMs)
                val last = end.coerceIn(0, durationMs)
                if (last <= first) return
                val previous = result.lastOrNull()
                if (previous != null && first <= previous.endMs) {
                    result[result.lastIndex] = previous.copy(endMs = maxOf(last, previous.endMs))
                } else result.add(BoloPlaybackTimeRange(first, last))
            }
            if (audio == null) video.forEach { add(it.startMs, it.endMs) }
            else {
                var v = 0
                var a = 0
                while (v < video.size && a < audio.size) {
                    val left = video[v]
                    val right = audio[a]
                    add(maxOf(left.startMs, right.startMs), minOf(left.endMs, right.endMs))
                    if (left.endMs <= right.endMs) v++ else a++
                }
            }
            return result
        }

        suspend fun parse(
            kind: BoloPlaybackTrack,
            hints: PlayerStreamIndexData?,
            totalBytes: suspend () -> Long?,
            read: suspend (Long, Int) -> ByteArray?,
        ): BoloPlaybackIndex? {
            suspend fun header(at: Long, limit: Long): Atom? {
                if (limit - at < 8) return null
                val bytes = read(at, 8) ?: return null
                val first = Cursor(bytes)
                var size = first.u32()
                val type = first.type()
                val length = if (size == 1L) 16 else 8
                if (size == 1L) {
                    if (limit - at < 16) return null
                    size = Cursor(read(add(at, 8), 8) ?: return null).u64()
                }
                if (size == 0L) size = (totalBytes() ?: return null) - at
                require(size >= length)
                val end = add(at, size)
                require(totalBytes()?.let { end <= it } != false)
                return Atom(type, at, end, length)
            }
            suspend fun contents(atom: Atom): ByteArray? {
                val length = atom.end - atom.start
                if (length > ScanLimit) return null
                if (length <= ReadLimit) return read(atom.start, length.toInt())
                val bytes = ByteArray(length.toInt())
                var offset = 0
                while (offset < bytes.size) {
                    val count = minOf(ReadLimit, bytes.size - offset)
                    (read(atom.start + offset, count) ?: return null).copyInto(bytes, offset)
                    offset += count
                }
                return bytes
            }

            var movie: ByteArray? = null
            var index: ByteArray? = null
            var indexStart = 0L
            // 从文件头按 box 边界确认首个索引；后续 SIDX 不能作为播放器零点。
            val indexHint = hints?.index
            val scanEnd = indexHint?.endExclusive ?: ScanLimit
            var position = 0L
            var boxes = 0
            while ((movie == null || index == null) && position < scanEnd && boxes++ < 4096) {
                if (scanEnd - position < 8) return null
                val atom = header(position, scanEnd) ?: return null
                if (atom.type == "moof" || atom.type == "mdat") return null
                if (atom.type == "moov") {
                    if (atom.end > ScanLimit && hints?.initialization?.let {
                            atom.start >= it.start && atom.end <= it.endExclusive
                        } != true) return null
                    movie = contents(atom)
                }
                if (atom.type == "sidx" && index == null) {
                    if (atom.end > scanEnd || indexHint?.let { atom.start != it.start } == true) return null
                    index = contents(atom)
                    indexStart = atom.start
                }
                position = atom.end
            }
            val initialization = movie ?: return null
            val data = index ?: return null
            val movieBox = atoms(initialization, 0, initialization.size).single { it.type == "moov" }
            val movieChildren = atoms(initialization, movieBox.body, movieBox.end.toInt())
            val mvhd = movieChildren.single { it.type == "mvhd" }
            val movieClock = clock(initialization, mvhd)
            val tracks = movieChildren.filter { it.type == "trak" }.map { track ->
                val children = atoms(initialization, track.body, track.end.toInt())
                val trackHeader = children.single { it.type == "tkhd" }
                val tkhd = Cursor(initialization, trackHeader.body, trackHeader.end.toInt())
                val version = tkhd.version()
                tkhd.skip(if (version == 1) 16 else 8)
                val id = tkhd.u32()
                val media = children.single { it.type == "mdia" }
                val fields = atoms(initialization, media.body, media.end.toInt())
                val handlerBox = fields.single { it.type == "hdlr" }
                val handler = Cursor(initialization, handlerBox.body, handlerBox.end.toInt())
                handler.skip(8)
                val type = handler.type()
                val mediaClock = clock(initialization, fields.single { it.type == "mdhd" })
                var trim = 0L
                var empty = 0L
                val editBoxes = children.filter { it.type == "edts" }
                require(editBoxes.size <= 1)
                editBoxes.singleOrNull()?.let { edits ->
                    val lists = atoms(initialization, edits.body, edits.end.toInt()).filter { it.type == "elst" }
                    require(lists.size <= 1)
                    val list = lists.singleOrNull()
                    if (list != null) {
                        val cursor = Cursor(initialization, list.body, list.end.toInt())
                        val editVersion = cursor.version()
                        val count = cursor.u32()
                        require(count in 1..2)
                        repeat(count.toInt()) { entry ->
                            val duration = if (editVersion == 1) cursor.u64() else cursor.u32()
                            val time = if (editVersion == 1) cursor.i64() else cursor.i32()
                            require(cursor.u32() == 0x00010000L)
                            if (time == -1L && entry == 0 && count == 2L) empty = duration
                            else {
                                require(time >= 0 && (entry == 0 && count == 1L || entry == 1 && count == 2L))
                                trim = time
                            }
                        }
                        require(cursor.remaining == 0)
                    }
                }
                TrackInfo(id, type, mediaClock, trim, empty, movieClock)
            }
            val selected = tracks.single { it.type == if (kind == BoloPlaybackTrack.Video) "vide" else "soun" }
            require(tracks.count { it.type == "vide" || it.type == "soun" } == 1)
            val sidx = atoms(data, 0, data.size).single { it.type == "sidx" }
            val cursor = Cursor(data, sidx.body, sidx.end.toInt())
            val version = cursor.version()
            require(cursor.u32() == selected.id)
            val timescale = cursor.u32()
            require(timescale > 0)
            val earliest = if (version == 1) cursor.u64() else cursor.u32()
            val firstOffset = if (version == 1) cursor.u64() else cursor.u32()
            // 常规 edit list 只平移时间轴；编码延迟可产生负起点。
            // 与 mpv 独立流的起始时间归零保持一致，固定偏移在减去首个展示时间时抵消。
            val trim = rescale(selected.trim, selected.timescale, 1_000_000)
            val empty = rescale(selected.empty, selected.movieClock, 1_000_000)
            fun presentationTime(ticks: Long): Long {
                val relative = rescale(ticks, timescale, 1_000_000) - trim
                return if (relative < 0) relative + empty else add(relative, empty)
            }
            val origin = presentationTime(earliest)
            fun playbackTime(ticks: Long): Long {
                val time = presentationTime(ticks)
                require(time >= origin && (origin >= 0 || time <= Long.MAX_VALUE + origin))
                return (time - origin) / 1_000
            }
            cursor.skip(2)
            val count = cursor.u16()
            require(count > 0 && cursor.remaining == count * 12)
            val indexEnd = add(indexStart, sidx.end)
            var offset = add(indexEnd, firstOffset)
            // first_offset 可跨过其他 box，但不能跳过未纳入索引的首批媒体。
            var gap = indexEnd
            var skipped = 0
            while (gap < offset) {
                if (skipped++ >= 4096) return null
                val atom = header(gap, offset) ?: return null
                if (atom.end > offset || atom.type == "moof" || atom.type == "mdat") return null
                gap = atom.end
            }
            var time = earliest
            val segments = ArrayList<BoloPlaybackSegment>(count)
            repeat(count) {
                val size = cursor.u32()
                val duration = cursor.u32()
                cursor.u32() // SAP 信息不参与完整片段缓存覆盖判断。
                require(size in 1..0x7fffffffL && duration > 0)
                val end = add(offset, size)
                val nextTime = add(time, duration)
                require(totalBytes()?.let { end <= it } != false)
                segments.add(BoloPlaybackSegment(offset, end,
                    playbackTime(time), playbackTime(nextTime)))
                offset = end
                time = nextTime
            }
            return BoloPlaybackIndex(segments)
        }

        private fun clock(bytes: ByteArray, atom: Atom): Long {
            val cursor = Cursor(bytes, atom.body, atom.end.toInt())
            val version = cursor.version()
            cursor.skip(if (version == 1) 16 else 8)
            return cursor.u32().also { require(it > 0) }
        }

        private fun atoms(bytes: ByteArray, start: Int, end: Int): List<Atom> {
            val result = mutableListOf<Atom>()
            var position = start
            while (position < end) {
                val cursor = Cursor(bytes, position, end)
                var size = cursor.u32()
                val type = cursor.type()
                val header = if (size == 1L) 16 else 8
                if (size == 1L) size = cursor.u64()
                if (size == 0L) size = (end - position).toLong()
                require(size >= header && size <= end - position)
                val last = position + size.toInt()
                result.add(Atom(type, position.toLong(), last.toLong(), header))
                position = last
            }
            return result
        }

        private fun add(left: Long, right: Long): Long {
            require(left >= 0 && right >= 0 && left <= Long.MAX_VALUE - right)
            return left + right
        }

        private fun rescale(value: Long, scale: Long, unit: Long): Long {
            require(value >= 0 && scale > 0 && value / scale <= Long.MAX_VALUE / unit)
            return add(value / scale * unit, value % scale * unit / scale)
        }
    }

    private class Atom(val type: String, val start: Long, val end: Long, val header: Int) {
        val body: Int get() = start.toInt() + header
    }

    private class TrackInfo(
        val id: Long, val type: String, val timescale: Long, val trim: Long, val empty: Long, val movieClock: Long,
    )

    private class Cursor(private val bytes: ByteArray, private var position: Int = 0, private val end: Int = bytes.size) {
        val remaining: Int get() = end - position
        fun skip(count: Int) { require(count >= 0 && count <= remaining); position += count }
        fun u16(): Int { require(remaining >= 2); return (byte() shl 8) or byte() }
        fun u32(): Long { require(remaining >= 4); return (u16().toLong() shl 16) or u16().toLong() }
        fun u64(): Long {
            val high = u32()
            require(high <= Int.MAX_VALUE)
            return (high shl 32) or u32()
        }
        fun i32(): Long = u32().toInt().toLong()
        fun i64(): Long = (u32() shl 32) or u32()
        fun type(): String { require(remaining >= 4); return buildString { repeat(4) { append(byte().toChar()) } } }
        fun version(): Int {
            val value = u32()
            val version = (value ushr 24).toInt()
            require(version in 0..1)
            return version
        }
        private fun byte(): Int = bytes[position++].toInt() and 0xff
    }
}
