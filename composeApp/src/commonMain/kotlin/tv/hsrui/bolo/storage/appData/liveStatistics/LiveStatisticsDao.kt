package tv.hsrui.bolo.storage.appData.liveStatistics

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.hsrui.bolo.storage.appData.playbackStatistics.addPlaybackStatistics

@Dao
internal abstract class LiveStatisticsDao {
    @Query("SELECT * FROM live_stats WHERE room_id = :roomId")
    abstract suspend fun get(roomId: Long): LiveStatisticsRecord?

    @Query("""
        SELECT COALESCE(SUM(total_played_ms), 0) AS total_played_ms,
               COALESCE(SUM(play_count), 0) AS play_count,
               COUNT(*) AS room_count, COUNT(DISTINCT up_mid) AS up_count
        FROM live_stats WHERE total_played_ms > 0 OR play_count > 0
    """)
    abstract suspend fun summary(): LiveStatisticsSummary

    @Query("""
        SELECT d.local_date, SUM(d.total_played_ms) AS total_played_ms, SUM(d.play_count) AS play_count
        FROM live_daily_stats d JOIN live_stats r ON r.room_id = d.room_id
        WHERE d.local_date >= :fromDate AND d.local_date < :untilDateExclusive
          AND (:roomId IS NULL OR r.room_id = :roomId) AND (:upMid IS NULL OR r.up_mid = :upMid)
        GROUP BY d.local_date ORDER BY d.local_date
    """)
    abstract suspend fun dailyStats(fromDate: String, untilDateExclusive: String, roomId: Long?, upMid: Long?): List<LiveDailyStats>

    @Query("""
        SELECT r.room_id, r.short_room_id, r.up_mid, r.up_name,
               SUM(s.total_played_ms) AS total_played_ms, SUM(s.play_count) AS play_count
        FROM (
            SELECT room_id, total_played_ms, play_count FROM live_stats WHERE :fromDate IS NULL
            UNION ALL
            SELECT room_id, total_played_ms, play_count FROM live_daily_stats
            WHERE local_date >= :fromDate AND local_date < :untilDateExclusive
        ) s JOIN live_stats r ON r.room_id = s.room_id
        GROUP BY r.room_id HAVING SUM(s.total_played_ms) > 0
        ORDER BY total_played_ms DESC, r.room_id ASC LIMIT :limit
    """)
    abstract suspend fun topRooms(fromDate: String?, untilDateExclusive: String?, limit: Int): List<LiveRoomRanking>

    @Query("""
        SELECT r.up_mid,
               (SELECT up_name FROM live_stats WHERE up_mid = r.up_mid
                ORDER BY last_viewed_at_ms DESC, room_id DESC LIMIT 1) AS up_name,
               SUM(s.total_played_ms) AS total_played_ms, SUM(s.play_count) AS play_count
        FROM (
            SELECT room_id, total_played_ms, play_count FROM live_stats WHERE :fromDate IS NULL
            UNION ALL
            SELECT room_id, total_played_ms, play_count FROM live_daily_stats
            WHERE local_date >= :fromDate AND local_date < :untilDateExclusive
        ) s JOIN live_stats r ON r.room_id = s.room_id
        GROUP BY r.up_mid HAVING SUM(s.total_played_ms) > 0
        ORDER BY total_played_ms DESC, r.up_mid ASC LIMIT :limit
    """)
    abstract suspend fun topUps(fromDate: String?, untilDateExclusive: String?, limit: Int): List<LiveUpRanking>

    @Transaction
    open suspend fun rankings(fromDate: String?, untilDateExclusive: String?, limit: Int) =
        LiveRankings(topRooms(fromDate, untilDateExclusive, limit), topUps(fromDate, untilDateExclusive, limit))

    @Transaction
    open suspend fun statistics(fromDate: String, untilDateExclusive: String, limit: Int, includeRankings: Boolean) =
        LiveStatisticsSnapshot(summary(), dailyStats(fromDate, untilDateExclusive, null, null),
            if (includeRankings) rankings(fromDate, untilDateExclusive, limit) else LiveRankings(emptyList(), emptyList()))

