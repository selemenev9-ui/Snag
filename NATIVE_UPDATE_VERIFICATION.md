# Native app updates — 0.1.6-beta.1

9 October 2026. Scope: home update card and native APK download/install in both editions. Video processing is unchanged.

Implementation:
- Android DownloadManager owns the transfer and persists its ID; a process restart restores progress. Cancellation removes only Snag's own APK download.
- Before staging an APK: SHA-256 must match the release manifest, package must match the current edition, version must match the expected newer version, and APK signing certificates must match the installed app.
- PackageInstaller requests self-update without additional user action on API 31+, with a visible activity for the initial unknown-source permission and any system-required confirmation. Android 10/11 use system confirmation. Installation is blocked while known video processing/download/save work is active.
- The home card can be dismissed for that version; the update remains available in Settings.

Build checks: both final APKs built at versionCode 8, versionName 0.1.6-beta.1. 42 JVM tests passed. Final Classic lint succeeded with zero errors (43 warnings, 8 hints). The first lint run crashed internally while a source file was being changed; the subsequent complete run succeeded. Both APK certificate SHA-256 values match the existing beta: 4c2422dce24783a2b5e001d8508f6539e3410a5feef2578f225deb02863c73fa.

The internal Classic versionCode 7 build is used only as the updater baseline for the code 7 -> 8 self-update check; it is not a release asset. Its native download/install implementation is identical to the final version. The final version also refreshes old cached manifests lacking SHA-256 and localizes the installer activity on Android 10–12.

OnePlus NE2213 is the physical validation device. Android 10/11 and other vendors are not claimed as physically tested. Full APK downloads are used; no delta update or universal silent-install guarantee.

## Completed device verification

- Classic code 7 discovered the live code 8 manifest. The home card displayed the update. Download was started, cancelled, restarted, and restored after force-stopping/reopening Snag. The real GitHub APK reached 95%, then the verified/ready state.
- Tapped Install update inside Snag, followed the actual Android unknown-source permission screen and enabled installation for Snag Classic. Returning to Snag initiated PackageInstaller; no browser or external APK opening was used. The installed app is now code 8 / 0.1.6-beta.1.
- Classic history (10 records) and appearance settings remain byte-identical to the pre-update snapshot.
- Paper was then installed over its older version with adb install -r, preserving its history and appearance files byte-for-byte. This Paper bootstrap install is not claimed as a second native self-update test.
- Both installed APK SHA-256 values match the published release assets. Evidence: build/review-verification/native-update/final-verification.json and 01-home-update.png, 02-download-restored.png, 03-verified-ready.png. Private history snapshots were not published.

Known polish follow-up: the older Settings helper text still says Android will ask for confirmation; the new installation activity correctly says it may ask. Installation behavior is as documented above.
