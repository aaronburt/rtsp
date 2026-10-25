package uk.co.aaronburt.rtsp.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.co.aaronburt.rtsp.snapshot.NoSnapshotStore
import uk.co.aaronburt.rtsp.snapshot.SnapshotPruningRepository
import uk.co.aaronburt.rtsp.snapshot.SnapshotRecorder
import uk.co.aaronburt.rtsp.snapshot.SnapshotStore
import uk.co.aaronburt.rtsp.snapshot.StoreSnapshotRecorder

class AppContainer(
    repository: CameraRepository = FakeCameraRepository(),
    val snapshotStore: SnapshotStore = NoSnapshotStore,
) {
    val cameraRepository: CameraRepository = SnapshotPruningRepository(repository, snapshotStore)

    val snapshotRecorder: SnapshotRecorder = StoreSnapshotRecorder(
        snapshotStore,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
}
