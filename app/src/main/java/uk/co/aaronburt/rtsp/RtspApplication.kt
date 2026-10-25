package uk.co.aaronburt.rtsp

import android.app.Application
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.co.aaronburt.rtsp.data.AppContainer
import uk.co.aaronburt.rtsp.data.PersistentCameraRepository
import uk.co.aaronburt.rtsp.snapshot.FileSnapshotStore
import uk.co.aaronburt.rtsp.storage.createSavedCameraRepository

class RtspApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            repository = PersistentCameraRepository(
                saved = createSavedCameraRepository(this),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            ),
            snapshotStore = FileSnapshotStore(File(filesDir, "snapshots")),
        )
    }
}
