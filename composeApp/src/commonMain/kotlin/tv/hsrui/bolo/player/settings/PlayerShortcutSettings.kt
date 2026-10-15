package tv.hsrui.bolo.player.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import eu.anifantakis.lib.ksafe.KSafePlain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class PlayerShortcutAction(val id: String, val title: String, private val defaultKey: Key) {
    TogglePlayback("toggle_playback", "播放／暂停", Key.Spacebar),
    SeekBackward("seek_backward", "快退", Key.DirectionLeft),
    SeekForward("seek_forward", "快进", Key.DirectionRight),
    VolumeUp("volume_up", "增加音量", Key.DirectionUp),
    VolumeDown("volume_down", "减少音量", Key.DirectionDown),
    ToggleMuted("toggle_muted", "静音开关", Key.M),
    ToggleDanmaku("toggle_danmaku", "弹幕开关", Key.D),
    ToggleFullscreen("toggle_fullscreen", "切换全屏", Key.F),
    SeekToStart("seek_to_start", "跳到开头", Key.Zero),
    PreviousEpisode("previous_episode", "上一集", Key.LeftBracket),
    NextEpisode("next_episode", "下一集", Key.RightBracket),
    DecreaseSpeed("decrease_speed", "降低倍速", Key.Comma),
    IncreaseSpeed("increase_speed", "提高倍速", Key.Period),
    ResetSpeed("reset_speed", "恢复原速", Key.Slash);

    val defaultBindings: List<PlayerShortcutBinding?>
        get() = listOf(PlayerShortcutBinding(defaultKey.keyCode),
            if (this == SeekToStart) PlayerShortcutBinding(Key.NumPad0.keyCode) else null)

    val isEpisode: Boolean get() = this == PreviousEpisode || this == NextEpisode
    val isVolume: Boolean get() = this == VolumeUp || this == VolumeDown || this == ToggleMuted
}

@Serializable
data class PlayerShortcutBinding(val keyCode: Long, val modifiers: Int = 0) {
    val isValid: Boolean
        get() = Key(keyCode).let { key ->
            keyCode.toInt() != Key.Unknown.keyCode.toInt() && key.shortcutModifier == 0 && !key.isShortcutDelete &&
                keyCode.toInt() !in listOf(Key.Escape, Key.Tab, Key.Enter, Key.NumPadEnter).map { it.keyCode.toInt() } &&
                modifiers and 15 == modifiers && modifiers.countOneBits() <= 2
        }

    val text: String
        get() = formatShortcutText(Key(keyCode), modifiers)
}

internal fun formatShortcutText(key: Key?, modifiers: Int): String = buildList {
    if (modifiers and 1 != 0) add(Key.CtrlLeft)
    if (modifiers and 2 != 0) add(Key.AltLeft)
    if (modifiers and 4 != 0) add(Key.ShiftLeft)
    if (modifiers and 8 != 0) add(Key.MetaLeft)
    add(key)
}.joinToString("+") { it?.toString()?.removePrefix("Key: ") ?: "…" }

internal val Key.shortcutModifier: Int
    // 桌面编码的高位是键盘位置；修饰键和保留键不按位置区分。
    get() = when (keyCode.toInt()) {
        Key.CtrlLeft.keyCode.toInt(), Key.CtrlRight.keyCode.toInt() -> 1
        Key.AltLeft.keyCode.toInt(), Key.AltRight.keyCode.toInt() -> 2
        Key.ShiftLeft.keyCode.toInt(), Key.ShiftRight.keyCode.toInt() -> 4
        Key.MetaLeft.keyCode.toInt(), Key.MetaRight.keyCode.toInt() -> 8
        else -> 0
    }

internal val Key.isShortcutDelete: Boolean
    get() = keyCode.toInt() == Key.Delete.keyCode.toInt() || keyCode.toInt() == Key.Backspace.keyCode.toInt() ||
        keyCode.toInt() == Key.NumPadDelete.keyCode.toInt()

internal val KeyEvent.shortcutModifiers: Int
    get() = (if (isCtrlPressed) 1 else 0) or (if (isAltPressed) 2 else 0) or
        (if (isShiftPressed) 4 else 0) or (if (isMetaPressed) 8 else 0)

internal fun Map<String, List<PlayerShortcutBinding?>>.shortcutAction(event: KeyEvent): PlayerShortcutAction? {
    val binding = PlayerShortcutBinding(event.key.keyCode, event.shortcutModifiers)
    return PlayerShortcutAction.entries.firstOrNull { this[it.id]?.contains(binding) == true }
}

class PlayerShortcutSettings(settingsKSafe: KSafePlain) {
    private val json = Json { ignoreUnknownKeys = true }
    private var storedDesktopBindings by settingsKSafe("", key = "player_shortcuts_desktop_bindings")
    var desktopBindings: Map<String, List<PlayerShortcutBinding?>> by mutableStateOf(readBindings())
        private set

    private fun readBindings(): Map<String, List<PlayerShortcutBinding?>> {
        val stored = runCatching {
            json.decodeFromString<Map<String, List<PlayerShortcutBinding?>>>(storedDesktopBindings)
        }.getOrNull().orEmpty()
        val used = mutableSetOf<PlayerShortcutBinding>()
        return PlayerShortcutAction.entries.associate { action ->
            val bindings = stored[action.id] ?: action.defaultBindings
            action.id to List(2) { index -> bindings.getOrNull(index)?.takeIf { it.isValid && used.add(it) } }
        }
    }

    fun bind(action: PlayerShortcutAction, slot: Int, binding: PlayerShortcutBinding?): Set<PlayerShortcutAction> {
        require(slot in 0..1 && (binding == null || binding.isValid))
        val updated = desktopBindings.mapValues { it.value.toMutableList() }
        val replaced = mutableSetOf<PlayerShortcutAction>()
        if (binding != null) for (other in PlayerShortcutAction.entries) {
            updated.getValue(other.id).forEachIndexed { index, value ->
                if (value == binding && (other != action || index != slot)) {
                    updated.getValue(other.id)[index] = null
                    replaced.add(other)
                }
            }
        }
        updated.getValue(action.id)[slot] = binding
        save(updated)
        return replaced
    }

    fun reset(action: PlayerShortcutAction): Set<PlayerShortcutAction> {
        val defaults = action.defaultBindings
        val replaced = mutableSetOf<PlayerShortcutAction>()
        val updated = desktopBindings.mapValues { (id, bindings) ->
            if (id == action.id) defaults else bindings.map { binding ->
                if (binding != null && binding in defaults) {
                    PlayerShortcutAction.entries.firstOrNull { it.id == id }?.let(replaced::add)
                    null
                } else binding
            }
        }
        save(updated)
        return replaced
    }

    private fun save(bindings: Map<String, List<PlayerShortcutBinding?>>) {
        if (desktopBindings == bindings) return
        val snapshot = bindings.mapValues { it.value.toList() }
        storedDesktopBindings = json.encodeToString(snapshot)
        desktopBindings = snapshot
    }
}
