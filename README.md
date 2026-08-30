# Pixel Now Playing Archive Kit

This folder contains everything project-specific needed to export a Pixel's Now
Playing history, rebuild the personal archive app, install it, and import the
history on another phone. It does not depend on the original Google APK or on
files elsewhere in the old workspace.

The included app is **Now Playing Archive 1.11** (`com.brennan.nowplayingarchive`).
It stores imported history locally, merges multiple exports without duplicates,
supports favorites/search/day/time filters, and can export its combined database.

## Folder contents

- `app/` — complete Android source code and resources.
- `gradle/`, `gradlew`, `gradlew.bat` — Gradle wrapper.
- `setup-toolchain.ps1` — downloads and verifies a portable JDK, Gradle, and
  Android SDK inside this folder.
- `build.ps1` — builds signed release or debug APKs.
- `export_now_playing.py` — ADB/UI-automation exporter.
- `export-history.ps1` — convenient exporter wrapper.
- `install.ps1` — installs the APK and optionally copies the newest JSON export.
- `signing/` and `keystore.properties` — the release signing key and credentials.
- `archive/` — the completed Pixel 7 JSON/CSV export supplied with this kit.
- `licenses/` — the official Google Sans Flex OFL and font README.
- `releases/` — the already-built signed 1.11 APK.
- `screenshots/` — on-device UI verification captures for this release.
- `CHECKSUMS.sha256` — hashes for the APK, archive, exporter, and signing files.

## Requirements

- Windows 10 or later with PowerShell 7 or Windows PowerShell 5.1.
- Python 3 for history export. The exporter uses only Python's standard library.
- USB debugging enabled on the Pixel(s), with the computer authorized.
- Internet access for the initial toolchain setup and first Gradle dependency
  download. The downloaded tools and Gradle cache remain under `.toolchain/`.

The setup script installs portable copies; it does not require administrator
rights or alter the system-wide Java/Android installation.

## Quick start using the already-built APK and included export

Attach and authorize the destination Pixel, then run:

```powershell
Set-Location .\pixel-now-playing-archive-kit
.\setup-toolchain.ps1
.\install.ps1 -Serial YOUR_DESTINATION_SERIAL `
    -Apk .\releases\NowPlayingArchive-v1.11-release.apk `
    -Json .\archive\pixel7-now-playing-export.json
```

The JSON is copied to:

```text
Downloads/NowPlayingArchive/pixel7-now-playing-export.json
```

Open **Now Playing Archive**, tap the settings gear, choose **Import history**,
and select that file.

If exactly one authorized phone is attached, `-Serial` may be omitted.

## Export from another Pixel

1. Enable **Developer options → USB debugging** on the source Pixel.
2. Connect it by USB and approve the computer's debugging key.
3. Unlock the phone.
4. Open **Now Playing**, select **History**, and leave it in portrait orientation.
5. Run:

```powershell
.\export-history.ps1 -Serial YOUR_SOURCE_SERIAL
```

The script pauses until you confirm that History is visible. It then:

- detects the effective display size;
- temporarily enables stay-awake over USB if necessary;
- reopens History at its newest entry;
- scrolls using display-relative coordinates;
- checkpoints JSON and CSV after each page;
- restores the original stay-awake setting when finished.

Do not touch or lock the source phone during the automated scroll. An interrupted
run leaves its latest checkpoint on disk. New exports are written under
`archive/` with timestamped names.

You can also call the Python script directly:

```powershell
python .\export_now_playing.py `
    --serial YOUR_SOURCE_SERIAL `
    --output .\archive\my-pixel-export.json
```

Use `python .\export_now_playing.py --help` for all options.

## Build the app

Install the pinned portable toolchain once:

```powershell
.\setup-toolchain.ps1
```

Build the signed, optimized release:

```powershell
.\build.ps1 -Variant Release
```

Output:

```text
releases/NowPlayingArchive-release.apk
```

