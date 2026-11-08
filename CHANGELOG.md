# Changelog

## 1.9.1 — 8 November 2026

- Fix phone connection failing with “TV did not confirm the command result” ([#5](https://github.com/sreeego/tv-home-studio/issues/5)). The package-state batch already ended in a semicolon; appending another semicolon produced invalid shell syntax. Separate the exit-status marker with a newline instead.
- Add regression coverage executing the generated batch and checking successful and failed command results.
- Phone-only hotfix: TV Home remains 4.9. Update the phone app in place; no TV reinstall is needed.

## 1.9.0 — 7 November 2026

Studio 1.9 bundles TV Home 4.9.

- Round actual app artwork, remove redundant outer cards, and retain focus outlines in all three layouts.
- Support six favorites. Classic shows favorites and all other apps; Panel lists apps alphabetically and moves Power, a centered bold clock and status into its sidebar. Remove redundant All apps buttons. Match the Panel Power card to its neighboring sidebar controls.
- Cinema shows favorites plus More apps, with one shared container for the remaining apps. Both docks support a cached wallpaper blur or dark/light shades; settings are available on TV and phone.
- Filter user-facing apps using TV launcher metadata, system-app status and a small compatibility list for older built-in consumer apps. Keep Silk, Appstore, Photos and Music without exposing ordinary internal system launchers. Reject inaccessible or permission-protected activities.
- Add manual running-app inspection and per-app Stop in the phone, plus system app-details access from TV Manage apps. Protected system apps cannot be stopped through this list; there is no automatic task killer.
- Check for a phone update quietly on launch, at most once per day, with an immediate manual check. Use official GitHub release links; downloads and installation remain user-controlled.
- Reclaim removed wallpapers and stale uploads, add explicit unused-wallpaper cleanup, and discard abandoned phone image imports. Preserve images referenced by saved themes, the screensaver and recovery settings.
- Preserve invalid configuration, offer explicit last-good/default recovery, and cache validated settings. Load the app catalog/artwork and wallpapers off the UI thread, with a bounded two-image wallpaper cache.
- Restore settings even when an old wallpaper image is no longer available; retain existing image references and omit missing files. Settings backups do not contain wallpaper binaries.
- Add Celsius/Fahrenheit weather display and ensure the weather worker always releases its running flag.
- Reduce ADB status round trips, avoid unchanged restore-ledger writes and increase file-transfer chunks while respecting the peer's negotiated packet size.
- Centralize versions and the cleanup package catalog. Build non-debuggable release APKs using the existing distribution certificate; embed the matching TV release in the phone APK. Add Java/Python regression checks to GitHub Actions.
- Omit the playback widget, PiP and music/lyrics screensaver. Sleep, wake, power, Home-routing and idle timing remain unchanged. The reported Amazon-screensaver takeover is not claimed fixed.
- Verify the app filter, all three layouts, Settings/Home navigation and Cinema More apps on the AFTSS test stick. Refresh the three layout screenshots with weather/location hidden. See [validation](docs/VALIDATION.md) for build, regression and device checks and their limits.

## 1.8.0 — 31 October 2026

- Add a separate screensaver wallpaper setting: use the active design’s image/rotation or upload one custom image shared by all designs. Theme wallpapers are unchanged.
- Add a single-image picker, preview, explicit Save and Use theme wallpaper reset in phone Themes → Home idle screen. Cancel and upload failures keep the previous setting.
- Preserve existing installations with theme-following as the default. Validate custom image IDs and reject saves before the image exists on the TV.
- Studio 1.8 embeds TV Home 4.8. Includes the swipe animation, weather-free screenshots and simpler download documentation from 1.7.1.

## 1.7.1 — 31 October 2026

- Add a 340 ms slide-up screensaver entrance and a 260 ms upward swipe dismissal. Respect each theme’s animation toggle. No continuous animation, idle timing, playback or Home-routing changes.
- Consume remote keys during dismissal; cancel/remove immediately on focus loss, leaving Home, rebuild or sleep.
- Replace the public theme, theme-picker and comparison screenshots; add the screensaver. All current screenshots hide weather and location. Expand the README feature catalog and simplify release pages.
- Studio 1.7.1 embeds TV Home 4.7.1.

## 1.7.0 — 31 October 2026

- Replace the black idle clock with the selected wallpaper, bold clock, weekday, date and selected-city weather. Reuse the wallpaper bitmap and weather cache, with minute-only updates and small text-position shifts.
- Preserve Home-only activation, first-key dismissal, two-minute idle / fifteen-minute sleep timing, manual Sleep and other apps’ playback policy. Weather Off and per-theme weather visibility are respected.
- Start the early Home watcher before artwork loading; attach a lightweight startup cover on a new boot before building the full launcher. Same-boot service reconnects keep foreground-aware recovery.
- When the service is connected, late BOOT_COMPLETED delivery checks the foreground instead of unconditionally launching Home over an app.
- Studio 1.7 embeds TV Home 4.7. Layout designs are unchanged. Stock Home can still appear before Fire OS connects the service; this is not a zero-flash guarantee.

## 1.6.0 — 30 October 2026

- Release the hidden Home window’s drawing surface with INVISIBLE instead of retaining an alpha-zero translucent buffer. Keep its view hierarchy for reuse and pause inactive work.
- Hide the duplicate activity UI while the service owns Home. Opaque rendering is used only for visible Home, avoiding unnecessary drawing underneath it.
- Optional Home-only idle screen: dim moving clock on black after 2 minutes, system sleep after 15 idle minutes. First key dismisses without opening an app. Toggle from TV Customize or phone Themes. No global sleep timeout changes; apps retain their own playback behavior.
- Preserve manual Sleep, service repair and early Home-return routing.
- Studio 1.6 embeds TV Home 4.6. No guarantee against burn-in or stock Home exposure during cold boot/service failure.


## 1.5.0 — 30 October 2026

- Handle app-initiated Home intents regardless of the caller app, including the YouTube Back-to-Home request observed on Fire TV.
- Watch Home activity-resume events for returns to an existing Home task without a new start intent.
- Preserve launch and Settings bypasses for indirect Home signals. No Back-button remapping, app allowlist, root or disabling of Amazon Home.
- Retain the rendered Home window between app visits, transparent and without input while hidden. Reveal that buffer before refreshing a changed app catalog or artwork.
- Prepare Home while the screen is off and remove the deliberate wake delay. Ignore window events queued before a newer Home request; retry a deferred Home activity request for a bounded interval.
- Run service repair from a resumed Home activity: Fire OS could kill a provider-only process between disabling and re-enabling accessibility, leaving redirection disabled after installation/repair.
- Apply the shared routing changes to Classic, Cinema and Panel without changing their designs.
- Use a translucent window format: an alpha-zero opaque overlay could still obscure a player for Fire OS power policy, suppressing its keep-screen-on request. No global sleep timers are disabled.
- Cinema's idle heading now sits lower above the dock with a time-of-day greeting above it. Focused app names retain their existing caption placement. Custom screensaver work is deferred.
- Document ARMv7 operation on AFTSS, APK architecture, OS minimums and differences from upstream device support. Add a minimum-API check to the computer installer.
- Studio 1.5 embeds TV Home 4.5. See [validation](docs/VALIDATION.md) for checks and remaining limits; this is not a zero-flash guarantee.

## 1.4.0 — 30 October 2026

Studio 1.4 bundles TV Home 4.4. First public preview.

- Restored TV Customize → Themes with Classic, Cinema and Panel, preserving saved profile options and creating an unused preset only when selected.
- Includes the preceding Cinema bold-clock, left-caption and focus-clearance fixes.
- Added prominent upstream attribution, full licenses, device screenshots, a computer installer with reversible cleanup, and phone/computer recovery guides.
- Persistent Home rendering and bounded wake recovery retained; no guarantee that Fire OS can never display stock Home.
