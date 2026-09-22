package tv.hsrui.network.feature.reply.actions

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
import tv.hsrui.network.feature.reply.ReplySectionType.Companion.ReplySectionType
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.model.BaseResponse

suspend fun deleteReply(
    replySection: ReplySectionType,
    rpid: Long,
): BaseResponse {
    require(replySection.oid > 0 && rpid > 0) { "无效的评论标识" }
    val loginStorage: LoginStorage = getKoin().get()
    check(loginStorage.isLoggedIn) { "请先登录" }

    return ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Reply.DELETE) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("type", replySection.typeCode.toString())
                    append("oid", replySection.oid.toString())
                    append("rpid", rpid.toString())
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }.body()
}

suspend fun switchReplyLike(
    replyItem: ReplyItem
): Pair<BaseResponse, ReplyLikeState> {
    val replySection =
        ReplySectionType(replyItem.typeCode, replyItem.oid) ?: error("无法处理的评论类型")

    return if (replyItem.likeState == ReplyLikeState.Like) Pair(
        modifyReplyLike(replySection = replySection, rpid = replyItem.rpid, targetState = false),
        ReplyLikeState.Normal
    )
    else Pair(
        modifyReplyLike(replySection = replySection, rpid = replyItem.rpid, targetState = true),
        ReplyLikeState.Like
    )
}

suspend fun switchReplyDisLike(
    replyItem: ReplyItem
): Pair<BaseResponse, ReplyLikeState> {
    val replySection =
        ReplySectionType(replyItem.typeCode, replyItem.oid) ?: error("无法处理的评论类型")

    return if (replyItem.likeState == ReplyLikeState.Dislike) Pair(
        modifyReplyDislike(replySection = replySection, rpid = replyItem.rpid, targetState = false),
        ReplyLikeState.Normal
    )
    else Pair(
        modifyReplyDislike(replySection = replySection, rpid = replyItem.rpid, targetState = true),
        ReplyLikeState.Dislike
    )
}

suspend fun modifyReplyLike(
    replySection: ReplySectionType,
    rpid: Long,
    targetState: Boolean
): BaseResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Reply.LIKE) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("type", replySection.typeCode.toString())
                    append("oid", replySection.oid.toString())
                    append("rpid", rpid.toString())
                    append("action", if (targetState) "1" else "0")
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}

suspend fun modifyReplyDislike(
    replySection: ReplySectionType,
    rpid: Long,
    targetState: Boolean
): BaseResponse {
    val loginStorage: LoginStorage = getKoin().get()

    val response = ApiClient.httpClient.post(ApiUrls.BASE + ApiUrls.Reply.DISLIKE) {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("type", replySection.typeCode.toString())
                    append("oid", replySection.oid.toString())
                    append("rpid", rpid.toString())
                    append("action", if (targetState) "1" else "0")
                    append("csrf", loginStorage.cookies.csrf)
                }
            )
        )
    }

    return response.body()
}
