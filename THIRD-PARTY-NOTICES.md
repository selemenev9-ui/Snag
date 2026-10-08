# Snag — third-party components

Snag follows the parent project's GPL-3.0-or-later license. Third-party components retain their own licenses and notices.

| Component | Pinned version | Upstream source / notices |
|---|---|---|
| youtubedl-android library and FFmpeg runtime | 0.18.1 | https://github.com/yausername/youtubedl-android/tree/0.18.1 · GPL-3.0 |
| Native runtime build instructions | upstream 0.18.1 | https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_FFMPEG.md |
| FFmpeg and associated codecs | from the pinned upstream runtime AAR | https://ffmpeg.org/legal.html · https://github.com/FFmpeg/FFmpeg |
| x264, used by the FFmpeg runtime | from the pinned upstream runtime | https://code.videolan.org/videolan/x264 |
| yt-dlp bundled executable | 2026.08.19 | https://github.com/yt-dlp/yt-dlp/releases/tag/2026.08.19 · Unlicense and bundled third-party notices |
| yt-dlp third-party notices | matching executable version | https://github.com/yt-dlp/yt-dlp/blob/2026.08.19/THIRD_PARTY_LICENSES.txt |
| AndroidX, Jetpack Compose and Media3 | see gradle/libs.versions.toml | https://android.googlesource.com/platform/frameworks/support · Apache-2.0 |
| Kotlin and kotlinx.coroutines | see version catalog | https://github.com/JetBrains/kotlin · https://github.com/Kotlin/kotlinx.coroutines · Apache-2.0 |
| Coil | 3.6.3 | https://github.com/coil-kt/coil · Apache-2.0 |

Snag consumes the pinned upstream Android AARs rather than building the bundled native runtimes locally. Their build recipes, codec licenses and version-specific notices remain relevant. The desktop FFmpeg binary and its Windows provenance are not part of this Android release.

The download engine can be replaced by an official yt-dlp update. The licenses and notices belonging to the installed engine version continue to apply. The bundled yt-dlp file is under app/src/main/assets/yt-dlp; no signing material or private media is included in the source repository.
