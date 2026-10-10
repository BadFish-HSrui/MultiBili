package tv.hsrui.bolo.storage.statistics

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsDao
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackDailyStatsEntity
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsRecord
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackWatchedRangeEntity

@Database(
    entities = [PlaybackStatisticsRecord::class, PlaybackWatchedRangeEntity::class, PlaybackDailyStatsEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(StatisticsDatabaseConstructor::class)
abstract class StatisticsDatabase : RoomDatabase() {
    internal abstract fun playbackStatisticsDao(): PlaybackStatisticsDao
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
    .build()
