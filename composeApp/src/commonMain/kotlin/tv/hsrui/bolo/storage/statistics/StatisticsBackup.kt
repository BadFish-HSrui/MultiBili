package tv.hsrui.bolo.storage.statistics

import androidx.room3.executeSQL
import androidx.room3.immediateTransaction
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

private val statisticsBackupTables = listOf("playback_stats", "playback_source", "playback_watched_range",
    "playback_daily_stats", "playback_source_daily_stats", "playback_legacy_totals")
private val statisticsSchemaQueries = listOf(
    "PRAGMA user_version",
    "SELECT identity_hash FROM room_master_table WHERE id = 42",
    "SELECT type, name, tbl_name, replace(sql, 'IF NOT EXISTS ', '') FROM sqlite_master " +
        "WHERE tbl_name IN (${statisticsBackupTables.joinToString { "'$it'" }}) ORDER BY type, name",
)

internal suspend fun StatisticsDatabase.exportBackup(destination: String) = withContext(Dispatchers.IO) {
    // 先通过 Room 打开数据库，确保首次导出空库时表结构已经创建。
    val sourcePath = useReaderConnection { connection ->
        connection.usePrepared("PRAGMA database_list") { rows ->
            var path: String? = null
            while (rows.step()) if (rows.getText(1) == "main") path = rows.getText(2)
            checkNotNull(path?.takeIf(String::isNotBlank)) { "统计数据库路径不可用" }
        }
    }
    BundledSQLiteDriver().open(sourcePath, SQLITE_OPEN_READWRITE or SQLITE_OPEN_FULLMUTEX).use { source ->
        source.prepare("PRAGMA busy_timeout = 5000").use { it.step() }
        currentCoroutineContext().ensureActive()
        source.prepare("VACUUM INTO ?").use {
            it.bindText(1, destination)
            it.step()
        }
    }
    currentCoroutineContext().ensureActive()
    validateBackup(destination)
}

internal suspend fun StatisticsDatabase.validateBackup(path: String) = withContext(Dispatchers.IO) {
    openStatisticsBackup(path).use { source -> validateStatisticsBackup(source) }
}

internal suspend fun StatisticsDatabase.restoreBackup(path: String) = withContext(Dispatchers.IO) {
    openStatisticsBackup(path).use { source ->
        validateStatisticsBackup(source)
        useWriterConnection { target ->
            target.immediateTransaction {
                // 显式清理子表，不依赖调用方连接上的外键开关。
                executeSQL("DELETE FROM playback_watched_range")
                executeSQL("DELETE FROM playback_daily_stats")
                executeSQL("DELETE FROM playback_source_daily_stats")
                executeSQL("DELETE FROM playback_legacy_totals")
                executeSQL("DELETE FROM playback_stats")
                executeSQL("DELETE FROM playback_source")
                for (table in statisticsBackupTables) {
                    source.prepare("SELECT * FROM $table").use { rows ->
                        val columns = rows.getColumnNames()
                        val parameters = columns.joinToString { "?" }
                        usePrepared("INSERT INTO $table (${columns.joinToString { "`$it`" }}) VALUES ($parameters)") { insert ->
                            var count = 0
                            while (rows.step()) {
                                if (count++ % 256 == 0) currentCoroutineContext().ensureActive()
                                insert.reset()
                                columns.indices.forEach { index ->
                                    when (rows.getColumnType(index)) {
                                        1 -> insert.bindLong(index + 1, rows.getLong(index))
                                        3 -> insert.bindText(index + 1, rows.getText(index))
                                        5 -> insert.bindNull(index + 1)
                                        else -> error("备份包含无效的统计字段")
                                    }
                                }
                                insert.step()
                            }
                        }
                    }
                }
                usePrepared("PRAGMA foreign_key_check") { check(!it.step()) { "备份数据关联无效" } }
                executeSQL("UPDATE playback_source SET is_active = 0 WHERE is_active <> 0")
                currentCoroutineContext().ensureActive()
            }
        }
    }
}

