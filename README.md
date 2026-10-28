# GRID Prefs Editor

Android app for editing the GRID Autosport (Feral) `preferences` file with proper controls
instead of hand-editing text: dropdowns for Ultra low / Low / Medium / High / Ultra high,
on/off switches, an FPS picker, MSAA picker, and a resolution slider.

## Get the APK (no Android Studio needed)
1. Create a new GitHub repo and upload everything in this folder (keep `.github/`).
2. Open the repo's **Actions** tab -> **Build APK** -> wait ~3-5 min.
3. Download the `GridPrefsEditor-debug-apk` artifact, unzip, install `app-debug.apk`.

## Or build locally
Open this folder in Android Studio (Hedgehog or newer) and press Run, or run `gradle assembleDebug`
with JDK 17 and the Android SDK installed. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Using it
- **Open** your `preferences` file (system file picker). An untouched backup is stored inside the app each time.
- Edit on the **Graphics / Resolution / Extras / All values** tabs. Changes are in memory until you tap **Save**.
- Android 11+ hides `Android/data`; copy the file somewhere visible (Downloads) with a file manager, edit it here,
  then copy it back over the game's file while the game is closed.
- Only the lines you change are rewritten; everything else (including binary blobs) stays byte-identical.

## Tabs
- **Graphics**: profile (Main / High / Power saver), quick presets, all `gfxconfig_*` levels, switches, MSAA.
- **Resolution**: `window_scale` slider (percent of native), FPS caps, dynamic-resolution target and minimum.
- **Extras**: on/off tweaks stored as plain 0/1 flags.
- **All values**: every setting in the file with a name filter; ints, decimals and text are editable, binary blobs are not.

## Parser test
`javac -d out app/src/main/java/app/gridprefs/core/*.java tools/SelfTest.java && java -cp out SelfTest samples/preferences_ULTRA`
