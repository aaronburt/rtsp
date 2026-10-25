package uk.co.aaronburt.rtsp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun parsesPlainAndPrefixedVersions() {
        assertEquals(Version(1, 2, 3), Version.parse("1.2.3"))
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(0, 10, 0, "rc.1"), Version.parse("v0.10.0-rc.1"))
        assertEquals(Version(1, 0, 0), Version.parse("1.0.0+build5"))
    }

    @Test
    fun rejectsMalformedVersions() {
        assertNull(Version.parse(""))
        assertNull(Version.parse("latest"))
        assertNull(Version.parse("1.2"))
        assertNull(Version.parse("v1.2.3.4"))
        assertNull(Version.parse("99999999999.0.0"))
    }

    @Test
    fun ordersNumericallyNotLexically() {
        assertTrue(Version.parse("0.10.0")!! > Version.parse("0.9.0")!!)
        assertTrue(Version.parse("1.0.0")!! > Version.parse("0.99.99")!!)
        assertTrue(Version.parse("0.1.10")!! > Version.parse("0.1.9")!!)
    }

    @Test
    fun preReleaseSortsBelowRelease() {
        assertTrue(Version.parse("1.0.0")!! > Version.parse("1.0.0-rc.1")!!)
        assertTrue(Version.parse("1.0.0-rc.2")!! > Version.parse("1.0.0-rc.1")!!)
        assertTrue(Version.parse("1.0.0-rc.10")!! > Version.parse("1.0.0-rc.9")!!)
        assertTrue(Version.parse("1.0.0-rc.1")!! > Version.parse("1.0.0-beta")!!)
    }

    @Test
    fun isNewerOnlyForStrictlyHigherValidTags() {
        assertTrue(UpdateDecision.isNewer("v0.1.1", "0.1.0"))
        assertFalse(UpdateDecision.isNewer("v0.1.0", "0.1.0"))
        assertFalse(UpdateDecision.isNewer("v0.0.9", "0.1.0"))
        assertFalse(UpdateDecision.isNewer("nightly", "0.1.0"))
        assertTrue(UpdateDecision.isNewer("v0.1.0", "0.1.0-dev"))
    }

    @Test
    fun autoCheckIsThrottled() {
        val interval = 1_000L
        assertTrue(UpdateDecision.shouldAutoCheck(0L, 5_000L, interval))
        assertFalse(UpdateDecision.shouldAutoCheck(4_500L, 5_000L, interval))
        assertTrue(UpdateDecision.shouldAutoCheck(4_000L, 5_000L, interval))
        assertTrue(UpdateDecision.shouldAutoCheck(9_000L, 5_000L, interval))
    }
}
