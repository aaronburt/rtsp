package uk.co.aaronburt.rtsp.snapshot

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SnapshotExporter(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    suspend fun prepare(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val directory = File(appContext.cacheDir, "snapshot-exports")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create snapshot folder")
        val expiredBefore = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.name.startsWith("RTSP_snapshot_") && it.lastModified() < expiredBefore }
            ?.forEach { it.delete() }
        val file = File.createTempFile("RTSP_snapshot_", ".jpg", directory)
        try {
            file.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) throw IOException("Cannot encode snapshot")
            }
            file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    @RequiresApi(29)
    suspend fun saveToPhotos(file: File): Uri = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RTSP")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Cannot create photo")
        try {
            writeDocument(file, uri)
            val published = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            if (published <= 0) throw IOException("Cannot publish photo")
            uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    suspend fun writeDocument(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open destination")
        output.use { target -> file.inputStream().use { it.copyTo(target) } }
    }

    fun shareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Snapshot", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
