package tv.hsrui.bolo.storage.statistics

import tv.hsrui.bolo.storage.desktopStorageDirectory
import java.io.File

internal actual fun statisticsDatabasePath(): String {
    return File(desktopStorageDirectory(), "statistics.db").absolutePath
}
