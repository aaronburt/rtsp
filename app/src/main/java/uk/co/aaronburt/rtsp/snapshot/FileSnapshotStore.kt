package uk.co.aaronburt.rtsp.snapshot

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FileSnapshotStore(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
) : SnapshotStore {
    private val lock = Any()
    private val state = MutableStateFlow(load())

    override val snapshots: StateFlow<Map<String, Snapshot>> = state.asStateFlow()

    override fun save(cameraId: String, jpeg: ByteArray) {
        synchronized(lock) {
            directory.mkdirs()
            val target = fileFor(cameraId)
            val temp = File(directory, target.name + TEMP_SUFFIX)
            temp.writeBytes(jpeg)
            val capturedAt = clock()
            temp.setLastModified(capturedAt)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) {
                    temp.delete()
                    return
                }
            }
            state.update { it + (cameraId to Snapshot(cameraId, target, capturedAt)) }
        }
    }

    override fun delete(cameraId: String) {
        synchronized(lock) {
            fileFor(cameraId).delete()
            state.update { it - cameraId }
        }
    }

    private fun load(): Map<String, Snapshot> {
        directory.listFiles().orEmpty().forEach { file ->
            if (file.name.endsWith(TEMP_SUFFIX)) file.delete()
        }
        return directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(EXTENSION) }
            .mapNotNull { file ->
                val id = decodeId(file.name.removeSuffix(EXTENSION)) ?: return@mapNotNull null
                id to Snapshot(id, file, file.lastModified())
            }
            .toMap()
    }

    private fun fileFor(cameraId: String) = File(directory, encodeId(cameraId) + EXTENSION)

    private fun encodeId(id: String): String =
        id.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

    private fun decodeId(name: String): String? {
        if (name.isEmpty() || name.length % 2 != 0) return null
        val bytes = ByteArray(name.length / 2)
        for (index in bytes.indices) {
            bytes[index] = name.substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
        }
        return String(bytes, Charsets.UTF_8)
    }

    private companion object {
        const val EXTENSION = ".jpg"
        const val TEMP_SUFFIX = ".tmp"
    }
}