private fun openStatisticsBackup(path: String): SQLiteConnection =
    BundledSQLiteDriver().open(path, SQLITE_OPEN_READONLY or SQLITE_OPEN_FULLMUTEX).also { connection ->
        try {
            connection.prepare("PRAGMA trusted_schema = OFF").use { it.step() }
        } catch (error: Exception) {
            connection.close()
            throw error
        }
    }

private suspend fun StatisticsDatabase.validateStatisticsBackup(source: SQLiteConnection) {
    useReaderConnection { current ->
        for (sql in statisticsSchemaQueries) {
            val expected = current.usePrepared(sql) { it.readBackupSchemaRows(normalizeSql = sql.contains("sqlite_master")) }
            val actual = source.prepare(sql).use { it.readBackupSchemaRows(normalizeSql = sql.contains("sqlite_master")) }
            check(expected == actual) { "备份数据库版本或结构不兼容" }
        }
    }
    source.prepare("PRAGMA integrity_check").use {
        check(it.step() && it.getText(0) == "ok" && !it.step()) { "备份数据库已损坏" }
    }
    source.prepare("PRAGMA foreign_key_check").use { check(!it.step()) { "备份数据关联无效" } }
    for (table in statisticsBackupTables) {
        val invalidFields = source.prepare("PRAGMA table_info($table)").use { columns ->
            buildList {
                while (columns.step()) {
                    val name = columns.getText(1)
                    val type = if (columns.getText(2) == "INTEGER") "integer" else "text"
                    val nullable = columns.getLong(3) == 0L && columns.getLong(5) == 0L
                    add("typeof(`$name`) NOT IN ('$type'${if (nullable) ", 'null'" else ""})")
                }
            }
        }
        source.prepare("SELECT 1 FROM $table WHERE ${invalidFields.joinToString(" OR ")} LIMIT 1").use {
            check(!it.step()) { "备份包含无效的统计字段" }
        }
    }
    val invalidQueries = listOf(
        "SELECT 1 FROM playback_stats WHERE id <= 0 OR avid <= 0 OR cid <= 0 OR duration_ms < 0 " +
            "OR total_played_ms < 0 OR play_count < 0 OR content_type NOT IN ('video', 'media') LIMIT 1",
        "SELECT 1 FROM playback_watched_range WHERE start_ms < 0 OR end_ms <= start_ms LIMIT 1",
        "SELECT 1 FROM playback_daily_stats WHERE total_played_ms < 0 OR play_count < 0 LIMIT 1",
        "SELECT 1 FROM playback_source WHERE id <= 0 OR is_active NOT IN (0, 1) OR (source_key = '0' AND is_active <> 0) LIMIT 1",
        "SELECT 1 FROM playback_source_daily_stats WHERE total_played_ms < 0 OR play_count < 0 LIMIT 1",
        "SELECT 1 FROM playback_legacy_totals WHERE total_played_ms < 0 OR play_count < 0 LIMIT 1",
    )
    for (sql in invalidQueries) {
        currentCoroutineContext().ensureActive()
        source.prepare(sql).use { check(!it.step()) { "备份包含无效的统计数据" } }
    }
    source.prepare("SELECT local_date FROM playback_daily_stats UNION SELECT local_date FROM playback_source_daily_stats").use { dates ->
        while (dates.step()) {
            currentCoroutineContext().ensureActive()
            val date = dates.getText(0)
            check(runCatching { LocalDate.parse(date).toString() == date }.getOrDefault(false)) { "备份包含无效日期" }
        }
    }
}

private fun SQLiteStatement.readBackupSchemaRows(normalizeSql: Boolean = false): List<List<String?>> = buildList {
    while (step()) add(List(getColumnCount()) { index ->
        if (isNull(index)) null else getText(index).let { value ->
            if (normalizeSql && index == 3) value.replace("IF NOT EXISTS ", "").replace(Regex("[\\s`]+"), "").lowercase() else value
        }
    })
}
