package tv.hsrui.bolo.ui.common.update

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.network.feature.update.AppReleaseData
import tv.hsrui.network.feature.update.downloadAppReleaseAsset
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun ShowAppUpdateAction(release: AppReleaseData, modifier: Modifier) {
    val context = LocalContext.current
    val snackbarManager: SnackbarManager = koinInject()
    val scope = rememberCoroutineScope()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var downloading by remember(release) { mutableStateOf(false) }
    var progress by remember(release) { mutableStateOf<Float?>(null) }
    var apkFile by remember(release) { mutableStateOf<File?>(null) }
    var pendingInstall by remember(release) { mutableStateOf(false) }
    var openingInstaller by remember(release) { mutableStateOf(false) }

    val installerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        openingInstaller = false
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        openingInstaller = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            pendingInstall = true
        } else {
            snackbarManager.showMessage("未授予安装权限，可点击安装重试")
        }
    }

    // 下载可能在系统浏览器或后台完成，仅在弹窗所属页面恢复前台后发起安装。
    LaunchedEffect(pendingInstall, lifecycleState) {
        if (!pendingInstall || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        pendingInstall = false
        val file = apkFile
        if (file == null || !file.isFile) {
            apkFile = null
            snackbarManager.showMessage("安装包已失效，请重新下载")
            return@LaunchedEffect
        }
        try {
            openingInstaller = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                permissionLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
                )
            } else {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.download.files", file)
                installerLauncher.launch(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        clipData = ClipData.newRawUri("安装包", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            openingInstaller = false
            snackbarManager.showMessage("打开安装界面失败：${e.message ?: "未知错误"}")
        }
    }

    Box(modifier = modifier.height(32.dp), contentAlignment = Alignment.Center) {
        if (downloading) {
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val currentProgress = progress
                if (currentProgress == null) {
                    LinearWavyProgressIndicator(modifier = Modifier.weight(1f))
                } else {
                    LinearWavyProgressIndicator(progress = { currentProgress }, modifier = Modifier.weight(1f))
                    Text("${(currentProgress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                TextButton(
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    enabled = !openingInstaller && !pendingInstall,
                    onClick = {
                        if (downloading || openingInstaller || pendingInstall) return@TextButton
                        if (apkFile?.isFile == true) {
                            pendingInstall = true
                        } else {
                            apkFile = null
                            val asset = release.universalApk
                            if (asset == null) {
                                snackbarManager.showMessage("未找到 universal 安装包")
                                return@TextButton
                            }
                            downloading = true
                            progress = null
                            scope.launch {
                                try {
                                    val downloaded = withContext(Dispatchers.IO) {
                                        val directory = File(context.cacheDir, "app-updates")
                                        check(directory.isDirectory || directory.mkdirs()) { "无法创建安装包缓存目录" }
                                        // 留出系统安装器读取文件的时间，仅清理一天前的更新缓存。
                                        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                                        directory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }
                                            ?.forEach { it.delete() }
                                        val partial = File.createTempFile("update-", ".part", directory)
                                        val complete = File(directory, "${partial.nameWithoutExtension}.apk")
                                        var published = false
                                        try {
                                            partial.outputStream().buffered().use { output ->
                                                downloadAppReleaseAsset(
                                                    url = asset.downloadUrl,
                                                    write = { bytes, count -> output.write(bytes, 0, count) },
                                                    onProgress = { received, total ->
                                                        val size = asset.size.takeIf { it > 0 } ?: total
                                                        withContext(Dispatchers.Main) {
                                                            progress = size?.let { (received.toDouble() / it).toFloat().coerceIn(0f, 1f) }
                                                        }
                                                    },
                                                )
                                            }
                                            ensureActive()
                                            check(asset.size <= 0 || partial.length() == asset.size) { "安装包大小不匹配" }
                                            check(partial.renameTo(complete)) { "无法保存安装包" }
                                            val archive = context.packageManager.getPackageArchiveInfo(complete.absolutePath, 0)
                                            check(archive?.packageName == context.packageName) { "安装包无效或包名不匹配" }
                                            ensureActive()
                                            published = true
                                            complete
                                        } finally {
                                            partial.delete()
                                            if (!published) complete.delete()
                                        }
                                    }
                                    apkFile = downloaded
                                    pendingInstall = true
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    snackbarManager.showMessage("下载失败：${e.message ?: "未知错误"}")
                                } finally {
                                    downloading = false
                                }
                            }
                        }
                    },
                ) {
                    Text(if (apkFile != null) "安装" else "下载并安装", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
