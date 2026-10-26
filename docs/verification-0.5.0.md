# v0.5.0 verification

## Automated checks

Run both variants' unit tests, GitHub debug lint and the connected Android UI tests:

```sh
./gradlew testGithubDebugUnitTest testFdroidDebugUnitTest lintGithubDebug connectedGithubDebugAndroidTest -PreleaseVersion=0.5.0
```

The unit suites cover stream default persistence and snapshot export. `LiveToolsInstrumentedTest` uses a synthetic native video surface to verify that snapshots contain current frame pixels, work in fullscreen and fail safely when playback stops. It also checks picture-in-picture entry, stream continuity, camera quality and mute restoration, and resource cleanup when the PiP activity closes. `FullscreenOrientationTest` and `VideoZoomGestureTest` cover the existing fullscreen and zoom behavior.

`Media3PlaybackInstrumentedTest` needs a configured RTSP fixture host; those network-specific cases are skipped when none is provided. The UI tests run on an Android 10 or newer device or emulator. A physical camera acceptance check remains useful before publishing the release.
