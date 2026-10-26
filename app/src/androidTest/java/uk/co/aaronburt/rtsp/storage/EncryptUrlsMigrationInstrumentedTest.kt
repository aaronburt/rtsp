package uk.co.aaronburt.rtsp.storage

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
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
import org.junit.Assert.assertNull
import org.junit.Test

class EncryptUrlsMigrationInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-${UUID.randomUUID()}.db"
    private val alias = "migration-key-${UUID.randomUUID()}"
    private val legacyMain = "rtsp://10.0.0.7:554/live?user=viewer&password=legacy-secret"
    private val legacySub = "rtsp://10.0.0.7:554/sub"
    private val scopes = mutableListOf<CoroutineScope>()
    private val databases = mutableListOf<CameraDatabase>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        databases.forEach { it.close() }
        context.deleteDatabase(dbName)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun version1RowsAreEncryptedOnUpgrade() {
        createVersion1Database()

        val cipher = KeystoreKey.cipher(alias)
        val database = CameraDatabase.open(context, dbName, cipher)
        databases += database
        val raw = runBlocking { database.cameraDao().all() }.single()
        assertFalse(raw.mainUrlCipher.contains("legacy-secret"))
        assertFalse(raw.mainUrlCipher.contains("10.0.0.7"))
        assertEquals(legacyMain, cipher.decrypt(raw.mainUrlCipher, mainUrlContext("legacy")))
        assertEquals(legacySub, cipher.decrypt(raw.subUrlCipher!!, subUrlContext("legacy")))

        val scope = CoroutineScope(SupervisorJob())
        scopes += scope
        val repository = RoomCameraRepository(
            dao = database.cameraDao(),
            cipher = cipher,
            scope = scope,
            io = Dispatchers.IO,
        )
        runBlocking { withTimeout(10_000) { repository.loaded.first { it } } }
        val camera = repository.get("legacy")
        assertEquals(legacyMain, camera?.mainUrl)
        assertEquals(legacySub, camera?.subUrl)
        assertNull(repository.get("missing"))
    }

    private fun createVersion1Database() {
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
                "('legacy', 'Legacy', '$legacyMain', '$legacySub', '', NULL, 'Tcp', 200, 1, 1, 0, 1)",
        )
        legacy.version = 1
        legacy.close()
    }
}
