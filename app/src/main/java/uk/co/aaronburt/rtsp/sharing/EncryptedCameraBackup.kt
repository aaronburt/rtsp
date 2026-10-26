package uk.co.aaronburt.rtsp.sharing

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
import uk.co.aaronburt.rtsp.data.Camera
import uk.co.aaronburt.rtsp.data.Transport

private const val FRAME = "RTSPBACKUP1"
private val MAGIC = byteArrayOf(0x52, 0x54, 0x53, 0x50, 0x42, 0x4b, 0x50, 0x31)
private const val KDF_ITERATIONS = 600_000
private const val SALT_BYTES = 16
private const val NONCE_BYTES = 12
private const val TAG_BITS = 128
private const val CHUNK_CHARS = 850
private const val MAX_PARTS = 256
private const val MAX_ARCHIVE_BYTES = 220_000
private const val MAX_JSON_BYTES = 1_048_576
private const val MAX_CAMERAS = 500
private const val MAX_PASSWORD_CHARS = 1_024

/** Camera configuration backups are compressed, then encrypted before any QR text is created. */
object EncryptedCameraBackup {
    fun encode(cameras: List<Camera>, password: CharArray): List<String> {
        require(cameras.size in 1..MAX_CAMERAS)
        require(password.isNotEmpty() && password.size <= MAX_PASSWORD_CHARS)
        val json = encodeJson(cameras).toByteArray(Charsets.UTF_8)
        require(json.size <= MAX_JSON_BYTES)
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(json) }
        }.toByteArray()
        json.fill(0)

        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt)
        val encrypted = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(MAGIC)
                doFinal(compressed)
            }
        } finally {
            key.fill(0)
            compressed.fill(0)
        }
        val envelope = ByteBuffer.allocate(MAGIC.size + 4 + SALT_BYTES + NONCE_BYTES + encrypted.size)
            .put(MAGIC)
            .putInt(KDF_ITERATIONS)
            .put(salt)
            .put(nonce)
            .put(encrypted)
            .array()
        salt.fill(0)
        nonce.fill(0)
        encrypted.fill(0)
        require(envelope.size <= MAX_ARCHIVE_BYTES)

        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(envelope)
        envelope.fill(0)
        val id = digest(encoded)
        val chunks = encoded.chunked(CHUNK_CHARS)
        require(chunks.size in 1..MAX_PARTS)
        return chunks.mapIndexed { index, chunk -> "$FRAME:$id:${index + 1}:${chunks.size}:$chunk" }
    }

    fun decode(qrPages: List<String>, password: CharArray): List<Camera> {
        require(password.isNotEmpty() && password.size <= MAX_PASSWORD_CHARS)
        require(qrPages.isNotEmpty() && qrPages.size <= MAX_PARTS)
        val pages = linkedMapOf<Int, String>()
        var transferId: String? = null
        var total: Int? = null
        for (page in qrPages) {
            require(page.length <= CHUNK_CHARS + 80)
            val fields = page.split(':', limit = 5)
            require(fields.size == 5 && fields[0] == FRAME)
            val id = fields[1].also { require(it.matches(Regex("[a-f0-9]{32}"))) }
            val index = fields[2].toInt()
            val count = fields[3].toInt()
            require(count in 1..MAX_PARTS && index in 1..count)
            val chunk = fields[4]
            require(chunk.isNotEmpty() && chunk.length <= CHUNK_CHARS && chunk.matches(Regex("[A-Za-z0-9_-]+")))
            require(transferId == null || transferId == id)
            require(total == null || total == count)
            require(pages[index] == null || pages[index] == chunk)
            transferId = id
            total = count
            pages[index] = chunk
        }
        val count = total ?: error("No backup pages selected")
        require(pages.size == count && (1..count).all(pages::containsKey)) { "Select every QR image from this backup" }
        val encoded = (1..count).joinToString("") { pages.getValue(it) }
        require(digest(encoded) == transferId)
        val envelope = Base64.getUrlDecoder().decode(encoded)
        require(envelope.size in (MAGIC.size + 4 + SALT_BYTES + NONCE_BYTES + 16)..MAX_ARCHIVE_BYTES)
        val input = ByteBuffer.wrap(envelope)
        val magic = ByteArray(MAGIC.size).also { input.get(it) }
        require(MessageDigest.isEqual(magic, MAGIC))
        require(input.int == KDF_ITERATIONS)
        val salt = ByteArray(SALT_BYTES).also { input.get(it) }
        val nonce = ByteArray(NONCE_BYTES).also { input.get(it) }
        val encrypted = ByteArray(input.remaining()).also { input.get(it) }
        val key = deriveKey(password, salt)
        val compressed = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(MAGIC)
                doFinal(encrypted)
            }
        } finally {
            key.fill(0)
            salt.fill(0)
            nonce.fill(0)
            encrypted.fill(0)
            envelope.fill(0)
        }
        val jsonBytes = try {
            GZIPInputStream(ByteArrayInputStream(compressed)).use { inputStream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= MAX_JSON_BYTES)
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally {
            compressed.fill(0)
        }
        return try {
            decodeJson(jsonBytes.toString(Charsets.UTF_8))
        } finally {
            jsonBytes.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, KDF_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encodeJson(cameras: List<Camera>): String {
        val entries = JSONArray()
        cameras.forEach { camera ->
            entries.put(JSONArray().apply {
                put(camera.name)
                put(camera.url)
                put(camera.subUrl ?: JSONObject.NULL)
                put(camera.username)
                put(camera.password)
                put(camera.transport.name)
                put(camera.latencyMs)
                put(camera.autoReconnect)
                put(camera.startMuted)
                put(camera.favourite)
            })
        }
        return JSONObject().put("version", 1).put("cameras", entries).toString()
    }

    private fun decodeJson(text: String): List<Camera> {
        val root = JSONObject(text)
        require(root.getInt("version") == 1)
        val rows = root.getJSONArray("cameras")
        require(rows.length() in 1..MAX_CAMERAS)
        return List(rows.length()) { index ->
            val row = rows.getJSONArray(index)
            require(row.length() == 10)
            fun stringAt(i: Int, max: Int): String = (row.get(i) as? String)
                ?.also { require(it.length <= max) } ?: error("Invalid backup")
            fun flagAt(i: Int) = row.get(i) as? Boolean ?: error("Invalid backup")
            fun rtspUrl(value: String): String {
                val uri = java.net.URI(value)
                require(uri.scheme.equals("rtsp", ignoreCase = true) && !uri.rawAuthority.isNullOrBlank())
                return value
            }
            val name = stringAt(0, 512)
            require(name.isNotBlank())
            val latency = row.get(6)
            require(latency is Int && latency in 0..60_000)
            Camera(
                id = "",
                name = name,
                url = rtspUrl(stringAt(1, 8_192)),
                subUrl = if (row.isNull(2)) null else rtspUrl(stringAt(2, 8_192)),
                username = stringAt(3, 4_096),
                password = stringAt(4, 4_096),
                transport = Transport.valueOf(stringAt(5, 16)),
                latencyMs = latency,
                autoReconnect = flagAt(7),
                startMuted = flagAt(8),
                favourite = flagAt(9),
            )
        }
    }

    private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.US_ASCII))
        .take(16)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

object CameraQrImages {
    fun matrix(value: String, size: Int = 1_000) = QRCodeWriter().encode(
        value,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q, EncodeHintType.MARGIN to 4),
    )
}
