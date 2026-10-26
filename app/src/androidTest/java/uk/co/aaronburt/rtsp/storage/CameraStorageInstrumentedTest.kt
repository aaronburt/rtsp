package uk.co.aaronburt.rtsp.storage

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraStorageInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "test-${UUID.randomUUID()}.db"
    private val alias = "test-key-${UUID.randomUUID()}"
    private val password = "Zq9-unique-camera-password-7Xw"
    private val scopes = mutableListOf<CoroutineScope>()
    private val databases = mutableListOf<CameraDatabase>()

    @Before
    fun clearLog() {
        Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        databases.forEach { it.close() }
        context.deleteDatabase(dbName)
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    private fun open(): SavedCameraRepository {
        val database = CameraDatabase.open(context, dbName)
        databases += database
        val scope = CoroutineScope(SupervisorJob())
        scopes += scope
        val repository = RoomCameraRepository(
            dao = database.cameraDao(),
            cipher = KeystoreKey.cipher(alias),
            scope = scope,
            io = Dispatchers.IO,
        )
        runBlocking { withTimeout(10_000) { repository.loaded.first { it } } }
        return repository
    }

    private fun restart(repository: SavedCameraRepository): SavedCameraRepository {
        databases.forEach { it.close() }
        databases.clear()
        scopes.forEach { it.cancel() }
        scopes.clear()
        return open()
    }

    private fun settle(repository: SavedCameraRepository) {
        databases.last().openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        Thread.sleep(300)
    }

    private fun camera(id: String) = SavedCamera(
        id = id,
        name = "Front Door",
        mainUrl = "rtsp://192.168.1.20:554/main",
        subUrl = "rtsp://192.168.1.20:554/sub",
        username = "viewer",
        password = password,
        transport = CameraTransport.Udp,
        latencyMs = 500,
        favourite = true,
    )

    @Test
    fun createReadUpdateDelete() {
        val repo = open()
        val id = repo.newId()
        repo.save(camera(id))
        assertEquals("Front Door", repo.get(id)?.name)

        repo.save(camera(id).copy(name = "Porch", password = "changed-$password"))
        settle(repo)
        val reopened = restart(repo)
        assertEquals("Porch", reopened.get(id)?.name)
        assertEquals("changed-$password", reopened.get(id)?.password)

        reopened.delete(id)
        settle(reopened)
        val afterDelete = restart(reopened)
        assertNull(afterDelete.get(id))
        assertTrue(afterDelete.cameras.value.isEmpty())
    }

    @Test
    fun allFieldsSurviveRestart() {
        val repo = open()
        val id = repo.newId()
        repo.save(camera(id))
        settle(repo)
        val saved = restart(repo).get(id)
        assertNotNull(saved)
        assertEquals("rtsp://192.168.1.20:554/main", saved!!.mainUrl)
        assertEquals("rtsp://192.168.1.20:554/sub", saved.subUrl)
        assertEquals("viewer", saved.username)
        assertEquals(password, saved.password)
        assertEquals(CameraTransport.Udp, saved.transport)
        assertEquals(500, saved.latencyMs)
        assertTrue(saved.favourite)
        assertEquals("rtsp://viewer:$password@192.168.1.20:554/sub", saved.streamUrl(StreamKind.Sub))
    }

    @Test
    fun passwordNeverAppearsInDatabaseFiles() {
        val repo = open()
        repeat(3) { repo.save(camera(repo.newId())) }
        settle(repo)
        databases.forEach { it.close() }
        databases.clear()

        val dbFile = context.getDatabasePath(dbName)
        val files = listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"), File(dbFile.path + "-journal"))
            .filter { it.exists() }
        assertTrue(files.isNotEmpty())
        val needle = password.toByteArray()
        files.forEach { file ->
            assertFalse("${file.name} contains the plaintext password", contains(file.readBytes(), needle))
        }
        assertFalse(contains(dbFile.readBytes(), "192.168.1.20".toByteArray()))
    }

    @Test
    fun storedCiphertextIsAesGcmWithFreshIvPerCamera() {
        val repo = open()
        repo.save(camera("a"))
        repo.save(camera("b"))
        settle(repo)
        val rows = runBlocking { databases.last().cameraDao().all() }.associateBy { it.id }
        val a = rows.getValue("a").passwordCipher
        val b = rows.getValue("b").passwordCipher
        assertNotNull(a)
        assertNotNull(b)
        assertTrue(a != b)
    }

    @Test
    fun ciphertextCannotBeMovedBetweenCameras() {
        val repo = open()
        repo.save(camera("a"))
        repo.save(camera("b").copy(password = "other-password"))
        settle(repo)
        val dao = databases.last().cameraDao()
        val rows = runBlocking { dao.all() }.associateBy { it.id }
        runBlocking { dao.upsert(rows.getValue("b").copy(passwordCipher = rows.getValue("a").passwordCipher)) }
        val reopened = restart(repo)
        assertEquals("", reopened.get("b")?.password)
        assertEquals(password, reopened.get("a")?.password)
    }

    @Test
    fun keystoreKeyIsReusedAcrossInstances() {
        val first = KeystoreKey.cipher(alias).encrypt(password, "ctx")
        assertEquals(password, KeystoreKey.cipher(alias).decrypt(first, "ctx"))
        assertNull(KeystoreKey.cipher("$alias-other").decrypt(first, "ctx"))
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("$alias-other")
    }

    @Test
    fun passwordNeverAppearsInLogs() {
        val repo = open()
        val id = repo.newId()
        repo.save(camera(id))
        println(repo.get(id))
        println(repo.cameras.value)
        settle(repo)
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d"))
        val log = process.inputStream.bufferedReader().readText()
        process.waitFor()
        assertFalse(log.contains(password))
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
