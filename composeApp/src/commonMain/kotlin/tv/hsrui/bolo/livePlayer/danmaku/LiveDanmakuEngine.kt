package tv.hsrui.bolo.livePlayer.danmaku

import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuData
import tv.hsrui.network.liveFeature.danmaku.LiveDanmakuMode
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min

internal class LiveDanmakuEngine {
    class Entry(val key: Long, val data: LiveDanmakuData, val born: Double) {
        var width = 0f
        var height = 0f
        var x = 0f
        var y = 0f
        var speed = 0f
        var expires = born + 4_000.0
    }

    private val pending = ArrayDeque<LiveDanmakuData>()
    private val active = mutableListOf<Entry>()
    private var sequence = 0L
    private var lastTime = 0.0
    private var previousWidth = 0f
    private var previousHeight = 0f
    private var previousScale = 0f
    private var previousArea = 0f
    private var previousDensity = 0f
    private var previousSpeed = 0f
    val pendingCount: Int get() = pending.size
    val activeCount: Int get() = active.size
    var admittedCount = 0L
        private set

    fun enqueue(data: LiveDanmakuData) {
        if (data.mode == null) return
        if (pending.size == 256) pending.removeFirst()
        pending.addLast(data)
    }

    fun clear() {
        pending.clear()
        active.clear()
    }

    fun filter(allows: (LiveDanmakuMode?) -> Boolean) {
        pending.removeAll { !allows(it.mode) }
        active.removeAll { !allows(it.data.mode) }
    }

    fun frame(
        now: Double,
        width: Float,
        height: Float,
        density: Float,
        scale: Float,
        area: Float,
        speedFactor: Float,
        allows: (LiveDanmakuMode?) -> Boolean,
        measure: (LiveDanmakuData) -> Pair<Float, Float>,
    ): List<Entry> {
        filter(allows)
        if (width <= 0f || height <= 0f) {
            clear()
            lastTime = now
            return emptyList()
        }
        val elapsed = (now - lastTime).coerceAtLeast(0.0)
        val reflow = width != previousWidth || height != previousHeight || scale != previousScale ||
            area != previousArea || density != previousDensity
        val survivors = active.toList()
        active.clear()
        for (entry in survivors) {
            if (entry.data.mode == LiveDanmakuMode.Scroll) entry.x -= (elapsed * entry.speed / 1_000).toFloat()
            if (now >= entry.expires || entry.data.mode == LiveDanmakuMode.Scroll && entry.x + entry.width <= 0f) continue
            if (reflow) {
                val remaining = entry.expires - now
                val progress = ((entry.x + entry.width) / (previousWidth + entry.width)).coerceIn(0f, 1f)
                val measured = measure(entry.data)
                entry.width = measured.first
                entry.height = measured.second
                entry.x = if (entry.data.mode == LiveDanmakuMode.Scroll) progress * (width + entry.width) - entry.width
                    else (width - entry.width) / 2
                if (entry.data.mode == LiveDanmakuMode.Scroll) entry.speed = ((entry.x + entry.width) * 1_000 / remaining).toFloat()
                if (!place(entry, height, area, density * 4)) continue
            } else if (entry.data.mode == LiveDanmakuMode.Scroll && speedFactor != previousSpeed) {
                val nextSpeed = scrollSpeed(width / density, entry.width / density) * density * speedFactor
                if (entry.speed != nextSpeed) {
                    entry.speed = nextSpeed
                    entry.expires = now + (entry.x + entry.width) / nextSpeed * 1_000
                }
            }
            active.add(entry)
        }
        while (pending.isNotEmpty()) {
            val data = pending.removeFirst()
            if (active.size >= 500) continue
            val entry = Entry(++sequence, data, now)
            val measured = measure(data)
            entry.width = measured.first
            entry.height = measured.second
            entry.x = if (data.mode == LiveDanmakuMode.Scroll) width else (width - entry.width) / 2
            if (data.mode == LiveDanmakuMode.Scroll) {
                entry.speed = scrollSpeed(width / density, entry.width / density) * density * speedFactor
                entry.expires = now + (width + entry.width) / entry.speed * 1_000
            }
            if (place(entry, height, area, density * 4)) {
                active.add(entry)
                admittedCount++
            }
        }
        lastTime = now
        previousWidth = width
        previousHeight = height
        previousScale = scale
        previousArea = area
        previousDensity = density
        previousSpeed = speedFactor
        return active.toList()
    }

    private fun place(entry: Entry, height: Float, area: Float, gap: Float): Boolean {
        if (!entry.width.isFinite() || !entry.height.isFinite() || entry.width <= 0 || entry.height <= 0) return false
        val scrolling = entry.data.mode == LiveDanmakuMode.Scroll
        val bottom = entry.data.mode == LiveDanmakuMode.Bottom
        val areaHeight = height * if (scrolling) area else min(area, .5f)
        val lower = if (bottom) height - areaHeight else 0f
        val upper = if (bottom) height else areaHeight
        val peers = active.filter { (it.data.mode == LiveDanmakuMode.Scroll) == scrolling }
        val lanes = mutableListOf(if (bottom) height - entry.height else 0f)
        peers.forEach { lanes.add(if (bottom) it.y - entry.height else it.y + it.height) }
        if (bottom) lanes.sortDescending() else lanes.sort()
        for (y in lanes) {
            if (y < lower || y + entry.height > upper) continue
            val collision = peers.any { other ->
                if (y >= other.y + other.height || y + entry.height <= other.y) false
                else if (!scrolling) true
                else {
                    val left = if (entry.x < other.x) entry else other
                    val right = if (left === entry) other else entry
                    val clearance = right.x - left.x - left.width - gap
                    val lifetime = min((left.x + left.width) / left.speed, (right.x + right.width) / right.speed)
                    clearance < 0 || right.speed > left.speed && clearance < (right.speed - left.speed) * lifetime
                }
            }
            if (!collision) { entry.y = y; return true }
        }
        return false
    }

    private fun scrollSpeed(width: Float, textWidth: Float): Float {
        val seconds = if (width < 558f) 4.5 else {
            val bounds = when {
                width <= 888 -> 7.0 to 13.0
                width <= 1082 -> 8.0 to 14.0
                width <= 1246 -> 9.0 to 14.0
                width <= 1452 -> 10.0 to 15.0
                width <= 1744 -> 11.0 to 16.0
                width <= 2314 -> 13.0 to 18.0
                width <= 2560 -> 14.0 to 20.0
                else -> floor((width - 2560) / 500.0).let { 16 + it to 22 + it }
            }
            val spread = bounds.second - bounds.first
            val center = ln(spread / .1 - 1) / .2 + .2
            bounds.first + spread / (1 + exp(-.2 * (textWidth / width - center)))
        }
        return ((width + textWidth) / seconds).toFloat()
    }
}
