package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import tv.hsrui.bolo.ui.theme.BoloShapes
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowConfirmDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    cancelEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
    confirmText: String = "确认",
    dismissOnClickOutside: Boolean = true,
    forcedDisplaySeconds: Int? = null,
    content: @Composable () -> Unit,
) {
    var remainingSeconds by remember(forcedDisplaySeconds) {
        mutableIntStateOf(forcedDisplaySeconds?.coerceAtLeast(0) ?: 0)
    }
    LaunchedEffect(forcedDisplaySeconds) {
        val durationMillis = (forcedDisplaySeconds?.coerceAtLeast(0) ?: 0).toLong() * 1_000L
        val startedAt = TimeSource.Monotonic.markNow()
        while (true) {
            val remainingMillis = (durationMillis - startedAt.elapsedNow().inWholeMilliseconds).coerceAtLeast(0L)
            remainingSeconds = ((remainingMillis + 999L) / 1_000L).toInt()
            if (remainingSeconds == 0) break
            delay(remainingMillis - (remainingSeconds - 1L) * 1_000L)
        }
    }
    BasicAlertDialog(
        onDismissRequest = {
            if (cancelEnabled) onCancel()
        },
        properties = DialogProperties(dismissOnClickOutside = dismissOnClickOutside),
    ) {
        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = BoloShapes.InfoCard.Default,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp),
            ) {
                content()

                Row {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = cancelEnabled,
                    ) {
                        Text("取消")
                    }
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    OutlinedButton(
                        onClick = { if (remainingSeconds == 0 && confirmEnabled) onConfirm() },
                        enabled = confirmEnabled && remainingSeconds == 0,
                    ) {
                        Box {
                            Text(
                                text = confirmText,
                                modifier = if (remainingSeconds > 0) {
                                    Modifier.alpha(0f).clearAndSetSemantics {}
                                } else {
                                    Modifier
                                },
                            )
                            if (remainingSeconds > 0) {
                                Box(
                                    modifier = Modifier.matchParentSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${remainingSeconds}s", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShowConfirmDialog(
    title: @Composable () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    icon: @Composable (() -> Unit)? = null,
    text: String = "",
    dismissOnClickOutside: Boolean = true,
    cancelEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
    confirmText: String = "确认",
    textModifier: Modifier = Modifier.alpha(0.8F),
    forcedDisplaySeconds: Int? = null,
    content: @Composable () -> Unit = {},
) {
    ShowConfirmDialog(
        onCancel = onCancel,
        onConfirm = onConfirm,
        cancelEnabled = cancelEnabled,
        confirmEnabled = confirmEnabled,
        confirmText = confirmText,
        dismissOnClickOutside = dismissOnClickOutside,
        forcedDisplaySeconds = forcedDisplaySeconds,
    ) {
        if (icon != null) icon()

        title()

        if (text.isNotEmpty()) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp).then(textModifier),
            )
        }
        content()
    }
}
