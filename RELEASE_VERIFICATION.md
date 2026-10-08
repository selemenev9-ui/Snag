# Verification — 0.1.4-beta.1

Device: OnePlus 10 Pro 5G, NE2213, Android 16 / API 36. Date: 8 October 2026.

- **42 JVM unit tests passed**, zero failures. Includes newer-version / correct-edition update selection, rejection of untrusted download URLs, and bounded sticker-window movement, shortening and playback-speed cases.
- Final lint completed successfully with **zero errors**; existing warnings/hints remain.
- **17 targeted Android tests passed** on Paper before the last accessibility-label and browser-routing polish: real processing/cancellation/save flows, custom-size forecasts, actual resolution choices, sticker-window controls, native language/theme switching and the published update manifest.
- After making the sticker descriptions configuration-aware, **3 targeted tests passed on each edition**.
- On the final APKs after adding explicit browser routing, **3 native tests passed on each edition**: English/Russian with light/dark, manifest fetch from the phone, and resolution of release links to a browser/chooser rather than Snag.
- Paper's real prepare/save path was checked with a measured output, a readable saved URI and an unchanged SHA-256 of the source. Its test copy and owned records were removed afterwards.
- Classic's initial beta installation used `install -r`; its history file remained byte-for-byte identical, preserving 10 existing records. Subsequent installs used the same package and certificate.
- Current APK versionCode: **5**. Beta package IDs: `app.snag.paper.debug` and `app.snag.debug`.
- Both APKs have the same signing-certificate SHA-256 as the previously installed 0.1.3 beta: `4c2422dce24783a2b5e001d8508f6539e3410a5feef2578f225deb02863c73fa`. Private signing material is not published.

The complete old UI suite was not repeatedly rerun for visual-only adjustments. Native font scaling at 150% was not rerun in this release; the custom-size test's Compose density check is not claimed as a physical system-font test. ARM64/API 36 is the observed device configuration; other Android 10+ ARM64 devices need beta feedback.

Video-service coverage carried forward from the verified 0.1.3 downloader fixes and the user's real YouTube/TikTok/Instagram checks. Those external services were not all redownloaded for this UI release. No blanket claim is made that every link or quality is available.

The APK hash for each uploaded file is in SHA256SUMS. App updates are user-confirmed downloads; the on-device check does not silently install an APK. This is a beta, not a store certification or a claim of finished design.
