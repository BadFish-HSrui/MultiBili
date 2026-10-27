package tv.hsrui.bolo.storage.statistics

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsDao
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackDailyStatsEntity
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsRecord
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackWatchedRangeEntity
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsSourceEntity
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackSourceDailyStatsEntity
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackLegacyTotalsEntity
import tv.hsrui.bolo.storage.appData.liveStatistics.LiveStatisticsDao
import tv.hsrui.bolo.storage.appData.liveStatistics.LiveStatisticsRecord
import tv.hsrui.bolo.storage.appData.liveStatistics.LiveDailyStatsEntity
import tv.hsrui.bolo.storage.appData.liveStatistics.LiveSourceDailyStatsEntity

@Database(
    entities = [PlaybackStatisticsRecord::class, PlaybackWatchedRangeEntity::class, PlaybackDailyStatsEntity::class,
        PlaybackStatisticsSourceEntity::class, PlaybackSourceDailyStatsEntity::class, PlaybackLegacyTotalsEntity::class,
        LiveStatisticsRecord::class, LiveDailyStatsEntity::class, LiveSourceDailyStatsEntity::class],
    version = 3,
    exportSchema = true,
)
@ConstructedBy(StatisticsDatabaseConstructor::class)
abstract class StatisticsDatabase : RoomDatabase() {
    internal abstract fun playbackStatisticsDao(): PlaybackStatisticsDao
    internal abstract fun liveStatisticsDao(): LiveStatisticsDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object StatisticsDatabaseConstructor : RoomDatabaseConstructor<StatisticsDatabase> {
    override fun initialize(): StatisticsDatabase
}

internal expect fun statisticsDatabasePath(): String

internal fun createStatisticsDatabase(): StatisticsDatabase = Room.databaseBuilder<StatisticsDatabase>(
    name = statisticsDatabasePath(),
    factory = StatisticsDatabaseConstructor::initialize,
).setDriver(BundledSQLiteDriver())
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .addMigrations(statisticsMigration1To2, statisticsMigration2To3)
    .build()

internal val statisticsMigration1To2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `playback_source` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`source_key` TEXT NOT NULL, `is_active` INTEGER NOT NULL DEFAULT 0)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playback_source_source_key` ON `playback_source` (`source_key`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `playback_source_daily_stats` (`record_id` INTEGER NOT NULL, " +
            "`source_id` INTEGER NOT NULL, `local_date` TEXT NOT NULL, `total_played_ms` INTEGER NOT NULL DEFAULT 0, " +
            "`play_count` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`record_id`, `source_id`, `local_date`), " +
            "FOREIGN KEY(`record_id`) REFERENCES `playback_stats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
            "FOREIGN KEY(`source_id`) REFERENCES `playback_source`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_source_daily_stats_source_id` ON `playback_source_daily_stats` (`source_id`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `playback_legacy_totals` (`record_id` INTEGER NOT NULL, " +
            "`total_played_ms` INTEGER NOT NULL DEFAULT 0, `play_count` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`record_id`), " +
            "FOREIGN KEY(`record_id`) REFERENCES `playback_stats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("INSERT INTO playback_source (source_key) SELECT '0' WHERE EXISTS (SELECT 1 FROM playback_stats)")
        connection.execSQL("INSERT INTO playback_legacy_totals SELECT id, total_played_ms, play_count FROM playback_stats")
        connection.execSQL("INSERT INTO playback_source_daily_stats " +
            "SELECT record_id, (SELECT id FROM playback_source WHERE source_key = '0'), local_date, total_played_ms, play_count FROM playback_daily_stats")
        connection.execSQL(statisticsRebuildTotalsSql())
        currentCoroutineContext().ensureActive()
    }
}

internal val statisticsMigration2To3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `live_stats` (`room_id` INTEGER NOT NULL, " +
            "`short_room_id` INTEGER, `up_mid` INTEGER NOT NULL, `up_name` TEXT NOT NULL, " +
            "`total_played_ms` INTEGER NOT NULL DEFAULT 0, `play_count` INTEGER NOT NULL DEFAULT 0, " +
            "`first_viewed_at_ms` INTEGER NOT NULL, `last_viewed_at_ms` INTEGER NOT NULL, PRIMARY KEY(`room_id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stats_up_mid` ON `live_stats` (`up_mid`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_live_stats_last_viewed_at_ms_room_id` " +
            "ON `live_stats` (`last_viewed_at_ms` DESC, `room_id` DESC)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `live_daily_stats` (`room_id` INTEGER NOT NULL, " +
            "`local_date` TEXT NOT NULL, `total_played_ms` INTEGER NOT NULL DEFAULT 0, `play_count` INTEGER NOT NULL DEFAULT 0, " +
            "PRIMARY KEY(`room_id`, `local_date`), FOREIGN KEY(`room_id`) REFERENCES `live_stats`(`room_id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_live_daily_stats_local_date_room_id` " +
            "ON `live_daily_stats` (`local_date`, `room_id`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `live_source_daily_stats` (`room_id` INTEGER NOT NULL, " +
            "`source_id` INTEGER NOT NULL, `local_date` TEXT NOT NULL, `total_played_ms` INTEGER NOT NULL DEFAULT 0, " +
            "`play_count` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`room_id`, `source_id`, `local_date`), " +
            "FOREIGN KEY(`room_id`) REFERENCES `live_stats`(`room_id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
            "FOREIGN KEY(`source_id`) REFERENCES `playback_source`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_live_source_daily_stats_source_id` ON `live_source_daily_stats` (`source_id`)")
        currentCoroutineContext().ensureActive()
    }
}

/** SUM 保持整数溢出检查，避免 SQLite 的普通整数加法溢出后静默转成 REAL。 */
internal fun statisticsRecordTotalsSql(recordId: String): String = """
    SELECT SUM(total_played_ms), SUM(play_count) FROM (
        SELECT d.total_played_ms, d.play_count
        FROM playback_source_daily_stats d JOIN playback_source s ON s.id = d.source_id
        WHERE d.record_id = $recordId AND s.source_key <> '0'
        UNION ALL
        SELECT MAX(COALESCE(SUM(d.total_played_ms), 0),
                   COALESCE((SELECT total_played_ms FROM playback_legacy_totals WHERE record_id = $recordId), 0)),
               MAX(COALESCE(SUM(d.play_count), 0),
                   COALESCE((SELECT play_count FROM playback_legacy_totals WHERE record_id = $recordId), 0))
        FROM playback_source_daily_stats d JOIN playback_source s ON s.id = d.source_id
        WHERE d.record_id = $recordId AND s.source_key = '0'
    )
""".trimIndent()

internal fun statisticsRebuildTotalsSql(recordId: Long? = null): String =
    "UPDATE playback_stats SET (total_played_ms, play_count) = (${statisticsRecordTotalsSql("playback_stats.id")})" +
        if (recordId == null) "" else " WHERE id = $recordId"
