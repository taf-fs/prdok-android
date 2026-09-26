package io.tafdev.prdok.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AppReleaseTest {

    @Test
    fun `tags map to the same version codes as the build script`() {
        assertEquals(AppVersion("1.0.0", 10_000), AppVersion.fromTag("v1.0.0"))
        assertEquals(AppVersion("1.2.3", 10_203), AppVersion.fromTag("v1.2.3"))
        assertEquals(AppVersion("2.10.0", 21_000), AppVersion.fromTag("2.10.0"))
    }

    @Test
    fun `tags that aren't release versions are ignored`() {
        assertNull(AppVersion.fromTag("v1.2"))
        assertNull(AppVersion.fromTag("v1.2.3-beta"))
        assertNull(AppVersion.fromTag("v1.100.0"))
        assertNull(AppVersion.fromTag("latest"))
    }

    @Test
    fun `parses the tag, the page and the attached apk`() {
        val release = ReleaseParser.parse(
            """
            {
              "tag_name": "v1.1.0",
              "html_url": "https://github.com/o/r/releases/tag/v1.1.0",
              "assets": [
                { "name": "notes.txt", "browser_download_url": "https://github.com/o/r/notes.txt" },
                { "name": "prdok-1.1.0.apk", "browser_download_url": "https://github.com/o/r/prdok-1.1.0.apk" }
              ]
            }
            """,
        )

        assertEquals(
            AppRelease(
                AppVersion("1.1.0", 10_100),
                pageUrl = "https://github.com/o/r/releases/tag/v1.1.0",
                apkUrl = "https://github.com/o/r/prdok-1.1.0.apk",
            ),
            release,
        )
    }

    @Test
    fun `without an apk the download falls back to the release page`() {
        val release = ReleaseParser.parse("""{"tag_name": "v1.1.0", "html_url": "https://page", "assets": []}""")
        assertEquals("https://page", release?.downloadUrl)
    }

    @Test
    fun `a release with an odd tag reads as none`() {
        assertNull(ReleaseParser.parse("""{"tag_name": "nightly", "html_url": "https://page"}"""))
    }

    @Test
    fun `a body that isn't a json object fails`() {
        assertThrows(IllegalArgumentException::class.java) { ReleaseParser.parse("<html>") }
        assertThrows(IllegalArgumentException::class.java) { ReleaseParser.parse("[]") }
    }
}
