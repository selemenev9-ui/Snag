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
