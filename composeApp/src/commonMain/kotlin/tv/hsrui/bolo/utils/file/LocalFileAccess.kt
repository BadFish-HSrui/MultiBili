package tv.hsrui.bolo.utils.file

/** 系统窗口取消返回 null；导入始终返回应用临时副本，位置字符串仅由平台实现解释。 */
expect class LocalFileAccess() {
    suspend fun temporaryPath(fileName: String): String
    suspend fun deleteTemporaryFile(path: String)
    suspend fun saveFile(sourcePath: String, suggestedName: String): String?
    suspend fun importFile(): String?
    suspend fun showFileInFolder(location: String): Boolean
}
