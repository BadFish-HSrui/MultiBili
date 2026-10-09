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
        SELECT COALESCE(SUM(total_played_ms), 0) AS total_played_ms,
               COALESCE(SUM(play_count), 0) AS play_count,
               COUNT(DISTINCT avid) AS video_count,
               COUNT(DISTINCT CASE WHEN content_type = 'video' AND up_mid > 0 THEN up_mid END) AS up_count
        FROM playback_stats WHERE total_played_ms > 0 OR play_count > 0
    """)
    abstract suspend fun statisticsSummary(): PlaybackStatisticsSummary

    @Query("""
        SELECT ranked.avid,
               COALESCE((SELECT title FROM playback_stats
                         WHERE avid = ranked.avid AND TRIM(title) <> ''
                         ORDER BY last_viewed_at_ms DESC, id DESC LIMIT 1), '') AS title,
               CASE WHEN latest.content_type = 'media' THEN
                   (SELECT part_title FROM playback_stats
                    WHERE avid = ranked.avid AND TRIM(part_title) <> ''
                    ORDER BY last_viewed_at_ms DESC, id DESC LIMIT 1)
               END AS part_title,
               latest.content_type,
               ranked.total_played_ms, ranked.play_count
        FROM (
            SELECT source.avid, SUM(source.total_played_ms) AS total_played_ms, SUM(source.play_count) AS play_count
            FROM (
                SELECT avid, total_played_ms, play_count FROM playback_stats
                WHERE :fromDate IS NULL AND (:contentType IS NULL OR content_type = :contentType)
                UNION ALL
                SELECT h.avid, d.total_played_ms, d.play_count
                FROM playback_daily_stats d JOIN playback_stats h ON h.id = d.record_id
                WHERE d.local_date >= :fromDate AND d.local_date < :untilDateExclusive
                  AND (:contentType IS NULL OR h.content_type = :contentType)
            ) AS source
            GROUP BY source.avid HAVING SUM(source.total_played_ms) > 0
            ORDER BY total_played_ms DESC, source.avid ASC LIMIT :limit
        ) AS ranked
        JOIN playback_stats latest ON latest.id = (
            SELECT id FROM playback_stats WHERE avid = ranked.avid
            ORDER BY last_viewed_at_ms DESC, id DESC LIMIT 1
        )
        ORDER BY ranked.total_played_ms DESC, ranked.avid ASC
    """)
    abstract suspend fun topVideos(
        fromDate: String?, untilDateExclusive: String?, limit: Int, contentType: String? = null,
    ): List<PlaybackVideoRanking>

    @Query("""
        WITH media_records AS (
            SELECT *,
                   CASE WHEN season_id > 0 THEN season_id ELSE 0 END AS group_season_id,
                   CASE WHEN season_id > 0 THEN 0 ELSE avid END AS group_avid
            FROM playback_stats WHERE content_type = 'media'
        ), ranked AS (
            SELECT source.group_season_id, source.group_avid,
                   SUM(source.total_played_ms) AS total_played_ms, SUM(source.play_count) AS play_count
            FROM (
                SELECT group_season_id, group_avid, total_played_ms, play_count
                FROM media_records WHERE :fromDate IS NULL
                UNION ALL
                SELECT h.group_season_id, h.group_avid, d.total_played_ms, d.play_count
                FROM playback_daily_stats d JOIN media_records h ON h.id = d.record_id
                WHERE d.local_date >= :fromDate AND d.local_date < :untilDateExclusive
            ) AS source
            GROUP BY source.group_season_id, source.group_avid HAVING SUM(source.total_played_ms) > 0
            ORDER BY total_played_ms DESC, source.group_season_id ASC, source.group_avid ASC LIMIT :limit
        )
        SELECT latest.avid,
               COALESCE((SELECT title FROM media_records
                         WHERE group_season_id = ranked.group_season_id AND group_avid = ranked.group_avid
                           AND TRIM(title) <> ''
                         ORDER BY last_viewed_at_ms DESC, id DESC LIMIT 1), '') AS title,
               CASE WHEN ranked.group_season_id = 0 THEN
                   (SELECT part_title FROM media_records
                    WHERE group_season_id = ranked.group_season_id AND group_avid = ranked.group_avid
                      AND TRIM(part_title) <> ''
                    ORDER BY last_viewed_at_ms DESC, id DESC LIMIT 1)
               END AS part_title,
               latest.content_type,
               ranked.total_played_ms, ranked.play_count
        FROM ranked
        JOIN media_records latest ON latest.id = (
            SELECT id FROM media_records
            WHERE group_season_id = ranked.group_season_id AND group_avid = ranked.group_avid
            ORDER BY last_viewed_at_ms DESC, id DESC LIMIT 1
        )
        ORDER BY ranked.total_played_ms DESC, ranked.group_season_id ASC, ranked.group_avid ASC
    """)
    abstract suspend fun topMediaSeasons(fromDate: String?, untilDateExclusive: String?, limit: Int): List<PlaybackVideoRanking>

    @Query("""
        SELECT ranked.up_mid,
               (SELECT up_name FROM playback_stats
                WHERE content_type = 'video' AND up_mid = ranked.up_mid AND TRIM(up_name) <> ''
                ORDER BY last_viewed_at_ms DESC, id DESC LIMIT 1) AS up_name,
               ranked.total_played_ms, ranked.play_count
        FROM (
            SELECT source.up_mid, SUM(source.total_played_ms) AS total_played_ms, SUM(source.play_count) AS play_count
            FROM (
                SELECT up_mid, total_played_ms, play_count FROM playback_stats
                WHERE :fromDate IS NULL AND content_type = 'video' AND up_mid > 0
                UNION ALL
                SELECT h.up_mid, d.total_played_ms, d.play_count
                FROM playback_daily_stats d JOIN playback_stats h ON h.id = d.record_id
                WHERE d.local_date >= :fromDate AND d.local_date < :untilDateExclusive
                  AND h.content_type = 'video' AND h.up_mid > 0
            ) AS source
            GROUP BY source.up_mid HAVING SUM(source.total_played_ms) > 0
            ORDER BY total_played_ms DESC, source.up_mid ASC LIMIT :limit
        ) AS ranked
        ORDER BY ranked.total_played_ms DESC, ranked.up_mid ASC
    """)
    abstract suspend fun topUps(fromDate: String?, untilDateExclusive: String?, limit: Int): List<PlaybackUpRanking>

    @Transaction
    open suspend fun rankings(fromDate: String?, untilDateExclusive: String?, limit: Int): PlaybackRankings =
        PlaybackRankings(
            videos = topVideos(fromDate, untilDateExclusive, limit, contentType = "video"),
            ups = topUps(fromDate, untilDateExclusive, limit),
            media = topMediaSeasons(fromDate, untilDateExclusive, limit),
        )

    @Transaction
    open suspend fun statistics(
        fromDate: String, untilDateExclusive: String, limit: Int, includeRankings: Boolean,
    ): PlaybackStatisticsSnapshot {
        val summary = statisticsSummary()
        if (fromDate == untilDateExclusive) {
            return PlaybackStatisticsSnapshot(summary, emptyList(), emptyList(), emptyList())
        }
        return PlaybackStatisticsSnapshot(
            summary = summary,
            dailyStats = dailyStats(fromDate, untilDateExclusive, null, null, null),
            videos = if (includeRankings) topVideos(fromDate, untilDateExclusive, limit) else emptyList(),
            ups = if (includeRankings) topUps(fromDate, untilDateExclusive, limit) else emptyList(),
        )
    }

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
