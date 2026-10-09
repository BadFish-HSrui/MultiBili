package tv.hsrui.bolo.storage.appData.playbackStatistics

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update

@Dao
internal abstract class PlaybackStatisticsDao {
    @Query("SELECT * FROM playback_stats WHERE avid = :avid AND cid = :cid")
    abstract suspend fun get(avid: Long, cid: Long): PlaybackStatisticsRecord?

    @Query("SELECT * FROM playback_stats ORDER BY last_viewed_at_ms DESC, id DESC LIMIT :limit")
    abstract suspend fun firstPage(limit: Int): List<PlaybackStatisticsRecord>

    @Query("SELECT * FROM playback_stats WHERE (last_viewed_at_ms, id) < (:time, :id) ORDER BY last_viewed_at_ms DESC, id DESC LIMIT :limit")
    abstract suspend fun nextPage(time: Long, id: Long, limit: Int): List<PlaybackStatisticsRecord>

    @Query("SELECT COALESCE(SUM(total_played_ms), 0) FROM playback_stats WHERE avid = :avid")
    abstract suspend fun totalPlayedMs(avid: Long): Long

    @Query("""
        SELECT d.local_date, SUM(d.total_played_ms) AS total_played_ms, SUM(d.play_count) AS play_count
        FROM playback_daily_stats d JOIN playback_stats h ON h.id = d.record_id
        WHERE d.local_date >= :fromDate AND d.local_date < :untilDateExclusive
          AND (:avid IS NULL OR h.avid = :avid) AND (:cid IS NULL OR h.cid = :cid)
          AND (:upMid IS NULL OR (h.content_type = 'video' AND h.up_mid = :upMid))
        GROUP BY d.local_date ORDER BY d.local_date
    """)
    abstract suspend fun dailyStats(
        fromDate: String, untilDateExclusive: String, avid: Long?, cid: Long?, upMid: Long?,
    ): List<PlaybackDailyStats>

    @Query("""
        SELECT d.local_date, d.total_played_ms, d.play_count
        FROM playback_daily_stats d JOIN playback_stats h ON h.id = d.record_id
        WHERE h.avid = :avid AND h.cid = :cid AND d.local_date IN (:dates)
    """)
    abstract suspend fun dailyStatsForRecord(avid: Long, cid: Long, dates: List<String>): List<PlaybackDailyStats>

    @Query("""
        INSERT INTO playback_daily_stats (record_id, local_date, total_played_ms, play_count)
        VALUES (:recordId, :localDate, :playedMs, :playCount)
        ON CONFLICT(record_id, local_date) DO UPDATE SET
            total_played_ms = MAX(total_played_ms, excluded.total_played_ms),
            play_count = MAX(play_count, excluded.play_count)
    """)
    abstract suspend fun upsertDailyStats(recordId: Long, localDate: String, playedMs: Long, playCount: Long)

    @Query("SELECT * FROM playback_watched_range WHERE record_id = :id ORDER BY start_ms")
    abstract suspend fun ranges(id: Long): List<PlaybackWatchedRangeEntity>

    @Query("SELECT * FROM playback_watched_range WHERE record_id = :id AND start_ms <= :end AND end_ms >= :start ORDER BY start_ms")
    abstract suspend fun overlappingRanges(id: Long, start: Long, end: Long): List<PlaybackWatchedRangeEntity>

    @Query("DELETE FROM playback_watched_range WHERE record_id = :id AND start_ms <= :end AND end_ms >= :start")
    abstract suspend fun deleteOverlappingRanges(id: Long, start: Long, end: Long)

    @Insert
    abstract suspend fun insert(record: PlaybackStatisticsRecord): Long

    @Update
    abstract suspend fun update(record: PlaybackStatisticsRecord)

    @Insert
    abstract suspend fun insertRange(range: PlaybackWatchedRangeEntity)

    @Transaction
    open suspend fun save(
        metadata: PlaybackStatisticsMetadata,
        firstViewedAtMs: Long,
        lastViewedAtMs: Long,
        basePlayedMs: Long,
        playedMs: Long,
        basePlayCount: Long,
        playCount: Long,
        dailyStats: List<PlaybackDailyStats>,
        ranges: List<PlaybackWatchedRange>,
    ): PlaybackStatisticsRecord {
        val old = get(metadata.avid, metadata.cid)
        val isVideo = metadata.contentType == "video"
        var record = PlaybackStatisticsRecord(
            id = old?.id ?: 0L,
            avid = metadata.avid,
            bvid = metadata.bvid ?: old?.bvid,
            cid = metadata.cid,
            contentType = metadata.contentType,
            title = metadata.title.ifBlank { old?.title.orEmpty() },
            partTitle = metadata.partTitle ?: old?.partTitle,
            partNumber = if (isVideo) metadata.partNumber ?: old?.partNumber else null,
            upMid = if (isVideo) metadata.upMid ?: old?.upMid else null,
            upName = if (isVideo) metadata.upName ?: old?.upName else null,
            seasonId = metadata.seasonId,
            episodeId = metadata.episodeId,
            durationMs = metadata.durationMs.takeIf { it > 0L } ?: old?.durationMs ?: 0L,
            totalPlayedMs = maxOf(old?.totalPlayedMs ?: 0L, basePlayedMs + playedMs),
            playCount = maxOf(old?.playCount ?: 0L, basePlayCount + playCount),
            firstViewedAtMs = old?.firstViewedAtMs ?: firstViewedAtMs,
            lastViewedAtMs = maxOf(old?.lastViewedAtMs ?: lastViewedAtMs, lastViewedAtMs),
        )
        if (old == null) record = record.copy(id = insert(record)) else update(record)
        for (day in dailyStats) {
            upsertDailyStats(record.id, day.localDate, day.totalPlayedMs, day.playCount)
        }
        for (range in ranges) {
            require(range.startMs >= 0L && range.endMs > range.startMs)
            val overlap = overlappingRanges(record.id, range.startMs, range.endMs)
            val start = minOf(range.startMs, overlap.firstOrNull()?.startMs ?: range.startMs)
            val end = maxOf(range.endMs, overlap.maxOfOrNull { it.endMs } ?: range.endMs)
            deleteOverlappingRanges(record.id, start, end)
            insertRange(PlaybackWatchedRangeEntity(record.id, start, end))
        }
        return record
    }
}
