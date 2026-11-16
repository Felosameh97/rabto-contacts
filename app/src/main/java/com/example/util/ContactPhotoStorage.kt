package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ContactPhotoStorage {

    private const val MAX_DIM_PX = 720

    fun photosDir(context: Context): File {
        return File(context.filesDir, "contact_photos").apply { mkdirs() }
    }

    /**
     * Copies and downscales a photo picked via Android Photo Picker (`PickVisualMedia`)
     * into app-private storage so no broad media/storage permissions are ever required.
     */
    suspend fun savePickedPhoto(
        context: Context,
        sourceUri: Uri,
        contactIdHint: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }

            val srcW = boundsOpts.outWidth.coerceAtLeast(1)
            val srcH = boundsOpts.outHeight.coerceAtLeast(1)
            var sampleSize = 1
            while (srcW / sampleSize > MAX_DIM_PX || srcH / sampleSize > MAX_DIM_PX) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = resolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return@withContext null

            val safeKey = contactIdHint
                ?.replace(Regex("[^a-zA-Z0-9_-]"), "")
                ?.take(24)
                ?.ifBlank { null }
                ?: UUID.randomUUID().toString().replace("-", "").take(12)

            val outFile = File(photosDir(context), "photo_${safeKey}_${System.currentTimeMillis() % 100000}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 84, fos)
            }
            bitmap.recycle()
            outFile.absolutePath
        }.getOrNull()
    }
}
