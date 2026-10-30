package tv.hsrui.bolo.storage.statistics

import androidx.room3.executeSQL
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.hsrui.bolo.storage.appData.liveStatistics.LiveStatisticsManager
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsManager

/** 数据库级操作共用写锁；恢复和清除期间不允许打开新的统计会话。 */
class StatisticsManager internal constructor(private val database: () -> StatisticsDatabase) {
    private val writeMutex = Mutex()
    private val operationMutex = Mutex()
    private var replacing = false
    val playback = PlaybackStatisticsManager(database, writeMutex = writeMutex, canOpen = { !replacing })
    val live = LiveStatisticsManager(database, writeMutex, canOpen = { !replacing })

    suspend fun flush() {
        playback.flush()
        live.flush()
    }

    suspend fun exportBackup(destination: String) = operationMutex.withLock {
        flush()
        writeMutex.withLock { database().exportBackup(destination) }
    }

    suspend fun validateBackup(path: String) = database().validateBackup(path)

    suspend fun restoreBackup(path: String) = replace { database().restoreBackup(path) }

    suspend fun clear() = replace {
        database().useWriterConnection { connection ->
            connection.immediateTransaction {
                val tables = listOf(
                    "live_source_daily_stats", "live_daily_stats", "live_stats",
                    "playback_watched_range", "playback_daily_stats", "playback_source_daily_stats",
                    "playback_legacy_totals", "playback_stats", "playback_source",
                )
                for (table in tables) executeSQL("DELETE FROM $table")
                currentCoroutineContext().ensureActive()
            }
        }
    }

    private suspend fun replace(block: suspend () -> Unit) = withContext(Dispatchers.Main.immediate) {
        operationMutex.withLock {
            check(!playback.hasActiveSession && !live.hasActiveSession) { "请先退出播放页面再恢复或清除统计数据" }
            replacing = true
            try {
                flush()
                writeMutex.withLock { block() }
            } finally {
                replacing = false
            }
        }
    }
}
