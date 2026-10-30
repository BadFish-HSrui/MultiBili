package tv.hsrui.bolo.storage.statistics

import androidx.room3.PooledConnection
import androidx.room3.executeSQL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.LocalDate
import tv.hsrui.bolo.storage.appData.liveStatistics.LiveStatisticsRecord

internal val liveStatisticsTables = listOf("live_stats", "live_daily_stats", "live_source_daily_stats")

internal suspend fun PooledConnection.restoreLiveStatistics(source: SQLiteConnection) {
    source.prepare("SELECT * FROM live_stats ORDER BY room_id").use { rows ->
        while (rows.step()) {
            currentCoroutineContext().ensureActive()
            val incoming = rows.readLiveRecord()
            val old = usePrepared("SELECT * FROM live_stats WHERE room_id = ?") {
                it.bindLong(1, incoming.roomId)
                if (it.step()) it.readLiveRecord() else null
            }
            val latest = if (old == null || incoming.lastViewedAtMs > old.lastViewedAtMs) incoming else old
            val fallback = if (latest === incoming) old else incoming
            val merged = latest.copy(shortRoomId = latest.shortRoomId ?: fallback?.shortRoomId,
                firstViewedAtMs = minOf(incoming.firstViewedAtMs, old?.firstViewedAtMs ?: incoming.firstViewedAtMs),
                lastViewedAtMs = maxOf(incoming.lastViewedAtMs, old?.lastViewedAtMs ?: incoming.lastViewedAtMs))
            usePrepared("INSERT INTO live_stats (room_id, short_room_id, up_mid, up_name, first_viewed_at_ms, last_viewed_at_ms) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(room_id) DO UPDATE SET short_room_id = excluded.short_room_id, " +
                "up_mid = excluded.up_mid, up_name = excluded.up_name, first_viewed_at_ms = excluded.first_viewed_at_ms, " +
                "last_viewed_at_ms = excluded.last_viewed_at_ms") {
                it.bindLong(1, merged.roomId)
                merged.shortRoomId?.let { value -> it.bindLong(2, value) } ?: it.bindNull(2)
                it.bindLong(3, merged.upMid)
                it.bindText(4, merged.upName)
                it.bindLong(5, merged.firstViewedAtMs)
                it.bindLong(6, merged.lastViewedAtMs)
                it.step()
            }
            source.prepare("SELECT s.source_key, d.local_date, d.total_played_ms, d.play_count " +
                "FROM live_source_daily_stats d JOIN playback_source s ON s.id = d.source_id WHERE d.room_id = ?").use { days ->
                days.bindLong(1, incoming.roomId)
                while (days.step()) {
                    currentCoroutineContext().ensureActive()
                    val sourceId = backupSourceId(days.getText(0))
                    usePrepared("INSERT INTO live_source_daily_stats (room_id, source_id, local_date, total_played_ms, play_count) " +
                        "VALUES (?, ?, ?, ?, ?) ON CONFLICT(room_id, source_id, local_date) DO UPDATE SET " +
                        "total_played_ms = MAX(total_played_ms, excluded.total_played_ms), play_count = MAX(play_count, excluded.play_count)") {
                        it.bindLong(1, incoming.roomId)
                        it.bindLong(2, sourceId)
                        it.bindText(3, days.getText(1))
                        it.bindLong(4, days.getLong(2))
                        it.bindLong(5, days.getLong(3))
                        it.step()
                    }
                }
            }
            executeSQL("DELETE FROM live_daily_stats WHERE room_id = ${incoming.roomId}")
            executeSQL("INSERT INTO live_daily_stats SELECT room_id, local_date, SUM(total_played_ms), SUM(play_count) " +
                "FROM live_source_daily_stats WHERE room_id = ${incoming.roomId} GROUP BY room_id, local_date")
            executeSQL("UPDATE live_stats SET (total_played_ms, play_count) = (SELECT COALESCE(SUM(total_played_ms), 0), " +
                "COALESCE(SUM(play_count), 0) FROM live_source_daily_stats WHERE room_id = ${incoming.roomId}) " +
                "WHERE room_id = ${incoming.roomId}")
        }
    }
    usePrepared("SELECT SUM(total_played_ms), SUM(play_count) FROM live_stats") { it.step() }
}

private fun SQLiteStatement.readLiveRecord() = LiveStatisticsRecord(
    roomId = getLong(0), shortRoomId = if (isNull(1)) null else getLong(1), upMid = getLong(2), upName = getText(3),
    totalPlayedMs = getLong(4), playCount = getLong(5), firstViewedAtMs = getLong(6), lastViewedAtMs = getLong(7),
)

internal suspend fun SQLiteConnection.validateLiveStatistics() {
    requireNoRows("SELECT 1 FROM live_stats WHERE room_id <= 0 OR short_room_id < 0 OR up_mid <= 0 " +
        "OR TRIM(up_name) = '' OR total_played_ms < 0 OR play_count < 0 LIMIT 1", "备份包含无效的直播记录")
    requireNoRows("SELECT 1 FROM live_daily_stats WHERE total_played_ms < 0 OR play_count < 0 LIMIT 1", "备份包含无效的直播每日统计")
    requireNoRows("SELECT 1 FROM live_source_daily_stats d JOIN playback_source s ON s.id = d.source_id " +
        "WHERE d.total_played_ms < 0 OR d.play_count < 0 OR s.source_key = '0' LIMIT 1", "备份包含无效的直播来源贡献")
    prepare("SELECT local_date FROM live_daily_stats UNION SELECT local_date FROM live_source_daily_stats").use { dates ->
        while (dates.step()) {
            currentCoroutineContext().ensureActive()
            val date = dates.getText(0)
            check(runCatching { LocalDate.parse(date).toString() == date }.getOrDefault(false)) { "备份包含无效日期" }
        }
    }
    requireNoRows("SELECT 1 FROM live_stats r WHERE (r.total_played_ms, r.play_count) <> " +
        "(SELECT COALESCE(SUM(total_played_ms), 0), COALESCE(SUM(play_count), 0) FROM live_source_daily_stats " +
        "WHERE room_id = r.room_id) LIMIT 1", "备份直播累计缓存与来源数据不一致")
    val daily = "SELECT room_id, local_date, SUM(total_played_ms) AS total_played_ms, SUM(play_count) AS play_count " +
        "FROM live_source_daily_stats GROUP BY room_id, local_date"
    requireNoRows("SELECT 1 FROM live_daily_stats c LEFT JOIN ($daily) d ON c.room_id = d.room_id AND c.local_date = d.local_date " +
        "WHERE d.room_id IS NULL OR c.total_played_ms <> d.total_played_ms OR c.play_count <> d.play_count LIMIT 1",
        "备份直播每日缓存与来源数据不一致")
    requireNoRows("SELECT 1 FROM ($daily) d LEFT JOIN live_daily_stats c ON c.room_id = d.room_id AND c.local_date = d.local_date " +
        "WHERE c.room_id IS NULL LIMIT 1", "备份缺少直播每日缓存")
    prepare("SELECT SUM(total_played_ms), SUM(play_count) FROM live_stats").use { it.step() }
}
