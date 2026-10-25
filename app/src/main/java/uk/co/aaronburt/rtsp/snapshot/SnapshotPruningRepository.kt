package uk.co.aaronburt.rtsp.snapshot

import uk.co.aaronburt.rtsp.data.CameraRepository

class SnapshotPruningRepository(
    private val delegate: CameraRepository,
    private val store: SnapshotStore,
) : CameraRepository by delegate {
    override fun delete(id: String) {
        delegate.delete(id)
        store.delete(id)
    }
}
