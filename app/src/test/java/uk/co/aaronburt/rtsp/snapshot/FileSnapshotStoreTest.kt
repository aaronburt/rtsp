package uk.co.aaronburt.rtsp.snapshot

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileSnapshotStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(clock: () -> Long = { 1_000L }) = FileSnapshotStore(File(folder.root, "snapshots"), clock)

    @Test
    fun startsEmpty() {
        assertTrue(store().snapshots.value.isEmpty())
    }

    @Test
    fun savePublishesSnapshotWithTimestampAndBytes() {
        val store = store { 5_000L }
        store.save("front-door", byteArrayOf(1, 2, 3))
        val snapshot = store.snapshots.value.getValue("front-door")
        assertEquals(5_000L, snapshot.capturedAt)
        assertArrayEquals(byteArrayOf(1, 2, 3), snapshot.file.readBytes())
    }

    @Test
    fun saveReplacesPreviousSnapshot() {
        var now = 1_000L
        val store = store { now }
        store.save("cam", byteArrayOf(1))
        now = 9_000L
        store.save("cam", byteArrayOf(2, 2))
        val snapshot = store.snapshots.value.getValue("cam")
        assertEquals(9_000L, snapshot.capturedAt)
        assertArrayEquals(byteArrayOf(2, 2), snapshot.file.readBytes())
        assertEquals(1, store.snapshots.value.size)
    }

    @Test
    fun deleteRemovesFileAndEntry() {
        val store = store()
        store.save("cam", byteArrayOf(1))
        val file = store.snapshots.value.getValue("cam").file
        store.delete("cam")
        assertNull(store.snapshots.value["cam"])
        assertFalse(file.exists())
    }

    @Test
    fun deleteUnknownCameraIsHarmless() {
        val store = store()
        store.delete("missing")
        assertTrue(store.snapshots.value.isEmpty())
    }

    @Test
    fun reloadsSnapshotsFromDisk() {
        val first = store { 7_000L }
        first.save("a/b c", byteArrayOf(4))
        val second = store()
        val snapshot = second.snapshots.value.getValue("a/b c")
        assertEquals("a/b c", snapshot.cameraId)
        assertArrayEquals(byteArrayOf(4), snapshot.file.readBytes())
    }

    @Test
    fun unsafeCameraIdsStayInsideTheDirectory() {
        val store = store()
        store.save("../../escape", byteArrayOf(1))
        val file = store.snapshots.value.getValue("../../escape").file
        assertEquals(File(folder.root, "snapshots").canonicalPath, file.canonicalFile.parentFile?.path)
    }

    @Test
    fun leftoverTempFilesAreRemovedOnLoad() {
        val directory = File(folder.root, "snapshots").apply { mkdirs() }
        val leftover = File(directory, "6162.jpg.tmp").apply { writeBytes(byteArrayOf(1)) }
        store()
        assertFalse(leftover.exists())
    }
}
