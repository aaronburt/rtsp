package uk.co.aaronburt.rtsp.update

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import uk.co.aaronburt.rtsp.BuildConfig

class UpdateException(message: String) : IOException(message)

object GitHubReleases {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    private val userAgent = "rtsp-android/${BuildConfig.VERSION_NAME}"

    fun latest(repo: String = BuildConfig.UPDATE_REPO): ReleaseInfo? {
        val connection = open("https://api.github.com/repos/$repo/releases/latest", "application/vnd.github+json")
        try {
            when (val code = connection.responseCode) {
                200 -> return ReleaseParser.parse(connection.inputStream.bufferedReader().use { it.readText() })
                404 -> return null
                403, 429 -> throw UpdateException("GitHub rate limit reached, try again later")
                else -> throw UpdateException("GitHub returned HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun download(release: ReleaseInfo, target: File, onProgress: (Long, Long) -> Unit) {
        val connection = open(release.apkUrl, "application/octet-stream")
        try {
            val code = connection.responseCode
            if (code != 200) throw UpdateException("Download failed with HTTP $code")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            val partial = File(target.parentFile, target.name + ".part")
            var written = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                    output.fd.sync()
                }
            }
            if (release.apkSize > 0 && written != release.apkSize) {
                partial.delete()
                throw UpdateException("Download was incomplete")
            }
            if (!partial.renameTo(target)) {
                partial.delete()
                throw UpdateException("Could not save the download")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Only https downloads are allowed" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
    }
}
