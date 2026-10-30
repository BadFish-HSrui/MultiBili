package tv.hsrui.bolo.storage.statistics

import androidx.room3.PooledConnection
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
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackStatisticsRecord
import kotlin.uuid.Uuid

private val statisticsV1Tables = listOf("playback_stats", "playback_watched_range", "playback_daily_stats")
private val statisticsBackupTables = statisticsV1Tables + listOf("playback_source", "playback_source_daily_stats", "playback_legacy_totals")
private val statisticsRecordColumns = listOf("avid", "bvid", "cid", "content_type", "title", "part_title", "part_number",
    "up_mid", "up_name", "season_id", "episode_id", "duration_ms", "total_played_ms", "play_count", "first_viewed_at_ms", "last_viewed_at_ms")

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
    Unit
}

internal suspend fun StatisticsDatabase.restoreBackup(path: String) = withContext(Dispatchers.IO) {
    openStatisticsBackup(path).use { source ->
        val version = validateStatisticsBackup(source)
        useWriterConnection { target ->
            target.immediateTransaction {
                if (version >= 2) {
                    source.prepare("SELECT source_key FROM playback_source").use { rows ->
                        while (rows.step()) {
                            currentCoroutineContext().ensureActive()
                            backupSourceId(rows.getText(0))
                        }
                    }
                }
                source.prepare("SELECT * FROM playback_stats ORDER BY id").use { rows ->
                    while (rows.step()) {
                        currentCoroutineContext().ensureActive()
                        val incoming = rows.readStatisticsRecord()
                        val recordId = mergeStatisticsRecord(incoming)
                        if (version == 1) {
                            backupSourceId("0")
                            mergeLegacyTotals(recordId, incoming.totalPlayedMs, incoming.playCount)
                        } else {
                            source.prepare("SELECT total_played_ms, play_count FROM playback_legacy_totals WHERE record_id = ?").use { legacy ->
                                legacy.bindLong(1, incoming.id)
                                if (legacy.step()) mergeLegacyTotals(recordId, legacy.getLong(0), legacy.getLong(1))
                            }
                        }
                        val dailySql = if (version == 1) {
                            "SELECT '0', local_date, total_played_ms, play_count FROM playback_daily_stats WHERE record_id = ?"
                        } else {
                            "SELECT s.source_key, d.local_date, d.total_played_ms, d.play_count " +
                                "FROM playback_source_daily_stats d JOIN playback_source s ON s.id = d.source_id WHERE d.record_id = ?"
                        }
                        source.prepare(dailySql).use { days ->
                            days.bindLong(1, incoming.id)
                            while (days.step()) {
                                currentCoroutineContext().ensureActive()
                                val sourceId = backupSourceId(days.getText(0))
                                usePrepared("INSERT INTO playback_source_daily_stats (record_id, source_id, local_date, total_played_ms, play_count) " +
                                    "VALUES (?, ?, ?, ?, ?) ON CONFLICT(record_id, source_id, local_date) DO UPDATE SET " +
                                    "total_played_ms = MAX(total_played_ms, excluded.total_played_ms), play_count = MAX(play_count, excluded.play_count)") {
                                    it.bindLong(1, recordId)
                                    it.bindLong(2, sourceId)
                                    it.bindText(3, days.getText(1))
                                    it.bindLong(4, days.getLong(2))
                                    it.bindLong(5, days.getLong(3))
                                    it.step()
                                }
                            }
                        }
                        executeSQL("DELETE FROM playback_daily_stats WHERE record_id = $recordId")
                        executeSQL("INSERT INTO playback_daily_stats SELECT record_id, local_date, SUM(total_played_ms), SUM(play_count) " +
                            "FROM playback_source_daily_stats WHERE record_id = $recordId GROUP BY record_id, local_date")
                        executeSQL(statisticsRebuildTotalsSql(recordId))
                        source.prepare("SELECT start_ms, end_ms FROM playback_watched_range WHERE record_id = ? ORDER BY start_ms").use { ranges ->
                            ranges.bindLong(1, incoming.id)
                            while (ranges.step()) {
                                currentCoroutineContext().ensureActive()
                                mergeStatisticsRange(recordId, ranges.getLong(0), ranges.getLong(1))
                            }
                        }
                    }
                }
                if (version >= 3) restoreLiveStatistics(source)
                // 汇总也必须能用 Long 表示；不能提交一个统计页无法读取的数据库。
                usePrepared("SELECT SUM(total_played_ms), SUM(play_count) FROM playback_stats") { it.step() }
                usePrepared("PRAGMA foreign_key_check") { check(!it.step()) { "备份数据关联无效" } }
                executeSQL("UPDATE playback_source SET is_active = 0 WHERE is_active <> 0")
                currentCoroutineContext().ensureActive()
            }
        }
    }
}

