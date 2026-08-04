package com.example.ananas.editor

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ImageRepository(private val context: Context) {

    private val sessionMutex = Mutex()
    private val sessionEpoch = AtomicLong(0L)
    private val exportSequence = AtomicLong(0L)
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val lowRamDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && activityManager.isLowRamDevice
    private val decodeMaxPixels: Long = when {
        lowRamDevice || activityManager.memoryClass <= 128 -> 4_000_000L
        activityManager.memoryClass <= 256 -> 6_000_000L
        else -> 8_000_000L
    }
    private val decodeMaxDimension: Int = if (decodeMaxPixels <= 4_000_000L) 3072 else 4096

    suspend fun loadBitmap(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Unable to open image")
        input.use { BitmapFactory.decodeStream(it, null, bounds) }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Unsupported or damaged image")
        }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Unable to decode image")

        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val oriented = applyExifOrientation(decoded, orientation)
        scaleToLimits(oriented)
    }


    /**
     * Batch imports are first copied to a private temporary file. Some Photo Picker and
     * cloud providers do not reliably support opening the same URI several times for
     * bounds, pixels and EXIF. A single local copy also lets batch work use a smaller,
     * predictable memory budget on low-RAM devices.
     */
    suspend fun loadBatchBitmap(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "batch_input").apply { mkdirs() }
        val temporary = File(directory, "batch_${System.nanoTime()}.source")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(temporary)).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            } ?: throw IOException("The selected image could not be opened")

            if (temporary.length() <= 0L) throw IOException("The selected image is empty")
            decodeFileWithLimits(
                file = temporary,
                maxPixels = if (lowRamDevice) 1_800_000L else 3_000_000L,
                maxDimension = if (lowRamDevice) 1920 else 2560,
            )
        } finally {
            temporary.delete()
            directory.listFiles()?.forEach { stale ->
                if (System.currentTimeMillis() - stale.lastModified() > CACHE_MAX_AGE_MS) stale.delete()
            }
        }
    }

    suspend fun loadBitmap(file: File): Bitmap = withContext(Dispatchers.IO) {
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
            ?: throw IOException("Unable to restore editing session")
        if (decoded.config == Bitmap.Config.ARGB_8888) decoded else {
            decoded.copyArgb().also {
                if (it !== decoded && !decoded.isRecycled) decoded.recycle()
            }
        }
    }

    suspend fun persistSession(bitmap: Bitmap, originalBitmap: Bitmap? = null): File =
        withContext(Dispatchers.IO) {
            val expectedEpoch = sessionEpoch.get()
            sessionMutex.withLock {
                if (expectedEpoch != sessionEpoch.get()) throw CancellationException("Session was cleared")

                val directory = File(context.filesDir, "sessions").apply { mkdirs() }
                val target = File(directory, SESSION_CURRENT)
                val temporary = File(directory, "$SESSION_CURRENT.tmp")
                writePng(bitmap, temporary)

                val originalTarget = File(directory, SESSION_ORIGINAL)
                val originalTemporary = File(directory, "$SESSION_ORIGINAL.tmp")
                originalBitmap?.let { writePng(it, originalTemporary) }

                if (expectedEpoch != sessionEpoch.get()) {
                    temporary.delete()
                    originalTemporary.delete()
                    throw CancellationException("Session was cleared")
                }

                replaceAtomically(temporary, target)
                if (originalBitmap != null) replaceAtomically(originalTemporary, originalTarget)
                target
            }
        }

    fun sessionOriginalFile(): File? =
        File(context.filesDir, "sessions/$SESSION_ORIGINAL").takeIf(File::exists)

    fun clearSession() {
        sessionEpoch.incrementAndGet()
        File(context.filesDir, "sessions").deleteRecursively()
    }

    fun createCameraUri(): Uri {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS
        directory.listFiles()?.forEach { cached ->
            if (cached.lastModified() < cutoff) cached.delete()
        }
        val file = File(directory, "capture_${System.currentTimeMillis()}.jpg")
        if (!file.createNewFile()) throw IOException("Unable to create camera output")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun saveToGallery(
        bitmap: Bitmap,
        format: ExportFormat,
        quality: Int,
        namePrefix: String = "Ananas",
    ): Uri = withContext(Dispatchers.IO) {
        val safePrefix = namePrefix
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .take(48)
            .ifBlank { "Ananas" }
        val displayName = "${safePrefix}_${System.currentTimeMillis()}_${exportSequence.incrementAndGet()}.${format.extension}"
        val compressFormat = format.toCompressFormat()
        val exportBitmap = prepareForExport(bitmap, format)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Ananas")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IOException("Unable to create gallery item")

                try {
                    context.contentResolver.openOutputStream(uri, "w")?.use { rawOutput ->
                        BufferedOutputStream(rawOutput).use { output ->
                            if (!exportBitmap.compress(compressFormat, quality.coerceIn(1, 100), output)) {
                                throw IOException("Image encoder failed")
                            }
                            output.flush()
                        }
                    } ?: throw IOException("Unable to open gallery output")

                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    uri
                } catch (error: Throwable) {
                    context.contentResolver.delete(uri, null, null)
                    throw error
                }
            } else {
                @Suppress("DEPRECATION")
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Ananas"
                ).apply {
                    if (!exists() && !mkdirs()) throw IOException("Unable to create Pictures/Ananas")
                }
                @Suppress("DEPRECATION")
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                    put(MediaStore.Images.Media.DATA, File(directory, displayName).absolutePath)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IOException("Unable to create gallery item")
                try {
                    context.contentResolver.openOutputStream(uri, "w")?.use { rawOutput ->
                        BufferedOutputStream(rawOutput).use { output ->
                            if (!exportBitmap.compress(compressFormat, quality.coerceIn(1, 100), output)) {
                                throw IOException("Image encoder failed")
                            }
                            output.flush()
                        }
                    } ?: throw IOException("Unable to open gallery output")
                    uri
                } catch (error: Throwable) {
                    context.contentResolver.delete(uri, null, null)
                    throw error
                }
            }
        } finally {
            if (exportBitmap !== bitmap && !exportBitmap.isRecycled) exportBitmap.recycle()
        }
    }

    suspend fun createShareUri(bitmap: Bitmap, format: ExportFormat, quality: Int): Uri =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS
            directory.listFiles()?.forEach { old ->
                if (old.lastModified() < cutoff) old.delete()
            }
            val file = File(directory, "ananas_share_${System.currentTimeMillis()}.${format.extension}")
            val exportBitmap = prepareForExport(bitmap, format)
            try {
                FileOutputStream(file).use { output ->
                    if (!exportBitmap.compress(format.toCompressFormat(), quality.coerceIn(1, 100), output)) {
                        throw IOException("Unable to prepare image for sharing")
                    }
                }
            } finally {
                if (exportBitmap !== bitmap && !exportBitmap.isRecycled) exportBitmap.recycle()
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

    fun displayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }


    private fun decodeFileWithLimits(file: File, maxPixels: Long, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Unsupported or damaged image")
        }

        var sample = 1
        while (
            max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 1.5f ||
            (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > maxPixels * 2L
        ) sample *= 2

        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
                inSampleSize = sample
            },
        ) ?: throw IOException("Unable to decode image")

        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val oriented = applyExifOrientation(decoded, orientation)

        val dimensionScale = min(
            maxDimension.toFloat() / oriented.width.coerceAtLeast(1),
            maxDimension.toFloat() / oriented.height.coerceAtLeast(1),
        )
        val pixelScale = sqrt(maxPixels.toDouble() / (oriented.width.toLong() * oriented.height).coerceAtLeast(1L)).toFloat()
        val scale = min(1f, min(dimensionScale, pixelScale))
        if (scale >= 0.999f) return oriented

        val scaled = Bitmap.createScaledBitmap(
            oriented,
            max(1, (oriented.width * scale).toInt()),
            max(1, (oriented.height * scale).toInt()),
            true,
        )
        if (scaled !== oriented && !oriented.isRecycled) oriented.recycle()
        return scaled
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (
            max(width / sample, height / sample) > decodeMaxDimension * 1.5f ||
            (width.toLong() / sample) * (height.toLong() / sample) > decodeMaxPixels * 2
        ) {
            sample *= 2
        }
        return sample
    }

    private fun scaleToLimits(bitmap: Bitmap): Bitmap {
        val dimensionScale = min(
            decodeMaxDimension.toFloat() / bitmap.width.coerceAtLeast(1),
            decodeMaxDimension.toFloat() / bitmap.height.coerceAtLeast(1)
        )
        val pixelScale = sqrt(decodeMaxPixels.toDouble() / (bitmap.width.toLong() * bitmap.height).coerceAtLeast(1L)).toFloat()
        val scale = min(1f, min(dimensionScale, pixelScale))
        if (scale >= 0.999f) return bitmap

        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return scaled
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else {
                bitmap.copyArgb().also {
                    if (it !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                }
            }
        }

        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return if (transformed.config == Bitmap.Config.ARGB_8888) transformed else {
            transformed.copyArgb().also {
                if (it !== transformed && !transformed.isRecycled) transformed.recycle()
            }
        }
    }


    private fun writePng(bitmap: Bitmap, target: File) {
        FileOutputStream(target).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("Unable to preserve editing session")
            }
        }
    }

    private fun replaceAtomically(temporary: File, target: File) {
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IOException("Unable to replace editing session")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    private fun prepareForExport(bitmap: Bitmap, format: ExportFormat): Bitmap {
        if (format != ExportFormat.JPEG || !bitmap.hasAlpha()) return bitmap
        return Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { flattened ->
            val canvas = Canvas(flattened)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }
    }

    private fun ExportFormat.toCompressFormat(): Bitmap.CompressFormat = when (this) {
        ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        ExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    companion object {
        private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val SESSION_CURRENT = "active_session.png"
        private const val SESSION_ORIGINAL = "original_session.png"
    }
}
