package tv.hsrui.bolo.storage.statistics

import java.io.File

internal actual fun statisticsDatabasePath(): String {
    val userDirectory = System.getProperty("user.home")
    val base = when {
        System.getProperty("os.name").startsWith("Mac") -> File(userDirectory, "Library/Application Support")
        System.getProperty("os.name").startsWith("Windows") ->
            System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let(::File) ?: File(userDirectory, "AppData/Local")
        else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() && File(it).isAbsolute }?.let(::File)
            ?: File(userDirectory, ".local/share")
    }
    val directory = File(base, "tv.hsrui.bolo")
    check(directory.isDirectory || directory.mkdirs()) { "无法创建统计信息目录" }
    return File(directory, "statistics.db").absolutePath
}
