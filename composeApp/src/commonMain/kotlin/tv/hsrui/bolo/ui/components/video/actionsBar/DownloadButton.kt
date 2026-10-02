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
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.ui.components.dialog.ShowVideoDownloadDialog
import tv.hsrui.network.feature.video.VideoInfoData

@Composable
fun DownloadButton(videoInfo: VideoInfoData, canClick: Boolean, modifier: Modifier = Modifier) {
    var open by rememberSaveable(videoInfo.avid, videoInfo.cid) { mutableStateOf(false) }
    Surface(onClick = { open = true }, enabled = canClick && videoInfo.avid > 0 && videoInfo.cid > 0,
        color = Color.Transparent, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Download, contentDescription = "下载", tint = Color.Gray, modifier = Modifier.size(24.dp))
            Text("下载", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (open && canClick) ShowVideoDownloadDialog(videoInfo.avid, videoInfo.cid, videoInfo.title, onDismiss = { open = false })
}