internal suspend fun PooledConnection.backupSourceId(key: String): Long {
    val existing = usePrepared("SELECT id FROM playback_source WHERE source_key = ?") {
        it.bindText(1, key)
        if (it.step()) it.getLong(0) else null
    }
    if (existing != null) return existing
    usePrepared("INSERT INTO playback_source (source_key, is_active) VALUES (?, 0)") {
        it.bindText(1, key)
        it.step()
    }
    return usePrepared("SELECT last_insert_rowid()") { check(it.step()); it.getLong(0) }
}

private suspend fun PooledConnection.mergeLegacyTotals(recordId: Long, playedMs: Long, playCount: Long) {
    usePrepared("INSERT INTO playback_legacy_totals (record_id, total_played_ms, play_count) VALUES (?, ?, ?) " +
        "ON CONFLICT(record_id) DO UPDATE SET total_played_ms = MAX(total_played_ms, excluded.total_played_ms), " +
        "play_count = MAX(play_count, excluded.play_count)") {
        it.bindLong(1, recordId)
        it.bindLong(2, playedMs)
        it.bindLong(3, playCount)
        it.step()
    }
}

private suspend fun PooledConnection.mergeStatisticsRecord(incoming: PlaybackStatisticsRecord): Long {
    val old = usePrepared("SELECT * FROM playback_stats WHERE avid = ? AND cid = ?") {
        it.bindLong(1, incoming.avid)
        it.bindLong(2, incoming.cid)
        if (it.step()) it.readStatisticsRecord() else null
    }
    val latest = if (old == null || incoming.lastViewedAtMs > old.lastViewedAtMs) incoming else old
    val fallback = if (latest === incoming) old else incoming
    val isVideo = latest.contentType == "video"
    val merged = latest.copy(
        bvid = latest.bvid?.takeIf(String::isNotBlank) ?: fallback?.bvid,
        title = latest.title.ifBlank { fallback?.title.orEmpty() },
        partTitle = latest.partTitle?.takeIf(String::isNotBlank) ?: fallback?.partTitle,
        partNumber = if (isVideo) latest.partNumber?.takeIf { it > 0 } ?: fallback?.partNumber else null,
        upMid = if (isVideo) latest.upMid?.takeIf { it > 0 } ?: fallback?.upMid else null,
        upName = if (isVideo) latest.upName?.takeIf(String::isNotBlank) ?: fallback?.upName else null,
        seasonId = if (isVideo) null else latest.seasonId?.takeIf { it > 0 } ?: fallback?.seasonId,
        episodeId = if (isVideo) null else latest.episodeId?.takeIf { it > 0 } ?: fallback?.episodeId,
        durationMs = latest.durationMs.takeIf { it > 0 } ?: fallback?.durationMs ?: 0L,
        firstViewedAtMs = minOf(incoming.firstViewedAtMs, old?.firstViewedAtMs ?: incoming.firstViewedAtMs),
        lastViewedAtMs = maxOf(incoming.lastViewedAtMs, old?.lastViewedAtMs ?: incoming.lastViewedAtMs),
        totalPlayedMs = old?.totalPlayedMs ?: 0L,
        playCount = old?.playCount ?: 0L,
    )
    val sql = if (old == null) {
        "INSERT INTO playback_stats (${statisticsRecordColumns.joinToString()}) VALUES (${statisticsRecordColumns.joinToString { "?" }})"
    } else {
        "UPDATE playback_stats SET ${statisticsRecordColumns.joinToString { "$it = ?" }} WHERE id = ?"
    }
    usePrepared(sql) {
        val values = listOf(merged.avid, merged.bvid, merged.cid, merged.contentType, merged.title, merged.partTitle,
            merged.partNumber?.toLong(), merged.upMid, merged.upName, merged.seasonId, merged.episodeId, merged.durationMs,
            merged.totalPlayedMs, merged.playCount, merged.firstViewedAtMs, merged.lastViewedAtMs)
        values.forEachIndexed { index, value ->
            when (value) {
                null -> it.bindNull(index + 1)
                is Long -> it.bindLong(index + 1, value)
                is String -> it.bindText(index + 1, value)
            }
        }
        if (old != null) it.bindLong(values.size + 1, old.id)
        it.step()
    }
    return old?.id ?: usePrepared("SELECT last_insert_rowid()") { check(it.step()); it.getLong(0) }
}

