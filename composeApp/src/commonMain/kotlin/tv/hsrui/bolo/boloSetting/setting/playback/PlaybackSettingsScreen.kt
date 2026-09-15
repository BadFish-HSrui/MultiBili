package tv.hsrui.bolo.boloSetting.setting.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.boloSetting.PlaybackProgressReportMode
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded

@Composable
fun PlaybackSettingsScreen(modifier: Modifier = Modifier) {
    val settings: BoloSettings = koinInject()
    var showReportStartInfo by remember { mutableStateOf(false) }
    var showDanmakuAutoEnableInfo by remember { mutableStateOf(false) }

    if (showDanmakuAutoEnableInfo) {
        ShowInfoDialog(onConfirm = { showDanmakuAutoEnableInfo = false }) {
            Text(
                text = "关闭后，沿用上次开关状态",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }

    if (showReportStartInfo) {
        ShowInfoDialog(onConfirm = { showReportStartInfo = false }) {
            Text(
                text = "此选项只影响播放量增加，需要开启上报播放进度才会出现在历史记录中",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (!isExpanded()) ShowTopBarWithNavigationButton(title = { Text("播放设置") })
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding.calculateWithoutBottom()), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("播放习惯", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playerAutoPlayOnOpenEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playerAutoPlayOnOpenEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("打开视频自动播放", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.playerAutoPlayOnOpenEnabled, onCheckedChange = null)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playerAutoEnableDanmakuOnOpenEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playerAutoEnableDanmakuOnOpenEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("打开视频自动开启弹幕", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { showDanmakuAutoEnableInfo = true },
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "打开视频自动开启弹幕说明",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.playerAutoEnableDanmakuOnOpenEnabled, onCheckedChange = null)
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("信息上报", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playerReportStartEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playerReportStartEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("上报开始播放", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { showReportStartInfo = true },
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "上报开始播放说明",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Box(Modifier.weight(1f))
                                Switch(checked = settings.playerReportStartEnabled, onCheckedChange = null)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("上报播放进度", style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.weight(1F))
                                SingleChoiceSegmentedButtonRow(modifier = Modifier) {
                                    val modes = PlaybackProgressReportMode.entries
                                    modes.forEachIndexed { index, mode ->
                                        SegmentedButton(
                                            selected = mode == settings.playerReportProgressMode,
                                            onClick = {
                                                settings.playerReportProgressMode = mode
                                            },
                                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        ) {
                                            Text(mode.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
