# Snag · Android beta

Make a video easier to send. Download a supported link, keep a moment, compress a large file, or make a GIF, audio clip or Telegram sticker — on your phone.

## Download

**Android 10 or later · ARM64 phones · English and Russian in each APK.**

- **[Snag Paper](https://github.com/selemenev9-ui/Snag/releases/download/v0.1.6-beta.1/Snag-Paper.apk)** — the new design, with warm light and dark themes.
- **[Snag Classic](https://github.com/selemenev9-ui/Snag/releases/download/v0.1.6-beta.1/Snag-Classic.apk)** — the earlier design, for comparison.
- [All releases and checksums](https://github.com/selemenev9-ui/Snag/releases)

Both editions can be installed together. They have separate private data and history. Saved copies go to the phone's Download/Snag folder. Install the same edition over its previous version to keep its history.

Download an APK, open it on your Android phone and approve installation from your browser or file manager when Android asks. This beta is not a Google Play release.

## Try it

1. Choose a short video from your phone, or share/paste a supported public video link.
2. Open the studio. Keep the full video or choose a moment.
3. Pick a format and, for video, a target size. The source size and a compression forecast help you decide; small targets can reduce detail.
4. Prepare, preview the actual result, then share it or **save a copy**.

Temporary results are stored in Snag's private cache. Save a copy if you want to keep one; Android may remove cached files. Your original is not overwritten.

For a Telegram video sticker, Snag automatically selects a window of at most 3 seconds. Move it along the timeline or shorten it. Static stickers select one frame and can open Telegram's import dialog. A video sticker is exported as WebM; importing an animated sticker into a pack still requires Telegram's supported workflow. Snag does not silently turn an animated sticker into a static image.

## Settings and updates

Tap the gear on the home screen to select **English / Русский / system language** and **light / dark / system appearance**.

Snag checks for updates once a day when opened, or manually in Settings. A new version appears on the home screen. Download it inside Snag, then tap Install update. The APK is checked for integrity, edition, version and signing certificate. Android may require one-time permission and installation confirmation; on Android 12+ Snag requests self-update without an extra confirmation when the system allows it. History and settings are retained. Older 0.1.4/0.1.5 versions need their browser download once to receive this updater. Full APK downloads are still required.

The yt-dlp download engine updates separately. It checks for updates when Snag is opened and can recover from certain extractor errors. Website changes, authentication requirements and server restrictions can still affect individual downloads.

## Help test

Please compare Paper and Classic with the **same video and the same settings**. Which feels clearer? Is Save copy easy to find? Does large system text fit? Does a vertical video keep its proportions? If something fails, include the edition, version, Android model/version, steps and the exact error. Share a public sample link only if appropriate; do not post private videos or credentials.

[Report a bug](https://github.com/selemenev9-ui/Snag/issues/new/choose)

## Privacy and beta limits

- No account, analytics SDK or media-upload backend. Processing is local.
- Network requests go to video services, metadata/SponsorBlock providers where used, and GitHub for updates. Offline local editing needs no video-service account.
- Android 10+ and ARM64 only in this beta. Large, long jobs can take time and heat the phone.
- No guarantee that every service, private video or resolution is downloadable.
- A small file-size limit is not a promise of lossless quality. Forecasts are estimates; the actual prepared file is measured.
- Telegram pack import currently applies to static stickers; video and static modes are separate.
- Beta APKs retain the existing development signing certificate for continuity with the installed test versions. They are intended for testing.

## Build and release

JDK 17, Android SDK platform 37, Android build tools and the included Gradle wrapper are required.

```sh
./gradlew :app:assembleDebug -PsnagDesign=paper
./gradlew :app:assembleDebug -PsnagDesign=classic
./gradlew :app:testDebugUnitTest :app:lintDebug -PsnagDesign=paper
```

The comparison editions preserve the beta package IDs `app.snag.paper.debug` and `app.snag.debug`. Raise `versionCode` for every distributed update. Retain the original signing key privately: a different developer's default debug key cannot update installed copies. Signing material and local Android paths are intentionally absent from this repository. **Do not distribute a newly signed build as an update to this beta.**

For the next release: build both editions with the preserved key, check them on a device, upload both APKs and SHA256SUMS to a GitHub prerelease, then update `update.json` to point to that release. The manifest is updated after assets are available. Keep asset names `Snag-Paper.apk` and `Snag-Classic.apk` stable. GitHub's `latest` redirect can omit prereleases; use the explicit release page and the manifest.

## Source and third-party software

GPL-3.0-or-later, following the parent project. See [LICENSE](LICENSE) and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md). This repository contains the Android application source, pinned dependency versions and build configuration. It does not contain private test media, signing keys, local build caches or the desktop application.

---

## По-русски

**Snag Paper** — новый дизайн со светлой и тёмной темами. **Snag Classic** — прежнее оформление для сравнения. В каждом APK есть русский и английский; язык и тема выбираются в настройках по значку шестерёнки.

Обе версии устанавливаются рядом. Для обновления выбирайте ту же версию оформления и устанавливайте поверх: история сохранится. Не удаляйте приложение перед обновлением. Проверка новых выпусков — в настройках; движок yt-dlp обновляется отдельно.

Попробуйте один ролик в обеих версиях: выбор видео → студия → размер/момент → подготовка → просмотр → сохранение или отправка. Готовый файл сначала временный: нажмите «Сохранить копию», чтобы оставить его на телефоне. Исходник не перезаписывается.

Для видеостикера выбирается окно до 3 секунд: его можно перемещать и сокращать. Статичный стикер и видеостикер — разные результаты. Статичный можно импортировать в Telegram; WebM-видеостикер сохраняется/отправляется отдельно.

Требования беты: Android 10+, ARM64. Ошибки, шаги воспроизведения и мнение о двух вариантах дизайна можно оставить в [Issues](https://github.com/selemenev9-ui/Snag/issues).
