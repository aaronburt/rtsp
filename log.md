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

### Chunk 3 result (2026-10-25, branch chunk/03-storage)
- Built on main so it does not depend on PR 2 or PR 3. Everything is in package storage and uses its own names (SavedCamera, SavedCameraRepository) so it cannot clash with PR 2's data.Camera and data.CameraRepository.
- Model: SavedCamera has id, name, mainUrl, optional subUrl, username, password, transport (Auto, Tcp, Udp), latencyMs, autoReconnect, startMuted, favourite, createdAt. streamUrl(StreamKind.Main or Sub) builds the credentialled URL in memory only (percent-encoded userinfo, Sub falls back to Main when there is no sub URL). toString never prints the password, username or URL.
- Room 2.8.3 via KSP 2.2.21-2.0.4, schema exported to app/schemas (v1, no destructive migration). Table cameras stores URLs with credentials stripped, the username in plain text, and the password only as passwordCipher.
- Encryption: AES-256-GCM with a non-exportable Android Keystore key (alias uk.co.aaronburt.rtsp.camera_credentials, created on first use). Stored format is Base64 of version byte, IV length, IV, ciphertext and tag. The camera id is bound in as GCM associated data, so a ciphertext copied to another row does not decrypt. EncryptedSharedPreferences and security-crypto are not used (deprecated). AesGcmCredentialCipher takes any SecretKey so the JVM tests run without a device.
- If a password cannot be decrypted (key lost, row tampered) it loads as an empty string and the camera stays usable; the user re-enters it. Backups are off, so Keystore keys and the database never travel to another device.
- SavedCameraRepository is a StateFlow plus synchronous get, save, delete, setFavourite and newId, the same shape as PR 2's CameraRepository, so the shell can use it without UI changes. RoomCameraRepository keeps a write-through cache: reads and the StateFlow update immediately, Room writes run in order on a single background queue, a failed write re-reads the database. cameras starts empty and loaded turns true once the first read finishes; saves and deletes made before that are kept. Passwords are decrypted once at load and held in memory for the live view.
- save() accepts URLs with user:pass@ in them and moves the credentials into username and password, so a credential-bearing URL can never reach the database.
- Factory: createSavedCameraRepository(context) opens the database with the Keystore cipher on Dispatchers.IO.
- Tests: 37 new JVM tests (URL credentials, cipher round trip, wrong key, wrong context, tamper, garbage, repository over a fake DAO including restart, pre-load saves, failed write) and 7 instrumented tests on the satellite emulator (API 36): create, read, update and delete across a restart, every field survives restart, the plaintext password is absent from cameras.db, -wal and -shm, ciphertext is unique per row and cannot be moved between rows, the Keystore key is reused by a new instance, the password is absent from logcat. assembleDebug, testDebugUnitTest (40 tests), lintDebug (0 errors) and assembleRelease with R8 pass. Run the instrumented tests with ./gradlew connectedDebugAndroidTest.
- Dependencies added: Room (Apache 2.0), KSP (Apache 2.0), kotlinx-coroutines-android and -test, androidx.test runner and ext-junit for androidTest. No Play Services, nothing proprietary.
- Expect small catalog and build.gradle.kts conflicts with PR 3, which adds the same coroutines entries. Keep one copy.

### Wiring chunk 3 into PR 2 (not merged when this was written)
- Verified on a scratch branch off chunk/02-app-shell: this commit cherry-picks cleanly, assembleDebug and PR 2's 25 unit tests pass, and on the emulator an added camera shows in the list and is still there after force-stop and relaunch, with no plaintext password in the database or logcat.
- Add data/StoredCameraRepository.kt implementing PR 2's CameraRepository over a SavedCameraRepository. save maps Camera.url to mainUrl, keeps the existing subUrl and createdAt, and maps Transport to CameraTransport by name; toCamera is the reverse; delete, setFavourite and newId pass through; retry is a no-op until chunk 6; cameras is saved.cameras mapped with stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), SharingStarted.Eagerly, initial).
- AppContainer takes a Context: class AppContainer(context: Context) { val cameraRepository: CameraRepository = StoredCameraRepository(createSavedCameraRepository(context)) }.
- RtspApplication must build the container in onCreate (lateinit var container, private set). Writing val container = AppContainer(this) as a property initializer crashes at launch because the application has no base context yet.
- Behaviour changes in the shell: the sample cameras disappear (the list starts empty), online and offline are not stored so every stored camera shows online until chunk 6, and Retry does nothing. FakeCameraRepository and its test can stay for UI tests. Screens that call get(id) at construction see null only if the process starts directly on an edit or live route before loading finishes; from the list this does not happen.
- Not stored by the shell yet: PR 2's Camera has one url. Main and Sub need a subUrl on Camera and a Sub URL field in the form (PR 3's RtspApp already has one); until then the adapter preserves whatever subUrl is stored.
- Emulator note: the satellite emulator was idle (the other threads had finished) and was used for the instrumented tests and the wiring check. The app's data was cleared afterwards.

## Backburner
- ONVIF discovery and profile import (WS-Discovery, GetProfiles, GetStreamUri, Discover button in the add-camera form). Was chunk 8.
- ONVIF PTZ (ContinuousMove, Stop, presets, on-screen joystick and zoom). Was chunk 9.
- Cameras are added by entering the RTSP URL or host, path and credentials by hand until these return. The Camera model leaves room for an ONVIF port later.
