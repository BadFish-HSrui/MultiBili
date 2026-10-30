package tv.hsrui.bolo.storage.appData.liveStatistics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import tv.hsrui.bolo.storage.appData.playbackStatistics.addPlaybackStatistics
import tv.hsrui.bolo.storage.statistics.StatisticsDatabase
import tv.hsrui.network.liveFeature.room.LiveRoomInfoData
import kotlin.time.Clock
import kotlin.time.Duration

/** 会话状态归 Main，排队保存与播放器页面的生命周期分离。 */
class LiveStatisticsManager internal constructor(
    private val database: () -> StatisticsDatabase,
    private val writeMutex: Mutex,
    private val canOpen: () -> Boolean,
    private val wallTime: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = mutableSetOf<Session>()
    private var active: Session? = null
    internal val hasActiveSession get() = active != null
    private var writeJob: Job? = null
    private var writeRequested = false
    private val mutableSaveError = MutableStateFlow<String?>(null)
    val saveError = mutableSaveError.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                delay(30_000L)
                pending.toList().forEach { it.checkpoint() }
            }
        }
    }

    fun open(room: LiveRoomInfoData): Session? {
        if (!canOpen() || room.roomId <= 0L || room.upMid <= 0L || room.upName.isBlank()) return null
        active?.takeIf { it.roomId == room.roomId }?.let {
            it.updateMetadata(room)
            return it
        }
        active?.close()
        return Session(room).also {
            active = it
            pending.add(it)
            it.checkpoint()
        }
    }

    suspend fun get(roomId: Long): LiveStatisticsRecord? = database().liveStatisticsDao().get(roomId)
    suspend fun summary(): LiveStatisticsSummary = database().liveStatisticsDao().summary()
    suspend fun totalPlayedMs(): Long = summary().totalPlayedMs

    suspend fun dailyStats(
        fromDate: LocalDate,
        untilDateExclusive: LocalDate,
        roomId: Long? = null,
        upMid: Long? = null,
    ): List<LiveDailyStats> {
        require(fromDate <= untilDateExclusive)
        if (fromDate == untilDateExclusive) return emptyList()
        return database().liveStatisticsDao().dailyStats(fromDate.toString(), untilDateExclusive.toString(), roomId, upMid)
    }

    suspend fun rankings(
        fromDate: LocalDate? = null,
        untilDateExclusive: LocalDate? = null,
        limit: Int = 5,
    ): LiveRankings {
        require((fromDate == null) == (untilDateExclusive == null))
        require(limit > 0)
        if (fromDate != null && untilDateExclusive != null) require(fromDate <= untilDateExclusive)
        return database().liveStatisticsDao().rankings(fromDate?.toString(), untilDateExclusive?.toString(), limit)
    }

    suspend fun statistics(
        fromDate: LocalDate,
        untilDateExclusive: LocalDate,
        limit: Int = 5,
        includeRankings: Boolean = true,
    ): LiveStatisticsSnapshot {
        require(fromDate <= untilDateExclusive && limit > 0)
        return database().liveStatisticsDao().statistics(fromDate.toString(), untilDateExclusive.toString(), limit, includeRankings)
    }

    suspend fun historyPage(
        type: LiveHistoryType,
        cursor: LiveHistoryCursor? = null,
        limit: Int = 50,
        sort: LiveHistorySort = LiveHistorySort.Time,
        descending: Boolean = true,
    ): List<LiveHistoryItem> {
        require(limit > 0)
        return database().liveStatisticsDao().history(
            type == LiveHistoryType.Rooms, cursor?.sortValue, cursor?.id, limit, sort.name, descending,
        )
    }

    suspend fun flush() = withContext(Dispatchers.Main.immediate) {
        val sessions = pending.toList()
        sessions.forEach {
            it.closingJob?.join()
            it.capturePlayback()
        }
        val targets = sessions.map { it to it.revision }
        requestWrite()?.join()
        mutableSaveError.value?.let { error(it) }
        check(targets.all { (session, revision) -> session.savedRevision >= revision }) { "直播记录尚未保存完成" }
    }

    private fun requestWrite(): Job? {
        writeRequested = true
        writeJob?.let { return it }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val failed = mutableSetOf<Long>()
            var failure: String? = null
            try {
                withPlaybackReportBackgroundExecution {
                    do {
                        writeRequested = false
                        val blocked = failed.toMutableSet()
                        for (session in pending.toList()) {
                            if (session.roomId in blocked) continue
                            if (session.closingJob?.isActive == true) {
                                blocked.add(session.roomId)
                                continue
                            }
                            try {
                                writeMutex.withLock { session.save() }
                                if (session.closed && !session.dirty) pending.remove(session)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                failed.add(session.roomId)
                                blocked.add(session.roomId)
                                failure = error.message ?: "直播记录保存失败"
                                println("直播记录保存失败：$failure")
                            }
                        }
                    } while (writeRequested)
                    mutableSaveError.value = failure
                }
            } finally {
                writeJob = null
            }
        }
        writeJob = job
        job.start()
        return job
    }

    inner class Session internal constructor(room: LiveRoomInfoData) {
        val roomId = room.roomId
        private var metadata = wallTime().let { now ->
            LiveStatisticsRecord(
                roomId = room.roomId,
                shortRoomId = room.shortRoomId,
                upMid = room.upMid,
                upName = room.upName,
                firstViewedAtMs = now,
                lastViewedAtMs = now,
            )
        }
        private var counted = false
        private var playedTime = Duration.ZERO
        private var generation = -1L
        private var sourceTime = Duration.ZERO
        private val sourceDays = mutableMapOf<String, Duration>()
        private val dailyTotals = mutableMapOf<String, LiveDailyStats>()
        private val baseDays = mutableMapOf<String, LiveDailyStats>()
        private val pendingDates = mutableSetOf<String>()
        private var writerSourceId: Long? = null
        private var observe: (() -> BoloPlaybackObservation?)? = null
        internal var revision = 1L
            private set
        internal var savedRevision = 0L
            private set
        internal var dirty = true
            private set
        internal var closed = false
            private set
        internal var closingJob: Job? = null
            private set

        internal fun updateMetadata(room: LiveRoomInfoData) {
            val updated = metadata.copy(
                shortRoomId = room.shortRoomId ?: metadata.shortRoomId,
                upMid = room.upMid,
                upName = room.upName,
            )
            if (updated != metadata) {
                metadata = updated
                revision++
                dirty = true
                checkpoint()
            }
        }

        internal fun bindPlayback(observe: () -> BoloPlaybackObservation?) {
            this.observe = observe
        }

        internal fun capturePlayback() {
            if (!closed) observe?.invoke()?.let(::updatePlayback)
        }

        internal fun updatePlayback(observation: BoloPlaybackObservation) {
            if (closed || observation.generation < generation) return
            if (observation.generation != generation) {
                generation = observation.generation
                sourceTime = Duration.ZERO
                sourceDays.clear()
            }
            val elapsed = observation.playedTime - sourceTime
            if (elapsed <= Duration.ZERO) return
            var allocated = playedTime
            for ((date, total) in observation.dailyPlayedTime) {
                val increment = total - (sourceDays[date] ?: Duration.ZERO)
                if (increment <= Duration.ZERO) continue
                val previousMs = allocated.inWholeMilliseconds
                allocated += increment
                addDay(date, allocated.inWholeMilliseconds - previousMs, 0L)
                sourceDays[date] = total
            }
            if (!counted) {
                val date = checkNotNull(observation.firstStartedLocalDate)
                addDay(date, 0L, 1L)
                counted = true
            }
            playedTime += elapsed
            sourceTime = observation.playedTime
            metadata = metadata.copy(lastViewedAtMs = maxOf(metadata.lastViewedAtMs, observation.viewedAtMs))
            revision++
            dirty = true
        }

        private fun addDay(date: String, playedMs: Long, count: Long) {
            if (playedMs == 0L && count == 0L) return
            val old = dailyTotals[date] ?: LiveDailyStats(date)
            dailyTotals[date] = old.copy(
                totalPlayedMs = addPlaybackStatistics(old.totalPlayedMs, playedMs),
                playCount = addPlaybackStatistics(old.playCount, count),
            )
            pendingDates.add(date)
        }

        fun checkpoint() {
            capturePlayback()
            if (dirty) requestWrite()
        }

        /** 接收控制器排队的最终快照后关闭；下一次进入的保存不能越过这次收尾。 */
        internal fun closeAfter(capture: suspend () -> Unit) {
            if (closed || closingJob != null) return
            capturePlayback()
            observe = null
            if (active === this) active = null
            val job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    withPlaybackReportBackgroundExecution { capture() }
                } finally {
                    closingJob = null
                    close()
                }
            }
            closingJob = job
            job.start()
        }

        fun close() {
            if (closed || closingJob != null) return
            capturePlayback()
            closed = true
            observe = null
            if (active === this) active = null
            requestWrite()
        }

        internal suspend fun save() {
            if (!dirty) return
            val snapshot = metadata
            val snapshotRevision = revision
            val days = pendingDates.map { dailyTotals.getValue(it) }
            pendingDates.clear()
            dirty = false
            try {
                val dao = database().liveStatisticsDao()
                if (days.isNotEmpty() && writerSourceId == null) {
                    writerSourceId = database().playbackStatisticsDao().writerSourceId()
                }
                val missing = days.map { it.localDate }.filterNot(baseDays::containsKey)
                if (missing.isNotEmpty()) {
                    val saved = dao.sourceDays(roomId, checkNotNull(writerSourceId), missing).associateBy { it.localDate }
                    missing.forEach { baseDays[it] = saved[it] ?: LiveDailyStats(it) }
                }
                dao.save(snapshot, writerSourceId, days.map { day ->
                    val base = baseDays.getValue(day.localDate)
                    day.copy(
                        totalPlayedMs = addPlaybackStatistics(base.totalPlayedMs, day.totalPlayedMs),
                        playCount = addPlaybackStatistics(base.playCount, day.playCount),
                    )
                })
                savedRevision = snapshotRevision
            } catch (error: Exception) {
                pendingDates.addAll(days.map { it.localDate })
                dirty = true
                throw error
            }
        }
    }
}
