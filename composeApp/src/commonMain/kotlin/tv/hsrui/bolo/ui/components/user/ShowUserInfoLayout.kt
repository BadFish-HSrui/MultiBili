package tv.hsrui.bolo.ui.components.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.ui.theme.BoloShapes

@Composable
internal fun ShowUserInfoLayout(
    face: String,
    name: String,
    sign: String,
    level: Int,
    levelString: String,
    isVip: Boolean,
    vipTypeString: String,
    modifier: Modifier = Modifier,
    shape: Shape = BoloShapes.List.Top,
    fixedHeight: Dp? = null,
    signMinLines: Int = 1,
    onClick: (() -> Unit)? = null,
    statistics: @Composable RowScope.() -> Unit,
) {
    val content: @Composable ColumnScope.() -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
        ) {
            Column(modifier = Modifier.width(64.dp)) {
                AsyncImage(
                    model = face,
                    contentDescription = "个人头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1F).clip(CircleShape),
                )
                if (isVip) {
                    Surface(
                        shape = CircleShape,
                        color = BiliColor.ThemeColor,
                        modifier = Modifier.width(64.dp),
                    ) {
                        Text(
                            text = vipTypeString,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1F).padding(start = 8.dp).wrapContentHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = BiliColor.getLevelColor(level)) {
                        Text(
                            text = levelString,
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    Text(
                        text = name,
                        textAlign = TextAlign.Center,
                        color = if (isVip) BiliColor.ThemeColor else LocalContentColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1F, fill = false).padding(horizontal = 4.dp),
                    )
                }
                Text(
                    text = sign.ifEmpty { "这个人很懒，没有签名喵" },
                    style = MaterialTheme.typography.bodyMedium,
                    minLines = signMinLines,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(0.75F),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 2.dp)
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelMedium) {
                    Row(modifier = Modifier.fillMaxWidth(), content = statistics)
                }
            }
        }
    }
    val cardModifier = modifier
        .then(if (fixedHeight == null) Modifier.wrapContentHeight() else Modifier.height(fixedHeight))
        .fillMaxWidth()
    if (onClick == null) {
        Card(modifier = cardModifier, shape = shape, content = content)
    } else {
        Card(onClick = onClick, modifier = cardModifier, shape = shape, content = content)
    }
}
