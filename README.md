<p align="center">
  <img src="branding/play-store-512.png" alt="RTSP icon" width="96">
</p>

<h1 align="center">RTSP</h1>

<p align="center">A fast, private Android viewer for your own RTSP cameras.</p>

<p align="center">
  <img src="docs/screenshots/cameras.png" alt="Camera list with last-seen snapshots" width="23%">
  <img src="docs/screenshots/live.png" alt="Live view with main and sub stream toggle" width="23%">
  <img src="docs/screenshots/settings.png" alt="Settings" width="23%">
</p>
<p align="center">
  <img src="docs/screenshots/multiview.png" alt="Multiview showing several cameras at once" width="70%">
</p>

- **Camera list**: every camera with its last snapshot and a last-seen time.
- **Live view**: one camera with a main and sub stream toggle, mute and fullscreen.
- **Multiview**: several cameras on screen at once.
- **Settings**: playback options and update checks.

## Features

- Add any `rtsp://` endpoint with optional sub stream URL, username and password
- Passwords are encrypted on the device with the Android Keystore
- TCP, UDP or automatic transport and adjustable latency
- Automatic reconnect with backoff
- Multiview grids of 1, 2x2 or 2x3 using sub streams to save bandwidth
- Favourites, search and last-seen snapshots
- No accounts and no cloud service

## Install

Download the latest APK from [GitHub Releases](https://github.com/aaronburt/rtsp/releases/latest) and install it. Requires Android 8.0 or newer. The app checks GitHub Releases for new versions and can download and install updates itself.

## Licence

Apache License 2.0. See [LICENSE](LICENSE).
