package uk.co.aaronburt.rtsp.storage

import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AesGcmCredentialCipherTest {
    private val key: SecretKey = newKey()
    private val cipher = AesGcmCredentialCipher { key }

    @Test
    fun roundTrips() {
        val sealed = cipher.encrypt("s3cret-pässword", "cam-1")
        assertEquals("s3cret-pässword", cipher.decrypt(sealed, "cam-1"))
    }

    @Test
    fun roundTripsEmptyString() {
        assertEquals("", cipher.decrypt(cipher.encrypt("", "cam-1"), "cam-1"))
    }

    @Test
    fun ciphertextDoesNotContainPlaintext() {
        val sealed = cipher.encrypt("hunter2hunter2", "cam-1")
        assertFalse(sealed.contains("hunter2"))
        assertFalse(String(Base64.getDecoder().decode(sealed), Charsets.ISO_8859_1).contains("hunter2"))
    }

    @Test
    fun sameInputGivesDifferentCiphertext() {
        assertNotEquals(cipher.encrypt("pw", "cam-1"), cipher.encrypt("pw", "cam-1"))
    }

    @Test
    fun wrongContextFails() {
        assertNull(cipher.decrypt(cipher.encrypt("pw", "cam-1"), "cam-2"))
    }

    @Test
    fun wrongKeyFails() {
        val other = AesGcmCredentialCipher { newKey() }
        assertNull(other.decrypt(cipher.encrypt("pw", "cam-1"), "cam-1"))
    }

    @Test
    fun tamperedCiphertextFails() {
        val raw = Base64.getDecoder().decode(cipher.encrypt("pw", "cam-1"))
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 1).toByte()
        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(raw), "cam-1"))
    }

    @Test
    fun garbageFailsWithoutThrowing() {
        assertNull(cipher.decrypt("not base64 !!", "cam-1"))
        assertNull(cipher.decrypt("", "cam-1"))
        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(byteArrayOf(1, 12, 0)), "cam-1"))
        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(byteArrayOf(9, 12) + ByteArray(40)), "cam-1"))
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
