# 0.1.5-beta.1 — 9 October 2026

Scope: fix the removal snackbar lifetime; preserve Undo and allow manual dismissal. MainScreen now passes SnackbarDuration.Long and withDismissAction=true. Material's accessibility timeout adjustment is retained. No media pipeline changes.

- Both APKs built, versionCode 6; certificate SHA-256 remains 4c2422dce24783a2b5e001d8508f6539e3410a5feef2578f225deb02863c73fa.
- 42 JVM tests, zero failures/errors in the current testDebugUnitTest XML reports.
- Paper assembleDebug + lintDebug: BUILD SUCCESSFUL in 2m 29s. Existing lint warnings remain; zero errors.
- OnePlus NE2213, Android 16: UndoSnackbarTest passed in 10.259 seconds against the new Classic APK. It checks the real MainScreen timeout, snackbar disappearance, and restoring only the latest of two sequential removals. Test creates/removes only owned in-memory cards, never user media.
- Classic installed with adb install -r over 0.1.4-beta.1: history.json (10 records) and appearance.xml byte-identical before/after update and after the UI test. Private snapshots remain only in local build/review-verification/undo-upgrade.
- Paper is deliberately left on 0.1.4-beta.1 so the user can exercise the normal Settings -> Check for updates -> Download -> Android install flow. ADB installation is not claimed as validation of that complete browser/installer flow.

Artifacts: dist/beta-0.1.5, build/review-verification/undo-build.txt, undo-paper-build.txt, undo-upgrade/result.json. Instrumentation invocation: adb shell am instrument -w -e class app.snag.core.UndoSnackbarTest app.snag.debug.test/androidx.test.runner.AndroidJUnitRunner.

Publication verified: both public APK URLs return ZIP/APK data and GitHub asset digests match local SHA-256. The live update manifest was activated afterwards. On the still-installed Paper 0.1.4-beta.1, tapping the actual Settings -> Check for updates button displays “A new version is available”, “0.1.5-beta.1”, and “Open update download”. Its stored remote versionCode is 6. Screenshot/XML: build/review-verification/undo-upgrade/paper-update-available.*. Left this screen open; the user will complete the browser download and Android installer step.
