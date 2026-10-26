package uk.co.aaronburt.rtsp.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.co.aaronburt.rtsp.recording.NoRecordingController
import uk.co.aaronburt.rtsp.recording.NoRecordingStore
import uk.co.aaronburt.rtsp.recording.RecordingController
import uk.co.aaronburt.rtsp.recording.RecordingStore
import uk.co.aaronburt.rtsp.snapshot.NoSnapshotStore
import uk.co.aaronburt.rtsp.snapshot.SnapshotPruningRepository
import uk.co.aaronburt.rtsp.snapshot.SnapshotRecorder
import uk.co.aaronburt.rtsp.snapshot.SnapshotStore
import uk.co.aaronburt.rtsp.snapshot.StoreSnapshotRecorder
import uk.co.aaronburt.rtsp.ui.settings.DisplayPreferences
import uk.co.aaronburt.rtsp.ui.settings.InMemoryDisplayPreferences
import uk.co.aaronburt.rtsp.player.CameraConnectionTester
import uk.co.aaronburt.rtsp.player.NoConnectionTester

class AppContainer(
    repository: CameraRepository = FakeCameraRepository(),
    val snapshotStore: SnapshotStore = NoSnapshotStore,
    val displayPreferences: DisplayPreferences = InMemoryDisplayPreferences(),
    val recordingStore: RecordingStore = NoRecordingStore,
    val recordingController: RecordingController = NoRecordingController,
    val connectionTester: CameraConnectionTester = NoConnectionTester,
) {
    val cameraRepository: CameraRepository = SnapshotPruningRepository(repository, snapshotStore)

    val snapshotRecorder: SnapshotRecorder = StoreSnapshotRecorder(
        snapshotStore,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}
