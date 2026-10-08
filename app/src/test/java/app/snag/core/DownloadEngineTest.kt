package app.snag.core

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class DownloadEngineTest {
    @Test fun readsVersionFromExecutableZipappAndRejectsTruncatedFiles() {
        val file = File.createTempFile("snag-zipapp", ".zip")
        try {
            file.outputStream().use { out ->
                out.write("#!/usr/bin/env python3\n".toByteArray())
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("yt_dlp/version.py"))
                    zip.write("__version__ = '2026.08.19'\n".toByteArray())
                    zip.closeEntry()
                }
            }
            assertEquals("2026.08.19", DownloadEngine.version(file))
            file.writeBytes(byteArrayOf(0x50, 0x4b, 3))
            assertNull(DownloadEngine.version(file))
        } finally { file.delete() }
    }
}
