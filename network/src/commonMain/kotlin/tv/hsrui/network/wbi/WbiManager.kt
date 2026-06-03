package tv.hsrui.network.wbi

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.invoke
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class WbiManager(private val wbiStorage: KSafe) {
    private var lastUpdateTime by wbiStorage(0L)
    private var wbiKey by wbiStorage("")

    suspend fun getWbiKey(): String {
        if (!isKeyTimeValid()) {
            wbiKey = fetchWbiString()
            lastUpdateTime = Clock.System.now().epochSeconds
        }

        return wbiKey
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