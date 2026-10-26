package uk.co.aaronburt.rtsp

import android.app.Application
import android.os.Environment
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.co.aaronburt.rtsp.data.AppContainer
import uk.co.aaronburt.rtsp.data.PersistentCameraRepository
import uk.co.aaronburt.rtsp.data.MonitoredCameraRepository
import uk.co.aaronburt.rtsp.player.Media3ConnectionTester
import uk.co.aaronburt.rtsp.recording.FileRecordingStore
import uk.co.aaronburt.rtsp.recording.StreamRecorder
import uk.co.aaronburt.rtsp.snapshot.FileSnapshotStore
import uk.co.aaronburt.rtsp.storage.createSavedCameraRepository
import uk.co.aaronburt.rtsp.ui.settings.SharedDisplayPreferences
import uk.co.aaronburt.rtsp.ui.settings.SharedStreamDefaultsPreferences
import uk.co.aaronburt.rtsp.update.Updater

class RtspApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val recordingStore = FileRecordingStore(
            File(getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir, "recordings"),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val connectionTester = Media3ConnectionTester(this)
        container = AppContainer(
            repository = MonitoredCameraRepository(
                delegate = PersistentCameraRepository(
                    saved = createSavedCameraRepository(this),
                    scope = scope,
                ),
                tester = connectionTester,
                scope = scope,
            ),
            snapshotStore = FileSnapshotStore(File(filesDir, "snapshots")),
            displayPreferences = SharedDisplayPreferences.create(this),
            recordingStore = recordingStore,
            recordingController = StreamRecorder(recordingStore),
            connectionTester = connectionTester,
            streamDefaults = SharedStreamDefaultsPreferences.create(this),
        )
        Updater.start(this)
    }
}
