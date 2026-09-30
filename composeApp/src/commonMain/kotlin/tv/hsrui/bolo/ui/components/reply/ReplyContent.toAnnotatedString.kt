package tv.hsrui.bolo.ui.components.reply

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.navigation.openMedia
import tv.hsrui.bolo.navigation.openUserSpace
import tv.hsrui.bolo.navigation.openVideo
import tv.hsrui.bolo.navigation.parseExternalLink
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.utils.url.openUrl
import tv.hsrui.network.feature.link.fetchExternalLinkRedirect
import tv.hsrui.network.feature.link.isBilibiliShortLink
import tv.hsrui.network.feature.reply.ReplyItem

private val replyBvidRegex = Regex("(?<![A-Za-z0-9_])BV1[1-9A-HJ-NP-Za-km-z]{9}(?![A-Za-z0-9_])")
private val replyUrlRegex = Regex("""(?:https?://|bilibili://|//)[^\s<>"'()\[\]{}，。！？；：、]+""")

fun ReplyItem.ReplyContent.toRichString(scope: CoroutineScope): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
    val mentions = atNameToMid.filterKeys { it.isNotEmpty() }.mapKeys { "@${it.key}" }
    // 服务端标记与昵称优先匹配最长字面量；普通 URL 整段匹配，避免识别其中的 BV 或关键词。
    val literalKeys = (emote.keys + jump.keys + mentions.keys)
        .filter { it.isNotEmpty() }
        .sortedByDescending { it.length }
        .map { Regex.escape(it) }
    val pattern = (literalKeys + replyUrlRegex.pattern + replyBvidRegex.pattern).joinToString("|")
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        Regex(pattern).findAll(text).forEach { match ->
            val rawText = match.value
            val keyText = if (rawText !in jump && rawText !in emote && rawText !in mentions &&
                replyUrlRegex.matches(rawText)
            ) {
                rawText.trimEnd('.', ',', '!', ';', ':', '?')
            } else {
                rawText
            }
            append(text.substring(lastIndex, match.range.first))
            lastIndex = match.range.first + keyText.length
            when {
                emote.contains(keyText) -> {
                    val emoteItem = emote.getValue(keyText)
                    // 使用单字符占位，保持行内图片前后文本与链接范围的偏移一致。
                    appendInlineContent(id = keyText)
                    inlineContentMap[keyText] = InlineTextContent(
                        placeholder = Placeholder(
                            width = if (emoteItem.isBig) 3.em else (1.2).em,
                            height = if (emoteItem.isBig) 3.em else (1.2).em,
                            placeholderVerticalAlign = if (emoteItem.isBig) PlaceholderVerticalAlign.TextBottom else PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        AsyncImage(model = emoteItem.url, contentDescription = emoteItem.text)
                    }
                }
                mentions.contains(keyText) -> {
                    val mid = mentions.getValue(keyText)
                    if (mid > 0) {
                        appendReplyLink(keyText, keyText) { openUserSpace(mid) }
                    } else {
                        append(keyText)
                    }
                }
                else -> {
                    val jumpItem = jump[keyText]
                    if (jumpItem != null && jumpItem.iconUrl.isNotBlank()) {
                        appendInlineContent(id = keyText)
                        inlineContentMap[keyText] = InlineTextContent(
                            placeholder = Placeholder(
                                width = 1.em,
                                height = 1.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                            )
                        ) {
                            AsyncImage(model = jumpItem.iconUrl, contentDescription = null)
                        }
                    }
                    val targets = listOfNotNull(jumpItem?.jumpAppUrl, keyText)
                        .filter(String::isNotBlank).distinct()
                    val shortLink = targets.firstOrNull()?.takeIf(::isBilibiliShortLink)
                    var openJob: Job? = null
                    val onClick = targets.firstNotNullOfOrNull { replyLinkAction(it, keyText) }
                        ?: if (jumpItem != null || shortLink != null) {
                            {
                                if (openJob?.isActive != true) {
                                    openJob = scope.launch {
                                        // 随机短链只在点击后解析，保留外部目标的原有优先级。
                                        val resolvedAction = try {
                                            shortLink?.let { fetchExternalLinkRedirect(it) }
                                                ?.let { replyLinkAction(it, keyText) }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            null
                                        }
                                        ensureActive()
                                        if (resolvedAction != null) {
                                            resolvedAction()
                                        } else if (targets.none {
                                            ensureActive()
                                            openUrl(it)
                                        }) {
                                            ensureActive()
                                            showSnackbarMessage("链接跳转失败: $keyText")
                                        }
                                    }
                                }
                                Unit
                            }
                        } else {
                            null
                        }
                    if (onClick != null) {
                        appendReplyLink(jumpItem?.title?.ifBlank { keyText } ?: keyText, keyText, onClick)
                    } else {
                        append(keyText)
                    }
                }
            }
        }
        append(text.substring(lastIndex))
    }
    return Pair(annotatedString, inlineContentMap)
}

private fun AnnotatedString.Builder.appendReplyLink(label: String, tag: String, onClick: () -> Unit) {
    withLink(
        LinkAnnotation.Clickable(
            tag = tag,
            styles = TextLinkStyles(style = SpanStyle(color = BiliColor.Blue)),
            linkInteractionListener = { onClick() },
        )
    ) {
        append(label)
    }
}

private fun replyLinkAction(target: String, fallbackKeyword: String): (() -> Unit)? {
    if (replyBvidRegex.matches(target)) return { openVideo(target) }
    val url = runCatching { Url(if (target.startsWith("//")) "https:$target" else target) }.getOrNull()
        ?: return null
    val isAppUrl = url.protocol.name == "bilibili"
    val isWebUrl = url.protocol.name == "http" || url.protocol.name == "https"
    if ((isAppUrl && url.host == "search") || (isWebUrl && url.host == "search.bilibili.com")) {
        val keyword = url.parameters["keyword"]?.trim().orEmpty().ifBlank { fallbackKeyword.trim() }
        if (keyword.isEmpty()) return null
        return {
            val navigator: Navigator = KoinPlatformTools.defaultContext().get().get()
            navigator.navigateTo(BoloRoute.Search.Results(keyword))
        }
    }
    val pathSegments = url.encodedPath.trim('/').split('/')
    val isShortLink = isBilibiliShortLink(url.toString())
    val bvid = when {
        isAppUrl && url.host == "video" -> url.parameters["bvid"] ?: pathSegments.firstOrNull()
        isWebUrl && (url.host == "bilibili.com" || url.host.endsWith(".bilibili.com")) &&
            pathSegments.firstOrNull() == "video" -> pathSegments.getOrNull(1)
        isShortLink -> pathSegments.singleOrNull()
        else -> null
    }
    bvid?.takeIf { replyBvidRegex.matches(it) }?.let { return { openVideo(it) } }
    val mediaRoute = (if (isShortLink) {
        pathSegments.singleOrNull()?.let(::parseExternalLink)
    } else {
        parseExternalLink(target)
    }) as? BoloRoute.View.Media ?: return null
    return { openMedia(seasonId = mediaRoute.seasonId, episodeId = mediaRoute.episodeId) }
}