Build a debug APK instead:

```powershell
.\build.ps1 -Variant Debug
```

The build uses:

- Microsoft OpenJDK 17.0.20.1;
- Gradle 9.4.1;
- Android Gradle Plugin 9.2.0;
- Android API 37 and Build Tools 36.0.0.

Downloads are pinned and SHA-256 verified by `setup-toolchain.ps1`. Android SDK
licenses are accepted by the setup process because those packages are required
to compile the app.

## Install a newly built version

```powershell
.\install.ps1 -Serial YOUR_DESTINATION_SERIAL
```

The helper selects `releases/NowPlayingArchive-release.apk`, installs it with
`adb install -r`, copies the newest JSON under `archive/` to Downloads, and
launches the app. Use `-SkipJsonCopy` when only updating the APK.

Manual equivalent:

```powershell
.\.toolchain\android-sdk\platform-tools\adb.exe `
    -s YOUR_DESTINATION_SERIAL install -r `
    .\releases\NowPlayingArchive-release.apk
```

Because releases are signed with the included key, Android accepts later builds
as in-place updates and retains the app database.

## Signing key — back this up

The release signing material is:

```text
signing/now-playing-archive-release.jks
keystore.properties
```

Both are deliberately included so this folder can produce compatible updates.
Keep the folder private: `keystore.properties` contains the signing passwords.
If the key is lost, a differently signed build requires uninstalling the old app
first, which deletes its private database unless the combined history is exported.

## Moving another phone later

1. Export the old phone into a new JSON file.
2. Install or update Now Playing Archive on the destination phone.
3. Copy the JSON with `adb push` or `install.ps1 -Json ...`.
4. Import it through the app's settings.

Imports are merged using timestamp, title, and artist. Re-importing an existing
file does not create duplicates, and a favorite flag can upgrade an existing row.

## Important limitations

- The exporter reads the visible Google Now Playing History UI; UI changes in a
  future Google release may require updating its selectors.
- The archive app cannot write data back into Google's private Now Playing
  database without root/system privileges.
- UI icons and placeholder geometry recovered from the supplied Google APK are
  included for this private personal-use project. Review or replace those assets
  before distributing the app.
- The app has no network permission and does not upload history.

## Verification

The bundled 1.11 release was built with R8 optimization, signed with the included
keystore, passed Android lint, and was installed as an in-place update on Android
17. Its filter behavior was verified on-device: day and time selections remain
independently active, the selected menu item is checked, and selecting it again
clears that filter. The History title and gear scroll away from the absolute top.
Reversing direction reveals the floating search control in direct proportion to
the scroll distance, and scrolling down smoothly hides it while exposing the list.
The History heading, date headers, and song titles use the same stronger visual
hierarchy as the Pixel UI, while artist/time subtitles remain lighter. The search
label and settings glyph are intentionally smaller, and the bottom navigation has
Pixel-matched icon sizing, vertical inset, circular selection surfaces, tab
spacing, and screen-edge placement. Date headers use the original app's smaller,
darker treatment independently of the lighter song subtitles; on the verification
device, both render to a 47 px glyph height. The app bundles the official
six-axis Google Sans Flex variable TTF and applies the same Material 3 optical
size, rounded-terminal, width, and weight axes found in Google's APK. Search,
headings, song titles, subtitles, filters, and settings all use that family with
role-appropriate variations rather than synthesized static-font weights.
With the chrome hidden, the viewport holds ten complete 82 dp song rows and part
of an eleventh.

Verification captures are `screenshots/filters-both-selected.png`,
`screenshots/scrolling-search.png`, and
`screenshots/scrolling-search-partial.png`. The current top-level typography and
navigation comparison is `screenshots/v1.11-flex-home.png`; the corresponding
search view is `screenshots/v1.11-flex-search.png`.

Bundled APK SHA-256:

```text
See `CHECKSUMS.sha256` for the current release hash.
```
