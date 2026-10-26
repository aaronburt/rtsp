package uk.co.aaronburt.rtsp.storage

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface SavedCameraRepository {
    val cameras: StateFlow<List<SavedCamera>>

    val loaded: StateFlow<Boolean>

    fun get(id: String): SavedCamera?

    fun save(camera: SavedCamera)

    fun delete(id: String)

    fun setFavourite(id: String, favourite: Boolean)

    fun newId(): String
}

fun createSavedCameraRepository(context: Context): SavedCameraRepository {
    val database = CameraDatabase.open(context)
    return RoomCameraRepository(
        dao = database.cameraDao(),
        cipher = KeystoreKey.cipher(),
        scope = CoroutineScope(SupervisorJob()),
        io = Dispatchers.IO,
    )
}

class RoomCameraRepository(
    private val dao: CameraDao,
    private val cipher: CredentialCipher,
    scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : SavedCameraRepository {
    private val state = MutableStateFlow<List<SavedCamera>>(emptyList())
    private val ready = MutableStateFlow(false)
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val touchedBeforeLoad = mutableSetOf<String>()

    override val cameras: StateFlow<List<SavedCamera>> = state

    override val loaded: StateFlow<Boolean> = ready

    init {
        scope.launch(io) {
            val rows = runCatching { dao.all().map(::toModel) }.getOrDefault(emptyList())
            synchronized(touchedBeforeLoad) {
                state.update { current -> ordered(rows.filterNot { it.id in touchedBeforeLoad } + current) }
                ready.value = true
            }
            for (write in writes) {
                try {
                    write()
                } catch (_: Exception) {
                    resync()
                }
            }
        }
    }

    override fun get(id: String): SavedCamera? = state.value.firstOrNull { it.id == id }

    override fun save(camera: SavedCamera) {
        val normalised = normalise(camera)
        touch(normalised.id)
        var saved = normalised
        state.update { list ->
            val existing = list.firstOrNull { it.id == normalised.id }
            saved = normalised.copy(
                createdAt = existing?.createdAt ?: normalised.createdAt.takeIf { it > 0 } ?: clock(),
            )
            ordered(list.filterNot { it.id == saved.id } + saved)
        }
        val snapshot = saved
        writes.trySend { dao.upsert(toEntity(snapshot)) }
    }

    override fun delete(id: String) {
        touch(id)
        state.update { list -> list.filterNot { it.id == id } }
        writes.trySend { dao.delete(id) }
    }

    override fun setFavourite(id: String, favourite: Boolean) {
        touch(id)
        state.update { list -> list.map { if (it.id == id) it.copy(favourite = favourite) else it } }
        writes.trySend { dao.setFavourite(id, favourite) }
    }

    override fun newId(): String = UUID.randomUUID().toString()

    private fun touch(id: String) {
        synchronized(touchedBeforeLoad) {
            if (!ready.value) touchedBeforeLoad += id
        }
    }

    private suspend fun resync() {
        runCatching { dao.all().map(::toModel) }.onSuccess { state.value = ordered(it) }
    }

    private fun normalise(camera: SavedCamera): SavedCamera {
        val main = splitUrlCredentials(camera.mainUrl)
        val sub = camera.subUrl?.takeIf { it.isNotBlank() }?.let(::splitUrlCredentials)
        val username = camera.username.ifEmpty { main.username ?: sub?.username ?: "" }
        val password = camera.password.ifEmpty { main.password ?: sub?.password ?: "" }
        return camera.copy(
            name = camera.name.trim(),
            mainUrl = main.url,
            subUrl = sub?.url,
            username = username,
            password = password,
        )
    }

    private fun toEntity(camera: SavedCamera) = CameraEntity(
        id = camera.id,
        name = camera.name,
        mainUrlCipher = cipher.encrypt(camera.mainUrl, mainUrlContext(camera.id)),
        subUrlCipher = camera.subUrl?.let { cipher.encrypt(it, subUrlContext(camera.id)) },
        username = camera.username,
        passwordCipher = camera.password.takeIf { it.isNotEmpty() }?.let { cipher.encrypt(it, camera.id) },
        transport = camera.transport.name,
        latencyMs = camera.latencyMs,
        autoReconnect = camera.autoReconnect,
        startMuted = camera.startMuted,
        favourite = camera.favourite,
        createdAt = camera.createdAt,
    )

    private fun toModel(entity: CameraEntity) = SavedCamera(
        id = entity.id,
        name = entity.name,
        mainUrl = cipher.decrypt(entity.mainUrlCipher, mainUrlContext(entity.id)).orEmpty(),
        subUrl = entity.subUrlCipher?.let { cipher.decrypt(it, subUrlContext(entity.id)) },
        username = entity.username,
        password = entity.passwordCipher?.let { cipher.decrypt(it, entity.id) } ?: "",
        transport = CameraTransport.entries.firstOrNull { it.name == entity.transport } ?: CameraTransport.Tcp,
        latencyMs = entity.latencyMs,
        autoReconnect = entity.autoReconnect,
        startMuted = entity.startMuted,
        favourite = entity.favourite,
        createdAt = entity.createdAt,
    )

    private fun ordered(list: List<SavedCamera>): List<SavedCamera> =
        list.sortedWith(compareBy<SavedCamera> { it.createdAt }.thenBy { it.name.lowercase() })
}
