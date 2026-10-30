package tv.hsrui.bolo.ui.components.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.hsrui.bolo.ui.theme.BoloShapes
import tv.hsrui.bolo.utils.isCompact

@Composable
fun ShowLiveAreaItem(
    title: String,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = if (isCompact()) BoloShapes.InfoCard.Compact else BoloShapes.InfoCard.Default
    val cardContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp))
                } else {
                    AsyncImage(
                        model = iconUrl?.takeIf { it.isNotBlank() },
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (onClick == null) {
        Card(shape = shape, modifier = modifier.height(56.dp)) { cardContent() }
    } else {
        Card(onClick = onClick, shape = shape, modifier = modifier.height(56.dp)) { cardContent() }
    }
}
