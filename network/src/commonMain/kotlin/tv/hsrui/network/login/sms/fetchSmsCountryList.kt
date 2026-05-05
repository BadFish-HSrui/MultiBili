package tv.hsrui.network.login.sms

import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.PassportUrls

suspend fun fetchSmsCountryList(): List<SmsCountryInfo> {
    val response = ApiClient.httpClient.get(PassportUrls.BASE+ PassportUrls.COUNTRY)
    val raw: SmsCountryList = response.body()
    return raw.data.common
}

@Serializable
private data class SmsCountryList(
    private val code: Int = -1,
    val message: String = "-1",
    val data: SmsCountryData = SmsCountryData(emptyList(), emptyList())
)

@Serializable
private data class SmsCountryData(
    val common: List<SmsCountryInfo>,
    val others: List<SmsCountryInfo>
)

@Serializable
data class SmsCountryInfo(
    val cname: String,
    @SerialName("country_id") val cid: String
)