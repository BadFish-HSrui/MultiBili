package tv.hsrui.network.feature.player

import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.model.BaseResponse

suspend fun reportPlaybackStart(avid: Long, cid: Long): BaseResponse {
    val loginStorage: LoginStorage = getKoin().get()
    return ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Play.REPORT_START) {
        expectSuccess = true
        setBody(FormDataContent(Parameters.build {
            append("aid", avid.toString())
            append("cid", cid.toString())
            append("csrf", loginStorage.cookies.csrf)
        }))
    }.body()
}

suspend fun reportPlaybackProgress(avid: Long, cid: Long, progressSeconds: Long): BaseResponse {
    val loginStorage: LoginStorage = getKoin().get()
    return ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Play.REPORT_PROGRESS) {
        expectSuccess = true
        setBody(FormDataContent(Parameters.build {
            append("aid", avid.toString())
            append("cid", cid.toString())
            append("progress", progressSeconds.coerceAtLeast(0L).toString())
            append("csrf", loginStorage.cookies.csrf)
        }))
    }.body()
}
