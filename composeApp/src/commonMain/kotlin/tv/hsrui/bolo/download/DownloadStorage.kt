package tv.hsrui.bolo.download

import eu.anifantakis.lib.ksafe.KSafePlain

class DownloadStorage(private val storage: KSafePlain) {
    suspend fun load(): List<DownloadTask> = storage.get("download_task_records", emptyList())
    suspend fun save(tasks: List<DownloadTask>) = storage.put("download_task_records", tasks.map {
        it.copy(downloadedBytes = 0, totalBytes = null, mergeProgress = 0f)
    })
}
