# Privacy and permissions

No project accounts, ads, analytics or telemetry uploads are implemented.

- The phone uses authenticated ADB over your local network. Its authorization key, TV address and restore ledger stay in app-private storage.
- Exported restore files include a device identifier or fingerprint. Keep them private; they are not included in this repository.
- User-selected photos are resized and sent to the connected TV. No wallpaper is bundled, and this project does not upload your photos to a hosted service.
- Weather is off until you select a city. City search and forecasts contact Open-Meteo, which receives the search/coordinates and the request's network address. There is no automatic location lookup or GPS permission.
- The phone checks GitHub for the latest stable release on launch, at most daily, or when you request a manual check. GitHub receives that request's network address; no TV identifier or settings are sent. Downloads open the official GitHub release page.
- Running-app inspection uses the connected TV's process list locally. Stopping an app requires an explicit choice and confirmation; there is no automatic task killer or uploaded process history.
- The launcher's accessibility service observes foreground-window changes for Home routing and provides its persistent Home window. WRITE_SECURE_SETTINGS allows enabling/disabling its own service without erasing unrelated services.
- READ_LOGS is granted through ADB so a local filtered watcher can recognize Home transitions earlier. The launcher does not store or upload log streams.
- Amazon's own apps and your installed streaming apps have their own privacy practices; hiding an icon does not disable that app.

Public APKs are sideloaded, with source and checksums supplied. Version 1.9 uses non-debuggable release builds signed with the maintainer's existing distribution certificate, originally used for development builds, to preserve upgrades from earlier releases. This is not a signing-key rotation. The private key is not in the repository. Independent debug builds use the builder's own key; release builds require explicit external signing configuration.
