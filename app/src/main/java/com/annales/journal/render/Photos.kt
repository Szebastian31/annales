package com.annales.journal.render

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Reading photos in, and putting sealed stamps into the phone's Photos. */
object Photos {
    private const val MAX_SIDE = 2400

    /** Decodes a picked photo upright (EXIF rotation applied on Android 9+), scaled down to a sensible size. */
    fun load(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // drawable onto a normal Canvas
                val side = maxOf(info.size.width, info.size.height)
                if (side > MAX_SIDE) decoder.setTargetSampleSize(side / MAX_SIDE + 1)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val side = maxOf(bounds.outWidth, bounds.outHeight)
            val opts = BitmapFactory.Options().apply { inSampleSize = if (side > MAX_SIDE) side / MAX_SIDE + 1 else 1 }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }.getOrNull()

    fun writeJpeg(bitmap: Bitmap, file: File) = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    fun writePng(bitmap: Bitmap, file: File) = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /** Adds a sealed stamp to an "Annales" album in Photos (Android 10+). Returns false where that isn't possible. */
    fun saveToGallery(context: Context, png: File, name: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Annales")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out -> png.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        }.getOrElse { resolver.delete(uri, null, null); false }
    }
}
