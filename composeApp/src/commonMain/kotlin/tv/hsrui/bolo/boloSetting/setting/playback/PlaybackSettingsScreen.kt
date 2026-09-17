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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.boloSetting.PlaybackLoudnessMode
import tv.hsrui.bolo.boloSetting.PlaybackProgressReportMode
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog
import tv.hsrui.bolo.ui.components.slider.ShowSlider
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.Quality
import tv.hsrui.network.feature.player.enumModels.VideoQuality
import kotlin.math.roundToInt

@Composable
fun PlaybackSettingsScreen(modifier: Modifier = Modifier) = LookaheadScope {
    // 单双栏切换会移动同一导航内容；保留局部 Lookahead 作用域，避免滑块沿用已失效的对齐线状态。
    val settings: BoloSettings = koinInject()
    var showReportStartInfo by remember { mutableStateOf(false) }
    var showReportProgressImmediatelyInfo by remember { mutableStateOf(false) }
    var showDanmakuAutoEnableInfo by remember { mutableStateOf(false) }
    var showDynamicLoudnessInfo by remember { mutableStateOf(false) }
    var showRecordQualitySelectionInfo by remember { mutableStateOf(false) }

    if (showRecordQualitySelectionInfo) {
        ShowInfoDialog(onConfirm = { showRecordQualitySelectionInfo = false }) {
            Text(
                text = "开启后修改音画质会同时修改默认项",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }

    if (showDynamicLoudnessInfo) {
        ShowInfoDialog(onConfirm = { showDynamicLoudnessInfo = false }) {
            Text(
                text = "开启后使用FFmpeg的loudnorm而不是volume-gain实现动态音量均衡。\n\n\n" +
                        "volume-gain：与官方网页播放器行为一致，根据服务器返回数据，在原始音量上叠加固定增益/减益，对于原始高低音量差异过大的视频效果不佳。\n\n" +
                        "loudnorm：让所有音量向目标音量靠近，能避免过大或过小声音，但会损失动态范围。",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }

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

    if (showReportProgressImmediatelyInfo) {
        ShowInfoDialog(onConfirm = { showReportProgressImmediatelyInfo = false }) {
            Text(
                text = "播放视频后立即上报播放记录。\n\n" +
                        "这样可以使视频立刻出现在历史记录中，否则会根据 ‘上报播放进度’ 选项值在退出或一段时间后上报。\n\n" +
                        "官方行为：退出视频后才会上报进度出现在播放记录中。",
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
        Box(
            Modifier.fillMaxSize().padding(innerPadding.calculateWithoutBottom()),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "播放习惯",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(8.dp)
                        )
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
                                Switch(
                                    checked = settings.playerAutoPlayOnOpenEnabled,
                                    onCheckedChange = null
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playerAutoEnableDanmakuOnOpenEnabled,
                                    role = Role.Switch,
                                    onValueChange = {
                                        settings.playerAutoEnableDanmakuOnOpenEnabled = it
                                    },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "打开视频自动开启弹幕",
                                    style = MaterialTheme.typography.bodyLarge
                                )
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
                                Switch(
                                    checked = settings.playerAutoEnableDanmakuOnOpenEnabled,
                                    onCheckedChange = null
                                )
                            }
                            val audioQualities = listOf(
                                AudioQuality.QUALITY_64K,
                                AudioQuality.QUALITY_132K,
                                AudioQuality.QUALITY_192K,
                            )
                            for (isVideo in listOf(true, false)) {
                                HorizontalDivider()
                                val title = if (isVideo) "默认播放画质" else "默认播放音质"
                                val qualities: List<Quality> = if (isVideo) VideoQuality.entries else audioQualities
                                val savedQuality = if (isVideo) settings.playerDefaultVideoQuality else settings.playerDefaultAudioQuality
                                val savedIndex = qualities.indexOfFirst { it.code == savedQuality.code }
                                var previewIndex by remember(isVideo, savedIndex) { mutableStateOf<Int?>(null) }
                                val displayedIndex = previewIndex ?: savedIndex
                                val valueText = qualities[displayedIndex].shortTitle
                                val saveIndex: (Int) -> Unit = { index ->
                                    if (isVideo) settings.playerDefaultVideoQuality = VideoQuality.entries[index]
                                    else settings.playerDefaultAudioQuality = audioQualities[index]
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Spacer(Modifier.weight(1f))
                                    Row(
                                        modifier = Modifier.widthIn(max = 304.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            valueText,
                                            modifier = Modifier.width(64.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                        )
                                        ShowSlider(
                                            value = displayedIndex.toFloat(),
                                            onValueChange = { previewIndex = it.roundToInt().coerceIn(qualities.indices) },
                                            onValueChangeFinished = {
                                                previewIndex?.let(saveIndex)
                                                previewIndex = null
                                            },
                                            valueRange = 0f..qualities.lastIndex.toFloat(),
                                            steps = qualities.size - 2,
                                            centered = false,
                                            showStops = true,
                                            showTicks = true,
                                            modifier = Modifier.weight(1f).widthIn(max = 200.dp).semantics {
                                                contentDescription = title
                                                stateDescription = valueText
                                            },
                                        )
                                        IconButton(
                                            onClick = {
                                                previewIndex = null
                                                saveIndex(qualities.lastIndex)
                                            },
                                            enabled = displayedIndex != qualities.lastIndex,
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(Icons.Rounded.RestartAlt, contentDescription = "重置$title")
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playerRecordQualitySelectionEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playerRecordQualitySelectionEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("记录音画质选择", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { showRecordQualitySelectionInfo = true },
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "记录音画质选择说明",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.playerRecordQualitySelectionEnabled, onCheckedChange = null)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playerHideAudioQualitySelectorEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playerHideAudioQualitySelectorEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("隐藏播放器音质选项", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.playerHideAudioQualitySelectorEnabled, onCheckedChange = null)
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "音量均衡",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playerDynamicLoudnessEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playerDynamicLoudnessEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "动态音量均衡（实验性）",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                IconButton(
                                    onClick = { showDynamicLoudnessInfo = true },
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "动态音量均衡（实验性）说明",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = settings.playerDynamicLoudnessEnabled,
                                    onCheckedChange = null
                                )
                            }
                            if (settings.playerDynamicLoudnessEnabled) {
                                HorizontalDivider()
                                listOf(
                                    Triple("目标音量", -20f..-8f, 1f),
                                    Triple("动态范围", 6f..16f, 1f),
                                    Triple("峰值音量", -4f..0f, 0.5f),
                                ).forEachIndexed { index, (title, range, step) ->
                                    val savedValue = when (index) {
                                        0 -> settings.playerDynamicLoudnessTargetLufs
                                        1 -> settings.playerDynamicLoudnessRangeLu
                                        else -> settings.playerDynamicLoudnessTruePeakDbtp
                                    }
                                    val defaultValue = (range.start + range.endInclusive) / 2f
                                    var previewValue by remember(index, savedValue) { mutableStateOf<Float?>(null) }
                                    val saveValue: (Float) -> Unit = { value ->
                                        when (index) {
                                            0 -> settings.playerDynamicLoudnessTargetLufs = value
                                            1 -> settings.playerDynamicLoudnessRangeLu = value
                                            else -> settings.playerDynamicLoudnessTruePeakDbtp = value
                                        }
                                    }
                                    val displayedValue = previewValue ?: savedValue
                                    val valueText = when (index) {
                                        0 -> "${displayedValue.roundToInt()} LUFS"
                                        1 -> "${displayedValue.roundToInt()} LU"
                                        else -> "$displayedValue dBTP"
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                        Spacer(Modifier.weight(1f))
                                        Row(
                                            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(valueText, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1)
                                            ShowSlider(
                                                value = displayedValue,
                                                onValueChange = { previewValue = ((it / step).roundToInt() * step).coerceIn(range) },
                                                onValueChangeFinished = {
                                                    if (settings.playerDynamicLoudnessEnabled) {
                                                        previewValue?.let(saveValue)
                                                    }
                                                    previewValue = null
                                                },
                                                valueRange = range,
                                                steps = ((range.endInclusive - range.start) / step).roundToInt() - 1,
                                                centered = true,
                                                showStops = false,
                                                showTicks = false,
                                                modifier = Modifier.weight(1f).widthIn(max = 200.dp).semantics {
                                                    contentDescription = title
                                                    stateDescription = valueText
                                                },
                                            )
                                            IconButton(
                                                onClick = {
                                                    previewValue = null
                                                    saveValue(defaultValue)
                                                },
                                                enabled = displayedValue != defaultValue,
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Icon(Icons.Rounded.RestartAlt, contentDescription = "重置$title")
                                            }
                                        }
                                    }
                                }
                            } else {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp)
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("音量均衡", style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.weight(1f))
                                    SingleChoiceSegmentedButtonRow {
                                        val modes = PlaybackLoudnessMode.entries
                                        modes.forEachIndexed { index, mode ->
                                            SegmentedButton(
                                                selected = mode == settings.playerLoudnessMode,
                                                onClick = { settings.playerLoudnessMode = mode },
                                                shape = SegmentedButtonDefaults.itemShape(
                                                    index = index,
                                                    count = modes.size
                                                ),
                                                modifier = Modifier.padding(vertical = 8.dp),
                                            ) {
                                                Text(
                                                    mode.title,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "信息上报",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(8.dp)
                        )
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
                                Switch(
                                    checked = settings.playerReportStartEnabled,
                                    onCheckedChange = null
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                                    .padding(horizontal = 16.dp),
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
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = modes.size
                                            ),
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        ) {
                                            Text(
                                                mode.title,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playerReportProgressImmediatelyEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playerReportProgressImmediatelyEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("立即上报播放进度", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { showReportProgressImmediatelyInfo = true },
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "立即上报播放进度说明",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Box(Modifier.weight(1f))
                                Switch(
                                    checked = settings.playerReportProgressImmediatelyEnabled,
                                    onCheckedChange = null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
