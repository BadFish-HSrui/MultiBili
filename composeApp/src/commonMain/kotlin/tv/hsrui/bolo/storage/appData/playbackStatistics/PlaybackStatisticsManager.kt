package tv.hsrui.bolo.storage.appData.playbackStatistics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import tv.hsrui.bolo.player.base.BoloPlaybackObservation
import tv.hsrui.bolo.player.withPlaybackReportBackgroundExecution
import tv.hsrui.bolo.storage.statistics.StatisticsDatabase
import tv.hsrui.network.feature.media.MediaEpisode
import tv.hsrui.network.feature.media.MediaSeasonData
import tv.hsrui.network.feature.video.VideoInfoData
import kotlin.time.Clock
import kotlin.time.Duration

/** 播放状态在 Main 同步累计；数据库事务串行执行，不归播放页的协程作用域所有。 */
class PlaybackStatisticsManager internal constructor(
    private val database: () -> StatisticsDatabase,
    private val wallTime: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writeMutex = Mutex()
    private var writeJob: Job? = null
    private var writeRequested = false
    private val mutableSaveError = MutableStateFlow<String?>(null)
    val saveError = mutableSaveError.asStateFlow()
    private val pendingSessions = mutableSetOf<Session>()
    private var active: Session? = null
    private val mutableWatchedRanges = MutableStateFlow<List<PlaybackWatchedRange>>(emptyList())
    val watchedRanges = mutableWatchedRanges.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                delay(30_000L)
                pendingSessions.toList().forEach { it.checkpoint() }
            }
        }
    }

    fun open(video: VideoInfoData): Session? {
        val part = video.parts.firstOrNull { it.cid == video.cid }
        return open(PlaybackStatisticsMetadata(
            avid = video.avid, cid = video.cid, bvid = video.bvid.takeIf(String::isNotBlank),
            contentType = "video", title = video.title,
            partTitle = part?.title?.takeIf(String::isNotBlank), partNumber = part?.pageNumber?.takeIf { it > 0 },
            upMid = video.upMid.takeIf { it > 0L }, upName = video.upName.takeIf(String::isNotBlank),
            durationMs = (part?.duration?.toLong() ?: 0L).coerceAtLeast(0L) * 1_000L,
        ))
    }

    fun open(media: MediaSeasonData, episode: MediaEpisode): Session? = open(PlaybackStatisticsMetadata(
        avid = episode.avid, cid = episode.cid, bvid = episode.bvid.takeIf(String::isNotBlank),
        contentType = "media", title = media.title, partTitle = episode.displayTitle,
        seasonId = media.seasonId.takeIf { it > 0L }, episodeId = episode.episodeId.takeIf { it > 0L },
    ))

    private fun open(metadata: PlaybackStatisticsMetadata): Session? {
        if (metadata.avid <= 0L || metadata.cid <= 0L) return null
        val current = active
        if (current != null && current.matches(metadata)) {
            current.updateMetadata(metadata)
            return current
        }
        current?.close()
        return Session(metadata).also {
            active = it
            mutableWatchedRanges.value = emptyList()
            pendingSessions.add(it)
            it.checkpoint()
        }
    }

    suspend fun page(cursor: PlaybackStatisticsCursor? = null, limit: Int = 50): List<PlaybackStatisticsRecord> {
        require(limit > 0)
        val dao = database().playbackStatisticsDao()
        return if (cursor == null) dao.firstPage(limit) else dao.nextPage(cursor.lastViewedAtMs, cursor.id, limit)
    }

    suspend fun get(avid: Long, cid: Long): PlaybackStatisticsRecord? =
        database().playbackStatisticsDao().get(avid, cid)

    suspend fun totalPlayedMs(avid: Long): Long =
        database().playbackStatisticsDao().totalPlayedMs(avid)

    /** 在同一读事务中取得累计概览和日期范围内的趋势、时长排行；需要最新数据时先调用 flush。 */
    suspend fun statistics(
        fromDate: LocalDate,
        untilDateExclusive: LocalDate,
        limit: Int = 5,
    ): PlaybackStatisticsSnapshot {
        require(fromDate <= untilDateExclusive)
        require(limit > 0)
        return database().playbackStatisticsDao().statistics(fromDate.toString(), untilDateExclusive.toString(), limit)
    }

    /** 按记录时的本地日期查询已落盘统计；不补齐无播放的日期，也不触发保存。 */
    suspend fun dailyStats(
        fromDate: LocalDate,
        untilDateExclusive: LocalDate,
        avid: Long? = null,
        cid: Long? = null,
        upMid: Long? = null,
    ): List<PlaybackDailyStats> {
        require(fromDate <= untilDateExclusive)
        require(cid == null || avid != null)
        if (fromDate == untilDateExclusive) return emptyList()
        return database().playbackStatisticsDao().dailyStats(
            fromDate.toString(), untilDateExclusive.toString(), avid, cid, upMid,
        )
    }

    suspend fun ranges(avid: Long, cid: Long): List<PlaybackWatchedRange> {
        val dao = database().playbackStatisticsDao()
        val record = dao.get(avid, cid) ?: return emptyList()
        return dao.ranges(record.id).map { PlaybackWatchedRange(it.startMs, it.endMs) }
    }

    /** 等待已累计的数据落盘；失败保留脏数据并向调用方报告，不无限重试。 */
    suspend fun flush() = withContext(Dispatchers.Main.immediate) {
        pendingSessions.toList().forEach { it.capturePlayback() }
        requestWrite()?.join()
        mutableSaveError.value?.let { error(it) }
        check(pendingSessions.none { it.dirty }) { "播放记录尚未保存完成" }
    }

    private fun requestWrite(): Job? {
        writeRequested = true
        writeJob?.let { return it }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val failed = mutableSetOf<Pair<Long, Long>>()
            var failure: String? = null
            try {
                withPlaybackReportBackgroundExecution {
                    do {
                        writeRequested = false
                        for (session in pendingSessions.toList()) {
                            if (session.key in failed) continue
                            try {
                                writeMutex.withLock { session.save() }
                                if (session.closed && !session.dirty) pendingSessions.remove(session)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                // 同一视频的新会话不能越过失败的旧会话；其他视频和查询仍可继续。
                                failed.add(session.key)
                                failure = error.message ?: "播放记录保存失败"
                                println("播放记录保存失败：$failure")
                            }
                        }
                    } while (writeRequested)
                    mutableSaveError.value = failure
                }
            } finally { writeJob = null }
        }
        writeJob = job
        job.start()
        return job
    }

    internal fun requestFlush(execute: suspend (suspend () -> Unit) -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                execute { flush() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                println("播放记录收尾失败：${error.message}")
            }
        }
    }

    internal suspend fun close() = withContext(Dispatchers.Main.immediate) {
        active?.close()
        flush()
        scope.cancel()
    }

    inner class Session internal constructor(private var metadata: PlaybackStatisticsMetadata) {
        private val firstViewedAtMs = wallTime()
        private var lastViewedAtMs = firstViewedAtMs
        private var playedTime = Duration.ZERO
        private var playCount = 0L
        private var sourceGeneration = -1L
        private var sourcePlayedTime = Duration.ZERO
        private var sourceStartedPlayCount = 0L
        private var sourceEndedPlayCount = 0L
        private val sourceDailyPlayedTime = mutableMapOf<String, Duration>()
        private val sourceDailyStartedPlayCount = mutableMapOf<String, Long>()
        private var continuesPreviousPlay = false
        private var replayPending = false
        private var continuity = -1L
        private var lastPositionMs: Long? = null
        private var lastPositionAdvanceMs = 0.0
        private var sourcePositionAdvanceMs = 0.0
        private var playing = false
        internal var dirty = true
            private set
        internal var closed = false
            private set
        private var basePlayedMs: Long? = null
        private var basePlayCount: Long? = null
        private val baseDailyStats = mutableMapOf<String, PlaybackDailyStats>()
        private val dailyTotals = mutableMapOf<String, PlaybackDailyStats>()
        private val pendingDates = mutableSetOf<String>()
        private var rangesLoaded = false
        private var pendingRanges = emptyList<PlaybackWatchedRange>()
        private var watched = emptyList<PlaybackWatchedRange>()
        private var observe: (() -> BoloPlaybackObservation?)? = null
        internal val key get() = metadata.avid to metadata.cid

        internal fun bindPlayback(observe: () -> BoloPlaybackObservation?) {
            this.observe = observe
        }

        internal fun capturePlayback() {
            if (!closed) observe?.invoke()?.let { updatePlayback(it, elapsedOnly = true) }
        }

        internal fun matches(value: PlaybackStatisticsMetadata): Boolean =
            !closed && metadata.avid == value.avid && metadata.cid == value.cid

        internal fun updateMetadata(value: PlaybackStatisticsMetadata) {
            val updated = value.copy(durationMs = value.durationMs.takeIf { it > 0L } ?: metadata.durationMs)
            if (updated != metadata) {
                metadata = updated
                dirty = true
                checkpoint()
            }
        }

        internal fun updatePlayback(observation: BoloPlaybackObservation, elapsedOnly: Boolean = false) {
            if (closed || active !== this || observation.generation < sourceGeneration) return
            if (observation.generation != sourceGeneration) {
                continuesPreviousPlay = playCount > 0L && !replayPending
                sourceGeneration = observation.generation
                sourcePlayedTime = Duration.ZERO
                sourceStartedPlayCount = 0L
                sourceEndedPlayCount = 0L
                sourceDailyPlayedTime.clear()
                sourceDailyStartedPlayCount.clear()
                sourcePositionAdvanceMs = 0.0
                lastPositionMs = null
            }
            // 周期采样可能先于排队的位置事件被读取；耗时只取正增量，位置仍按事件顺序处理。
            val elapsed = observation.playedTime - sourcePlayedTime
            if (elapsed > Duration.ZERO) {
                var allocatedTime = playedTime
                for ((date, total) in observation.dailyPlayedTime) {
                    val increment = total - (sourceDailyPlayedTime[date] ?: Duration.ZERO)
                    if (increment <= Duration.ZERO) continue
                    val previousMs = allocatedTime.inWholeMilliseconds
                    allocatedTime += increment
                    // 共享会话余量，跨日及换源也不因分别截断毫秒而丢失总时长。
                    updateDailyStats(date, allocatedTime.inWholeMilliseconds - previousMs, 0L)
                    sourceDailyPlayedTime[date] = total
                }
                playedTime += elapsed
                sourcePlayedTime = observation.playedTime
                lastViewedAtMs = maxOf(lastViewedAtMs, observation.viewedAtMs)
                dirty = true
            }
            val starts = observation.startedPlayCount - sourceStartedPlayCount
            if (starts > 0L) {
                // 同一会话换源／重建延续当前一次播放；EOF 后重建则是新一次播放。
                val continues = sourceStartedPlayCount == 0L && continuesPreviousPlay
                playCount += starts - if (continues) 1L else 0L
                for ((date, count) in observation.dailyStartedPlayCount) {
                    val increment = count - (sourceDailyStartedPlayCount[date] ?: 0L)
                    if (increment <= 0L) continue
                    val discount = if (continues && date == observation.firstStartedLocalDate) 1L else 0L
                    updateDailyStats(date, 0L, increment - discount)
                    sourceDailyStartedPlayCount[date] = count
                }
                sourceStartedPlayCount = observation.startedPlayCount
                dirty = true
            }
            sourceEndedPlayCount = maxOf(sourceEndedPlayCount, observation.endedPlayCount)
            if (sourceStartedPlayCount > 0L) {
                // checkpoint 可能先读到重播；迟到的 EOF 不能把新一次播放标成待重播。
                replayPending = sourceEndedPlayCount == sourceStartedPlayCount
            }
            if (observation.durationMs > 0L && metadata.durationMs != observation.durationMs) {
                metadata = metadata.copy(durationMs = observation.durationMs)
                dirty = true
            }
            if (elapsedOnly) return
            if (observation.continuity != continuity) {
                continuity = observation.continuity
                lastPositionMs = null
            }
            val position = observation.positionMs
            if (position != null) {
                val previous = lastPositionMs
                val advance = observation.positionAdvanceMs - lastPositionAdvanceMs
                if (previous != null && position > previous && advance > 0.0 && position - previous <= advance + 1_500.0) {
                    val range = PlaybackWatchedRange(previous, position)
                    pendingRanges = mergeRanges(pendingRanges, listOf(range))
                    watched = mergeRanges(watched, listOf(range))
                    mutableWatchedRanges.value = watched
                    dirty = true
                }
                // 暂停及 seek 确认时的真实位置可作为恢复起点，不用状态快照中的旧位置替代。
                lastPositionMs = position
                lastPositionAdvanceMs = observation.positionAdvanceMs
            }
            if (observation.positionAdvanceMs >= sourcePositionAdvanceMs) {
                val stopped = playing && !observation.playing
                sourcePositionAdvanceMs = observation.positionAdvanceMs
                playing = observation.playing
                if (stopped) checkpoint()
            }
        }

        private fun updateDailyStats(date: String, playedMs: Long, starts: Long) {
            if (playedMs == 0L && starts == 0L) return
            val old = dailyTotals[date] ?: PlaybackDailyStats(date)
            dailyTotals[date] = old.copy(totalPlayedMs = old.totalPlayedMs + playedMs, playCount = old.playCount + starts)
            pendingDates.add(date)
            dirty = true
        }

        fun interrupt() {
            if (closed) return
            capturePlayback()
            lastPositionMs = null
            checkpoint()
        }

        fun close() {
            if (closed) return
            capturePlayback()
            closed = true
            observe = null
            if (active === this) {
                active = null
                mutableWatchedRanges.value = emptyList()
            }
            checkpoint()
        }

        internal fun checkpoint(): Job? {
            capturePlayback()
            return if (dirty || !rangesLoaded) requestWrite() else writeJob
        }

        internal suspend fun save() {
            if (!dirty && rangesLoaded) return
            val snapshotMetadata = metadata
            val snapshotTime = lastViewedAtMs
            val snapshotPlayedMs = playedTime.inWholeMilliseconds
            val snapshotPlayCount = playCount
            val snapshotDaily = pendingDates.map { dailyTotals.getValue(it) }
            val snapshotRanges = pendingRanges
            pendingRanges = emptyList()
            pendingDates.clear()
            dirty = false
            try {
                val dao = database().playbackStatisticsDao()
                // 单一写任务中先固定基值；即使提交后的返回被中断，重试也不以新总量重新加算。
                if (basePlayedMs == null || basePlayCount == null) {
                    val old = dao.get(snapshotMetadata.avid, snapshotMetadata.cid)
                    basePlayedMs = old?.totalPlayedMs ?: 0L
                    basePlayCount = old?.playCount ?: 0L
                }
                val missingDates = snapshotDaily.map { it.localDate }.filterNot(baseDailyStats::containsKey)
                if (missingDates.isNotEmpty()) {
                    val saved = dao.dailyStatsForRecord(snapshotMetadata.avid, snapshotMetadata.cid, missingDates)
                        .associateBy { it.localDate }
                    missingDates.forEach { baseDailyStats[it] = saved[it] ?: PlaybackDailyStats(it) }
                }
                val absoluteDaily = snapshotDaily.map { day ->
                    val base = baseDailyStats.getValue(day.localDate)
                    day.copy(totalPlayedMs = base.totalPlayedMs + day.totalPlayedMs, playCount = base.playCount + day.playCount)
                }
                val record = dao.save(snapshotMetadata, firstViewedAtMs, snapshotTime,
                    checkNotNull(basePlayedMs), snapshotPlayedMs, checkNotNull(basePlayCount), snapshotPlayCount,
                    absoluteDaily, snapshotRanges)
                if (!rangesLoaded) {
                    watched = mergeRanges(dao.ranges(record.id).map { PlaybackWatchedRange(it.startMs, it.endMs) }, watched)
                    rangesLoaded = true
                    if (active === this) mutableWatchedRanges.value = watched
                }
            } catch (error: Exception) {
                pendingRanges = mergeRanges(snapshotRanges, pendingRanges)
                pendingDates.addAll(snapshotDaily.map { it.localDate })
                dirty = true
                throw error
            }
        }
    }
}

private fun mergeRanges(first: List<PlaybackWatchedRange>, second: List<PlaybackWatchedRange>): List<PlaybackWatchedRange> {
    if (second.isEmpty()) return first
    val result = ArrayList<PlaybackWatchedRange>(first.size + second.size)
    var firstIndex = 0
    var secondIndex = 0
    while (firstIndex < first.size || secondIndex < second.size) {
        val range = if (secondIndex >= second.size ||
            (firstIndex < first.size && first[firstIndex].startMs <= second[secondIndex].startMs)) {
            first[firstIndex++]
        } else second[secondIndex++]
        val last = result.lastOrNull()
        if (last != null && range.startMs <= last.endMs) {
            result[result.lastIndex] = PlaybackWatchedRange(last.startMs, maxOf(last.endMs, range.endMs))
        } else result.add(range)
    }
    return result
}