    @Query("""
        SELECT h.id, CASE WHEN :rooms THEN r.short_room_id END AS short_room_id,
               r.up_mid, r.up_name, h.total_played_ms, h.play_count, h.last_viewed_at_ms
        FROM (
            SELECT CASE WHEN :rooms THEN room_id ELSE up_mid END AS id,
                   SUM(total_played_ms) AS total_played_ms, SUM(play_count) AS play_count,
                   MAX(last_viewed_at_ms) AS last_viewed_at_ms,
                   CASE :sort WHEN 'Time' THEN MAX(last_viewed_at_ms)
                              WHEN 'PlayCount' THEN SUM(play_count) ELSE SUM(total_played_ms) END AS sort_value
            FROM live_stats WHERE total_played_ms > 0 OR play_count > 0
            GROUP BY CASE WHEN :rooms THEN room_id ELSE up_mid END
        ) h JOIN live_stats r ON r.room_id = (
            SELECT room_id FROM live_stats WHERE CASE WHEN :rooms THEN room_id ELSE up_mid END = h.id
            ORDER BY last_viewed_at_ms DESC, room_id DESC LIMIT 1
        )
        WHERE :cursorValue IS NULL
           OR (:descending AND (h.sort_value, h.id) < (:cursorValue, :cursorId))
           OR (NOT :descending AND (h.sort_value, h.id) > (:cursorValue, :cursorId))
        ORDER BY CASE WHEN :descending THEN h.sort_value END DESC,
                 CASE WHEN NOT :descending THEN h.sort_value END ASC,
                 CASE WHEN :descending THEN h.id END DESC,
                 CASE WHEN NOT :descending THEN h.id END ASC LIMIT :limit
    """)
    abstract suspend fun history(rooms: Boolean, cursorValue: Long?, cursorId: Long?, limit: Int,
        sort: String, descending: Boolean): List<LiveHistoryItem>

    @Query("SELECT local_date, total_played_ms, play_count FROM live_source_daily_stats " +
        "WHERE room_id = :roomId AND source_id = :sourceId AND local_date IN (:dates)")
    abstract suspend fun sourceDays(roomId: Long, sourceId: Long, dates: List<String>): List<LiveDailyStats>

    @Query("SELECT * FROM live_daily_stats WHERE room_id = :roomId AND local_date = :date")
    protected abstract suspend fun cachedDay(roomId: Long, date: String): LiveDailyStatsEntity?

    @Insert
    protected abstract suspend fun insert(record: LiveStatisticsRecord)

    @Update
    protected abstract suspend fun update(record: LiveStatisticsRecord)

    @Query("""
        INSERT INTO live_source_daily_stats (room_id, source_id, local_date, total_played_ms, play_count)
        VALUES (:roomId, :sourceId, :date, :playedMs, :playCount)
        ON CONFLICT(room_id, source_id, local_date) DO UPDATE SET
            total_played_ms = MAX(total_played_ms, excluded.total_played_ms),
            play_count = MAX(play_count, excluded.play_count)
    """)
    protected abstract suspend fun upsertSourceDay(roomId: Long, sourceId: Long, date: String, playedMs: Long, playCount: Long)

    @Query("""
        INSERT INTO live_daily_stats (room_id, local_date, total_played_ms, play_count)
        VALUES (:roomId, :date, :playedMs, :playCount)
        ON CONFLICT(room_id, local_date) DO UPDATE SET total_played_ms = excluded.total_played_ms, play_count = excluded.play_count
    """)
    protected abstract suspend fun upsertDay(roomId: Long, date: String, playedMs: Long, playCount: Long)

    @Transaction
    open suspend fun save(metadata: LiveStatisticsRecord, sourceId: Long?, days: List<LiveDailyStats>) {
        val old = get(metadata.roomId)
        var record = metadata.copy(shortRoomId = metadata.shortRoomId ?: old?.shortRoomId,
            firstViewedAtMs = minOf(metadata.firstViewedAtMs, old?.firstViewedAtMs ?: metadata.firstViewedAtMs),
            lastViewedAtMs = maxOf(metadata.lastViewedAtMs, old?.lastViewedAtMs ?: metadata.lastViewedAtMs),
            totalPlayedMs = old?.totalPlayedMs ?: 0L, playCount = old?.playCount ?: 0L)
        if (old == null) insert(record)
        if (days.isNotEmpty()) {
            val writer = checkNotNull(sourceId)
            val saved = sourceDays(record.roomId, writer, days.map { it.localDate }).associateBy { it.localDate }
            for (day in days) {
                val previous = saved[day.localDate]
                val played = (day.totalPlayedMs - (previous?.totalPlayedMs ?: 0L)).coerceAtLeast(0L)
                val count = (day.playCount - (previous?.playCount ?: 0L)).coerceAtLeast(0L)
                val cached = cachedDay(record.roomId, day.localDate)
                upsertSourceDay(record.roomId, writer, day.localDate, day.totalPlayedMs, day.playCount)
                upsertDay(record.roomId, day.localDate, addPlaybackStatistics(cached?.totalPlayedMs ?: 0L, played),
                    addPlaybackStatistics(cached?.playCount ?: 0L, count))
                record = record.copy(totalPlayedMs = addPlaybackStatistics(record.totalPlayedMs, played),
                    playCount = addPlaybackStatistics(record.playCount, count))
            }
        }
        if (old != null || days.isNotEmpty()) update(record)
        currentCoroutineContext().ensureActive()
    }
}
