# v0.5.0 verification

## Automated checks

Run both variants' unit tests, GitHub debug lint and the connected Android UI tests:

```sh
./gradlew testGithubDebugUnitTest testFdroidDebugUnitTest lintGithubDebug connectedGithubDebugAndroidTest -PreleaseVersion=0.5.0
```

The unit suites cover stream default persistence and snapshot export. `LiveToolsInstrumentedTest` uses a synthetic native video surface to verify that snapshots contain current frame pixels, work in fullscreen and fail safely when playback stops. It also checks picture-in-picture entry, stream continuity, camera quality and mute restoration, and resource cleanup when the PiP activity closes. `FullscreenOrientationTest` and `VideoZoomGestureTest` cover the existing fullscreen and zoom behavior.

`Media3PlaybackInstrumentedTest` needs a configured RTSP fixture host; those network-specific cases are skipped when none is provided. The UI tests run on an Android 10 or newer device or emulator. A physical camera acceptance check remains useful before publishing the release.

## Automatic image previews

Enable **Settings → Camera previews → Refresh image previews** to update the camera list's still images. The option is off by default and persists across restarts. Visible tiles briefly open a muted sub stream (or the main stream if no sub stream is configured), capture a JPEG up to 640 pixels wide or tall, disconnect, and wait 15 seconds before trying again. At most two captures run at once. Slow or unresponsive cameras can take longer; attempts time out after 20 seconds and keep the previous image on failure.

Refresh work cancels when a tile scrolls out of view, the option is disabled, the camera list is left, or the app loses its resumed state. Previews do not keep the screen awake. `PreviewRefreshSchedulerTest` checks timing and concurrency, and `CameraPreviewRefreshTest` checks real surface pixels, sub stream selection, mute, disconnect, failure recovery, and lifecycle cancellation using a synthetic video surface.
