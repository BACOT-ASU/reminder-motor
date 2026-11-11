package com.bacad.remindermotor.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

/**
 * Menyimpan foto motor di penyimpanan internal aplikasi (tidak terlihat di galeri,
 * ikut terhapus saat aplikasi di-uninstall). Foto diperkecil supaya hemat ruang.
 */
object PhotoStore {
    private const val MAX_SIZE = 1024
    private const val JPEG_QUALITY = 85

    private fun photoDir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

    /** File sementara untuk hasil jepretan kamera, dibagikan ke aplikasi kamera lewat FileProvider. */
    fun newCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Salin foto dari galeri/kamera ke penyimpanan aplikasi. Jalankan di thread IO. */
    fun saveFromUri(context: Context, uri: Uri): String? {
        val bitmap = loadBitmap(context, uri, MAX_SIZE) ?: return null
        val file = File(photoDir(context), "motor_${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return file.absolutePath
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    /** Baca gambar, perkecil, dan putar sesuai orientasi kamera (EXIF). */
    fun loadBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? = runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, maxSize.toFloat() / maxOf(bitmap.width, bitmap.height))
        if (rotation != 0f || scale < 1f) {
            val matrix = Matrix().apply { postRotate(rotation); postScale(scale, scale) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        bitmap
    }.getOrNull()

    // ---- Untuk backup: foto disimpan sebagai teks base64 di dalam file JSON ----
    fun toBase64(path: String?): String? = path?.let { p ->
        runCatching { Base64.encodeToString(File(p).readBytes(), Base64.NO_WRAP) }.getOrNull()
    }

    fun fromBase64(context: Context, data: String?): String? = data?.let { d ->
        runCatching {
            val file = File(photoDir(context), "motor_${UUID.randomUUID()}.jpg")
            file.writeBytes(Base64.decode(d, Base64.NO_WRAP))
            file.absolutePath
        }.getOrNull()
    }
}
