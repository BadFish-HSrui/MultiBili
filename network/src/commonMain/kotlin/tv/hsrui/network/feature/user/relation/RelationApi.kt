package tv.hsrui.network.feature.user.relation

import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.login.storage.LoginStorage

suspend fun fetchRelationWith(mid: Long): RelationResponse {
    val response = ApiClient.httpClient.get(ApiUrls.BASE + ApiUrls.RELATION) {
        parameter("mid", mid)
    }

    return response.body()
}

suspend fun modifyRelation(mid: Long, relationAction: RelationAction): ModifyRelationResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE+ ApiUrls.MODIFY_RELATION){
        setBody(
            FormDataContent(
                Parameters.build {
                    append("fid", mid.toString())
                    append("act", relationAction.actionCode.toString())
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}