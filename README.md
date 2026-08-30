# Pixel Now Playing Archive Kit

This source repository contains two related tools:

- **Now Playing Archive**, an offline Android app that imports, merges,
  searches, filters, favorites, displays, and re-exports Pixel Now Playing
  history without modifying Google's private database.
- A Python/ADB UI-automation exporter that scrolls the visible Now Playing
  History screen and writes the JSON format accepted by the archive app.

The app has no network permission. Imported history stays in its private local
database until you clear it or export it manually.

This is necessary because without root, you cannot import / export the now
playing history database from another phone. If you have root on both the
src/dest phone, you can directly copy from the database (left as an exercise
for the reader).

## Requirements

- Windows 10 or later with PowerShell (but scripts / tooling should be easy to
  port)
- Internet access for the first toolchain setup and Gradle dependency download.
- Python 3.10 or later for history export; the exporter uses only the standard
  library.
- A Pixel with USB debugging enabled and this computer authorized for ADB.

No system-wide Java, Gradle, or Android SDK installation is required.

## Fresh-clone build and install

```powershell
git clone YOUR_REPOSITORY_URL pixel-now-playing-archive-kit
Set-Location .\pixel-now-playing-archive-kit
.\setup-toolchain.ps1
.\build.ps1 -Variant Release
# install apk only (can also do manually)
.\install.ps1 -Serial YOUR_DESTINATION_SERIAL -SkipJsonCopy
```

If exactly one authorized Android device is connected, `-Serial` can be omitted.

The pinned toolchain consists of Microsoft OpenJDK 17.0.20.1, Gradle 9.4.1,
Android Gradle Plugin 9.2.0, Android API 37, and Build Tools 36.0.0. Downloaded
archives are checked against SHA-256 values embedded in `setup-toolchain.ps1`.

## Export all history from another Pixel

Since, without root, you cannot export from the db directly, you'll have to
extract the history from screen UI hierarchy dumps via adb. This can be done
automatically with the export history script:

1. Enable **Developer options → USB debugging** on the source Pixel.
2. Connect it by USB and approve the debugging authorization prompt.
3. Unlock it and open **Now Playing → History** in portrait orientation.
4. Run:

```powershell
.\export-history.ps1 -Serial YOUR_SOURCE_SERIAL
```

The wrapper uses the portable ADB from `.toolchain/` when available, otherwise
an `adb` on `PATH`. It prompts before automation begins, detects display size,
temporarily enables stay-awake over USB, scrolls with display-relative
coordinates, checkpoints after every page, and restores the original stay-awake
setting when finished. Do not touch or lock the phone during the scroll.

By default it creates files under `archive/`:

```text
archive/now-playing-export-YYYYMMDD-HHMMSS.json
archive/now-playing-export-YYYYMMDD-HHMMSS.csv
```

Specify a destination or maximum number of scrolls when needed:

```powershell
.\export-history.ps1 `
    -Serial YOUR_SOURCE_SERIAL `
    -Output .\archive\my-phone.json `
    -MaxScrolls 30000
```

The direct Python interface is also available:

```powershell
python .\export_now_playing.py --help
python .\export_now_playing.py `
    --serial YOUR_SOURCE_SERIAL `
    --output .\archive\my-phone.json
```

The JSON document uses `schema_version: 1` and an `entries` array. Each entry
contains the recognized local timestamp, date label, time, title, artist, raw
subtitle, and newest-first position. This is exactly the format consumed by the
app. CSV is supplementary and is not imported by the app.

## Copy and import an export

You can copy the json over and install the apk manually, or attach the
destination phone, and run:

```powershell
.\install.ps1 `
    -Serial YOUR_DESTINATION_SERIAL `
    -Json .\archive\my-phone.json
```

The JSON is copied to `Download/NowPlayingArchive/`. In the app, open the gear,
choose **Import history**, and select the file. Multiple imports are merged using
recognized timestamp, title, and artist; re-importing a file does not duplicate
existing songs, and a later favorite flag can upgrade an existing entry.

## Limitations

- The exporter reads the visible Google UI and may require selector updates if
  Google changes that UI.
- It cannot read or write Google's private database without root/system access.
- UI assets recovered from the supplied Google APK are present for this personal
  project; review licensing before distributing the app.
- The archive app uses placeholder album art and does not fetch data online.
