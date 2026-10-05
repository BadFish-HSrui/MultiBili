package tv.hsrui.bolo.download

expect class DownloadFiles() {
    suspend fun exists(output: DownloadOutput): Boolean
    suspend fun delete(output: DownloadOutput)
    suspend fun openFile(output: DownloadOutput): Boolean
    suspend fun showFile(output: DownloadOutput): Boolean
    suspend fun pickDirectory(initialDirectory: String): String?
    // 空值解析为系统默认目录；已有目录始终显示路径，无法映射文件系统路径的 URI 保留原值。
    suspend fun directoryDisplayName(directory: String): String
    suspend fun requestPermission(directory: String): Boolean
    suspend fun clearTemporaryFiles(completedOutputs: List<DownloadOutput>)
    suspend fun prepare(id: String)
    fun path(id: String, name: String): String
    suspend fun writeStream(id: String, name: String, producer: suspend (suspend (ByteArray, Int) -> Unit) -> Unit)
    suspend fun publish(id: String, fileName: String, directory: String, onPublished: suspend (DownloadOutput) -> Unit)
    suspend fun clean(id: String, completedOutput: DownloadOutput? = null)
}
