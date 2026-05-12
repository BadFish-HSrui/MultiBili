package tv.hsrui.bolo.ui.components.video

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.bolo.utils.infoFormat.formatToDuration
import tv.hsrui.bolo.utils.infoFormat.formatToString
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.model.VideoCardExample

@Composable
fun ShowVideoCard(
    videoInfo: VideoCard,
    isWide: Boolean,
    modifier: Modifier = Modifier
) {
    val infoTextSize: TextUnit
    val coverAspectRatio: Float
    val maxTitleLines: Int
    val titleHeight: Dp
    val cardShape: Shape
    val coverUrl: String

    if (isWide) {
        infoTextSize = 12.sp
        coverAspectRatio = 16F / 9F
        maxTitleLines = 1
        titleHeight = 16.dp
        cardShape = BoloShapes.InfoCard.Default
        coverUrl = videoInfo.coverUrl + "@800w_450h_1c.webp"

    } else {
        infoTextSize = 10.sp
        coverAspectRatio = 4F / 3F
        maxTitleLines = 2
        titleHeight = 36.dp
        cardShape = BoloShapes.InfoCard.Compact
        coverUrl = videoInfo.coverUrl43 + "@400w_300h_1c.webp"
    }

    var isFocused by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f
    )

    Card(
        modifier = modifier
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {})
            .then(
                if (isFocused) Modifier.zIndex(1F).scale(animatedScale).border(
                    width = 2.dp,
                    color = Color.Cyan,
                    shape = cardShape
                )
                else Modifier
            ),
        shape = cardShape
    ) {
        Column {
            Box(modifier = Modifier.aspectRatio(coverAspectRatio)) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "视频封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                CompositionLocalProvider(
                    LocalContentColor provides Color.White,
                    LocalTextStyle provides TextStyle(fontSize = infoTextSize)
                ) {
                    Row(Modifier.align(Alignment.TopStart)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = "播放量",
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                videoInfo.viewCount.formatToString()
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShortText,
                                contentDescription = "弹幕量",
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                videoInfo.danmakuCount.formatToString()
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Text(videoInfo.duration.formatToDuration())
                    }

                    Row(modifier = Modifier.align(Alignment.BottomStart)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ThumbUp,
                                contentDescription = "点赞量",
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                "${videoInfo.likeCount.formatToString()} ${videoInfo.likeCount * 1000 / videoInfo.viewCount / 10.0F}%",
                            )
                        }
                        if (videoInfo.replyCount != -1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Comment,
                                    contentDescription = "评论量",
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    videoInfo.replyCount.formatToString()
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = videoInfo.title,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = maxTitleLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 4.dp, top = 4.dp, end = 4.dp)
                    .height(titleHeight)
            )
            Text(
                videoInfo.upName,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                modifier = Modifier.align(Alignment.End)
                    .padding(4.dp)
            )
        }
    }
}

@Preview
@Composable
private fun PreviewVideoCard() {
    ShowVideoCard(VideoCardExample, isWide = false)
}