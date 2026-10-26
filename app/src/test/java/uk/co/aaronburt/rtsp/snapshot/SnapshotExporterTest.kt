package uk.co.aaronburt.rtsp.snapshot

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnapshotExporterTest {
    @Test fun aPhotoIsPublishedAsAReadableJpegAndSharingGrantsReadAccess() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = ImageProvider(File(context.cacheDir, "published-test.jpg"))
        register(context, "media", provider)
        val exporter = SnapshotExporter(context)
        val bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val prepared = try { exporter.prepare(bitmap) } finally { bitmap.recycle() }
        try {
            val uri = exporter.saveToPhotos(prepared)
            assertEquals(0, provider.values.getAsInteger(MediaStore.Images.Media.IS_PENDING))
            val decoded = BitmapFactory.decodeFile(provider.file.path)
            assertEquals(160, decoded.width)
            assertEquals(90, decoded.height)
            assertTrue(Color.red(decoded.getPixel(80, 45)) > 240)
            decoded.recycle()
            val share = exporter.shareIntent(uri)
            assertEquals(Intent.ACTION_SEND, share.action)
            assertEquals("image/jpeg", share.type)
            assertEquals(uri, share.clipData!!.getItemAt(0).uri)
            assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        } finally { prepared.delete(); provider.file.delete() }
    }

    @Test fun failedPhotoWritesRemoveThePendingMediaEntry() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = ImageProvider(File(context.cacheDir, "failed-test.jpg"), failWrite = true)
        register(context, "media", provider)
        val exporter = SnapshotExporter(context)
        val bitmap = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        val prepared = try { exporter.prepare(bitmap) } finally { bitmap.recycle() }
        try {
            try { exporter.saveToPhotos(prepared); fail("Expected destination failure") }
            catch (_: FileNotFoundException) { }
            assertEquals(1, provider.deleted)
        } finally { prepared.delete(); provider.file.delete() }
    }

    @Test @Config(sdk = [28]) fun aLegacyDocumentDestinationReceivesTheCompleteJpeg() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = ImageProvider(File(context.cacheDir, "document-test.jpg"))
        register(context, "documents.test", provider)
        val exporter = SnapshotExporter(context)
        val bitmap = Bitmap.createBitmap(90, 160, Bitmap.Config.ARGB_8888)
        val prepared = try { exporter.prepare(bitmap) } finally { bitmap.recycle() }
        try {
            exporter.writeDocument(prepared, Uri.parse("content://documents.test/snapshot"))
            assertTrue(prepared.readBytes().contentEquals(provider.file.readBytes()))
            val decoded = BitmapFactory.decodeFile(provider.file.path)
            assertEquals(90, decoded.width)
            assertEquals(160, decoded.height)
            decoded.recycle()
        } finally { prepared.delete(); provider.file.delete() }
    }

    private fun register(context: Context, authority: String, provider: ImageProvider) {
        provider.attachInfo(context, ProviderInfo().apply {
            this.authority = authority
            applicationInfo = context.applicationInfo
            exported = true
            grantUriPermissions = true
        })
        ShadowContentResolver.registerProviderInternal(authority, provider)
    }
}

private class ImageProvider(val file: File, private val failWrite: Boolean = false) : ContentProvider() {
    val values = ContentValues()
    var deleted = 0
    override fun onCreate() = true
    override fun insert(uri: Uri, input: ContentValues?): Uri {
        input?.let(values::putAll)
        return Uri.withAppendedPath(uri, "1")
    }
    override fun update(uri: Uri, input: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        input?.let(values::putAll)
        return 1
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        deleted++
        file.delete()
        return 1
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (failWrite) throw FileNotFoundException("Destination unavailable")
        return ParcelFileDescriptor.open(file,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE)
    }
    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
}
