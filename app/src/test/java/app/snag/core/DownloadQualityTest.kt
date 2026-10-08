package app.snag.core

import com.yausername.youtubedl_android.mapper.VideoInfo
import com.yausername.youtubedl_android.mapper.VideoFormat
import org.junit.Assert.*
import org.junit.Test

class DownloadQualityTest {
    private fun field(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
    private fun format(id: String, width: Int, height: Int, bitrate: Int, audio: String = "aac") = VideoFormat().also {
        field(it, "formatId", id); field(it, "width", width); field(it, "height", height)
        field(it, "tbr", bitrate); field(it, "vcodec", if (width == 0) "none" else "h264")
        field(it, "acodec", audio); field(it, "ext", "mp4")
    }
    private fun info(vararg formats: VideoFormat) = VideoInfo().also { field(it, "formats", arrayListOf(*formats)) }
    @Test fun videoWith360OnlyDoesNotInventHigherResolutions() {
        val choices = Downloader.availableQualities(info(format("18", 640, 360, 500)))
        assertEquals(listOf(DownloadQuality("18", 640, 360)), choices)
    }
    @Test fun portraitChoicesGroupDuplicatesAndAttachAvailableAudio() {
        val choices = Downloader.availableQualities(info(format("low", 720, 1280, 100, "none"),
            format("high", 720, 1280, 500, "none"), format("audio", 0, 0, 100)))
        assertEquals(1, choices.size)
        assertEquals("high+ba", choices.single().selector)
        assertEquals("720p · 720×1280", choices.single().label)
    }
    @Test fun unknownDimensionsDoNotInventResolutionAndAudioOnlyIsNotVideo() {
        assertTrue(Downloader.availableQualities(info(format("unknown", 0, 0, 0))).isEmpty())
    }
}
