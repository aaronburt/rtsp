# v0.4.0 verification

The player reports the transport from successful RTSP SETUP response headers. A UDP preference may negotiate TCP through Media3's fallback; Stream details shows the negotiated result, not the saved preference. Until negotiation finishes, the value is unavailable.

Resolution and codec come from the selected video format. Frame rate uses the format's declared rate when available and otherwise estimates rendered frames per second. Video bitrate measures compressed video samples over the preceding reporting interval; it excludes audio and network overhead. Missing values are shown as unavailable. Measurements reset when the source changes.

The stall watchdog expects advancing video presentation timestamps, including for a visually static scene. Without progress for 15 seconds it leaves Playing and reconnects, or fails immediately when automatic reconnect is disabled. Paused/background players do not run the watchdog.

Camera status is kept in memory. New or changed connections are tested, and the camera list refreshes checks every minute while resumed. At most two probes run at once. Test connection uses the same RTSP player and saved transport/credentials, waits up to 20 seconds for media readiness, and releases the player on completion or cancellation. Editing an unsaved connection cancels its previous test.

## Automated checks

Run both variants' unit tests, debug builds, lint and the optimized GitHub release build:

```sh
./gradlew testGithubDebugUnitTest testFdroidDebugUnitTest assembleDebug assembleGithubRelease lintGithubDebug lintFdroidDebug -PreleaseVersion=0.4.0
```

`FullscreenOrientationTest` exercises entry, explicit exit, Android Back and navigation away, checks orientation restoration and confirms the player remains mounted across rotation.

`Media3PlaybackInstrumentedTest` is opt-in and skips unless `rtspTestHost` is supplied. It checks authentication rejection and success, 400/404 path errors, real video format/bitrate, native UDP, UDP-to-TCP fallback, and a frozen TCP connection that stays open.

## Local RTSP fixtures

Use MediaMTX (verified with v1.21.3), FFmpeg with libx264 and Python 3.8 or newer. The credentials below are disposable fixture credentials. Start each command in its own terminal from the repository root:

```sh
mediamtx tools/rtsp-fixtures/udp-tcp.yml
mediamtx tools/rtsp-fixtures/tcp-only.yml
python tools/rtsp-fixtures/freeze_proxy.py
ffmpeg -re -f lavfi -i testsrc2=size=1280x720:rate=15 -an -c:v libx264 -preset ultrafast -tune zerolatency -profile:v main -g 15 -pix_fmt yuv420p -f rtsp -rtsp_transport tcp rtsp://127.0.0.1:18554/camera
ffmpeg -re -f lavfi -i testsrc2=size=1280x720:rate=15 -an -c:v libx264 -preset ultrafast -tune zerolatency -profile:v main -g 15 -pix_fmt yuv420p -f rtsp -rtsp_transport tcp rtsp://127.0.0.1:28554/camera
```

The fixtures expose authenticated reads as `viewer` / `streamtest`. Port 18554 supports UDP and TCP, port 28554 requires TCP, and the proxy on port 38554 stops forwarding server bytes seven seconds after each connection starts. It keeps TCP open so the frame watchdog, rather than a disconnect, must detect the freeze. The configured `missing` path has no publisher and returns 404; an unconfigured path returns 400.

Run on a dedicated Android emulator (the host alias is `10.0.2.2`):

The emulator's NAT needs UDP forwarding to receive RTP from the host. Start the extra proxy below with your dedicated emulator serial. It reads only client port numbers from SETUP requests, creates UDP redirects, and removes them when the connection closes. Physical devices on the same network can use port 18554 directly and omit `rtspTestUdpPort`.

```sh
python tools/rtsp-fixtures/emulator_udp_proxy.py emulator-5570
```

```sh
./gradlew connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=uk.co.aaronburt.rtsp.ui.live.FullscreenOrientationTest,uk.co.aaronburt.rtsp.player.Media3PlaybackInstrumentedTest -Pandroid.testInstrumentationRunnerArguments.rtspTestHost=10.0.2.2 -Pandroid.testInstrumentationRunnerArguments.rtspTestUdpPort=48554 -PreleaseVersion=0.4.0
```

Stop the fixture processes when finished. The authentication and transport checks above use synthetic local streams; a physical camera check for issues #23 and #24 remains a release acceptance step.
