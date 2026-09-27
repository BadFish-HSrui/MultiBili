package tv.hsrui.bolo

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.storage.appData.AppDataStorage
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog

@Composable
fun AppStartup() {
    val appDataStorage: AppDataStorage = koinInject()
    val warnings = appDataStorage.oneTimeWarnings

    if (warnings.dynamicLoudnessPending) {
        ShowInfoDialog(
            onConfirm = { warnings.dynamicLoudnessPending = false },
            forcedDisplaySeconds = 5,
        ) {
            Text(
                text = "当前版本默认开启动态音量均衡\n\n" +
                    "部分设备在倍速下可能出现严重的音频处理阻塞导致卡顿\n\n" +
                    "如遇到问题可在播放设置中关闭",
                color = Color.Red,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}
