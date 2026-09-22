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
        private var anchorTimeMs = enteredAtMs
        private var anchorX = 0.0
        private var speed = 0.0
        private var viewportWidth = 0f

        fun remainingMs(animationTimeMs: Long): Double =
            if (item.mode == BoloDanmakuMode.Scroll) {
                if (speed > 0.0) (scrollX(animationTimeMs.toDouble()) + width) / speed * 1_000.0 else Double.POSITIVE_INFINITY
            } else 4_000.0 - (animationTimeMs - enteredAtMs)

        fun expired(animationTimeMs: Long) = remainingMs(animationTimeMs) <= 0.0

        fun x(animationTimeMs: Long, viewportWidth: Float): Float = xAt(animationTimeMs.toDouble(), viewportWidth)

        fun xAt(animationTimeMs: Double, viewportWidth: Float): Float =
            if (item.mode == BoloDanmakuMode.Scroll) scrollX(animationTimeMs).toFloat()
            else (viewportWidth - width) / 2f

        private fun scrollX(animationTimeMs: Double): Double = anchorX - speed * ((animationTimeMs - anchorTimeMs) / 1_000.0)

        fun revealOnEntry() {
            if (item.mode == BoloDanmakuMode.Scroll) anchorX = (viewportWidth - width).coerceAtLeast(0f).toDouble()
        }

        fun updateMotion(animationTimeMs: Long, viewportWidth: Float, newSpeed: Float) {
            if (item.mode != BoloDanmakuMode.Scroll) return
            if (this.viewportWidth == viewportWidth && speed == newSpeed.toDouble()) return
            // 变速保留当前位置；尺寸变化保留相对容器的水平位置。
            anchorX = if (this.viewportWidth > 0f) {
                scrollX(animationTimeMs.toDouble()) * viewportWidth / this.viewportWidth
            } else viewportWidth.toDouble()
            anchorTimeMs = animationTimeMs
            this.viewportWidth = viewportWidth
            speed = newSpeed.toDouble()
        }
    }

    private var items = emptyList<BoloDanmakuItem>()
    val itemCount: Int get() = items.size
    private val active = ArrayList<Entry>(500)
    private val pending = linkedMapOf<Long, BoloDanmakuItem>()
    private val scheduledIds = mutableSetOf<Long>()
    private val immediateIds = mutableSetOf<Long>()
    private var cursor = 0
    private var admissionPositionMs = 0L
    private var layoutChanged = true
    private var width = 0f
    private var height = 0f
    private var displayAreaRatio = 1f
    private var topBottomScrollEnabled = false
    private var verticalGap = 0f
    private var horizontalGap = 0f
    private var appliedSpeedFactor = 1f

    fun load(newItems: List<BoloDanmakuItem>) {
        val byId = newItems.asSequence().filter { it.progressMs >= 0 && it.content.isNotBlank() }.associateBy { it.id }
        items = byId.values.sortedWith(compareBy<BoloDanmakuItem> { it.progressMs }.thenBy { it.id })
        pending.entries.removeAll { byId[it.key] != it.value }
        immediateIds.retainAll(pending.keys)
        // 已处理 ID 保留到显式 Seek，避免淘汰后重新加载导致重复入场。
        cursor = lowerBound(admissionPositionMs)
    }

    fun append(newItems: List<BoloDanmakuItem>) = load(items + newItems)

    fun showImmediately(item: BoloDanmakuItem) {
        require(item.progressMs >= 0L && item.content.isNotBlank())
        append(listOf(item))
        scheduledIds.add(item.id)
        if (active.none { it.item.id == item.id }) {
            pending[item.id] = item
            immediateIds.add(item.id)
        }
    }

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
        immediateIds.clear()
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

    fun resize(
        width: Float,
        height: Float,
        verticalGap: Float,
        horizontalGap: Float,
        displayAreaRatio: Float,
        topBottomScrollEnabled: Boolean,
    ) {
        if (
            this.width == width && this.height == height &&
            this.verticalGap == verticalGap && this.horizontalGap == horizontalGap &&
            this.displayAreaRatio == displayAreaRatio && this.topBottomScrollEnabled == topBottomScrollEnabled
        ) return
        this.width = width
        this.height = height
        this.displayAreaRatio = displayAreaRatio
        this.topBottomScrollEnabled = topBottomScrollEnabled
        this.verticalGap = verticalGap
        this.horizontalGap = horizontalGap
        requestLayout()
    }

    /** 只接受内部动画时间，绝不依据媒体位置重建在屏条目。 */
    fun frame(
        animationTimeMs: Long,
        fontScale: Float,
        speedFactor: Float,
        filterLevel: Int,
        scrollEnabled: Boolean,
        topEnabled: Boolean,
        bottomEnabled: Boolean,
        baseSpeed: (Float, Float) -> Float,
        measure: (BoloDanmakuItem) -> Pair<Float, Float>,
    ): List<Entry> {
        fun isModeEnabled(mode: BoloDanmakuMode): Boolean = when (mode) {
            BoloDanmakuMode.Scroll -> scrollEnabled
            BoloDanmakuMode.Top -> topEnabled
            BoloDanmakuMode.Bottom -> bottomEnabled
        }
        active.removeAll {
            it.expired(animationTimeMs) || (!it.item.isOwn && (it.item.weight < filterLevel || !isModeEnabled(it.item.mode)))
        }
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return emptyList()
        if (layoutChanged) {
            val survivors = active.sortedByDescending { it.item.isOwn }
            active.clear()
            for (entry in survivors) {
                val (textWidth, textHeight) = measure(entry.item)
                entry.width = textWidth
                entry.height = textHeight
                entry.updateMotion(animationTimeMs, width, baseSpeed(width, textWidth) * speedFactor)
                entry.y = findLane(entry, animationTimeMs) ?: continue
                active.add(entry)
            }
            layoutChanged = false
        } else if (appliedSpeedFactor != speedFactor) {
            for (entry in active) {
                entry.updateMotion(animationTimeMs, width, baseSpeed(width, entry.width) * speedFactor)
            }
        }
        appliedSpeedFactor = speedFactor
        active.removeAll { it.expired(animationTimeMs) }
        for (item in pending.values.sortedByDescending { it.isOwn }) {
            if (!item.isOwn && (item.weight < filterLevel || !isModeEnabled(item.mode))) continue
            if ((!item.isOwn && active.size >= 500) || active.any { it.item.id == item.id }) continue
            // 入场时固定显示字号；后续倍率变化不影响在屏条目，也不修改源数据。
            val displayItem = item.copy(
                fontSize = (item.fontSize.takeIf { it.isFinite() && it > 0f } ?: 25f) * fontScale,
            )
            val (textWidth, textHeight) = measure(displayItem)
            if (!textWidth.isFinite() || !textHeight.isFinite() || textWidth <= 0f || textHeight <= 0f || textHeight > height) continue
            if (textHeight > displayHeight(item.mode)) continue
            val entry = Entry(displayItem, animationTimeMs, textWidth, textHeight, 0f)
            val speed = baseSpeed(width, textWidth) * speedFactor
            if (item.mode == BoloDanmakuMode.Scroll && (!speed.isFinite() || speed <= 0f)) continue
            entry.updateMotion(animationTimeMs, width, speed)
            if (item.id in immediateIds) entry.revealOnEntry()
            if (item.isOwn && active.size >= 500) removeOldestEntry()
            // 单条无法入场不阻塞后续条目，较短弹幕仍可尝试利用剩余轨道。
            var lane = findLane(entry, animationTimeMs)
            while (lane == null && item.isOwn && removeOldestEntry(item.mode)) {
                lane = findLane(entry, animationTimeMs)
            }
            entry.y = lane ?: continue
            active.add(entry)
        }
        pending.clear()
        immediateIds.clear()
        return active
    }

    private fun removeOldestEntry(mode: BoloDanmakuMode? = null): Boolean {
        val candidates = active.filter {
            mode == null || (it.item.mode == BoloDanmakuMode.Scroll) == (mode == BoloDanmakuMode.Scroll)
        }
        val oldest = candidates.minWithOrNull(compareBy<Entry> { it.item.isOwn }.thenBy { it.enteredAtMs }) ?: return false
        return active.remove(oldest)
    }

    private fun findLane(candidate: Entry, timeMs: Long): Float? {
        if (candidate.width <= 0f || candidate.height <= 0f || candidate.height > height) return null
        val bottom = candidate.item.mode == BoloDanmakuMode.Bottom
        val areaHeight = displayHeight(candidate.item.mode)
        val firstLane = findLaneInArea(candidate, timeMs, areaHeight, bottom)
        if (firstLane != null) return firstLane
        if (candidate.item.mode == BoloDanmakuMode.Scroll && topBottomScrollEnabled && displayAreaRatio < 0.5f) {
            return findLaneInArea(candidate, timeMs, areaHeight, bottom = true)
        }
        return null
    }

    private fun displayHeight(mode: BoloDanmakuMode): Float =
        height * if (mode == BoloDanmakuMode.Scroll) displayAreaRatio else min(displayAreaRatio, 0.5f)

    private fun findLaneInArea(candidate: Entry, timeMs: Long, areaHeight: Float, bottom: Boolean): Float? {
        val minY = if (bottom) height - areaHeight else 0f
        val maxY = if (bottom) height else areaHeight
        val candidates = ArrayList<Float>(active.size + 1)
        candidates.add(if (bottom) height - candidate.height else 0f)
        for (other in active) {
            if ((candidate.item.mode == BoloDanmakuMode.Scroll) != (other.item.mode == BoloDanmakuMode.Scroll)) continue
            candidates.add(
                if (bottom) other.y - verticalGap - candidate.height
                else other.y + other.height + verticalGap,
            )
        }
        if (bottom) candidates.sortDescending() else candidates.sort()
        for (y in candidates) {
            if (y < minY || y + candidate.height > maxY) continue
            candidate.y = y
            if (active.none { overlaps(candidate, it, timeMs) }) return y
        }
        return null
    }

    private fun overlaps(candidate: Entry, other: Entry, timeMs: Long): Boolean {
        // 滚动与固定弹幕独立排道，允许它们经过同一显示区域。
        if ((candidate.item.mode == BoloDanmakuMode.Scroll) != (other.item.mode == BoloDanmakuMode.Scroll)) return false
        if (
            candidate.y >= other.y + other.height + verticalGap ||
            other.y >= candidate.y + candidate.height + verticalGap
        ) return false
        if (candidate.item.mode != BoloDanmakuMode.Scroll || other.item.mode != BoloDanmakuMode.Scroll) return true
        val remainingMs = min(
            candidate.remainingMs(timeMs),
            other.remainingMs(timeMs),
        )
        fun distance(t: Double) = candidate.xAt(t, width) - other.xAt(t, width) - other.width
        return distance(timeMs.toDouble()) < horizontalGap || distance(timeMs + remainingMs) < horizontalGap
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
