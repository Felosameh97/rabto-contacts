package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

object ContactPhotoStorage {

    private const val MAX_DIM_PX = 720

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build()
    }

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

            persistBitmap(context, bitmap, contactIdHint)
        }.getOrNull()
    }

    suspend fun savePhotoBytes(
        context: Context,
        bytes: ByteArray,
        contactIdHint: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext null
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val srcW = boundsOpts.outWidth.coerceAtLeast(1)
            val srcH = boundsOpts.outHeight.coerceAtLeast(1)
            var sampleSize = 1
            while (srcW / sampleSize > MAX_DIM_PX || srcH / sampleSize > MAX_DIM_PX) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: return@withContext null
            persistBitmap(context, bitmap, contactIdHint)
        }.getOrNull()
    }

    suspend fun savePhotoFromUrl(
        context: Context,
        rawUrl: String,
        contactIdHint: String? = null,
        bearerToken: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (rawUrl.isBlank()) return@withContext null
        val highResUrl = if (rawUrl.contains("googleusercontent.com")) {
            rawUrl.replace(Regex("=s\\d+(-[a-zA-Z0-9-]+)?$"), "=s512-c")
        } else {
            rawUrl
        }
        runCatching {
            val reqBuilder = Request.Builder().url(highResUrl)
            if (!bearerToken.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $bearerToken")
            }
            val bytes = httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.bytes()
            } ?: return@withContext null
            savePhotoBytes(context, bytes, contactIdHint)
        }.getOrNull()
    }

    private fun persistBitmap(
        context: Context,
        bitmap: Bitmap,
        contactIdHint: String?,
    ): String {
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
        return outFile.absolutePath
    }
}
