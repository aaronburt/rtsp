package uk.co.aaronburt.rtsp.recording

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val EXTENSION = ".mp4"
private const val PARTIAL_SUFFIX = ".part"
private val unsafeChars = Regex("[^A-Za-z0-9 _-]+")
private val namePattern = Regex("^(.*)_\\d{8}_\\d{6}$")

class FileRecordingStore(private val directory: File) : RecordingStore {

    override fun list(): List<Recording> {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) } ?: return emptyList()
        return files
            .map { file ->
                Recording(
                    id = file.name,
                    title = titleOf(file.name.removeSuffix(EXTENSION)),
                    modifiedMs = file.lastModified(),
                    sizeBytes = file.length(),
                )
            }
            .sortedByDescending { it.modifiedMs }
    }

    override fun file(id: String): File? {
        if (id != File(id).name || !id.endsWith(EXTENSION)) return null
        return File(directory, id).takeIf { it.isFile }
    }

    override fun delete(id: String): Boolean = file(id)?.delete() ?: false

    override fun create(title: String, startedAtMs: Long): File {
        directory.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startedAtMs))
        val safe = title.replace(unsafeChars, "_").trim('_', ' ').ifEmpty { "camera" }
        var candidate = File(directory, "${safe}_$stamp$EXTENSION$PARTIAL_SUFFIX")
        var suffix = 1
        while (candidate.exists() || File(directory, candidate.name.removeSuffix(PARTIAL_SUFFIX)).exists()) {
            candidate = File(directory, "${safe}_$stamp-${suffix++}$EXTENSION$PARTIAL_SUFFIX")
        }
        return candidate
    }

    override fun commit(partial: File) {
        val target = File(partial.parentFile, partial.name.removeSuffix(PARTIAL_SUFFIX))
        if (!partial.renameTo(target)) {
            partial.delete()
            throw java.io.IOException("Could not save recording")
        }
    }

    override fun discard(partial: File) {
        partial.delete()
    }

    private fun titleOf(name: String): String =
        (namePattern.matchEntire(name)?.groupValues?.get(1) ?: name).replace('_', ' ')
}
