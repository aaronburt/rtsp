package uk.co.aaronburt.rtsp.sharing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.UUID

object CameraBackupImages {
    fun qrBitmap(value: String): Bitmap {
        val matrix = CameraQrImages.matrix(value)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    fun readQr(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2_048) sample *= 2
        val bitmap = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Unreadable QR image")
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            QRCodeReader().decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))),
                mapOf(DecodeHintType.TRY_HARDER to true),
            ).text
        } finally {
            bitmap.recycle()
        }
    }

    fun save(context: Context, qrPages: List<String>, tree: Uri? = null): List<Uri> {
        require(qrPages.isNotEmpty())
        return if (Build.VERSION.SDK_INT >= 29) saveToMediaStore(context, qrPages) else {
            requireNotNull(tree) { "Choose a folder to save the QR images" }
            saveToFolder(context, tree, qrPages)
        }
    }

    private fun saveToMediaStore(context: Context, qrPages: List<String>): List<Uri> {
        val resolver = context.contentResolver
        val created = mutableListOf<Uri>()
        try {
            qrPages.forEachIndexed { index, value ->
                val name = "rtsp-camera-backup-${System.currentTimeMillis()}-${UUID.randomUUID()}-${index + 1}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RTSP/Backups")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not save QR image")
                created += uri
                resolver.openOutputStream(uri, "w").use { stream ->
                    checkNotNull(stream)
                    val bitmap = qrBitmap(value)
                    try {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    } finally {
                        bitmap.recycle()
                    }
                }
                check(resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null) == 1)
            }
            return created
        } catch (failure: Exception) {
            created.forEach { runCatching { resolver.delete(it, null, null) } }
            throw failure
        }
    }

    private fun saveToFolder(context: Context, tree: Uri, qrPages: List<String>): List<Uri> {
        val resolver = context.contentResolver
        val directory = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val created = mutableListOf<Uri>()
        try {
            qrPages.forEachIndexed { index, value ->
                val name = "rtsp-camera-backup-${System.currentTimeMillis()}-${index + 1}.png"
                val uri = DocumentsContract.createDocument(resolver, directory, "image/png", name)
                    ?: error("Could not create QR image")
                created += uri
                resolver.openOutputStream(uri, "w").use { stream ->
                    checkNotNull(stream)
                    val bitmap = qrBitmap(value)
                    try {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            return created
        } catch (failure: Exception) {
            created.forEach { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            throw failure
        }
    }
}
