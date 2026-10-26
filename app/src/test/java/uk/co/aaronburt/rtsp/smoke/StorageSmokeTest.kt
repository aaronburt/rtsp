package uk.co.aaronburt.rtsp.smoke

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.co.aaronburt.rtsp.storage.AesGcmCredentialCipher
import uk.co.aaronburt.rtsp.storage.CameraDatabase
import uk.co.aaronburt.rtsp.storage.CameraTransport
import uk.co.aaronburt.rtsp.storage.CredentialCipher
import uk.co.aaronburt.rtsp.storage.RoomCameraRepository
import uk.co.aaronburt.rtsp.storage.SavedCamera
import uk.co.aaronburt.rtsp.storage.SavedCameraRepository
import uk.co.aaronburt.rtsp.ui.settings.SharedDisplayPreferences

@RunWith(RobolectricTestRunner::class)
class StorageSmokeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "smoke.db"
    private val password = "Zq9-unique-camera-password-7Xw"
    private val cipher: CredentialCipher = AesGcmCredentialCipher(
        object : () -> SecretKey {
            private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            override fun invoke() = key
        },
    )
    private val scopes = mutableListOf<CoroutineScope>()
    private val databases = mutableListOf<CameraDatabase>()

    @Before
    fun clean() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        databases.forEach { it.close() }
        context.deleteDatabase(dbName)
    }

    private fun open(): Pair<CameraDatabase, SavedCameraRepository> {
        val database = CameraDatabase.open(context, dbName, cipher)
        databases += database
        val scope = CoroutineScope(SupervisorJob())
        scopes += scope
        val repository = RoomCameraRepository(database.cameraDao(), cipher, scope, Dispatchers.IO)
        runBlocking { withTimeout(10_000) { repository.loaded.first { it } } }
        return database to repository
    }

    private fun close() {
        scopes.forEach { it.cancel() }
        scopes.clear()
        databases.forEach { it.close() }
        databases.clear()
    }

    private fun awaitRows(database: CameraDatabase, count: Int) = runBlocking {
        withTimeout(10_000) {
            while (database.cameraDao().all().size != count) delay(20)
        }
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
    fun camerasSurviveRestartAndDeleteSticks() {
        val (db, repo) = open()
        val id = repo.newId()
        repo.save(camera(id))
        awaitRows(db, 1)
        close()

        val (db2, reopened) = open()
        val saved = reopened.get(id)
        assertEquals("Front Door", saved?.name)
        assertEquals(password, saved?.password)
        assertEquals("rtsp://192.168.1.20:554/main", saved?.mainUrl)
        assertEquals(CameraTransport.Udp, saved?.transport)
        assertTrue(saved?.favourite == true)

        reopened.delete(id)
        awaitRows(db2, 0)
        close()
        assertNull(open().second.get(id))
    }

    @Test
    fun credentialsAreNeverStoredInPlaintext() {
        val (db, repo) = open()
        repo.save(camera(repo.newId()))
        awaitRows(db, 1)
        close()

        val file = context.getDatabasePath(dbName)
        val files = listOf(file, File(file.path + "-wal")).filter { it.exists() }
        assertTrue(files.isNotEmpty())
        files.forEach {
            val text = String(it.readBytes(), Charsets.ISO_8859_1)
            assertFalse("${it.name} leaks the password", text.contains(password))
            assertFalse("${it.name} leaks the host", text.contains("192.168.1.20"))
        }
    }

    @Test
    fun version1DatabaseIsEncryptedOnUpgrade() {
        val legacyMain = "rtsp://10.0.0.7:554/live?user=viewer&password=legacy-secret"
        val legacy = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        legacy.execSQL(
            "CREATE TABLE IF NOT EXISTS `cameras` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`mainUrl` TEXT NOT NULL, `subUrl` TEXT, `username` TEXT NOT NULL, `passwordCipher` TEXT, " +
                "`transport` TEXT NOT NULL, `latencyMs` INTEGER NOT NULL, `autoReconnect` INTEGER NOT NULL, " +
                "`startMuted` INTEGER NOT NULL, `favourite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        legacy.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        legacy.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'e81ac61b05028d4dbbb95d4dab49809e')",
        )
        legacy.execSQL(
            "INSERT INTO cameras (id, name, mainUrl, subUrl, username, passwordCipher, transport, latencyMs, " +
                "autoReconnect, startMuted, favourite, createdAt) VALUES " +
                "('legacy', 'Legacy', '$legacyMain', NULL, '', NULL, 'Tcp', 200, 1, 1, 0, 1)",
        )
        legacy.version = 1
        legacy.close()

        val (db, repo) = open()
        val raw = runBlocking { db.cameraDao().all() }.single()
        assertFalse(raw.mainUrlCipher.contains("legacy-secret"))
        assertEquals(legacyMain, repo.get("legacy")?.mainUrl)
    }

    @Test
    fun displayPreferencesSurviveReopening() {
        val storage = context.getSharedPreferences("smoke-display", Context.MODE_PRIVATE)
        val preferences = SharedDisplayPreferences(storage)
        assertFalse(preferences.pinchToZoom.value)
        preferences.setPinchToZoom(true)
        preferences.setAllowRecording(true)
        val reopened = SharedDisplayPreferences(storage)
        assertTrue(reopened.pinchToZoom.value)
        assertTrue(reopened.allowRecording.value)
        assertFalse(reopened.showClock.value)
    }
}
