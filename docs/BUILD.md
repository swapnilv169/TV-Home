# Build from source

Requirements: JDK 17+, Android SDK platform 34 and Build Tools 34.0.0, and internet access for the Gradle wrapper and Android Gradle Plugin dependencies. Android Studio provides the SDK manager. Gradle 8.9 / AGP 8.5.2 are pinned in the project.

Set ANDROID_HOME to your SDK, or create `launcher/local.properties` with `sdk.dir=...` for your machine. Do not commit it or any signing key.

From `launcher/`:

```sh
./gradlew :app:assembleDebug :companion:assembleDebug :app:lintDebug :companion:lintDebug
```

Windows:

```powershell
.\gradlew.bat :app:assembleDebug :companion:assembleDebug :app:lintDebug :companion:lintDebug
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`: TV launcher.
- `companion/build/outputs/apk/debug/companion-debug.apk`: phone companion, including that same TV APK under assets/TV-Home.apk.

The companion builds and embeds the matching TV variant automatically: debug includes debug, release includes release. Sources are native Java for TV, a local HTML/CSS/JavaScript UI in the phone WebView, and a shared Java weather client. No Flutter/React runtime is used by these two apps.

Your local debug key will differ from the maintainer's key, so the result may not update over the published APK. Keep your signing key private and stable for your own distribution. This repository does not contain private signing keys, ADB keys, personal wallpapers, TV backups or IPTV sources.

## Tests

`python -m unittest discover -s tests -p 'test_*.py'` exercises the computer installer's package-state recovery and device/allowlist checks without contacting a TV.

Native UI instrumentation is under `launcher/app/src/androidTest`; run it **only on a disposable emulator**, never a personal TV. It deliberately uses fixture apps and replaces test preferences. The expected viewport is 1920 × 1080 at density 320:

```text
adb -s emulator-5580 shell wm size 1920x1080
adb -s emulator-5580 shell wm density 320
```

Build `:app:assembleDebugAndroidTest`, install app-debug.apk and app-debug-androidTest.apk on that emulator, then run `adb -s emulator-5580 shell am instrument -w com.gathin.tvhome.test/com.gathin.tvhome.HomeVisualTest`.

After building both apps, run the host regression runner with a JVM `org.json` JAR (CI uses `org.json:json:20240303`):

```sh
python tools/run_checks.py --variant debug --json-jar /path/to/json.jar
```

Set `JAVA_HOME` and `ANDROID_HOME`, or pass `--android-jar /path/to/platforms/android-34/android.jar`. Use `--variant release` after a release build. The runner checks the generated cleanup catalog, runs Python tests, and compiles/runs every `tests/*Test.java` suite against the already-built app classes. It includes configuration validation, weather parsing, ADB transport, release versions and the existing Home/idle/boot policies. It does not contact a TV. Current public release checks are listed in [VALIDATION.md](VALIDATION.md).

### Home-return regression checks

These checks use the real HomeSignal parser/policy and need only a JDK; no TV or Android SDK runtime is contacted. From the repository root:

```text
javac -d build/home-signal launcher/app/src/main/java/com/gathin/tvhome/HomeSignal.java tests/HomeSignalTest.java
java -cp build/home-signal com.gathin.tvhome.HomeSignalTest
```

They cover the observed YouTube app-UID Home intent, other app UIDs, exact Home components/categories, existing-task resume events, and Settings/external-launch bypass guards.

### Home idle policy

```text
javac -d build/home-idle launcher/app/src/main/java/com/gathin/tvhome/HomeIdlePolicy.java tests/HomeIdlePolicyTest.java
java -cp build/home-idle com.gathin.tvhome.HomeIdlePolicyTest
```

### Boot-session policy

```text
javac -d build/boot-session launcher/app/src/main/java/com/gathin/tvhome/BootSession.java tests/BootSessionTest.java
java -cp build/boot-session com.gathin.tvhome.BootSessionTest
```

### Phone interface checks

With Playwright available to Node and its Chromium browser installed, run `node tests/phone-ui.cjs`. Alternatively set `CHROME_EXECUTABLE` to an installed Chrome binary. The test uses a mocked local TV bridge; it does not contact a real TV. It checks theme settings, cleanup selection, weather controls, and the screensaver image pick/cancel/upload-failure/save/reset workflow. Test screenshots go to ignored `build/phone-ui/`.


## GitHub Actions

Every push and pull request builds and lints both apps, then runs the Python and Java host regression suites and verifies cleanup-catalog consistency. You can also run **Actions → Build Android apps → Run workflow**. Each successful run provides two clearly labeled development artifacts: **PHONE** (with the matching TV launcher embedded) and **TV** (direct ADB installation), each with SHA-256 checksums.

CI uses a temporary debug signing key and never publishes a release. Its APKs cannot update over the public release APKs and may not update over earlier CI builds. Use [Releases](https://github.com/sreeego/tv-home-studio/releases/latest) for normal installation; no release signing keys are stored in GitHub Actions.

## Phone app release check

The phone quietly checks GitHub's latest stable release on launch, at most once per 24 hours, and shows an update entry when available. **Check for app updates** is available without a TV connection and checks immediately. It compares numeric versions and opens the official release page in the phone's browser; installation requires normal Android confirmation. After upgrading Studio, reconnect and select **Update / reinstall launcher** to send its bundled launcher to the TV. Offline, rate-limit and timeout failures leave installed apps unchanged.

## Signed release builds

`launcher/versions.properties` is the source of truth for both application versions. Installed TV version reporting uses package metadata; the phone's bundled launcher version is generated from the same properties.

Release APKs are **not debuggable**. Provide these environment variables outside the repository:

- `TVHOME_KEYSTORE`: absolute path to your existing private signing keystore.
- `TVHOME_STORE_PASSWORD`: its password.
- `TVHOME_KEY_ALIAS`: signing key alias.
- `TVHOME_KEY_PASSWORD`: signing key password.

Then, from `launcher/`:

```sh
./gradlew :app:assembleRelease :companion:assembleRelease :app:lintRelease :companion:lintRelease
```

Outputs are `app/build/outputs/apk/release/app-release.apk` and `companion/build/outputs/apk/release/companion-release.apk`. The latter embeds that exact release TV APK. The build rejects missing signing credentials rather than silently producing an unsigned distribution APK.

The expected signing certificate defaults to the existing public distribution certificate, so updates can retain installed user data. Keep using the existing private key; changing build machines alone does not require a new key. A fork using its own key must explicitly set `TVHOME_EXPECTED_CERT_SHA256` to that certificate's SHA-256. A different certificate cannot update an existing official installation. Never commit keystores or passwords.

CI stays debug-only: no private release key is uploaded and no release is published automatically.

## Shared cleanup catalog

Edit `launcher/common/cleanup-packages.json`, then run:

```sh
python tools/generate_cleanup.py
python tools/generate_cleanup.py --check
```

The computer helper reads the JSON directly. The companion uses generated `CleanupCatalog.java`. CI rejects stale generated output, preventing the two cleanup lists from drifting apart.
