package uk.co.aaronburt.rtsp.storage

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialCipher {
    fun encrypt(plain: String, context: String): String

    fun decrypt(encoded: String, context: String): String?
}

class AesGcmCredentialCipher(
    private val key: () -> SecretKey,
) : CredentialCipher {
    override fun encrypt(plain: String, context: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val out = ByteArray(2 + iv.size + sealed.size)
        out[0] = VERSION
        out[1] = iv.size.toByte()
        iv.copyInto(out, 2)
        sealed.copyInto(out, 2 + iv.size)
        return Base64.getEncoder().encodeToString(out)
    }

    override fun decrypt(encoded: String, context: String): String? = try {
        val raw = Base64.getDecoder().decode(encoded)
        if (raw.size < 2 || raw[0] != VERSION) {
            null
        } else {
            val ivSize = raw[1].toInt() and 0xFF
            if (raw.size < 2 + ivSize + TAG_BYTES) {
                null
            } else {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BYTES * 8, raw, 2, ivSize))
                cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
                String(cipher.doFinal(raw, 2 + ivSize, raw.size - 2 - ivSize), Charsets.UTF_8)
            }
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val TAG_BYTES = 16
    }
}
