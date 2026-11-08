# Source audit — 7 November 2026

This review checked the supplied findings against the 1.9 source. Changes below are implemented unless explicitly retained or deferred. Build/test coverage and remaining limitations are recorded in [VALIDATION.md](VALIDATION.md); this is not an independent security audit.

| Finding | Assessment and response |
| --- | --- |
| Unused wallpaper/upload files accumulate | Reference-aware collection after saves and stale-upload collection, plus explicit cleanup. Current and recovery references are protected. |
| Phone image cache accumulates | Failed imports and saved/cancelled drafts are deleted; abandoned prior-session imports are removed on startup. |
| Invalid config silently defaults | Original invalid data is preserved; recovery status and last-good config are exposed. Explicit recovery is required before saving. |
| Config repeatedly parsed | Validated data is cached; mutable callers receive isolated copies and hot helpers use the cache. |
| Debuggable public APKs | Release variants are non-debuggable, use externally supplied signing configuration and bundle the matching TV variant. The existing distribution certificate is retained for upgrades; it has not been rotated. |
| Hardcoded launcher versions | Installed metadata and bundled-TV metadata derive from one version-properties file. |
| No CI/tests | Outdated in part: CI already existed. Its workflow now runs Java/Python regression checks too. |
| Weather running flag | Reset in finally, including worker-start failure. Celsius/Fahrenheit display added. |
| Duplicated cleanup catalog | One catalog with a generated Java view and consistency check. |
| Repeated ledger writes | Unchanged writes are skipped while preserving durable backup before mutations. |
| Slow status polling | Package-state queries share one shell round trip and static device details are cached. TV-side package inspections still have a cost. |
| 4 KB file push | Not accurate as a blanket statement: earlier sync reads were 16 KB. They are now 64 KB; wire frames still respect the peer's negotiated maximum. |
| Main-thread icon/catalog loading | Background loading retains the previous catalog during refresh. |
| Main-thread wallpaper decode / cache thrash | Confirmed. Asynchronous decode and bounded two-image cache for Home and separate idle wallpaper. |
| Full UI rebuild / no virtualization | True. Retained for now: a virtualized navigation rewrite needs separate device profiling and regression work. No measured smoothness claims from source changes alone. |
| Exported BootReceiver | Receiver filters protected boot/package-replaced broadcasts. Kept unchanged because boot behavior is explicitly protected; not presented as a verified vulnerability. |
| Accessibility repair atomicity | Valid residual risk if the process dies between disable and re-enable. Existing repair starts in a resumed activity. Routing repair remains unchanged. |
| Home watcher retry | Constructor already avoids starting without READ_LOGS. Runtime logcat failure/revocation still retries; clock-based cursor remains. Kept unchanged pending a focused routing test. |
| Broad window events/power query | True for window events, not arbitrary accessibility content events. Necessary current recovery behavior; unchanged. |
| Redundant ternary / Reddit draft | Ternary simplified; Reddit draft moved outside the public checkout. |

Playback widget removed by request. PiP and music/lyrics screensavers are out of scope. Sleep, wake, power actions, Home routing and idle timing are deliberately unchanged. The reported Amazon-screensaver takeover has no confirmed cause yet; existing fallback behavior remains documented.
