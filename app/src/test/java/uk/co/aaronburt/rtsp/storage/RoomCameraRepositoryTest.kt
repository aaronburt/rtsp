package uk.co.aaronburt.rtsp.storage

import javax.crypto.KeyGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomCameraRepositoryTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmCredentialCipher { key }
    private val dao = FakeCameraDao()
    private var now = 1_000L

    private fun TestScope.repository(
        source: CameraDao = dao,
        credentialCipher: CredentialCipher = cipher,
    ): RoomCameraRepository {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return RoomCameraRepository(
            dao = source,
            cipher = credentialCipher,
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            clock = { now++ },
        )
    }

    private fun sample(id: String, name: String = "Cam $id") = SavedCamera(
        id = id,
        name = name,
        mainUrl = "rtsp://10.0.0.5:554/main",
        subUrl = "rtsp://10.0.0.5:554/sub",
        username = "admin",
        password = "hunter2hunter2",
    )

    @Test
    fun startsEmptyThenLoads() = runTest {
        val repo = repository()
        assertFalse(repo.loaded.value)
        advanceUntilIdle()
        assertTrue(repo.loaded.value)
        assertTrue(repo.cameras.value.isEmpty())
    }

    @Test
    fun saveMakesCameraImmediatelyReadable() = runTest {
        val repo = repository()
        advanceUntilIdle()
        repo.save(sample("a"))
        assertEquals("Cam a", repo.get("a")?.name)
        assertEquals(1, repo.cameras.value.size)
    }

    @Test
    fun saveWritesEncryptedPasswordAndCredentialFreeUrls() = runTest {
        val repo = repository()
        advanceUntilIdle()
        val withEmbedded = sample("a").copy(
            mainUrl = "rtsp://admin:hunter2hunter2@10.0.0.5:554/main",
            subUrl = "rtsp://admin:hunter2hunter2@10.0.0.5:554/sub",
            username = "",
            password = "",
        )
        repo.save(withEmbedded)
        advanceUntilIdle()
        val row = dao.rows.getValue("a")
        assertEquals("rtsp://10.0.0.5:554/main", cipher.decrypt(row.mainUrlCipher, mainUrlContext("a")))
        assertEquals("rtsp://10.0.0.5:554/sub", cipher.decrypt(row.subUrlCipher!!, subUrlContext("a")))
        assertEquals("admin", row.username)
        assertNotNull(row.passwordCipher)
        assertFalse(row.toString().contains("hunter2hunter2"))
        assertEquals("hunter2hunter2", repo.get("a")?.password)
    }

    @Test
    fun saveEncryptsUrlsIncludingQueryStringCredentials() = runTest {
        val repo = repository()
        advanceUntilIdle()
        val url = "rtsp://10.0.0.5:554/live?user=admin&password=hunter2hunter2"
        repo.save(sample("a").copy(mainUrl = url, subUrl = null))
        advanceUntilIdle()
        val row = dao.rows.getValue("a")
        assertFalse(row.mainUrlCipher.contains("hunter2hunter2"))
        assertFalse(row.mainUrlCipher.contains("10.0.0.5"))
        assertNull(row.subUrlCipher)
        assertEquals(url, repo.get("a")?.mainUrl)
    }

    @Test
    fun urlCiphertextIsBoundToItsCameraAndField() = runTest {
        val repo = repository()
        advanceUntilIdle()
        repo.save(sample("a"))
        advanceUntilIdle()
        val row = dao.rows.getValue("a")
        dao.rows["b"] = row.copy(id = "b")
        dao.rows["a"] = row.copy(mainUrlCipher = row.subUrlCipher!!)
        val reloaded = repository()
        advanceUntilIdle()
        assertEquals("", reloaded.get("a")?.mainUrl)
        assertEquals("rtsp://10.0.0.5:554/sub", reloaded.get("a")?.subUrl)
        assertEquals("", reloaded.get("b")?.mainUrl)
        assertNull(reloaded.get("b")?.subUrl)
    }

    @Test
    fun cameraWithoutPasswordStoresNoCiphertext() = runTest {
        val repo = repository()
        advanceUntilIdle()
        repo.save(sample("a").copy(username = "", password = ""))
        advanceUntilIdle()
        assertNull(dao.rows.getValue("a").passwordCipher)
    }

    @Test
    fun survivesRestart() = runTest {
        val first = repository()
        advanceUntilIdle()
        first.save(sample("a"))
        first.save(sample("b").copy(favourite = true, transport = CameraTransport.Udp, latencyMs = 500))
        advanceUntilIdle()

        val second = repository()
        advanceUntilIdle()
        assertEquals(first.cameras.value, second.cameras.value)
        val b = second.get("b")!!
        assertEquals("hunter2hunter2", b.password)
        assertEquals(CameraTransport.Udp, b.transport)
        assertEquals(500, b.latencyMs)
        assertTrue(b.favourite)
        assertEquals("rtsp://admin:hunter2hunter2@10.0.0.5:554/sub", b.streamUrl(StreamKind.Sub))
    }

    @Test
    fun updateKeepsCreationOrder() = runTest {
        val repo = repository()
        advanceUntilIdle()
        repo.save(sample("a"))
        repo.save(sample("b"))
        repo.save(sample("a").copy(name = "Renamed"))
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), repo.cameras.value.map { it.id })
        assertEquals("Renamed", repo.get("a")?.name)
        assertEquals("Renamed", dao.rows.getValue("a").name)
    }

    @Test
    fun deleteRemovesFromCacheAndStore() = runTest {
        val repo = repository()
        advanceUntilIdle()
        repo.save(sample("a"))
        advanceUntilIdle()
        repo.delete("a")
        assertNull(repo.get("a"))
        advanceUntilIdle()
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun favouritePersists() = runTest {
        val repo = repository()
        advanceUntilIdle()
        repo.save(sample("a"))
        repo.setFavourite("a", true)
        assertTrue(repo.get("a")!!.favourite)
        advanceUntilIdle()
        assertTrue(dao.rows.getValue("a").favourite)
    }

    @Test
    fun changingPasswordReplacesCiphertext() = runTest {
        val repo = repository()
        advanceUntilIdle()
        repo.save(sample("a"))
        advanceUntilIdle()
        val before = dao.rows.getValue("a").passwordCipher
        repo.save(sample("a").copy(password = "another-password"))
        advanceUntilIdle()
        assertTrue(before != dao.rows.getValue("a").passwordCipher)
        assertEquals("another-password", repo.get("a")?.password)
    }

    @Test
    fun savesBeforeLoadAreKept() = runTest {
        dao.rows["old"] = CameraEntity(
            id = "old", name = "Old", mainUrlCipher = cipher.encrypt("rtsp://h/s", mainUrlContext("old")),
            subUrlCipher = null, username = "",
            passwordCipher = null, transport = "Tcp", latencyMs = 200, autoReconnect = true,
            startMuted = true, favourite = false, createdAt = 1,
        )
        dao.rows["gone"] = dao.rows.getValue("old").copy(id = "gone", name = "Gone", createdAt = 2)
        val repo = repository()
        repo.save(sample("new"))
        repo.delete("gone")
        advanceUntilIdle()
        assertEquals(setOf("old", "new"), repo.cameras.value.map { it.id }.toSet())
        assertEquals(setOf("old", "new"), dao.rows.keys)
    }

    @Test
    fun undecryptablePasswordLoadsAsEmpty() = runTest {
        val first = repository()
        advanceUntilIdle()
        first.save(sample("a"))
        advanceUntilIdle()

        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val second = repository(credentialCipher = AesGcmCredentialCipher { otherKey })
        advanceUntilIdle()
        val camera = second.get("a")!!
        assertEquals("", camera.password)
        assertEquals("Cam a", camera.name)
    }

    @Test
    fun failedWriteResyncsFromStore() = runTest {
        val flaky = FakeCameraDao()
        val repo = repository(source = flaky)
        advanceUntilIdle()
        flaky.failNextUpsert = true
        repo.save(sample("a"))
        advanceUntilIdle()
        assertNull(repo.get("a"))
        repo.save(sample("a"))
        advanceUntilIdle()
        assertNotNull(repo.get("a"))
        assertNotNull(flaky.rows["a"])
    }

    @Test
    fun newIdsAreUnique() = runTest {
        val repo = repository()
        assertTrue(repo.newId() != repo.newId())
    }
}

class FakeCameraDao : CameraDao {
    val rows = linkedMapOf<String, CameraEntity>()
    var failNextUpsert = false

    override suspend fun all(): List<CameraEntity> = rows.values.toList()

    override suspend fun upsert(entity: CameraEntity) {
        if (failNextUpsert) {
            failNextUpsert = false
            error("write failed")
        }
        rows[entity.id] = entity
    }

    override suspend fun delete(id: String) {
        rows.remove(id)
    }

    override suspend fun setFavourite(id: String, favourite: Boolean) {
        rows[id]?.let { rows[id] = it.copy(favourite = favourite) }
    }
}
