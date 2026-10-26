package uk.co.aaronburt.rtsp.recording

import java.io.File

data class Recording(
    val id: String,
    val title: String,
    val modifiedMs: Long,
    val sizeBytes: Long,
)

interface RecordingStore {
    fun list(): List<Recording>

    fun file(id: String): File?

    fun delete(id: String): Boolean

    /** Creates a file to write into. Call [commit] once it is complete so it shows up in [list]. */
    fun create(title: String, startedAtMs: Long): File

    fun commit(partial: File)

    fun discard(partial: File)
}

object NoRecordingStore : RecordingStore {
    override fun list(): List<Recording> = emptyList()

    override fun file(id: String): File? = null

    override fun delete(id: String): Boolean = false

    override fun create(title: String, startedAtMs: Long): File =
        throw UnsupportedOperationException("Recording is not available")

    override fun commit(partial: File) = Unit

    override fun discard(partial: File) = Unit
}
