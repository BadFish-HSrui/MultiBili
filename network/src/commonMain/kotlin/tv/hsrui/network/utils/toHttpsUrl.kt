package tv.hsrui.network.utils

fun String.toHttpsUrl(): String = when {
    isEmpty() -> ""
    startsWith("//") -> "https:$this"
    startsWith("http://") -> replaceFirst("http://", "https://")
    else -> this
}