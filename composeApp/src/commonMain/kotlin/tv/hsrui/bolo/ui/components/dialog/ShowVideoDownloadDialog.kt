package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.download.DownloadManager
import tv.hsrui.bolo.download.VideoDownloadViewModel
import tv.hsrui.network.login.storage.LoginStorage

@Composable
fun ShowVideoDownloadDialog(avid: Long, cid: Long, title: String, onDismiss: () -> Unit) {
    val settings: BoloSettings = koinInject()
    val login: LoginStorage = koinInject()
    val manager: DownloadManager = koinInject()
    val owner = remember(avid, cid) { object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val model: VideoDownloadViewModel = viewModel(viewModelStoreOwner = owner) {
        VideoDownloadViewModel(avid, cid, title, settings.playback, login, manager)
    }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.submitted) { if (state.submitted) onDismiss() }
    ShowConfirmDialog(onCancel = onDismiss, onConfirm = model::submit, confirmText = "下载",
        confirmEnabled = state.source != null && state.spec != null && !state.isSubmitting && !state.isLoading) {
        Column(Modifier.width(280.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("下载规格", style = MaterialTheme.typography.titleLarge)
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            val source = state.source
            val spec = state.spec
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
            } else if (source != null && spec != null) {
                val qualities = source.videoQualities.filter { source.availableVideoCodecs(it).isNotEmpty() }
                val codecs = source.availableVideoCodecs(spec.videoQuality)
                val audio = source.audioQualities.filter { source.getExactAudio(it) != null }
                DownloadSpecSelector("清晰度", spec.videoQuality.title, qualities.map { it.title }, !state.isSubmitting) { model.selectQuality(qualities[it]) }
                DownloadSpecSelector("视频编码", spec.videoCodec.name, codecs.map { it.name }, !state.isSubmitting) { model.selectCodec(codecs[it]) }
                if (audio.isEmpty()) Text("音质：无音轨", style = MaterialTheme.typography.bodyMedium)
                else DownloadSpecSelector("音质", spec.audioQuality?.title.orEmpty(), audio.map { it.title }, !state.isSubmitting) { model.selectAudio(audio[it]) }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (!state.isLoading && source == null) TextButton(onClick = model::loadPlayInfo) { Text("重试") }
            if (state.isSubmitting) Text("正在创建下载任务…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DownloadSpecSelector(title: String, value: String, options: List<String>, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(value) }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, text ->
                    DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(index) })
                }
            }
        }
    }
}
