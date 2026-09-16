package tv.hsrui.bolo.ui.components.reply

import tv.hsrui.bolo.navigation.openUserSpace
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import tv.hsrui.bolo.debug.previewData.replyResponseJsonExample
import tv.hsrui.bolo.ui.components.reply.actionsBar.ReplyActionsBar
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.ui.theme.BiliColor.getLevelColor
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.ReplyResponse

@Composable
fun ShowSubReply(
    replyInfo: ReplyItem,
    isUpReply: Boolean,
    sendReply: () -> Unit,
    updateReply: (ReplyItem) -> Unit,
    modifier: Modifier = Modifier,
    isTop: Boolean = false
) {
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = replyInfo.userAvatarUrl,
                    contentDescription = "评论用户头像",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = replyInfo.userMid > 0) { openUserSpace(replyInfo.userMid) }
                )
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isUpReply) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BiliColor.ThemeColor,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    text = "UP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = replyInfo.userName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.alpha(0.8F).clickable(enabled = replyInfo.userMid > 0) { openUserSpace(replyInfo.userMid) }
                        )
                        Surface(
                            shape = CircleShape,
                            color = getLevelColor(replyInfo.userLevel),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "LV.${replyInfo.userLevel}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = replyInfo.replyDateString,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alpha(0.67F)
                    )
                }
            }

            val (annotatedString, inlineContentMap) =
                remember(replyInfo.rpid) { replyInfo.content.toRichString(scope) }

            Text(
                text = annotatedString,
                inlineContent = inlineContentMap,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )

            ReplyActionsBar(
                replyInfo = replyInfo,
                sendReply = sendReply,
                updateReply = updateReply,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        HorizontalDivider()

        if (isTop) {
            Text(
                text = "相关回复",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(start = 8.dp, top = 24.dp)
            )
        }
    }
}

@Preview(widthDp = 320, showBackground = true, backgroundColor = 0)
@Composable
fun SubReplyPreview() {
    val replyItem =
        remember {
            Json { ignoreUnknownKeys = true }.decodeFromString<ReplyResponse>(
                replyResponseJsonExample
            ).data.replies[19]
        }

    ShowSubReply(replyItem, false, {}, {})
}