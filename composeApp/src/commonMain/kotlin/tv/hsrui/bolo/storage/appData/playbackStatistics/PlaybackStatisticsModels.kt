package tv.hsrui.bolo.storage.appData.playbackStatistics

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

internal data class PlaybackStatisticsMetadata(
    val avid: Long,
    val cid: Long,
    val bvid: String?,
    val contentType: String,
    val title: String,
    val partTitle: String? = null,
    val partNumber: Int? = null,
    val upMid: Long? = null,
    val upName: String? = null,
    val seasonId: Long? = null,
    val episodeId: Long? = null,
    val durationMs: Long = 0L,
)

@Entity(
    tableName = "playback_stats",
    indices = [
        Index(value = ["avid", "cid"], unique = true),
        Index(value = ["last_viewed_at_ms", "id"], orders = [Index.Order.DESC, Index.Order.DESC]),
    ],
)
data class PlaybackStatisticsRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val avid: Long,
    val bvid: String?,
    val cid: Long,
    @ColumnInfo(name = "content_type") val contentType: String,
    val title: String,
    @ColumnInfo(name = "part_title") val partTitle: String?,
    @ColumnInfo(name = "part_number") val partNumber: Int?,
    @ColumnInfo(name = "up_mid") val upMid: Long?,
    @ColumnInfo(name = "up_name") val upName: String?,
    @ColumnInfo(name = "season_id") val seasonId: Long?,
    @ColumnInfo(name = "episode_id") val episodeId: Long?,
    @ColumnInfo(name = "duration_ms", defaultValue = "0") val durationMs: Long = 0L,
    @ColumnInfo(name = "total_played_ms", defaultValue = "0") val totalPlayedMs: Long = 0L,
    @ColumnInfo(name = "play_count", defaultValue = "0") val playCount: Long = 0L,
    @ColumnInfo(name = "first_viewed_at_ms") val firstViewedAtMs: Long,
    @ColumnInfo(name = "last_viewed_at_ms") val lastViewedAtMs: Long,
)

@Entity(
    tableName = "playback_watched_range",
    primaryKeys = ["record_id", "start_ms"],
    foreignKeys = [ForeignKey(
        entity = PlaybackStatisticsRecord::class,
        parentColumns = ["id"],
        childColumns = ["record_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
internal data class PlaybackWatchedRangeEntity(
    @ColumnInfo(name = "record_id") val recordId: Long,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
)

data class PlaybackWatchedRange(val startMs: Long, val endMs: Long)

@Entity(
    tableName = "playback_daily_stats",
    primaryKeys = ["record_id", "local_date"],
    indices = [Index(value = ["local_date", "record_id"])],
    foreignKeys = [ForeignKey(
        entity = PlaybackStatisticsRecord::class,
        parentColumns = ["id"],
        childColumns = ["record_id"],
        onDelete = ForeignKey.CASCADE,
    )],
)
internal data class PlaybackDailyStatsEntity(
    @ColumnInfo(name = "record_id") val recordId: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "total_played_ms", defaultValue = "0") val totalPlayedMs: Long = 0L,
    @ColumnInfo(name = "play_count", defaultValue = "0") val playCount: Long = 0L,
)

data class PlaybackDailyStats(
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "total_played_ms") val totalPlayedMs: Long = 0L,
    @ColumnInfo(name = "play_count") val playCount: Long = 0L,
)

data class PlaybackStatisticsSummary(
    @ColumnInfo(name = "total_played_ms") val totalPlayedMs: Long = 0L,
    @ColumnInfo(name = "play_count") val playCount: Long = 0L,
    @ColumnInfo(name = "video_count") val videoCount: Long = 0L,
    @ColumnInfo(name = "up_count") val upCount: Long = 0L,
)

data class PlaybackVideoRanking(
    val avid: Long,
    val title: String,
    @ColumnInfo(name = "part_title") val partTitle: String?,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "total_played_ms") val totalPlayedMs: Long,
    @ColumnInfo(name = "play_count") val playCount: Long,
)

data class PlaybackUpRanking(
    @ColumnInfo(name = "up_mid") val upMid: Long,
    @ColumnInfo(name = "up_name") val upName: String?,
    @ColumnInfo(name = "total_played_ms") val totalPlayedMs: Long,
    @ColumnInfo(name = "play_count") val playCount: Long,
)

data class PlaybackStatisticsSnapshot(
    val summary: PlaybackStatisticsSummary,
    val dailyStats: List<PlaybackDailyStats>,
    val videos: List<PlaybackVideoRanking>,
    val ups: List<PlaybackUpRanking>,
)

data class PlaybackStatisticsCursor(val lastViewedAtMs: Long, val id: Long)
