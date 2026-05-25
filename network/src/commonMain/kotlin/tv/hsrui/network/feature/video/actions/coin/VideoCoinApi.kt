package tv.hsrui.network.feature.video.actions.coin

import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage

suspend fun modifyVideoCoin(avid: Long,coinCount: Int): VideoCoinResponse{
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.VideoAction.COIN) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("aid", avid.toString())
                    append("multiply", coinCount.toString())
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}