private fun SQLiteStatement.readStatisticsRecord() = PlaybackStatisticsRecord(
    id = getLong(0), avid = getLong(1), bvid = nullableText(2), cid = getLong(3), contentType = getText(4), title = getText(5),
    partTitle = nullableText(6), partNumber = nullableLong(7)?.toInt(), upMid = nullableLong(8), upName = nullableText(9),
    seasonId = nullableLong(10), episodeId = nullableLong(11), durationMs = getLong(12), totalPlayedMs = getLong(13),
    playCount = getLong(14), firstViewedAtMs = getLong(15), lastViewedAtMs = getLong(16),
)

private fun SQLiteStatement.nullableText(index: Int): String? = if (isNull(index)) null else getText(index)
private fun SQLiteStatement.nullableLong(index: Int): Long? = if (isNull(index)) null else getLong(index)

private suspend fun PooledConnection.mergeStatisticsRange(recordId: Long, startMs: Long, endMs: Long) {
    var start = startMs
    var end = endMs
    while (true) {
        currentCoroutineContext().ensureActive()
        val overlap = usePrepared("SELECT MIN(start_ms), MAX(end_ms) FROM playback_watched_range " +
            "WHERE record_id = ? AND start_ms <= ? AND end_ms >= ?") {
            it.bindLong(1, recordId)
            it.bindLong(2, end)
            it.bindLong(3, start)
            check(it.step())
            minOf(start, it.nullableLong(0) ?: start) to maxOf(end, it.nullableLong(1) ?: end)
        }
        if (overlap.first == start && overlap.second == end) break
        start = overlap.first
        end = overlap.second
    }
    usePrepared("DELETE FROM playback_watched_range WHERE record_id = ? AND start_ms <= ? AND end_ms >= ?") {
        it.bindLong(1, recordId)
        it.bindLong(2, end)
        it.bindLong(3, start)
        it.step()
    }
    usePrepared("INSERT INTO playback_watched_range (record_id, start_ms, end_ms) VALUES (?, ?, ?)") {
        it.bindLong(1, recordId)
        it.bindLong(2, start)
        it.bindLong(3, end)
        it.step()
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

private suspend fun StatisticsDatabase.validateStatisticsBackup(source: SQLiteConnection): Int {
    val version = source.prepare("PRAGMA user_version").use { check(it.step()); it.getLong(0) }
    check(version in 1L..3L) { "备份数据库版本或结构不兼容" }
    val tables = when (version) {
        1L -> statisticsV1Tables
        2L -> statisticsBackupTables
        else -> statisticsBackupTables + liveStatisticsTables
    }
    useReaderConnection { current ->
        val identitySql = "SELECT identity_hash FROM room_master_table WHERE id = 42"
        val expectedIdentity = when (version) {
            1L -> listOf(listOf("c3c0d2345654df6dff01732f82564e95"))
            2L -> listOf(listOf("836b7484ad10262e1907251647092cd5"))
            else -> current.usePrepared(identitySql) { it.readBackupSchemaRows() }
        }
        check(expectedIdentity == source.prepare(identitySql).use { it.readBackupSchemaRows() }) { "备份数据库版本或结构不兼容" }
        val schemaSql = "SELECT type, name, tbl_name, sql FROM sqlite_master WHERE tbl_name IN " +
            "(${tables.joinToString { "'$it'" }}) ORDER BY type, name"
        val expected = current.usePrepared(schemaSql) { it.readBackupSchemaRows(normalizeSql = true) }
        val actual = source.prepare(schemaSql).use { it.readBackupSchemaRows(normalizeSql = true) }
        check(expected == actual) { "备份数据库版本或结构不兼容" }
    }
    source.prepare("PRAGMA integrity_check").use {
        check(it.step() && it.getText(0) == "ok" && !it.step()) { "备份数据库已损坏" }
    }
    source.prepare("PRAGMA foreign_key_check").use { check(!it.step()) { "备份数据关联无效" } }
    for (table in tables) {
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
        source.requireNoRows("SELECT 1 FROM $table WHERE ${invalidFields.joinToString(" OR ")} LIMIT 1", "备份包含无效的统计字段")
    }
    val invalidQueries = mutableListOf(
        "SELECT 1 FROM playback_stats WHERE id <= 0 OR avid <= 0 OR cid <= 0 OR duration_ms < 0 " +
            "OR total_played_ms < 0 OR play_count < 0 OR content_type NOT IN ('video', 'media') LIMIT 1",
        "SELECT 1 FROM playback_watched_range WHERE start_ms < 0 OR end_ms <= start_ms LIMIT 1",
        "SELECT 1 FROM playback_daily_stats WHERE total_played_ms < 0 OR play_count < 0 LIMIT 1",
    )
    if (version >= 2L) {
        invalidQueries += listOf(
            "SELECT 1 FROM playback_source WHERE id <= 0 OR is_active NOT IN (0, 1) OR (source_key = '0' AND is_active <> 0) LIMIT 1",
            "SELECT 1 FROM playback_source WHERE is_active = 1 LIMIT 1 OFFSET 1",
            "SELECT 1 FROM playback_legacy_totals WHERE total_played_ms < 0 OR play_count < 0 " +
                "OR NOT EXISTS (SELECT 1 FROM playback_source WHERE source_key = '0') LIMIT 1",
            "SELECT 1 FROM playback_source_daily_stats WHERE total_played_ms < 0 OR play_count < 0 LIMIT 1",
        )
        source.prepare("SELECT source_key FROM playback_source").use {
            while (it.step()) {
                currentCoroutineContext().ensureActive()
                val key = it.getText(0)
                check(key == "0" || runCatching { Uuid.parse(key).toString() == key }.getOrDefault(false)) { "备份包含无效来源" }
            }
        }
    }
    for (sql in invalidQueries) source.requireNoRows(sql, "备份包含无效的统计数据")
    val dateSql = "SELECT local_date FROM playback_daily_stats" +
        if (version >= 2L) " UNION SELECT local_date FROM playback_source_daily_stats" else " GROUP BY local_date"
    source.prepare(dateSql).use { dates ->
        while (dates.step()) {
            currentCoroutineContext().ensureActive()
            val date = dates.getText(0)
            check(runCatching { LocalDate.parse(date).toString() == date }.getOrDefault(false)) { "备份包含无效日期" }
        }
    }
    if (version >= 2L) {
        source.requireNoRows("SELECT 1 FROM playback_stats p WHERE (p.total_played_ms, p.play_count) <> " +
            "(${statisticsRecordTotalsSql("p.id")}) LIMIT 1", "备份累计缓存与来源数据不一致")
        val daily = "SELECT record_id, local_date, SUM(total_played_ms) AS total_played_ms, SUM(play_count) AS play_count " +
            "FROM playback_source_daily_stats GROUP BY record_id, local_date"
        source.requireNoRows("SELECT 1 FROM playback_daily_stats c LEFT JOIN ($daily) d " +
            "ON c.record_id = d.record_id AND c.local_date = d.local_date WHERE d.record_id IS NULL " +
            "OR c.total_played_ms <> d.total_played_ms OR c.play_count <> d.play_count LIMIT 1", "备份每日缓存与来源数据不一致")
        source.requireNoRows("SELECT 1 FROM ($daily) d LEFT JOIN playback_daily_stats c " +
            "ON c.record_id = d.record_id AND c.local_date = d.local_date WHERE c.record_id IS NULL LIMIT 1", "备份缺少每日缓存")
    }
    if (version >= 3L) source.validateLiveStatistics()
    source.prepare("SELECT SUM(total_played_ms), SUM(play_count) FROM playback_stats").use { it.step() }
    return version.toInt()
}

internal suspend fun SQLiteConnection.requireNoRows(sql: String, message: String) {
    currentCoroutineContext().ensureActive()
    prepare(sql).use { check(!it.step()) { message } }
}

private fun SQLiteStatement.readBackupSchemaRows(normalizeSql: Boolean = false): List<List<String?>> = buildList {
    while (step()) add(List(getColumnCount()) { index ->
        if (isNull(index)) null else getText(index).let { value ->
            if (normalizeSql && index == 3) value.replace("IF NOT EXISTS ", "").replace(Regex("[\\s`]+"), "").lowercase() else value
        }
    })
}
