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
