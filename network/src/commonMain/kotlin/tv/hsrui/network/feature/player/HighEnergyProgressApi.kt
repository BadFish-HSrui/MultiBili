package tv.hsrui.network.feature.player

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import tv.hsrui.network.client.ApiClient

private val highEnergyProgressJson = Json { ignoreUnknownKeys = true }

suspend fun fetchHighEnergyProgress(avid: Long, cid: Long): HighEnergyProgressData? = withTimeout(10_000L) {
    require(avid > 0L && cid > 0L) { "高能进度条需要有效的 AID 和 CID" }
    val response = ApiClient.httpClient.get("https://bvc.bilivideo.com/pbp/data") {
        parameter("aid", avid)
        parameter("cid", cid)
        parameter("r", "loader")
    }
    check(response.status.isSuccess()) { "高能进度条请求失败：HTTP ${response.status.value}" }
    parseHighEnergyProgress(response.bodyAsText())
}

private fun parseHighEnergyProgress(body: String): HighEnergyProgressData? {
    val root = highEnergyProgressJson.parseToJsonElement(body) as? JsonObject
    val modules = root?.get("modules") as? JsonArray ?: return null
    for (element in modules) {
        val module = element as? JsonObject ?: continue
        if ((module["load_mode"] as? JsonPrimitive)?.content != "pbp") continue
        val params = module["params"] as? JsonObject ?: continue
        val data = params["data"] as? JsonObject ?: continue
        val parsed = highEnergyProgressJson.decodeFromJsonElement<RawHighEnergyProgressData>(data).toData()
        if (parsed != null) return parsed
    }
    return null
}
