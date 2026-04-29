package tv.hsrui.bolo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import tv.hsrui.bolo.utils.formatToString
import tv.hsrui.network.model.VideoCard

@Composable
fun ShowVideoCard(
    videoInfo: VideoCard,
    isWide: Boolean,
    modifier: Modifier = Modifier
) {
    val infoTextStyle: TextStyle
    val coverAspectRatio: Float
    val maxTitleLines: Int
    val titleHeight: Dp
    val roundedCornerSize: Dp

    if (isWide) {
        infoTextStyle = MaterialTheme.typography.labelMedium
        coverAspectRatio = 16F / 9F
        maxTitleLines = 1
        titleHeight = 16.dp
        roundedCornerSize = 12.dp

    } else {
        infoTextStyle = MaterialTheme.typography.labelSmall
        coverAspectRatio = 4F / 3F
        maxTitleLines = 2
        titleHeight = 36.dp
        roundedCornerSize = 4.dp
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
            .focusable()
            .then(
                if (isFocused) Modifier.zIndex(1F).scale(animatedScale).border(
                    width = 2.dp,
                    color = Color.Cyan,
                    shape = RoundedCornerShape(roundedCornerSize)
                )
                else Modifier
            ),
        shape = RoundedCornerShape(roundedCornerSize)
    ) {
        Column {
            Box(modifier = Modifier.aspectRatio(coverAspectRatio)) {
                AsyncImage(
                    model = videoInfo.coverUrl,
                    contentDescription = "视频封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0.9f)
                        ) {
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
                                    videoInfo.viewCount.formatToString(),
                                    style = infoTextStyle
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
                                    videoInfo.danmakuCount.formatToString(),
                                    style = infoTextStyle
                                )
                            }
                            Spacer(modifier = Modifier.weight(1F))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(4.dp)
                            ) {
                                Text(
                                    videoInfo.duration,
                                    style = infoTextStyle
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1F))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0.9f)
                        ) {
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
                                    videoInfo.likeCount.formatToString(),
                                    style = infoTextStyle
                                )
                            }
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
                                    videoInfo.replyCount.formatToString(),
                                    style = infoTextStyle
                                )
                            }
                            Spacer(modifier = Modifier.weight(1F))
                        }
                    }
                }
            }
            Text(
                text = videoInfo.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = maxTitleLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 4.dp, top = 4.dp, end = 4.dp)
                    .height(titleHeight)
            )
            Text(
                videoInfo.upName,
                style = infoTextStyle,
                modifier = Modifier.align(Alignment.End)
                    .padding(4.dp)
            )
        }
    }
}

@Preview
@Composable
fun PreviewVideoCard() {
    val videoCard = VideoCard(
        avid = 115327790751441,
        bvid = "BV1gDxEzHE8Z",
        pic = "http://i2.hdslb.com/bfs/archive/7a7aa5e03fb63167e51a9d3d7a28ed2749128a45.jpg",
        title = "✨“我为你唱一曲如游丝的气息”《青衣DJ》✨/AI東 雪蓮",
        description = "原曲：青衣DJ\n人声：AI东雪莲\n图/动态图/音频：\npan.quark.cn/s/5d94a5c96ba9\n本身想跑花旦风格的，但是发现这个底模跑不出好看的\n做了22张动图，没用上的图和动图放网盘里了\n中秋快乐！\n这几天感冒严重，打火机日语完整版过几天做完",
        publishDate = 1759762729,
        stat = VideoCard.Stat(view = 1919810, like = 114514),
        _duration = 10000,
        owner = VideoCard.Owner(
            name = "东洋雪莲",
            face = "https://i2.hdslb.com/bfs/face/4cbf2f66d23a324ecca8d3c07adbcafecfef829b.jpg"
        )
    )
    ShowVideoCard(videoCard, isWide = false)
}