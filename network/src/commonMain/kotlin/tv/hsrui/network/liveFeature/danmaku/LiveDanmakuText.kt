package tv.hsrui.network.liveFeature.danmaku

/** 按 UTF-16 单元计数：可打印 ASCII 占 1，其余占 2，不改变原文。 */
fun String.liveDanmakuLength(): Int = sumOf { if (it in '\u0020'..'\u007e') 1 else 2 }
