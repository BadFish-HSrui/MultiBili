package tv.hsrui.bolo.player.subtitle

import tv.hsrui.network.feature.subtitle.SubtitleCue

class BoloSubtitleEngine {
    private var cues = emptyList<SubtitleCue>()
    private var latestEnds = emptyList<Double>()

    fun load(items: List<SubtitleCue>) {
        cues = items.filter {
            it.startSeconds.isFinite() && it.endSeconds.isFinite() &&
                it.startSeconds >= 0 && it.endSeconds > it.startSeconds && it.content.isNotBlank()
        }.sortedBy { it.startSeconds }
        var latestEnd = 0.0
        latestEnds = cues.map {
            latestEnd = maxOf(latestEnd, it.endSeconds)
            latestEnd
        }
    }

    fun textAt(positionMs: Long): String {
        val seconds = positionMs.coerceAtLeast(0L) / 1000.0
        var low = 0
        var high = cues.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (cues[middle].startSeconds <= seconds) low = middle + 1 else high = middle
        }
        val active = mutableListOf<String>()
        var index = low - 1
        while (index >= 0 && latestEnds[index] > seconds) {
            if (cues[index].endSeconds > seconds) active.add(cues[index].content)
            index--
        }
        return active.asReversed().joinToString("\n")
    }
}
