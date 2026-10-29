package tv.hsrui.bolo.boloSetting.setting.live

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
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog
import tv.hsrui.bolo.ui.components.slider.ShowSlider
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoCodec
import tv.hsrui.network.liveFeature.player.enumModels.LiveVideoQuality
import kotlin.math.roundToInt

@Composable
fun LiveSettingsScreen(modifier: Modifier = Modifier) = LookaheadScope {
    val settings = koinInject<BoloSettings>().live
    var infoMessage by remember { mutableStateOf<String?>(null) }
    infoMessage?.let { message ->
        ShowInfoDialog(onConfirm = { infoMessage = null }) {
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isExpanded()) ShowTopBarWithNavigationButton(title = { Text("直播设置") })
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding.calculateWithoutBottom()),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (getPlatform().isPhone) {
                            Text("播放习惯", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp)
                                        .toggleable(
                                            value = settings.autoFullscreenOnRotateEnabled,
                                            role = Role.Switch,
                                            onValueChange = { settings.autoFullscreenOnRotateEnabled = it },
                                        ).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("旋转自动全屏", style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.weight(1f))
                                    Switch(checked = settings.autoFullscreenOnRotateEnabled, onCheckedChange = null)
                                }
                            }
                        }
                        Text("音画设置", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("默认视频编码", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(max = 220.dp)) {
                                    LiveVideoCodec.entries.forEachIndexed { index, codec ->
                                        SegmentedButton(
                                            selected = codec == settings.defaultVideoCodec,
                                            onClick = { settings.defaultVideoCodec = codec },
                                            shape = SegmentedButtonDefaults.itemShape(index, LiveVideoCodec.entries.size),
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        ) {
                                            Text(codec.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                            val qualities = LiveVideoQuality.entries
                            val savedQualityIndex = qualities.indexOf(settings.defaultVideoQuality)
                            var previewQualityIndex by remember(savedQualityIndex) { mutableStateOf<Int?>(null) }
                            val displayedQualityIndex = previewQualityIndex ?: savedQualityIndex
                            val qualityText = qualities[displayedQualityIndex].title
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("默认播放画质", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Spacer(Modifier.weight(1f))
                                Row(
                                    modifier = Modifier.widthIn(max = 304.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(qualityText, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.End, maxLines = 1)
                                    ShowSlider(
                                        value = displayedQualityIndex.toFloat(),
                                        onValueChange = { previewQualityIndex = it.roundToInt().coerceIn(qualities.indices) },
                                        onValueChangeFinished = {
                                            previewQualityIndex?.let { settings.defaultVideoQuality = qualities[it] }
                                            previewQualityIndex = null
                                        },
                                        valueRange = 0f..qualities.lastIndex.toFloat(),
                                        steps = qualities.size - 2,
                                        centered = false,
                                        showStops = true,
                                        showTicks = true,
                                        modifier = Modifier.weight(1f).widthIn(max = 200.dp).semantics {
                                            contentDescription = "默认播放画质"
                                            stateDescription = qualityText
                                        },
                                    )
                                    IconButton(
                                        onClick = {
                                            previewQualityIndex = null
                                            settings.defaultVideoQuality = LiveVideoQuality.ORIGINAL
                                        },
                                        enabled = qualities[displayedQualityIndex] != LiveVideoQuality.ORIGINAL,
                                        modifier = Modifier.size(24.dp),
                                    ) { Icon(Icons.Rounded.RestartAlt, contentDescription = "重置默认播放画质") }
                                }
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.recordQualitySelectionEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.recordQualitySelectionEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("记录画质选择", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { infoMessage = "开启后修改音画质会同时修改默认项" },
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                ) { Icon(Icons.Outlined.Info, contentDescription = "记录画质选择说明", modifier = Modifier.size(16.dp)) }
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.recordQualitySelectionEnabled, onCheckedChange = null)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.optimizePlaybackSourceEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.optimizePlaybackSourceEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("优化直播源", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.optimizePlaybackSourceEnabled, onCheckedChange = null)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playbackBufferDisabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playbackBufferDisabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("关闭播放缓冲", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { infoMessage = "默认使用和官方类似的加载缓冲，禁用后会减少3～5秒播放延迟，但可能因网络波动频繁卡顿" },
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                ) { Icon(Icons.Outlined.Info, contentDescription = "关闭播放缓冲说明", modifier = Modifier.size(16.dp)) }
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.playbackBufferDisabled, onCheckedChange = null)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.localReplayEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.localReplayEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("开启本地回放", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.localReplayEnabled, onCheckedChange = null)
                            }
                            if (settings.localReplayEnabled) {
                                HorizontalDivider()
                                val cacheSizes = listOf(64, 128, 256, 512, 1024)
                                val savedIndex = cacheSizes.indexOf(settings.cacheSizeMiB)
                                var previewIndex by remember(savedIndex) { mutableStateOf<Int?>(null) }
                                val displayedIndex = previewIndex ?: savedIndex
                                val displayedSize = cacheSizes[displayedIndex]
                                val valueText = if (displayedSize == 1024) "1G" else "${displayedSize}M"
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("播放缓存", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    IconButton(
                                        onClick = { infoMessage = "可用于加载本地回放的空间大小,过大时可能无法获取足够空间。" },
                                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                    ) { Icon(Icons.Outlined.Info, contentDescription = "播放缓存说明", modifier = Modifier.size(16.dp)) }
                                    Spacer(Modifier.weight(1f))
                                    Row(
                                        modifier = Modifier.widthIn(max = 304.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(valueText, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.End, maxLines = 1)
                                        ShowSlider(
                                            value = displayedIndex.toFloat(),
                                            onValueChange = { previewIndex = it.roundToInt().coerceIn(cacheSizes.indices) },
                                            onValueChangeFinished = {
                                                previewIndex?.let { settings.cacheSizeMiB = cacheSizes[it] }
                                                previewIndex = null
                                            },
                                            valueRange = 0f..cacheSizes.lastIndex.toFloat(),
                                            steps = cacheSizes.size - 2,
                                            centered = false,
                                            showStops = true,
                                            showTicks = true,
                                            modifier = Modifier.weight(1f).widthIn(max = 200.dp).semantics {
                                                contentDescription = "播放缓存"
                                                stateDescription = valueText
                                            },
                                        )
                                        IconButton(
                                            onClick = { previewIndex = null; settings.cacheSizeMiB = 256 },
                                            enabled = displayedSize != 256,
                                            modifier = Modifier.size(24.dp),
                                        ) { Icon(Icons.Rounded.RestartAlt, contentDescription = "重置播放缓存") }
                                    }
                                }
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
                                    value = settings.dynamicLoudnessEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.dynamicLoudnessEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "动态音量均衡（实验性）",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                IconButton(
                                    onClick = { infoMessage = "开启后让所有音量向目标音量靠近，能避免过大或过小声音，但会损失动态范围。" },
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
                                    checked = settings.dynamicLoudnessEnabled,
                                    onCheckedChange = null
                                )
                            }
                            if (settings.dynamicLoudnessEnabled) {
                                HorizontalDivider()
                                listOf(
                                    Triple("目标音量", -20f..-8f, 1f),
                                    Triple("动态范围", 6f..16f, 1f),
                                    Triple("峰值音量", -4f..0f, 0.5f),
                                ).forEachIndexed { index, (title, range, step) ->
                                    val savedValue = when (index) {
                                        0 -> settings.dynamicLoudnessTargetLufs
                                        1 -> settings.dynamicLoudnessRangeLu
                                        else -> settings.dynamicLoudnessTruePeakDbtp
                                    }
                                    val defaultValue = (range.start + range.endInclusive) / 2f
                                    var previewValue by remember(index, savedValue) { mutableStateOf<Float?>(null) }
                                    val saveValue: (Float) -> Unit = { value ->
                                        when (index) {
                                            0 -> settings.dynamicLoudnessTargetLufs = value
                                            1 -> settings.dynamicLoudnessRangeLu = value
                                            else -> settings.dynamicLoudnessTruePeakDbtp = value
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
                                                    if (settings.dynamicLoudnessEnabled) {
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
                            }
                        }
                    }
                }
            }
        }
    }
}
