package tv.hsrui.network.wbi

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.invoke
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class WbiManager(private val wbiStorage: KSafe) {
    private var lastUpdateTime by wbiStorage(0L, key = "last_update_time")
    private var wbiKey by wbiStorage("", key = "wbi_key")
    private var webID by wbiStorage("", key = "web_id")
    private val updateMutex = Mutex()

    suspend fun getWbiKey(): String {
        tryUpdate()
        return wbiKey
    }

    suspend fun getWebID(): String {
        tryUpdate()
        return webID
    }

    suspend fun tryUpdate(force: Boolean = false) {
        updateMutex.withLock {
            if (!force && isKeyTimeValid()) return
            val updatedWbiKey = fetchWbiString()
            val updatedWebID = fetchWebID()
            wbiKey = updatedWbiKey
            webID = updatedWebID
            lastUpdateTime = Clock.System.now().epochSeconds
        }
    }

    private fun isKeyTimeValid(): Boolean {
        val now = Clock.System.now()
        val lastUpdate = Instant.fromEpochSeconds(lastUpdateTime)
        val timeZone = TimeZone.currentSystemDefault()

        return when {
            (now - lastUpdate) > 12.hours -> false
            now.toLocalDateTime(timeZone).date != lastUpdate.toLocalDateTime(timeZone).date -> false
            else -> true
        }
    }
}
