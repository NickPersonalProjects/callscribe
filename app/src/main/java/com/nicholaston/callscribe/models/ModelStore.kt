package com.nicholaston.callscribe.models

import android.content.Context
import java.io.File
import java.security.MessageDigest

class ModelStore(context: Context) {
    private val root = File(context.filesDir, "models")

    fun directory(model: DownloadableModel): File = File(root, model.id)

    fun file(model: DownloadableModel, modelFile: ModelFile): File =
        File(directory(model), modelFile.name)

    fun partialFile(model: DownloadableModel, modelFile: ModelFile): File =
        File(directory(model), "${modelFile.name}.part")

    fun isInstalled(model: DownloadableModel): Boolean =
        model.files.all { file ->
            val installed = file(model, file)
            installed.isFile &&
                installed.length() == file.bytes &&
                sha256(installed).equals(file.sha256, ignoreCase = true)
        }

    fun delete(model: DownloadableModel): Boolean =
        directory(model).takeIf(File::exists)?.deleteRecursively() ?: true

    fun availableBytes(): Long = root.parentFile?.usableSpace ?: 0

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
