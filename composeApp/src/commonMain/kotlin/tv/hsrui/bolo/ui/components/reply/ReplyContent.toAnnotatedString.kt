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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.common.snackbar.showSnackbarMessage
import tv.hsrui.bolo.ui.theme.BiliColor
import tv.hsrui.bolo.utils.url.openUrl
import tv.hsrui.network.feature.reply.ReplyItem

fun ReplyItem.ReplyContent.toRichString(scope: CoroutineScope): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
    // 把所有转义键组合为正则表达式
    val emoteKeys = emote.keys.map { Regex.escape(it) }
    val jumpKeys = jump.keys.map { Regex.escape(it) }
    val allKeys = (emoteKeys + jumpKeys).joinToString("|")
    // 声明注解字符串
    val annotatedString = buildAnnotatedString {
        // 无转义时直接返回原文本
        if (allKeys.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }
        // 声明一个索引用来标记处理进度
        var lastIndex = 0
        // 查找所有转义键
        Regex(allKeys).findAll(text).forEach { key ->
            // 转义键位置与文本
            val keyStart = key.range.first
            val keyEnd = key.range.last + 1
            val keyText = key.value
            // 添加从进度索引到转义键开始前的文本
            append(text.substring(lastIndex, keyStart))
            lastIndex = keyEnd
            // 判断转义键类型
            when {
                // 表情包
                emote.contains(keyText) -> {
                    // 获取表情包Item对象
                    val emoteItem = emote[keyText]!!
                    // 添加表情包标记
                    appendInlineContent(id = keyText, alternateText = emoteItem.text)
                    // 添加表情包显示
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
                // 跳转链接
                jump.contains(keyText) -> {
                    // 获取跳转链接Item对象
                    val jumpItem = jump[keyText]!!
                    // 跳过搜索关键词(蓝字)
                    if (jumpItem.jumpAppUrl.startsWith("bilibili://search")) {
                        append(keyText)
                        return@forEach
                    }
                    // 添加链接图标
                    appendInlineContent(id = keyText)
                    // 添加图标显示
                    inlineContentMap[keyText] = InlineTextContent(
                        placeholder = Placeholder(
                            width = 1.em,
                            height = 1.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        AsyncImage(model = jumpItem.iconUrl, contentDescription = null)
                    }
                    /* TODO: 需要判断是否为外部跳转并分别处理，以及未来的外部广告屏蔽功能，暂时只外部跳转 */
                    // 添加跳转链接显示文本
                    withLink(
                        link = LinkAnnotation.Clickable(
                            tag = keyText,
                            styles = TextLinkStyles(
                                style = SpanStyle(color = BiliColor.Blue)
                            )
                        ) {
                            scope.launch {
                                if (!openUrl(jumpItem.jumpAppUrl)) {
                                    if (!openUrl(keyText)) {
                                        showSnackbarMessage("链接跳转失败: $keyText")
                                    }
                                }
                            }
                        }
                    ) {
                        append(jumpItem.title)
                    }
                }
            }
        }
        append(text.substring(lastIndex))
    }

    return Pair(annotatedString, inlineContentMap)
}