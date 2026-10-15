package tv.hsrui.bolo.player.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.player.settings.PlayerShortcutAction
import tv.hsrui.bolo.player.settings.PlayerShortcutBinding
import tv.hsrui.bolo.player.settings.PlayerShortcutSettings
import tv.hsrui.bolo.player.settings.formatShortcutText
import tv.hsrui.bolo.player.settings.isShortcutDelete
import tv.hsrui.bolo.player.settings.shortcutModifier
import tv.hsrui.bolo.player.settings.shortcutModifiers

// 录入期间只收集按键；完整松开后才发布一次绑定，不创建文本输入会话。
private class ShortcutRecorder {
    var target by mutableStateOf<Pair<PlayerShortcutAction, Int>?>(null)
        private set
    var text by mutableStateOf("请按快捷键")
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val pressed = mutableSetOf<Key>()
    private val mainKeys = mutableSetOf<Key>()
    private var modifiers = 0
    private var clear = false

    fun begin(action: PlayerShortcutAction, slot: Int) {
        cancel()
        target = action to slot
    }

    fun cancel() {
        target = null
        resetPress()
    }

    private fun resetPress() {
        pressed.clear()
        mainKeys.clear()
        modifiers = 0
        clear = false
        text = "请按快捷键"
        error = null
    }

    fun onKeyEvent(event: KeyEvent, commit: (PlayerShortcutAction, Int, PlayerShortcutBinding?) -> Unit): Boolean {
        val current = target ?: return false
        val isUnknownKey = event.key.keyCode.toInt() == Key.Unknown.keyCode.toInt()
        if (isUnknownKey || event.key == Key.Escape || event.key == Key.Tab) {
            cancel()
            return isUnknownKey || event.key == Key.Escape
        }
        if (event.type == KeyEventType.KeyDown) {
            if (!pressed.add(event.key)) return true
            modifiers = modifiers or event.shortcutModifiers or event.key.shortcutModifier
            if (event.key.isShortcutDelete) clear = true
            else if (event.key.shortcutModifier == 0) mainKeys.add(event.key)
            val binding = mainKeys.singleOrNull()?.let { PlayerShortcutBinding(it.keyCode, modifiers) }
            error = when {
                clear -> null
                mainKeys.size > 1 -> "只能包含一个主键"
                modifiers.countOneBits() > 2 -> "最多三个键"
                binding != null && !binding.isValid -> "此按键保留给界面操作"
                else -> null
            }
            text = if (clear) "松开以清除" else error ?: binding?.text ?: formatShortcutText(null, modifiers)
        } else if (event.type == KeyEventType.KeyUp) {
            pressed.remove(event.key)
            if (pressed.isEmpty() && event.shortcutModifiers == 0) {
                val binding = mainKeys.singleOrNull()?.let { PlayerShortcutBinding(it.keyCode, modifiers) }
                if (clear || (error == null && binding?.isValid == true)) {
                    commit(current.first, current.second, if (clear) null else binding)
                    cancel()
                } else {
                    val message = error ?: "需要一个主键"
                    resetPress()
                    error = message
                    text = message
                }
            }
        }
        return true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoloPlayerShortcutSettingsSheet(
    settings: PlayerShortcutSettings,
    onDismissRequest: () -> Unit,
) {
    val recorder = remember { ShortcutRecorder() }
    val focusRequester = remember { FocusRequester() }
    var message by remember { mutableStateOf<String?>(null) }
    var escapePressed by remember { mutableStateOf(false) }
    var dismissOnEscapeRelease by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { recorder.cancel(); onDismissRequest() },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.key == Key.Escape) {
                if (event.type == KeyEventType.KeyDown && !escapePressed) {
                    escapePressed = true
                    dismissOnEscapeRelease = recorder.target == null
                    recorder.cancel()
                }
                if (event.type == KeyEventType.KeyUp) {
                    val dismiss = escapePressed && dismissOnEscapeRelease
                    escapePressed = false
                    dismissOnEscapeRelease = false
                    if (dismiss) onDismissRequest()
                }
                true
            } else recorder.onKeyEvent(event) { action, slot, binding ->
                val replaced = settings.bind(action, slot, binding)
                message = if (replaced.isEmpty()) null
                    else "已移除${replaced.joinToString("、") { it.title }}的重复绑定"
            }
        },
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        // Esc 松开后才关闭 Sheet，长按重复事件不会落到下层设置或播放页。
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        LaunchedEffect(windowFocused) {
            if (!windowFocused) {
                recorder.cancel()
                escapePressed = false
                dismissOnEscapeRelease = false
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().fillMaxHeight(0.85f).focusRequester(focusRequester)
                .focusable()
                // 点击监听放在内容内，使命中区域随 Sheet 移动。
                .pointerInput(recorder) {
                    awaitPointerEventScope {
                        while (true) {
                            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                                recorder.cancel()
                            }
                        }
                    }
                },
        ) {
            val rowWidth = (maxWidth - 32.dp).coerceAtLeast(560.dp)
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                Column(Modifier.horizontalScroll(rememberScrollState()).width(rowWidth)) {
                    PlayerShortcutAction.entries.forEach { action ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(action.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            repeat(2) { slot ->
                                val active = recorder.target == (action to slot)
                                OutlinedButton(
                                    onClick = {
                                        message = null
                                        recorder.begin(action, slot)
                                        focusRequester.requestFocus()
                                    },
                                    modifier = Modifier.width(148.dp),
                                    border = BorderStroke(1.dp, when {
                                        active && recorder.error != null -> MaterialTheme.colorScheme.error
                                        active -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }),
                                ) {
                                    Text(if (active) recorder.text else settings.desktopBindings[action.id]?.getOrNull(slot)?.text ?: "未绑定",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                                IconButton(
                                    onClick = {
                                        recorder.cancel()
                                        val replaced = settings.reset(action)
                                        message = if (replaced.isEmpty()) null
                                            else "已移除${replaced.joinToString("、") { it.title }}的重复绑定"
                                    },
                                    modifier = Modifier.size(32.dp),
                                    enabled = settings.desktopBindings[action.id] != action.defaultBindings,
                                ) {
                                    Icon(Icons.Rounded.RestartAlt, "重置${action.title}", Modifier.size(24.dp))
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
