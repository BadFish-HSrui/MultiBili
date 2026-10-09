package tv.hsrui.bolo

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import multibili.composeapp.generated.resources.Res
import multibili.composeapp.generated.resources.github_repo_url
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import tv.hsrui.bolo.storage.appData.AppDataStorage
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.common.update.CheckAppUpdate
import tv.hsrui.bolo.ui.components.dialog.ShowInfoDialog
import tv.hsrui.bolo.utils.url.openUrl

@Composable
fun AppStartup() {
    val appDataStorage: AppDataStorage = koinInject()
    val warnings = appDataStorage.oneTimeWarnings
    var isWatchTimeCheckFinished by rememberSaveable { mutableStateOf(false) }
    var showWatchTimePrompt by rememberSaveable { mutableStateOf(false) }
    var isAppUpdateCheckActive by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(warnings.dynamicLoudnessPending) {
        if (warnings.dynamicLoudnessPending || isWatchTimeCheckFinished) return@LaunchedEffect
        if (warnings.Check114514) {
            try {
                val totalPlayedMs = appDataStorage.playbackStatistics.totalPlayedMs()
                ensureActive()
                showWatchTimePrompt = totalPlayedMs >= 114_514_000L
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ensureActive()
            }
        }
        isWatchTimeCheckFinished = true
    }

    if (warnings.dynamicLoudnessPending) {
        ShowInfoDialog(
            onConfirm = { warnings.dynamicLoudnessPending = false },
            forcedDisplaySeconds = 5,
        ) {
            Text(
                text = "当前版本默认开启动态音量均衡\n\n" +
                    "部分设备在倍速下可能出现严重的音频处理阻塞导致卡顿\n\n" +
                    "如遇到问题可在播放设置中关闭",
                color = Color.Red,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    } else if (showWatchTimePrompt && warnings.Check114514) {
        val snackbarManager: SnackbarManager = koinInject()
        val scope = rememberCoroutineScope()
        val repositoryUrl = stringResource(Res.string.github_repo_url)
        var isOpeningRepository by remember { mutableStateOf(false) }
        val contentWidth = minOf(LocalWindowInfo.current.containerDpSize.width * 0.8F, 320.dp)
        ShowInfoDialog(
            onConfirm = {
                warnings.Check114514 = false
                showWatchTimePrompt = false
            },
            scrollableContent = true,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(contentWidth).padding(vertical = 12.dp),
            ) {
                Text(
                    text = "你已经使用 Multi Bili 播放超过 114514 秒了，喜欢的话就点个Star吧",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = buildAnnotatedString {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = "github_repository",
                                styles = TextLinkStyles(style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                )),
                            ) {
                                if (!isOpeningRepository) {
                                    isOpeningRepository = true
                                    scope.launch {
                                        try {
                                            val opened = openUrl(repositoryUrl)
                                            ensureActive()
                                            if (!opened) snackbarManager.showMessage("打开 GitHub 仓库失败")
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (_: Exception) {
                                            ensureActive()
                                            snackbarManager.showMessage("打开 GitHub 仓库失败")
                                        } finally {
                                            isOpeningRepository = false
                                        }
                                    }
                                }
                            },
                        ) { append("BadFish-HSrui/MultiBili") }
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else if (isWatchTimeCheckFinished && isAppUpdateCheckActive) {
        CheckAppUpdate(
            hideError = true,
            onFinished = { isAppUpdateCheckActive = false },
        )
    }
}
