# Log

## 2026-10-25
- Scaffolded project: package uk.co.aaronburt.rtsp, Kotlin + Compose, Media3 ExoPlayer RTSP, minSdk 26, compileSdk/targetSdk 36.
- Licence: Apache 2.0.
- Launcher icon: concept C (Viewfinder) in app/src/main/res; 512px Play Store icon in branding/.
- Dependency versions in gradle/libs.versions.toml were picked without a build available (Google Maven was unreachable from the cloud session). AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2025.10.01, Media3 1.8.0, Gradle 8.14.3. Not yet built; bump if Android Studio complains.
- Placeholder UI: URL entry screen then full-screen StreamScreen using createRtspPlayer (TCP by default).
- Cleartext traffic is allowed in network_security_config for LAN cameras and ONVIF over HTTP.
- Backups disabled and excluded from data extraction so camera credentials stay on the device.
- Rule from Aaron: no code comments and no documentation unless asked. Notes go in this file.

## Release deployment
- Releases are built by .github/workflows/release.yml when a v* tag is pushed, signed in CI, and published as a GitHub release with the APK attached (decision from Aaron, 2026-10-25).
- app/build.gradle.kts signs release builds only when RELEASE_KEYSTORE_PATH is set, so local release builds come out unsigned.
- Repo secrets needed: RELEASE_KEYSTORE_BASE64, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD.
- Create the keystore with: keytool -genkeypair -v -keystore release.jks -alias rtsp -keyalg RSA -keysize 4096 -validity 10000. Back it up somewhere safe and never commit it; losing it means users cannot update.
- To release: bump versionCode and versionName in app/build.gradle.kts, commit, then git tag v0.1.0 and git push origin v0.1.0.
- Untested: the project has never been built, so the first tag run may need version fixes.

## Build plan (updated 2026-10-25, awaiting Aaron's go-ahead)
Each chunk is meant to be its own thread and its own PR on its own git branch (chunk/NN-name). Chunks with no dependency on each other can be built in parallel on separate branches and merged in any order once their dependencies are on main. ONVIF is out of scope for now, see Backburner.

### 1. Build baseline and CI
- Scope: get the scaffold compiling on a real machine, fix version catalog issues, add a debug-build workflow on push and PR, add lint, one unit test to prove the test setup runs.
- Depends on: nothing. Needs Aaron's machine (JDK 17 and Android SDK 36) because Gradle cannot reach Google Maven from the cloud.
- Done: ./gradlew assembleDebug testDebugUnitTest lintDebug pass locally and in GitHub Actions; the app launches and plays an RTSP URL on a device or emulator.

### 2. App shell and theme
- Scope: dark theme with amber accent, typography, Material Symbols Rounded icons, navigation graph (list, live view, settings, multiview, add/edit), ViewModel and manual dependency wiring (no Hilt/Dagger), placeholder destinations.
- Depends on: 1.
- Done: every top-level destination is reachable from the nav bar or list screen and matches the dark/amber design; back stack and rotation behave.

### 3. Camera storage and secure credentials
- Scope: Camera model (name, host, port, path or full URL, main and sub stream paths, transport, latency override), Room database and repository, Android Keystore AES-GCM encryption for passwords, credentials injected into the URL only in memory, backup stays disabled.
- Depends on: 2.
- Done: instrumented tests show create, read, update and delete work, passwords never appear in the database file or logs, and stored ciphertext survives app restart.

### 4. Player engine and single live view
- Scope: Player interface with a Media3 implementation, Main/Sub stream switch, TCP/UDP toggle, latency buffer setting, auto-reconnect with backoff, buffering and error states, full-screen live view with overlay controls, keep-screen-on, clean release on lifecycle changes.
- Depends on: 3 (Camera model only).
- Done: a saved camera plays full screen, switching Main/Sub and TCP/UDP restarts the stream correctly, killing the camera triggers reconnect, no player leaks after leaving the screen.

### 5. Camera list and add/edit
- Scope: list screen with state chip per camera, add-camera button, add/edit form with validation, delete with confirm, tap to open live view. Online/offline shows "unknown" until chunk 6.
- Depends on: 3, 4.
- Done: full camera CRUD from the UI, survives restart, tapping a camera opens live view.

