package tv.hsrui.network.feature.video.actions.like

import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage

suspend fun modifyVideoLike(avid: Long, action: VideoLikeAction): VideoLikeResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.VideoAction.LIKE) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("aid", avid.toString())
                    append("like", action.actionCode.toString())
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }
    println(loginStorage.cookies.buvid3)

    return response.body()
}