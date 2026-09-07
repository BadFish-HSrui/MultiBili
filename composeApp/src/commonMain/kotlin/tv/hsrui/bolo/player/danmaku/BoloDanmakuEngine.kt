package tv.hsrui.bolo.player.danmaku

import kotlin.math.min

internal class BoloDanmakuEngine {
    internal class Entry(
        val item: BoloDanmakuItem,
        val enteredAtMs: Long,
        var width: Float,
        var height: Float,
        var y: Float,
    ) {
        val durationMs = if (item.mode == BoloDanmakuMode.Scroll) 8_000L else 4_000L
        fun expired(animationTimeMs: Long) = animationTimeMs - enteredAtMs >= durationMs
        fun x(animationTimeMs: Long, viewportWidth: Float): Float =
            if (item.mode == BoloDanmakuMode.Scroll) {
                viewportWidth - (viewportWidth + width) * ((animationTimeMs - enteredAtMs).toDouble() / durationMs).toFloat()
            } else (viewportWidth - width) / 2f
    }

    private var items = emptyList<BoloDanmakuItem>()
    val itemCount: Int get() = items.size
    private val active = ArrayList<Entry>(120)
    internal val activeEntries: List<Entry> get() = active
    private val pending = linkedMapOf<Long, BoloDanmakuItem>()
    private val scheduledIds = mutableSetOf<Long>()
    private var cursor = 0
    private var admissionPositionMs = 0L
    private var layoutChanged = true
    private var width = 0f
    private var height = 0f
    private var gap = 0f

    fun load(newItems: List<BoloDanmakuItem>) {
        val byId = newItems.asSequence().filter { it.progressMs >= 0 && it.content.isNotBlank() }.associateBy { it.id }
        items = byId.values.sortedWith(compareBy<BoloDanmakuItem> { it.progressMs }.thenBy { it.id })
        pending.entries.removeAll { byId[it.key] != it.value }
        // 已处理 ID 保留到显式 Seek，避免淘汰后重新加载导致重复入场。
        cursor = lowerBound(admissionPositionMs)
    }

    fun append(newItems: List<BoloDanmakuItem>) = load(items + newItems)

    fun advance(positionMs: Long) {
        // 普通回退不回拨已处理游标，只有显式 Seek 可以开始新的调度周期。
        if (positionMs < admissionPositionMs) return
        admissionPositionMs = positionMs
        while (cursor < items.size && items[cursor].progressMs <= positionMs) {
            val item = items[cursor++]
            if (scheduledIds.add(item.id)) pending[item.id] = item
        }
    }

    fun seek(positionMs: Long) {
        active.clear()
        pending.clear()
        scheduledIds.clear()
        admissionPositionMs = positionMs
        cursor = lowerBound(positionMs)
    }

    fun clear() {
        items = emptyList()
        seek(0)
    }

    fun requestLayout() {
        layoutChanged = true
    }

    fun resize(width: Float, height: Float, gap: Float) {
        if (this.width == width && this.height == height && this.gap == gap) return
        this.width = width
        this.height = height
        this.gap = gap
        requestLayout()
    }

    /** 只接受内部动画时间，绝不依据媒体位置重建在屏条目。 */
    fun frame(animationTimeMs: Long, measure: (BoloDanmakuItem) -> Pair<Float, Float>): List<Entry> {
        active.removeAll { it.expired(animationTimeMs) }
        if (width <= 0f || height <= 0f) return emptyList()
        if (layoutChanged) {
            val survivors = active.toList()
            active.clear()
            for (entry in survivors) {
                val (textWidth, textHeight) = measure(entry.item)
                entry.width = textWidth
                entry.height = textHeight
                entry.y = findLane(entry, animationTimeMs) ?: continue
                active.add(entry)
            }
            layoutChanged = false
        }
        for (item in pending.values) {
            if (active.size >= 120 || active.any { it.item.id == item.id }) continue
            val (textWidth, textHeight) = measure(item)
            if (textWidth <= 0f || textHeight <= 0f || textHeight > height) continue
            val entry = Entry(item, animationTimeMs, textWidth, textHeight, 0f)
            entry.y = findLane(entry, animationTimeMs) ?: continue
            active.add(entry)
        }
        pending.clear()
        return active
    }

    private fun findLane(candidate: Entry, timeMs: Long): Float? {
        if (candidate.width <= 0f || candidate.height <= 0f || candidate.height > height) return null
        val bottom = candidate.item.mode == BoloDanmakuMode.Bottom
        val candidates = ArrayList<Float>(active.size + 1)
        candidates.add(if (bottom) height - candidate.height else 0f)
        for (other in active) {
            candidates.add(if (bottom) other.y - gap - candidate.height else other.y + other.height + gap)
        }
        if (bottom) candidates.sortDescending() else candidates.sort()
        for (y in candidates) {
            if (y < 0f || y + candidate.height > height) continue
            candidate.y = y
            if (active.none { overlaps(candidate, it, timeMs) }) return y
        }
        return null
    }

    private fun overlaps(candidate: Entry, other: Entry, timeMs: Long): Boolean {
        if (candidate.y >= other.y + other.height + gap || other.y >= candidate.y + candidate.height + gap) return false
        if (candidate.item.mode != BoloDanmakuMode.Scroll || other.item.mode != BoloDanmakuMode.Scroll) return true
        val remainingMs = min(
            candidate.durationMs - (timeMs - candidate.enteredAtMs),
            other.durationMs - (timeMs - other.enteredAtMs),
        )
        fun distance(t: Long) = candidate.x(t, width) - other.x(t, width) - other.width
        return distance(timeMs) < gap || distance(timeMs + remainingMs) < gap
    }

    private fun lowerBound(positionMs: Long): Int {
        var low = 0
        var high = items.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (items[middle].progressMs < positionMs) low = middle + 1 else high = middle
        }
        return low
    }
}
