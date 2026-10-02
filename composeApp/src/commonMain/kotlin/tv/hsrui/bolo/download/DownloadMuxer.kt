package tv.hsrui.bolo.download

expect class DownloadMuxer() {
    suspend fun merge(video: String, audio: String?, output: String, onProgress: suspend (Float) -> Unit)
}
