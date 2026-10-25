package uk.co.aaronburt.rtsp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertFingerprintTest {
    @Test
    fun sha256IsLowercaseHex() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            CertFingerprint.sha256("abc".toByteArray()),
        )
    }

    @Test
    fun identicalSignerSetsMatch() {
        assertTrue(CertFingerprint.sameSigners(setOf("a", "b"), setOf("b", "a")))
    }

    @Test
    fun differentOrEmptySignerSetsDoNotMatch() {
        assertFalse(CertFingerprint.sameSigners(setOf("a"), setOf("b")))
        assertFalse(CertFingerprint.sameSigners(setOf("a"), setOf("a", "b")))
        assertFalse(CertFingerprint.sameSigners(emptySet(), emptySet()))
    }
}
