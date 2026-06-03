package tv.hsrui.bolo.ui.components.reply.actionsBar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.like_icon
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.actions.ReplyLikeState
import tv.hsrui.network.feature.reply.actions.switchReplyDisLike
import tv.hsrui.network.feature.reply.actions.switchReplyLike
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.utils.formatCountToString

@Composable
fun ReplyActionsBar(
    replyInfo: ReplyItem,
    updateReply: (ReplyItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val loginStorage: LoginStorage = koinInject()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.alpha(0.67F)
    ) {

        if (replyInfo.isUpLiked) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(width = 1.dp, color = BiliColor.ThemeColor),
                modifier = Modifier
            ) {
                Text(
                    text = "UP赞过",
                    style = MaterialTheme.typography.labelSmall,
                    color = BiliColor.ThemeColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        if (replyInfo.isUpReplied) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(width = 1.dp, color = BiliColor.ThemeColor),
                modifier = Modifier
            ) {
                Text(
                    text = "UP评论过",
                    style = MaterialTheme.typography.labelSmall,
                    color = BiliColor.ThemeColor,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = {
                if (loginStorage.isLoggedIn) scope.launch {
                    try {
                        val (result, newLikeState) = switchReplyLike(replyInfo)
                        if (result.isSuccess) {
                            updateReply(replyInfo.copy(actionCode = newLikeState.code))
                        } else {
                            showSnackbarMessage("[${result.code}]: ${result.message}")
                        }
                    } catch (e: Exception) {
                        showSnackbarMessage(e.message ?: "其他网络错误")
                    }
                }
            })
        ) {
            Icon(
                painter = painterResource(Res.drawable.like_icon),
                contentDescription = "点赞",
                tint = if (replyInfo.likeState == ReplyLikeState.Like) BiliColor.ThemeColor else LocalContentColor.current,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = replyInfo.likeCount.formatCountToString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Icon(
            painter = painterResource(Res.drawable.like_icon),
            contentDescription = "点踩",
            tint = if (replyInfo.likeState == ReplyLikeState.Dislike) BiliColor.ThemeColor else LocalContentColor.current,
            modifier = Modifier.size(16.dp).scale(scaleX = 1F, scaleY = -1F)
                .clickable(onClick = {
                    if (loginStorage.isLoggedIn) scope.launch {
                        try {
                            val (result, newLikeState) = switchReplyDisLike(replyInfo)
                            if (result.isSuccess) {
                                updateReply(replyInfo.copy(actionCode = newLikeState.code))
                            } else {
                                showSnackbarMessage("[${result.code}]: ${result.message}")
                            }
                        } catch (e: Exception) {
                            showSnackbarMessage(e.message ?: "其他网络错误")
                        }
                    }
                })
        )

        Text(
            text = "回复",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clickable(onClick = {})
        )
    }
}