# Tomato Clock

A clock app and home screen widget for the Pixel 9, built because the stock Google Clock cannot save timer presets and has no Pomodoro timer.

Native Android: Kotlin, Jetpack Compose, Material 3. Always dark, in the style of GitHub's contribution graph, with time rendered as a grid of green cells. The widget and launcher icon lean further into a Matrix look.

## Features

- **Saved timer presets.** Named timers with a short tag such as `TEA` or `EGG`. Tap a preset to select it, then press Start. Any number of timers can run at once, each with its own alarm and notification.
- **Pomodoro.** Focus, short break and long break phases with configurable lengths, rounds before a long break, and optional auto-start of the next phase.
- **Stopwatches.** As many as you like, each with laps. They keep counting when the app is closed.
- **Clock.** The current time drawn as a cell grid, plus a list of what is running and when it ends.
- **Home screen widget (4x1).** Shows only the time when nothing is running, as large as the box allows. When a timer or stopwatch is running it shows that one item; with several, the most recently started one is shown and the `<` and `>` edges page through the rest.
- **Reliable alarms.** Exact alarms so timers ring in Doze, a full-screen alert on the lock screen, and state that survives the app being killed or the phone rebooting.

## Building

Requirements: JDK 17 and the Android SDK with platform 35 and build-tools 35.0.0. Point Gradle at the SDK with `ANDROID_HOME` or a `local.properties` containing `sdk.dir=/path/to/android-sdk` (that file is git-ignored).

```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Install by copying the APK to the phone and opening it, or with `adb install -r` if a device is visible to adb.

## Project layout

```
app/src/main/kotlin/dev/jiankaichen/clock/
  data/      models and SharedPreferences-backed store
  timer/     countdown and stopwatch controllers, foreground service, alarm receivers, ringer
  widget/    the home screen widget
  ui/        Compose screens, theme, and the pixel-grid renderer
tools/       generators for the launcher icon and its preview page
```

`CLAUDE.md` describes the architecture in more depth, in particular the single-writer controller that keeps persistence, alarms, the service and the widget in sync.

## Status

Personal project, debug-signed, developed without a device attached. Tested on the owner's Pixel 9 by sideloading.

## License

MIT, see `LICENSE`.
