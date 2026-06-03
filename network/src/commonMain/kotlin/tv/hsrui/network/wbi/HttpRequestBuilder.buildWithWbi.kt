package tv.hsrui.network.wbi

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLParameter
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.utils.toMD5
import kotlin.time.Clock

suspend fun HttpRequestBuilder.buildWithWbi(block: HttpRequestBuilder.() -> Unit) {
    this.block()

    val wbiManager:WbiManager = getKoin().get()
    val wbiKey = wbiManager.getWbiKey()

    val params = url.parameters.entries()
        .associate { it.key to it.value.first() }
        .toMutableMap()

    val currentTime = Clock.System.now().epochSeconds.toString()
    params["wts"] = currentTime

    val queryForSign = params.entries
        .sortedBy { it.key }
        .joinToString("&") { (key, value) ->
            val filteredValue = value.replace(Regex("[!'()*]"), "")
            "${key.encodeURLParameter()}=${filteredValue.encodeURLParameter()}"
        }

    val wRid = (queryForSign + wbiKey).toMD5()

    parameter("wts", currentTime)
    parameter("w_rid", wRid)
}