# Snag 0.1.6-beta.1 — updates inside the app

- New dismissible update card on the home screen in Paper and Classic.
- Download updates inside Snag with progress, cancellation and retry; the Android download service keeps the transfer running in the background.
- Verify SHA-256, package, newer version and matching signing certificate before installation.
- Install directly from Snag. Android may require one-time permission to install updates. On Android 12+, self-updates request installation without an extra confirmation; the system can still require confirmation.
- Installation waits until current video work finishes. History, settings and saved media are retained.

Existing 0.1.4/0.1.5 builds need their usual browser download once to receive this updater. Subsequent versions use the new in-app flow. Full APK downloads are still required; no delta-update mechanism is claimed.

**Русский:** обновление теперь видно на главном экране. APK скачивается прямо в Snag с прогрессом и отменой, проверяется перед установкой. Разрешение на обновления выдаётся один раз; при необходимости Android покажет системное подтверждение. Удалять приложение не нужно. Для перехода со старой версии на этот выпуск браузер понадобится последний раз.

---

# Snag 0.1.5-beta.1 — dismissible undo message

Fixed the “Card removed · Undo” message staying on screen indefinitely. It now disappears automatically and has a close button. Undo still restores the latest removed card. Android accessibility timeout preferences remain respected.

Update the same edition over your existing installation; do not uninstall first. History and appearance settings are preserved. Both Paper and Classic are available below.

**Русский:** исправлена зависающая плашка «Карточка удалена — Отменить». Теперь она исчезает автоматически, а также закрывается крестиком. Отмена удаления работает как прежде. Для обновления скачайте ту же версию оформления и установите поверх приложения, без удаления.

---

# Snag 0.1.4-beta.1

Two working Android editions to compare: **Paper**, the new design, and **Classic**, the earlier design. Both support English and Russian and can be installed together.

- Paper: warm light and dark palettes, clearer file-size hierarchy, focused ready screen.
- Language and appearance controls in Settings; system defaults are supported.
- Telegram video stickers automatically select a window up to 3 seconds, movable along the timeline and adjustable to shorter lengths.
- A daily GitHub update check plus a manual check in Settings. Downloads are selected for the installed edition; installation remains explicit.
- Local video compression, clip/GIF/audio/static sticker export, save-copy and share workflows are retained.
- The yt-dlp engine continues to update separately.

**Requirements:** Android 10+ / ARM64. APKs are beta builds, signed for continuity with the existing test editions. Install the same edition over a previous copy to preserve its history. Current video-service availability can vary; very small target sizes can reduce detail.

Temporary prepared files are kept in the app's cache. Use **Save copy** to keep them on your phone. Your original is not overwritten. Static Telegram stickers can use pack import; animated WebM stickers remain a separate export and are not silently converted to static stickers.

Please test the same short video in both editions and [report feedback](https://github.com/selemenev9-ui/Snag/issues). English/Russian, light/dark, real preparation/save, sticker selection and update checks are covered by the release verification documented in RELEASE_VERIFICATION.md. SHA256SUMS identifies the uploaded APKs.

---

Paper — новое оформление; Classic — прежнее, для сравнения. В обоих есть русский/английский и выбор темы. Установка рядом; обновления поверх той же версии оформления сохраняют её историю. Видеостикер теперь выбирает окно до 3 секунд автоматически — двигайте его по шкале и сокращайте длину. Для сохранения готового файла нажмите «Сохранить копию».
