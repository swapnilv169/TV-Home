# Credits and provenance

This project is **completely vibe coded with Codex**. Human direction, iteration and device checks do not turn that into a claim of independent security review.

| Project | Role in this work | License retained |
| --- | --- | --- |
| [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) | Original Clean Your TV project and upstream of the launcher fork. | [Original MIT notice](../licenses/cleanyourtv-MIT.txt) |
| [itsmeterrylin/fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup) | Source checkout used as the base for our launcher; substantially adapted Home UI and routing. | [Original MIT notice](../licenses/fire-tv-cleanup-MIT.txt) |
| [udhaykumarbala/cleanyourtv-app](https://github.com/udhaykumarbala/cleanyourtv-app) | Reference for a reversible, phone-driven cleanup/install workflow. Its application code is not included in our companion. | [MIT notice retained for attribution](../licenses/cleanyourtv-app-MIT.txt) |
| [Cameron Gutman / AdbLib](https://github.com/cgutman/AdbLib) | Reused RSA/ADB key handling under `com.cgutman.adblib`; the transport is implemented separately. | [BSD 3-Clause](../licenses/AdbLib-BSD-3-Clause.txt) |
| [da-r-k / Idiot Box](https://github.com/da-r-k/idiot-box/releases/tag/v1.0.0) | Reviewed its v1.0.0 music/lyrics design as a possible future add-on. No playback widget, player, lyrics client or music screensaver is included in this release. | [MIT notice retained for attribution](../licenses/idiot-box-MIT.txt) |

[Reference checkout revisions](reference-revisions.json) record the source snapshots consulted. Repository account names are not substituted for the copyright holders named by those licenses. Original copyright, permission and disclaimer text is preserved. No affiliation or endorsement is implied.

The old upstream license also references the Outfit typeface. Its [OFL notice](../licenses/Outfit-OFL.txt) is retained; the current launcher uses system typefaces and does not include the Outfit font binary.

City search and weather use [Open-Meteo](https://open-meteo.com/), with attribution in the launcher and phone app. Forecast data is attributed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).


Screenshots show the maintainer's own TV with user-supplied wallpapers and installed third-party app logos. Those assets are not offered as wallpaper downloads, are not included in APKs, and are not covered by our MIT license. The screenshots document the launcher UI; rights to the depicted artwork and marks remain with their respective owners.

The [arnoldmitre/tv-home-studio fork](https://github.com/arnoldmitre/tv-home-studio) was also reviewed. CI was a useful direction; its Fire OS 6, automatic-location and Home-watcher changes were not adopted. No source from that fork was copied in this update.

The playback widget was removed from the release candidate. Music/lyrics screensaver ideas remain deferred; no media-session listener is included in the current launcher.
