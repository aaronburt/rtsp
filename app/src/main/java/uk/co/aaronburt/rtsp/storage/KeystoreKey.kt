package uk.co.aaronburt.rtsp.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeystoreKey(
    private val alias: String = DEFAULT_ALIAS,
) {
    @Volatile
    private var cached: SecretKey? = null

    fun secretKey(): SecretKey = cached ?: synchronized(this) {
        cached ?: loadOrCreate().also { cached = it }
    }

    private fun loadOrCreate(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "uk.co.aaronburt.rtsp.camera_credentials"
        private const val PROVIDER = "AndroidKeyStore"

        fun cipher(alias: String = DEFAULT_ALIAS): CredentialCipher {
            val key = KeystoreKey(alias)
            return AesGcmCredentialCipher(key::secretKey)
        }
    }
}
