package tv.hsrui.bolo.storage.statistics

import android.content.Context
import org.koin.mp.KoinPlatform.getKoin

internal actual fun statisticsDatabasePath(): String {
    val file = getKoin().get<Context>().applicationContext.getDatabasePath("statistics.db")
    val directory = checkNotNull(file.parentFile)
    check(directory.isDirectory || directory.mkdirs()) { "无法创建统计信息目录" }
    return file.absolutePath
}
