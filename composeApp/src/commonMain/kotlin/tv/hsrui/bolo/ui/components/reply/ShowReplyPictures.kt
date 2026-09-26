package tv.hsrui.bolo.ui.components.reply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.ui.components.image.ShowImage
import tv.hsrui.network.feature.reply.ReplyItem.ReplyContent.ReplyPicture

@Composable
fun ShowReplyPictures(
    pictures: List<ReplyPicture>,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    animationEnabled: Boolean = true,
) {
    if (pictures.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        // 多图时收窄每张预览，保留横向可滑动的视觉线索；单图仍可铺满内容宽度。
        val multiPictureMaxWidthRatio = 0.8f
        val maxItemWidth = if (pictures.size > 1) availableWidth * multiPictureMaxWidthRatio else availableWidth
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(pictures) { index, picture ->
                val ratio = if (picture.width > 0 && picture.height > 0) {
                    picture.width.toFloat() / picture.height
                } else 1f
                val previewRatio = ratio.coerceIn(9f / 21f, 16f / 9f)
                val height = minOf(240.dp, maxItemWidth / previewRatio)
                val itemWidth = height * previewRatio
                Surface(
                    onClick = { onImageClick(index) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(width = itemWidth, height = height),
                ) {
                    ShowImage(
                        url = picture.url,
                        contentDescription = "评论图片 ${index + 1}，共 ${pictures.size} 张，点击查看大图",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = if (ratio < 9f / 21f || ratio > 16f / 9f) ContentScale.Crop else ContentScale.Fit,
                        animationEnabled = animationEnabled,
                    )
                }
            }
        }
    }
}
