package tv.hsrui.bolo.boloSetting.setting.live

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.ui.components.slider.ShowSlider
import kotlin.math.roundToInt

@Composable
fun LiveDanmakuSettingsContent(settings: LiveDanmakuSettings, isActive: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().toggleable(settings.enabled, enabled = isActive, role = Role.Switch,
                    onValueChange = { settings.enabled = it }).height(32.dp).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("显示弹幕", style = MaterialTheme.typography.bodyMedium)
                Switch(settings.enabled, null, enabled = isActive, modifier = Modifier.size(39.dp, 24.dp).scale(.75f))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(4.dp)) {
                LiveDanmakuPercentageSlider("弹幕缩放", settings.scale, isActive, { settings.scale = it })
                HorizontalDivider()
                LiveDanmakuPercentageSlider("弹幕不透明度", settings.alpha, isActive, { settings.alpha = it },
                    range = 20..100, preview = settings::previewAlpha)
                HorizontalDivider()
                LiveDanmakuPercentageSlider("弹幕速度", settings.speed, isActive, { settings.speed = it })
                HorizontalDivider()
                LiveDanmakuPercentageSlider("弹幕显示区域", settings.displayAreaRatio, isActive,
                    { settings.displayAreaRatio = it }, range = 20..100, step = 5)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(4.dp)) {
                listOf("滚动弹幕", "顶部弹幕", "底部弹幕").forEachIndexed { index, label ->
                    if (index > 0) HorizontalDivider()
                    val checked = when (index) {
                        0 -> settings.scrollEnabled
                        1 -> settings.topEnabled
                        else -> settings.bottomEnabled
                    }
                    Row(
                        Modifier.fillMaxWidth().toggleable(checked, enabled = isActive, role = Role.Switch, onValueChange = {
                            when (index) {
                                0 -> settings.scrollEnabled = it
                                1 -> settings.topEnabled = it
                                else -> settings.bottomEnabled = it
                            }
                        }).height(32.dp).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Switch(checked, null, enabled = isActive, modifier = Modifier.size(39.dp, 24.dp).scale(.75f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDanmakuPercentageSlider(
    label: String,
    value: Float,
    isActive: Boolean,
    save: (Float) -> Unit,
    range: IntRange = 50..200,
    step: Int = 1,
    preview: ((Float?) -> Unit)? = null,
) {
    var candidate by remember { mutableStateOf<Int?>(null) }
    val currentPreview by rememberUpdatedState(preview)
    val currentSave by rememberUpdatedState(save)
    val enabled by rememberUpdatedState(isActive)
    val direction = LocalLayoutDirection.current
    fun discard() {
        candidate = null
        currentPreview?.invoke(null)
    }
    fun snap(number: Float): Int =
        (range.first + ((number - range.first) / step).roundToInt() * step).coerceIn(range)
    fun commit() {
        if (enabled) candidate?.let { currentSave(it / 100f) }
        discard()
    }
    val interactions = remember {
        val delegate = MutableInteractionSource()
        object : MutableInteractionSource by delegate {
            override suspend fun emit(interaction: Interaction) {
                if (interaction is DragInteraction.Cancel) discard()
                delegate.emit(interaction)
            }
            override fun tryEmit(interaction: Interaction): Boolean {
                if (interaction is DragInteraction.Cancel) discard()
                return delegate.tryEmit(interaction)
            }
        }
    }
    DisposableEffect(isActive) {
        discard()
        onDispose { discard() }
    }
    val percent = candidate ?: (value * 100).roundToInt()
    val centered = range == 50..200
    val position = if (centered) (percent - 100) / if (percent <= 100) 50f else 100f else percent.toFloat()
    Column(Modifier.padding(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$percent%", style = MaterialTheme.typography.bodySmall)
        }
        ShowSlider(
            value = position,
            onValueChange = {
                candidate = snap(if (centered) 100 + it * if (it <= 0) 50 else 100 else it)
                currentPreview?.invoke(candidate?.div(100f))
            },
            onValueChangeFinished = ::commit,
            valueRange = if (centered) -1f..1f else range.first.toFloat()..range.last.toFloat(),
            steps = if (centered) 0 else (range.last - range.first) / step - 1,
            centered = centered,
            showTicks = false,
            enabled = isActive,
            interactionSource = interactions,
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) discard() }
                .onPreviewKeyEvent { event ->
                    if (!isActive) return@onPreviewKeyEvent false
                    val sign = if (direction == LayoutDirection.Ltr) 1 else -1
                    val target = when (event.key) {
                        Key.DirectionRight -> percent + sign * step
                        Key.DirectionLeft -> percent - sign * step
                        Key.MoveHome -> range.first
                        Key.MoveEnd -> range.last
                        Key.PageUp -> percent + 10
                        Key.PageDown -> percent - 10
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.type == KeyEventType.KeyDown) {
                        candidate = snap(target.toFloat())
                        currentPreview?.invoke(candidate?.div(100f))
                    } else if (event.type == KeyEventType.KeyUp) commit()
                    true
                }.semantics {
                    contentDescription = label
                    stateDescription = "$percent%"
                    progressBarRangeInfo = ProgressBarRangeInfo(percent.toFloat(), range.first.toFloat()..range.last.toFloat(),
                        (range.last - range.first) / step - 1)
                    setProgress {
                        if (!isActive) false else {
                            candidate = snap(it)
                            commit()
                            true
                        }
                    }
                },
        )
    }
}
