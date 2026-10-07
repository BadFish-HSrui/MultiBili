package tv.hsrui.bolo.ui.common.update

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.BuildInfo
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog
import tv.hsrui.bolo.utils.url.openUrl
import tv.hsrui.network.feature.update.AppReleaseData
import tv.hsrui.network.feature.update.fetchLatestAppRelease

private val appReleaseVersionPattern = Regex("^(?:v)?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")

@Composable
fun CheckAppUpdate(
    hideError: Boolean = false,
    showLatestMessage: Boolean = false,
    onFinished: () -> Unit = {},
) {
    val snackbarManager: SnackbarManager = koinInject()
    val scope = rememberCoroutineScope()
    val finish by rememberUpdatedState(onFinished)
    var latestRelease by remember { mutableStateOf<AppReleaseData?>(null) }
    var isOpeningRelease by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val release = fetchLatestAppRelease()
            ensureActive()
            if (isNewerAppRelease(release.tagName, BuildInfo.appVersionCore, BuildInfo.appReleaseChannel)) {
                latestRelease = release
            } else {
                if (showLatestMessage) snackbarManager.showMessage("当前已是最新版本")
                finish()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (!hideError) {
                snackbarManager.showMessage("更新检测失败：${e.message?.takeIf { it.isNotBlank() } ?: "未知错误"}")
            }
            finish()
        }
    }

    latestRelease?.let { release ->
        val contentWidth = minOf(LocalWindowInfo.current.containerDpSize.width * 0.8F, 320.dp)
        ShowInfoDialog(
            title = { Text("发现新版本", style = MaterialTheme.typography.titleLarge) },
            onConfirm = {
                latestRelease = null
                finish()
            },
            confirmEnabled = !isOpeningRelease,
        ) {
            val linkStyle = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
            Text(
                text = buildAnnotatedString {
                    append("当前版本：${BuildInfo.appDisplayVersion}\n最新版本：${release.tagName.removePrefix("v")}\n\n")
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "github_release",
                            styles = TextLinkStyles(style = linkStyle),
                        ) {
                            if (!isOpeningRelease) {
                                isOpeningRelease = true
                                scope.launch {
                                    try {
                                        val opened = openUrl(release.htmlUrl)
                                        ensureActive()
                                        if (!opened) {
                                            snackbarManager.showMessage("打开 GitHub Release 失败")
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        snackbarManager.showMessage("打开 GitHub Release 失败")
                                    } finally {
                                        isOpeningRelease = false
                                    }
                                }
                            }
                        },
                    ) { append("GitHub Release") }
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(contentWidth).padding(vertical = 12.dp),
            )
        }
    }
}

private fun isNewerAppRelease(tagName: String, currentVersionCore: String, currentReleaseChannel: String): Boolean {
    val latest = parseAppVersionCore(tagName)
    val current = parseAppVersionCore(currentVersionCore)
    for (index in latest.indices) {
        val comparison = latest[index].compareTo(current[index])
        if (comparison != 0) return comparison > 0
    }
    return currentReleaseChannel != "stable"
}

private fun parseAppVersionCore(version: String): List<Long> {
    val match = requireNotNull(appReleaseVersionPattern.matchEntire(version)) { "无法识别版本号：$version" }
    return match.groupValues.drop(1).map { part ->
        requireNotNull(part.toLongOrNull()) { "版本号超出支持范围：$version" }
    }
}
