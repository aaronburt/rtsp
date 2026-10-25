package uk.co.aaronburt.rtsp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {
    private fun release(
        tag: String = "v0.2.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """[{"name":"rtsp-v0.2.0.apk","size":1234,"browser_download_url":"https://github.com/aaronburt/rtsp/releases/download/v0.2.0/rtsp-v0.2.0.apk"}]""",
        body: String = "## What's Changed\n* Add updater by @aaronburt in https://github.com/x/y/pull/1\n\n**Full Changelog**: https://github.com/x/y/compare/v0.1.0...v0.2.0",
    ) = """{"tag_name":"$tag","name":"$tag","draft":$draft,"prerelease":$prerelease,"html_url":"https://github.com/aaronburt/rtsp/releases/tag/$tag","body":${org.json.JSONObject.quote(body)},"assets":$assets}"""

    @Test
    fun parsesApkAssetAndNotes() {
        val parsed = ReleaseParser.parse(release())
        assertNotNull(parsed)
        parsed!!
        assertEquals("v0.2.0", parsed.tag)
        assertEquals("rtsp-v0.2.0.apk", parsed.apkName)
        assertEquals(1234L, parsed.apkSize)
        assertTrue(parsed.apkUrl.startsWith("https://"))
        assertEquals("What's Changed\n• Add updater by @aaronburt in https://github.com/x/y/pull/1", parsed.notes)
    }

    @Test
    fun ignoresDraftsAndPrereleases() {
        assertNull(ReleaseParser.parse(release(draft = true)))
        assertNull(ReleaseParser.parse(release(prerelease = true)))
    }

    @Test
    fun rejectsReleasesWithoutUsableApk() {
        assertNull(ReleaseParser.parse(release(assets = "[]")))
        assertNull(ReleaseParser.parse(release(assets = """[{"name":"notes.txt","size":1,"browser_download_url":"https://x/notes.txt"}]""")))
        assertNull(ReleaseParser.parse(release(assets = """[{"name":"a.apk","size":1,"browser_download_url":"http://x/a.apk"}]""")))
    }

    @Test
    fun rejectsBadTagsAndGarbage() {
        assertNull(ReleaseParser.parse(release(tag = "nightly")))
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("{}"))
    }

    @Test
    fun picksFirstApkWhenSeveralAssets() {
        val assets = """[{"name":"checksums.txt","size":1,"browser_download_url":"https://x/c.txt"},{"name":"b.apk","size":9,"browser_download_url":"https://x/b.apk"},{"name":"c.apk","size":8,"browser_download_url":"https://x/c.apk"}]"""
        assertEquals("b.apk", ReleaseParser.parse(release(assets = assets))!!.apkName)
    }

    @Test
    fun notesStripMarkdownNoise() {
        val plain = ReleaseNotes.plain("# Title\n\n\n\n- one\n* two\n**Full Changelog**: x")
        assertEquals("Title\n\n• one\n• two", plain)
        assertFalse(plain.contains("Full Changelog"))
    }
}