### 6. Connection test and online/offline status
- Scope: lightweight RTSP probe (OPTIONS then DESCRIBE with auth over a socket, no decoding, timeouts), Test button in the add/edit form with specific error messages (unreachable, auth failed, wrong path), periodic status refresh while the list is visible.
- Depends on: 5.
- Done: probe distinguishes timeout, 401, 404 and success against a real camera and a local test server; list chips update without leaking sockets.

### 7. Settings
- Scope: DataStore-backed settings: default transport, default latency buffer, reconnect behaviour, status check interval, keep-screen-on, multiview defaults; wired into player and list.
- Depends on: 4, 6.
- Done: changing a setting changes behaviour on the next stream start and persists across restarts.

### 8. Multiview (2x3 grid)
- Scope: grid of up to 6 tiles, default to sub streams, per-tile status and mute, tap to promote a tile to full live view, pause tiles when off screen, decoder-limit handling and staggered start, camera picker per tile.
- Depends on: 4, 5, 7.
- Done: 6 sub streams play concurrently on a mid-range device without crashing, tile promote and return works, backgrounding releases all decoders.

### 9. libVLC fallback engine
- Scope: second Player implementation using libVLC, automatic fallback on Media3 failure or per-camera engine choice, ABI splits to contain APK size.
- Depends on: 4.
- Done: a camera that Media3 cannot play (for example unusual codec or transport quirk) plays through libVLC, engine choice is stored per camera, release APK size is checked and recorded.
- Risk: libVLC is LGPL 2.1 (fine with Apache 2.0 when dynamically linked, but must be noted when shipping) and F-Droid may object to the prebuilt Maven artifact. Plan: keep it behind a build flavor so an F-Droid flavor can omit it.

