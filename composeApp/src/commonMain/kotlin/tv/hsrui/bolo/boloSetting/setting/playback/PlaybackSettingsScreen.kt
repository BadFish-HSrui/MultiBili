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
import tv.hsrui.bolo.PlatformType
import tv.hsrui.bolo.getPlatform
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.boloSetting.PlaybackEndBehavior
import tv.hsrui.bolo.boloSetting.PlaybackLoudnessMode
import tv.hsrui.bolo.boloSetting.PlaybackProgressReportMode
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog
import tv.hsrui.bolo.ui.components.slider.ShowSlider
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.feature.player.enumModels.AudioQuality
import tv.hsrui.network.feature.player.enumModels.Quality
import tv.hsrui.network.feature.player.enumModels.VideoCodec
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
    var showOptimizePlaybackSourceInfo by remember { mutableStateOf(false) }
    var showSubtitleAutoEnableInfo by remember { mutableStateOf(false) }

    if (showSubtitleAutoEnableInfo) {
        ShowInfoDialog(onConfirm = { showSubtitleAutoEnableInfo = false }) {
            Text(
                text = "智能模式下，根据官方接口提供的字幕开启建议决定。",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).alpha(0.8F),
            )
        }
    }

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

    if (showOptimizePlaybackSourceInfo) {
        ShowInfoDialog(onConfirm = { showOptimizePlaybackSourceInfo = false }) {
            Text(
                text = "重新排序获取的播放源列表，让官方/服务商CDN优先。\n\n" +
                        "B站通常将PCDN作为默认源，在最后一个备用源才提供官方/服务商CDN。",
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
                            if (getPlatform().type != PlatformType.Desktop) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                        value = settings.playback.backgroundPlaybackEnabled,
                                        role = Role.Switch,
                                        onValueChange = { settings.playback.backgroundPlaybackEnabled = it },
                                    ).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("后台播放", style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.weight(1f))
                                    Switch(checked = settings.playback.backgroundPlaybackEnabled, onCheckedChange = null)
                                }
                                HorizontalDivider()
                            }
                            if (getPlatform().isPhone) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                        value = settings.playback.autoFullscreenOnRotateEnabled,
                                        role = Role.Switch,
                                        onValueChange = { settings.playback.autoFullscreenOnRotateEnabled = it },
                                    ).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("旋转自动全屏", style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.weight(1f))
                                    Switch(checked = settings.playback.autoFullscreenOnRotateEnabled, onCheckedChange = null)
                                }
                                HorizontalDivider()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playback.autoPlayOnOpenEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playback.autoPlayOnOpenEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("打开视频自动播放", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = settings.playback.autoPlayOnOpenEnabled,
                                    onCheckedChange = null
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playback.autoEnableDanmakuOnOpenEnabled,
                                    role = Role.Switch,
                                    onValueChange = {
                                        settings.playback.autoEnableDanmakuOnOpenEnabled = it
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
                                    checked = settings.playback.autoEnableDanmakuOnOpenEnabled,
                                    onCheckedChange = null
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playback.resumeFromHistoryEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playback.resumeFromHistoryEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("从播放记录继续", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = settings.playback.resumeFromHistoryEnabled,
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
                                Text("播放结束行为", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(max = 220.dp)) {
                                    val behaviors = PlaybackEndBehavior.entries
                                    behaviors.forEachIndexed { index, behavior ->
                                        SegmentedButton(
                                            selected = behavior == settings.playback.endBehavior,
                                            onClick = { settings.playback.endBehavior = behavior },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = behaviors.size,
                                            ),
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        ) {
                                            Text(behavior.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
                            "音画设置",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("默认视频编码", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(max = 220.dp)) {
                                    val codecs = listOf(VideoCodec.AVC, VideoCodec.HEVC, VideoCodec.AV1)
                                    codecs.forEachIndexed { index, codec ->
                                        SegmentedButton(
                                            selected = codec == settings.playback.defaultVideoCodec,
                                            onClick = { settings.playback.defaultVideoCodec = codec },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = codecs.size,
                                            ),
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        ) {
                                            Text(codec.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
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
                                val savedQuality = if (isVideo) settings.playback.defaultVideoQuality else settings.playback.defaultAudioQuality
                                val savedIndex = qualities.indexOfFirst { it.code == savedQuality.code }
                                var previewIndex by remember(isVideo, savedIndex) { mutableStateOf<Int?>(null) }
                                val displayedIndex = previewIndex ?: savedIndex
                                val valueText = qualities[displayedIndex].shortTitle
                                val saveIndex: (Int) -> Unit = { index ->
                                    if (isVideo) settings.playback.defaultVideoQuality = VideoQuality.entries[index]
                                    else settings.playback.defaultAudioQuality = audioQualities[index]
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
                                    value = settings.playback.recordQualitySelectionEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playback.recordQualitySelectionEnabled = it },
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
                                Switch(checked = settings.playback.recordQualitySelectionEnabled, onCheckedChange = null)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playback.hideAudioQualitySelectorEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playback.hideAudioQualitySelectorEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("隐藏播放器音质选项", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.playback.hideAudioQualitySelectorEnabled, onCheckedChange = null)
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                    value = settings.playback.optimizePlaybackSourceEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playback.optimizePlaybackSourceEnabled = it },
                                ).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("优化播放源", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { showOptimizePlaybackSourceInfo = true },
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "优化播放源说明",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Switch(checked = settings.playback.optimizePlaybackSourceEnabled, onCheckedChange = null)
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "字幕开关",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(8.dp)
                        )
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("字幕自动开启", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { showSubtitleAutoEnableInfo = true },
                                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "字幕自动开启说明",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(max = 220.dp)) {
                                    val modes = listOf("关闭", "智能", "总是")
                                    val selectedIndex = when {
                                        settings.playback.subtitleAlwaysOn -> 2
                                        settings.playback.subtitleSmartEnabled -> 1
                                        else -> 0
                                    }
                                    modes.forEachIndexed { index, title ->
                                        SegmentedButton(
                                            selected = index == selectedIndex,
                                            onClick = {
                                                when (index) {
                                                    0 -> {
                                                        settings.playback.subtitleSmartEnabled = false
                                                        settings.playback.subtitleAlwaysOn = false
                                                    }
                                                    1 -> settings.playback.subtitleSmartEnabled = true
                                                    2 -> settings.playback.subtitleAlwaysOn = true
                                                }
                                            },
                                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        ) {
                                            Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            if (settings.playback.subtitleAlwaysOn) {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                        value = settings.playback.subtitleAutoChineseOnly,
                                        role = Role.Switch,
                                        onValueChange = { settings.playback.subtitleAutoChineseOnly = it },
                                    ).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("仅自动开启中文字幕", style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.weight(1f))
                                    Switch(checked = settings.playback.subtitleAutoChineseOnly, onCheckedChange = null)
                                }
                            }
                            if (settings.playback.subtitleSmartEnabled || settings.playback.subtitleAlwaysOn) {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(64.dp).toggleable(
                                        value = settings.playback.subtitleAutoExcludeAi,
                                        role = Role.Switch,
                                        onValueChange = { settings.playback.subtitleAutoExcludeAi = it },
                                    ).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("不自动开启AI字幕", style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.weight(1f))
                                    Switch(checked = settings.playback.subtitleAutoExcludeAi, onCheckedChange = null)
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
                                    value = settings.playback.dynamicLoudnessEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playback.dynamicLoudnessEnabled = it },
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
                                    checked = settings.playback.dynamicLoudnessEnabled,
                                    onCheckedChange = null
                                )
                            }
                            if (settings.playback.dynamicLoudnessEnabled) {
                                HorizontalDivider()
                                listOf(
                                    Triple("目标音量", -20f..-8f, 1f),
                                    Triple("动态范围", 6f..16f, 1f),
                                    Triple("峰值音量", -4f..0f, 0.5f),
                                ).forEachIndexed { index, (title, range, step) ->
                                    val savedValue = when (index) {
                                        0 -> settings.playback.dynamicLoudnessTargetLufs
                                        1 -> settings.playback.dynamicLoudnessRangeLu
                                        else -> settings.playback.dynamicLoudnessTruePeakDbtp
                                    }
                                    val defaultValue = (range.start + range.endInclusive) / 2f
                                    var previewValue by remember(index, savedValue) { mutableStateOf<Float?>(null) }
                                    val saveValue: (Float) -> Unit = { value ->
                                        when (index) {
                                            0 -> settings.playback.dynamicLoudnessTargetLufs = value
                                            1 -> settings.playback.dynamicLoudnessRangeLu = value
                                            else -> settings.playback.dynamicLoudnessTruePeakDbtp = value
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
                                                    if (settings.playback.dynamicLoudnessEnabled) {
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
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(max = 220.dp)) {
                                        val modes = PlaybackLoudnessMode.entries
                                        modes.forEachIndexed { index, mode ->
                                            SegmentedButton(
                                                selected = mode == settings.playback.loudnessMode,
                                                onClick = { settings.playback.loudnessMode = mode },
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
                                    value = settings.playback.reportStartEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playback.reportStartEnabled = it },
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
                                    checked = settings.playback.reportStartEnabled,
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
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.widthIn(max = 220.dp)) {
                                    val modes = PlaybackProgressReportMode.entries
                                    modes.forEachIndexed { index, mode ->
                                        SegmentedButton(
                                            selected = mode == settings.playback.reportProgressMode,
                                            onClick = {
                                                settings.playback.reportProgressMode = mode
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
                                    value = settings.playback.reportProgressImmediatelyEnabled,
                                    role = Role.Switch,
                                    onValueChange = { settings.playback.reportProgressImmediatelyEnabled = it },
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
                                    checked = settings.playback.reportProgressImmediatelyEnabled,
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
