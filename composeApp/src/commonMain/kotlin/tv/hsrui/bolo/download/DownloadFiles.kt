package tv.hsrui.bolo.download

expect class DownloadFiles() {
    suspend fun exists(output: DownloadOutput): Boolean
    suspend fun delete(output: DownloadOutput)
    suspend fun openFile(output: DownloadOutput): Boolean
    suspend fun showFile(output: DownloadOutput): Boolean
    suspend fun requestPermission(): Boolean
    suspend fun clearTemporaryFiles()
    suspend fun prepare(id: String)
    fun path(id: String, name: String): String
    suspend fun writeStream(id: String, name: String, producer: suspend (suspend (ByteArray, Int) -> Unit) -> Unit)
    suspend fun publish(id: String, fileName: String, onPublished: suspend (DownloadOutput) -> Unit)
    suspend fun clean(id: String)
}
