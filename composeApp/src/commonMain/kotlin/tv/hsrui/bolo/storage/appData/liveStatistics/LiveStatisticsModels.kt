package tv.hsrui.bolo.storage.appData.liveStatistics

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsSourceEntity

@Entity(tableName = "live_stats", indices = [Index(value = ["up_mid"]),
    Index(value = ["last_viewed_at_ms", "room_id"], orders = [Index.Order.DESC, Index.Order.DESC])])
data class LiveStatisticsRecord(
    @PrimaryKey @ColumnInfo(name = "room_id") val roomId: Long,
    /** null 表示尚未取得，0 表示接口明确返回没有短号。 */
    @ColumnInfo(name = "short_room_id") val shortRoomId: Long?,
    @ColumnInfo(name = "up_mid") val upMid: Long,
    @ColumnInfo(name = "up_name") val upName: String,
    @ColumnInfo(name = "total_played_ms", defaultValue = "0") val totalPlayedMs: Long = 0L,
    @ColumnInfo(name = "play_count", defaultValue = "0") val playCount: Long = 0L,
    @ColumnInfo(name = "first_viewed_at_ms") val firstViewedAtMs: Long,
    @ColumnInfo(name = "last_viewed_at_ms") val lastViewedAtMs: Long,
)

@Entity(tableName = "live_daily_stats", primaryKeys = ["room_id", "local_date"],
    indices = [Index(value = ["local_date", "room_id"])],
    foreignKeys = [ForeignKey(entity = LiveStatisticsRecord::class, parentColumns = ["room_id"],
        childColumns = ["room_id"], onDelete = ForeignKey.CASCADE)])
internal data class LiveDailyStatsEntity(
    @ColumnInfo(name = "room_id") val roomId: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "total_played_ms", defaultValue = "0") val totalPlayedMs: Long = 0L,
    @ColumnInfo(name = "play_count", defaultValue = "0") val playCount: Long = 0L,
)

@Entity(tableName = "live_source_daily_stats", primaryKeys = ["room_id", "source_id", "local_date"],
    indices = [Index(value = ["source_id"])], foreignKeys = [
        ForeignKey(entity = LiveStatisticsRecord::class, parentColumns = ["room_id"],
            childColumns = ["room_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlaybackStatisticsSourceEntity::class, parentColumns = ["id"],
            childColumns = ["source_id"], onDelete = ForeignKey.CASCADE),
    ])
internal data class LiveSourceDailyStatsEntity(
    @ColumnInfo(name = "room_id") val roomId: Long,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "total_played_ms", defaultValue = "0") val totalPlayedMs: Long = 0L,
    @ColumnInfo(name = "play_count", defaultValue = "0") val playCount: Long = 0L,
)

data class LiveDailyStats(
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "total_played_ms") val totalPlayedMs: Long = 0L,
    @ColumnInfo(name = "play_count") val playCount: Long = 0L,
)

data class LiveStatisticsSummary(
    @ColumnInfo(name = "total_played_ms") val totalPlayedMs: Long = 0L,
    @ColumnInfo(name = "play_count") val playCount: Long = 0L,
    @ColumnInfo(name = "room_count") val roomCount: Long = 0L,
    @ColumnInfo(name = "up_count") val upCount: Long = 0L,
)

data class LiveRoomRanking(
    @ColumnInfo(name = "room_id") val roomId: Long,
    @ColumnInfo(name = "short_room_id") val shortRoomId: Long?,
    @ColumnInfo(name = "up_mid") val upMid: Long,
    @ColumnInfo(name = "up_name") val upName: String,
    @ColumnInfo(name = "total_played_ms") val totalPlayedMs: Long,
    @ColumnInfo(name = "play_count") val playCount: Long,
)

data class LiveUpRanking(
    @ColumnInfo(name = "up_mid") val upMid: Long,
    @ColumnInfo(name = "up_name") val upName: String,
    @ColumnInfo(name = "total_played_ms") val totalPlayedMs: Long,
    @ColumnInfo(name = "play_count") val playCount: Long,
)

data class LiveRankings(val rooms: List<LiveRoomRanking>, val ups: List<LiveUpRanking>)

data class LiveStatisticsSnapshot(
    val summary: LiveStatisticsSummary,
    val dailyStats: List<LiveDailyStats>,
    val rankings: LiveRankings,
)

enum class LiveHistoryType { Rooms, Ups }
enum class LiveHistorySort { Time, PlayCount, Duration }

/** id 分别为真实房间 ID 或主播 UID；主播汇总不指定短号。 */
data class LiveHistoryItem(
    val id: Long,
    @ColumnInfo(name = "short_room_id") val shortRoomId: Long?,
    @ColumnInfo(name = "up_mid") val upMid: Long,
    @ColumnInfo(name = "up_name") val upName: String,
    @ColumnInfo(name = "total_played_ms") val totalPlayedMs: Long,
    @ColumnInfo(name = "play_count") val playCount: Long,
    @ColumnInfo(name = "last_viewed_at_ms") val lastViewedAtMs: Long,
)

data class LiveHistoryCursor(val sortValue: Long, val id: Long)
