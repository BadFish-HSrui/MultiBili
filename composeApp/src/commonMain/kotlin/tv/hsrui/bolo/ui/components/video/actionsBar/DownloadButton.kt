package tv.hsrui.bolo.ui.components.video.actionsBar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.download.DownloadType
import tv.hsrui.bolo.ui.components.dialog.ShowVideoDownloadDialog

@Composable
fun DownloadButton(
    id: Long,
    cid: Long,
    title: String,
    canClick: Boolean,
    modifier: Modifier = Modifier,
    type: DownloadType = DownloadType.Video,
) {
    var open by rememberSaveable(type, id, cid) { mutableStateOf(false) }
    val enabled = canClick && id > 0 && cid > 0
    Surface(onClick = { open = true }, enabled = enabled, color = Color.Transparent, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = "下载",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp).graphicsLayer {
                    scaleX = 1.16f
                    scaleY = 1.16f
                    translationY = 0.58.dp.toPx()
                },
            )
            Text(
                text = "下载",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (open && enabled) {
        ShowVideoDownloadDialog(id = id, cid = cid, title = title, type = type, onDismiss = { open = false })
    }
}