### 10. Recording and snapshots
- Scope: record button in live view writing MP4/MKV to app storage or a user-chosen folder via Storage Access Framework, snapshot capture, recordings list with playback and delete, foreground service for background recording.
- Depends on: 4, 9 (recommended: libVLC's record support is far less work than muxing raw RTP from Media3).
- Done: recording a stream for 10 minutes yields a playable file, stopping on stream loss finalises the file, snapshots save correctly.
- Open point: confirm during a short spike that libVLC record is the route; if not, this chunk grows.

### 11. Release hardening
- Scope: R8 keep rules verified on a release build, ABI splits, versioning, first signed tag through release.yml, secrets set up, smoke test checklist, F-Droid readiness review (no proprietary deps, reproducible build, metadata).
- Depends on: everything shipping in the first release (at minimum 1 to 7).
- Done: a v* tag produces a signed APK on GitHub releases that installs and plays a stream on a clean device.

### Order notes
- Critical path: 1, 2, 3, 4, 5. After 5, chunk 6 then 7 is one line; chunk 8 follows 7. Chunk 9 only needs 4 and can run in parallel with 5 to 8 on its own branch; chunk 10 follows 9.
- Suggested first release (v0.1): chunks 1 to 7 plus 11. v0.2 adds multiview. v0.3 adds libVLC and recording.
- Defaults chosen unless Aaron says otherwise: Room for cameras, DataStore for settings, no DI framework, libVLC placed before recording.

### Chunk 1 steps (proposed)
1. On Aaron's machine, run ./gradlew assembleDebug and fix whatever fails, starting with gradle/libs.versions.toml (AGP, Kotlin, Compose BOM, Media3, lifecycle versions were never verified).
2. Add JUnit to the version catalog and one small unit test for the stream URL check in RtspApp.
3. Run lintDebug, fix or baseline findings.
4. Add .github/workflows/build.yml running assembleDebug, testDebugUnitTest and lintDebug on push and pull request.
5. Install the debug build on a device or emulator and play a real RTSP URL over TCP and UDP.
6. Dry-run assembleRelease without secrets to confirm release.yml's build step is sane. No tag is pushed.
- Branch chunk/01-build-baseline, one PR. The repo has no git remote yet, so the PR and the CI run need a GitHub repo to be created and added as origin first.

### Chunk 1 result (2026-10-25, branch chunk/01-build-baseline)
- Version catalog needed no changes: assembleDebug, testDebugUnitTest, lintDebug and assembleRelease all pass as scaffolded (AGP 8.13.2, Kotlin 2.2.21, Compose BOM 2025.10.01, Media3 1.8.0, Gradle 8.14.3). Built locally on JDK 21; CI uses JDK 17 to match release.yml.
- Added JUnit 4.13.2 and StreamUrlTest (3 tests). The rtsp:// check moved from RtspApp into player/StreamUrl.kt as isRtspUrl so it can be tested.
- Lint had 5 real errors: Media3 RTSP calls in RtspPlayer.kt needed @androidx.annotation.OptIn(UnstableApi::class). Fixed, no baseline needed. Remaining lint output is warnings only (newer dependency versions available, cleartext base config, which is deliberate).
- local.properties must have sdk.dir written as C\:/Users/... on Windows, otherwise lint raises PropertyEscape. The file is gitignored.
- .gitignore now ignores build/ at any depth (app/build was showing as untracked).
- Added .github/workflows/build.yml: assembleDebug, testDebugUnitTest, lintDebug on push to main and on pull requests, uploads the debug APK, uploads reports on failure. Not run yet, no remote exists.
- assembleRelease without secrets succeeds with R8 and resource shrinking on and produces app-release-unsigned.apk (about 1.8 MB). release.yml copies app-release.apk, which only exists when the keystore secrets are present, so that step is only exercised by a real tag run.
- Remote added: https://github.com/aaronburt/rtsp. main and chunk/01-build-baseline pushed, PR 1 opened. build.yml passed on GitHub Actions with JDK 17 (assembleDebug, testDebugUnitTest, lintDebug). Actions warn that setup-java v4 is deprecated in favour of v5 and that Node 20 actions are being forced to Node 24; revisit when bumping action versions.
- Still open: play a real RTSP URL over TCP and UDP on a device or emulator (needs Aaron's URL and a device; emulator AVD satellite exists).

### Chunk 2 result (2026-10-25, branch chunk/02-app-shell)
- Wave A rule from Aaron: UI and player engine only, no on-device storage. Camera data is an in-memory FakeCameraRepository seeded with six sample cameras (one offline); chunk 3 replaces it behind the CameraRepository interface.
- Theme: dark/amber colour scheme from the redesign (tokens in ui/theme/Theme.kt), Inter Tight and JetBrains Mono bundled as variable fonts in res/font (both SIL OFL, from google/fonts), so no downloadable fonts or Play Services.
- Icons: Material Symbols Rounded vector drawables copied from google/material-design-icons (Apache 2.0) into res/drawable as ic_*.xml, used through painterResource. No icon font and no material-icons dependency.
- Navigation: navigation-compose 2.9.5 with string routes (cameras, multiview, recordings, settings, live/{id}, camera/add, camera/{id}/edit). Bottom bar shows on cameras, recordings and settings; multiview hides it, locks landscape and goes immersive. Manual wiring: RtspApplication holds AppContainer, ViewModels are built by factories in each ViewModel's companion.
- Live view plug-in point: ui/live/PlayerSlot.kt defines PlayerSlot = (Camera, Modifier) -> Unit and PlaceholderPlayerSlot. RtspApp(container, playerSlot) passes it to the live view and to multiview tiles, so the player engine only has to supply a slot. StreamScreen.kt and RtspPlayer.kt were left untouched for chunk 4.
- Live view is landscape/fullscreen aware: landscape or the fullscreen button hides everything but the video and system bars.
- Deliberately left out of the redesign because they are out of scope for now: PTZ card, Talk, PiP (ONVIF is on the Backburner), Find on network, Test connection button and result row (chunk 6), 3x3 multiview layout (plan says up to 6 tiles). Live stats row shows the configured buffer and stream choice instead of measured latency, resolution and fps, which only the player knows.
- Placeholders: Record toggles a REC badge and timer but writes nothing (chunk 10), Snapshot is disabled, Recordings and Settings are empty screens, Main/Sub switch only changes UI state (chunk 4 wires it to the player), Retry on an offline tile just marks the fake camera online.
- Add/edit form: name and rtsp:// validation, credentials typed into the URL move to the username and password fields on focus loss and on save, delete asks for confirmation. Password is held in the ViewModel only.
- Camera model in data/Camera.kt is a UI-level draft; chunk 3 owns the real model and may reshape it.
- Verified: assembleDebug, testDebugUnitTest (25 tests), lintDebug (0 errors) and assembleRelease pass locally. Walked the cameras, live, edit, add-with-errors and multiview screens on the emulator.
- Tested on a 320x640dp emulator, where the cameras grid shows one column; the grid is adaptive (148dp minimum) so phones 340dp and wider get two columns.

### Chunk 4 result (2026-10-25, branch chunk/04-player-engine)
- Player interface: player/PlayerEngine.kt (setSurface, setMuted, load, stop, release, listener callbacks). Media3Engine is the only implementation; a libVLC engine in chunk 9 implements the same interface.
- LiveStreamController owns everything engine-independent: state machine (Idle, Connecting, Buffering, Playing, Reconnecting, Failed), Main/Sub switch, TCP/UDP switch, latency switch, backoff reconnect (1s doubling to 30s, 10 attempts then Failed with a Retry), 15s stall watchdog, pause/resume, release. Stream config is plain in-memory input (StreamConfig), nothing is stored.
- Latency maps to DefaultLoadControl bufferForPlayback and bufferForPlaybackAfterRebuffer (presets 0, 250, 500, 1000, 2000 ms). Changing transport, quality or latency rebuilds the ExoPlayer and restarts the stream.
- UDP is requested by not forcing RTP over TCP. Media3 can fall back to TCP by itself if UDP setup fails, so UDP mode may silently end up on TCP.
- Media3Engine releases the whole ExoPlayer on stop and every reconnect, so decoders are freed when the screen is left or the app is stopped. After leaving the live view only the process-wide ExoPlayer:Frame, ExoPlayer:Backg and MediaCodec looper threads remain; no ExoPlayer:Playback or loader threads.
- Rendering: ui/PlayerSurface.kt binds a SurfaceView to a LiveStreamController and letterboxes to the video aspect. ui/live/Media3PlayerSlot.kt is the PlayerSlot implementation: one controller per slot, config built from the Camera (credentials injected into the URL in memory only, Transport.Auto maps to TCP, latencyMs and autoReconnect honoured), pause on ON_STOP and resume on ON_START, release on dispose, status panel (spinner, reconnect countdown, Retry on Failed). MainActivity passes it to RtspApp.
- PlayerSlot widened to (camera, quality, muted, modifier). LiveScreen passes its quality and mute state through movableContentOf so toggling fullscreen or rotating does not restart the stream; multiview tiles pass Sub and muted. ui.live.StreamQuality was replaced by player.StreamQuality.
- The old StreamScreen, createRtspPlayer, the standalone live view and the media3-ui dependency are gone. The shell's own overlays and controls are used as is.
- Camera has no sub stream URL yet, so the Sub toggle reloads the main URL until chunk 3 adds sub paths to the model (StreamConfig already supports subUrl).
- Tests: 30 new JVM tests (ReconnectPolicy, StreamConfig, StreamUrl credentials, LiveStreamController with a fake engine and virtual time). assembleDebug, testDebugUnitTest and lintDebug pass (lint: 0 errors, warnings only).
- Real stream test on an emulator against Aaron's camera (on the rtsp-test AVD, through the shell: edit a fake camera, save, live view plays; fullscreen toggle keeps playing; mute toggle works; multiview shows the stream next to five reconnecting fake cameras with no crash). Earlier standalone test: Main and Sub both play over TCP and UDP, switching restarts correctly, cutting the emulator's network shows Reconnecting with backoff and keeps the last frame, restoring it recovers on its own. The camera URL and credentials are not stored anywhere in the repo.
- Error text for a lost connection is generic (ERROR_CODE_IO_UNSPECIFIED covers many causes); wrong credentials and wrong path show up as a Media3 RTSP exception and get the "check path and credentials" message, but that mapping is unverified against a camera that rejects auth. Chunk 6's probe is the place for precise errors.
- The shared satellite emulator was being used by the chunk 2 thread at the same time, so a second AVD (rtsp-test, same system image) was created for this test.

## Backburner
- ONVIF discovery and profile import (WS-Discovery, GetProfiles, GetStreamUri, Discover button in the add-camera form). Was chunk 8.
- ONVIF PTZ (ContinuousMove, Stop, presets, on-screen joystick and zoom). Was chunk 9.
- Cameras are added by entering the RTSP URL or host, path and credentials by hand until these return. The Camera model leaves room for an ONVIF port later.
