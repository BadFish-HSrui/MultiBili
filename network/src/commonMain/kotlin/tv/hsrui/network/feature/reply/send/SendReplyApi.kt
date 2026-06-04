package tv.hsrui.network.feature.reply.send

import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import org.koin.mp.KoinPlatform.getKoin
import tv.hsrui.network.client.ApiClient
import tv.hsrui.network.constant.ApiUrls
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.ReplySectionType
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.model.BaseResponse

suspend fun sendRootReply(
    message: String,
    replySection: ReplySectionType
): BaseResponse {
    return sendReply(
        message = message,
        replySection = replySection
    )
}

suspend fun sendSubReply(
    message: String,
    replySection: ReplySectionType,
    targetReply: ReplyItem
): BaseResponse {
    return sendReply(
        message = message,
        replySection = replySection,
        rootRpid = targetReply.rootRpid.takeUnless { it == 0L } ?: targetReply.rpid,
        parentRpid = targetReply.rpid
    )
}

private suspend fun sendReply(
    message: String,
    replySection: ReplySectionType,
    rootRpid: Long? = null,
    parentRpid: Long? = null
): BaseResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Reply.SEND) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("message", message)
                    append("type", replySection.typeCode.toString())
                    append("oid", replySection.oid.toString())
                    if (rootRpid != null) append("root", rootRpid.toString())
                    if (parentRpid != null) append("parent", parentRpid.toString())
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}