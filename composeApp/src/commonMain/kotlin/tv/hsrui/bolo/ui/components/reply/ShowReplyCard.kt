package tv.hsrui.bolo.ui.components.reply

import tv.hsrui.bolo.navigation.openUserSpace
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import tv.hsrui.bolo.debug.previewData.replyResponseJsonExample
import tv.hsrui.bolo.ui.components.reply.actionsBar.ReplyActionsBar
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.ui.theme.BiliColor.getLevelColor
import tv.hsrui.network.feature.reply.ReplyItem
import tv.hsrui.network.feature.reply.ReplyItem.ReplyContent.ReplyPicture
import tv.hsrui.network.feature.reply.ReplyResponse

@Composable
fun ShowReplyCard(
    replyInfo: ReplyItem,
    isUpReply: Boolean,
    sendReply: () -> Unit,
    updateReply: (ReplyItem) -> Unit,
    onViewClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTop: Boolean = false,
    imageAnimationEnabled: Boolean = true,
    onImageClick: (List<ReplyPicture>, Int) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = replyInfo.userAvatarUrl,
                    contentDescription = "评论用户头像",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = replyInfo.userMid > 0) { openUserSpace(replyInfo.userMid) }
//                        .background(Color.Black)
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
                        if (isTop) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(width = 1.dp, color = BiliColor.ThemeColor),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = "置顶",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BiliColor.ThemeColor,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
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
                remember(replyInfo.content, scope) { replyInfo.content.toRichString(scope) }
            var expanded by rememberSaveable(replyInfo.rpid, replyInfo.content.text) { mutableStateOf(false) }
            var hasOverflow by remember(replyInfo.rpid, replyInfo.content.text) { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .animateContentSize(animationSpec = tween(300), alignment = Alignment.TopStart)
            ) {
                Text(
                    text = annotatedString,
                    inlineContent = inlineContentMap,
                    maxLines = if (expanded) Int.MAX_VALUE else 10,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) hasOverflow = it.hasVisualOverflow },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!expanded && hasOverflow) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Surface(
                            onClick = { expanded = true },
                            color = Color.Transparent,
                            contentColor = BiliColor.Blue,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("展开", style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            ShowReplyPictures(
                pictures = replyInfo.content.pictures,
                onImageClick = { onImageClick(replyInfo.content.pictures, it) },
                animationEnabled = imageAnimationEnabled,
                modifier = Modifier.padding(top = 8.dp),
            )

            ReplyActionsBar(
                replyInfo = replyInfo,
                sendReply = sendReply,
                updateReply = updateReply,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (!replyInfo.previewReplies.isNullOrEmpty() || replyInfo.replyCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = CardDefaults.shape
                ) {
                    Column(Modifier.padding(4.dp)) {
                        replyInfo.previewReplies?.forEach { preReply ->
                            val (annotatedString, inlineContentMap) =
                                remember(preReply.content, scope) { preReply.content.toRichString(scope) }

                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        style = SpanStyle(LocalContentColor.current.copy(alpha = 0.67F))
                                    ) {
                                        append("${preReply.userName}: ")
                                    }
                                    append(annotatedString)
                                    if (preReply.content.pictures.isNotEmpty()) append(" [图片]")
                                },
                                inlineContent = inlineContentMap,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (replyInfo.replyCount > 0) {
                            Text(
                                text = "共${replyInfo.replyCount}条回复,点击查看 >",
                                style = MaterialTheme.typography.bodySmall,
                                color = BiliColor.Blue,
                                modifier = Modifier
                                    .clickable(onClick = onViewClick)
                                    .padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 320, showBackground = true, backgroundColor = 0)
@Composable
fun ReplyCardPreview() {
    val replyItem =
        remember {
            Json { ignoreUnknownKeys = true }.decodeFromString<ReplyResponse>(
                replyResponseJsonExample
            ).data.replies[19]
        }

    ShowReplyCard(replyItem, false, {}, {}, {}, isTop = true)
}
