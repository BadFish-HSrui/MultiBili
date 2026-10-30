package tv.hsrui.bolo.ui.components.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tv.hsrui.bolo.livePlayer.danmaku.LiveDanmakuSender
import tv.hsrui.network.liveFeature.message.fansMedal.LiveFansMedalManager

@Composable
fun ShowLiveDanmakuInput(sender: LiveDanmakuSender, fansMedal: LiveFansMedalManager) {
    val focusRequester = remember { FocusRequester() }
    val medalState by fansMedal.state.collectAsState()
    Dialog(onDismissRequest = sender::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.matchParentSize().clickable(interactionSource = null, indication = null, onClick = sender::dismiss))
            Surface(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 640.dp)
                    .fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = sender.draft,
                        onValueChange = sender::updateDraft,
                        readOnly = sender.isSending,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 5,
                        label = { Text("发送弹幕") },
                        isError = sender.isTooLong || sender.sendError != null,
                        supportingText = {
                            Text("${sender.length}/${sender.config?.maxLength ?: "—"}")
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                    if (sender.isTooLong) Text(
                        "内容超长，请缩短后发送", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    sender.sendError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    sender.configError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            ShowLiveFansMedalSelector(fansMedal, enabled = !sender.isSending)
                            if (sender.isLoadingConfig) Text("正在加载发送配置…", style = MaterialTheme.typography.bodySmall)
                            else if (sender.configError != null) TextButton(onClick = sender::loadConfig) { Text("重试") }
                        }
                        TextButton(onClick = sender::dismiss) { Text("关闭") }
                        Button(
                            onClick = { if (!fansMedal.state.value.isChanging) sender.send() },
                            enabled = sender.canSend && !medalState.isChanging,
                        ) {
                            if (sender.isSending) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("发送中", Modifier.padding(start = 8.dp))
                            } else Text("发送")
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}
