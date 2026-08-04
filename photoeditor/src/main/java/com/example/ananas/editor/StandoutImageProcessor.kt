package com.example.ananas.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object StandoutImageProcessor {

    suspend fun compositeLayers(
        width: Int,
        height: Int,
        layers: List<EditorLayer>,
    ): Bitmap = withContext(Dispatchers.Default) {
        val visible = layers.filter { it.visible && it.opacity > 0f }
        if (visible.isEmpty()) return@withContext Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for ((index, layer) in visible.withIndex()) {
            currentCoroutineContext().ensureActive()
            val layerBitmap = if (layer.bitmap.width == width && layer.bitmap.height == height) {
                layer.bitmap
            } else {
                Bitmap.createScaledBitmap(layer.bitmap, width, height, true)
            }
            val next = blendLayer(result, layerBitmap, layer.opacity, if (index == 0) BlendModeOption.NORMAL else layer.blendMode)
            if (!result.isRecycled) result.recycle()
            if (layerBitmap !== layer.bitmap && !layerBitmap.isRecycled) layerBitmap.recycle()
            result = next
        }
        result
    }

    suspend fun createTextLayer(width: Int, height: Int, elements: List<TextElement>): Bitmap {
        val transparent = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ImageProcessor.applyTextElements(transparent, elements).also {
            if (it !== transparent && !transparent.isRecycled) transparent.recycle()
        }
    }

    suspend fun createStickerLayer(width: Int, height: Int, elements: List<StickerElement>): Bitmap {
        val transparent = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ImageProcessor.applyStickerElements(transparent, elements).also {
            if (it !== transparent && !transparent.isRecycled) transparent.recycle()
        }
    }

    suspend fun createDrawingLayer(width: Int, height: Int, strokes: List<DrawStroke>): Bitmap {
        val transparent = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ImageProcessor.applyDrawStrokes(transparent, strokes).also {
            if (it !== transparent && !transparent.isRecycled) transparent.recycle()
        }
    }

    suspend fun applySelectiveMask(source: Bitmap, settings: SelectiveMaskSettings): Bitmap =
        withContext(Dispatchers.Default) {
            if (settings.amount == settings.adjustment.default) return@withContext source.copyArgb()
            val processed = ImageProcessor.applyAdjustments(source, mapOf(settings.adjustment to settings.amount))
            val mask = createSelectiveMask(source, settings)
            blendWithMask(source, processed, mask).also {
                if (!processed.isRecycled) processed.recycle()
                if (!mask.isRecycled) mask.recycle()
            }
        }

    suspend fun createSelectiveMask(source: Bitmap, settings: SelectiveMaskSettings): Bitmap =
        withContext(Dispatchers.Default) {
            val width = source.width
            val height = source.height
            val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
            val canvas = Canvas(mask)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            when (settings.mode) {
                MaskMode.BRUSH -> drawMaskStrokes(canvas, width, height, settings.strokes)
                MaskMode.RADIAL -> {
                    val radius = max(1f, min(width, height) * settings.radius.coerceIn(0.03f, 1f))
                    val feather = (radius * settings.feather.coerceIn(0.01f, 1f)).coerceAtLeast(1f)
                    paint.shader = android.graphics.RadialGradient(
                        width * settings.centerX,
                        height * settings.centerY,
                        radius,
                        intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                        floatArrayOf(0f, (1f - feather / radius).coerceIn(0f, 0.98f), 1f),
                        Shader.TileMode.CLAMP,
                    )
                    canvas.drawCircle(width * settings.centerX, height * settings.centerY, radius, paint)
                }
                MaskMode.LINEAR -> {
                    val radians = Math.toRadians(settings.angle.toDouble())
                    val dx = cos(radians).toFloat() * width
                    val dy = sin(radians).toFloat() * height
                    val cx = width * settings.centerX
                    val cy = height * settings.centerY
                    val feather = settings.feather.coerceIn(0.02f, 0.8f)
                    paint.shader = LinearGradient(
                        cx - dx / 2f,
                        cy - dy / 2f,
                        cx + dx / 2f,
                        cy + dy / 2f,
                        intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                        floatArrayOf(0f, feather, 1f - feather, 1f),
                        Shader.TileMode.CLAMP,
                    )
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                }
                MaskMode.LUMINANCE -> {
                    val pixels = IntArray(width)
                    val minLum = min(settings.luminanceMin, settings.luminanceMax).coerceIn(0f, 1f)
                    val maxLum = max(settings.luminanceMin, settings.luminanceMax).coerceIn(0f, 1f)
                    val softness = settings.feather.coerceIn(0.01f, 0.4f)
                    val all = ByteArray(width * height)
                    for (y in 0 until height) {
                        if (y % 24 == 0) currentCoroutineContext().ensureActive()
                        source.getPixels(pixels, 0, width, 0, y, width, 1)
                        for (x in 0 until width) {
                            val p = pixels[x]
                            val lum = (Color.red(p) * 0.2126f + Color.green(p) * 0.7152f + Color.blue(p) * 0.0722f) / 255f
                            val low = smoothStep(minLum - softness, minLum + softness, lum)
                            val high = 1f - smoothStep(maxLum - softness, maxLum + softness, lum)
                            all[y * width + x] = (255f * low * high).roundToInt().coerceIn(0, 255).toByte()
                        }
                    }
                    mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(all))
                }
                MaskMode.COLOR_RANGE -> {
                    val pixels = IntArray(width * height)
                    source.getPixels(pixels, 0, width, 0, 0, width, height)
                    val all = ByteArray(pixels.size)
                    val hsv = FloatArray(3)
                    val tolerance = settings.colorTolerance.coerceIn(2f, 120f)
                    for (index in pixels.indices) {
                        if (index % (width * 24).coerceAtLeast(1) == 0) currentCoroutineContext().ensureActive()
                        Color.colorToHSV(pixels[index], hsv)
                        val distance = hueDistance(hsv[0], settings.targetHue)
                        val hueWeight = 1f - smoothStep(tolerance * 0.55f, tolerance, distance)
                        val saturationWeight = (hsv[1] * 1.4f).coerceIn(0.2f, 1f)
                        all[index] = (255f * hueWeight * saturationWeight).roundToInt().coerceIn(0, 255).toByte()
                    }
                    mask.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(all))
                }
            }
            if (settings.mode != MaskMode.BRUSH && settings.strokes.isNotEmpty()) {
                drawMaskStrokes(canvas, width, height, settings.strokes)
            }
            if (settings.invert) invertAlphaMask(mask) else mask
        }

    suspend fun autoSubjectMask(source: Bitmap, threshold: Float, feather: Float): Bitmap =
        withContext(Dispatchers.Default) {
            val scale = min(1f, sqrt(AUTO_MASK_MAX_PIXELS.toFloat() / (source.width.toLong() * source.height).coerceAtLeast(1L)))
            val workWidth = max(1, (source.width * scale).roundToInt())
            val workHeight = max(1, (source.height * scale).roundToInt())
            val work = if (workWidth == source.width && workHeight == source.height) source else Bitmap.createScaledBitmap(source, workWidth, workHeight, true)
            val pixels = IntArray(workWidth * workHeight)
            work.getPixels(pixels, 0, workWidth, 0, 0, workWidth, workHeight)

            var borderR = 0L
            var borderG = 0L
            var borderB = 0L
            var count = 0L
            val step = max(1, min(workWidth, workHeight) / 160)
            fun sample(index: Int) {
                val p = pixels[index]
                borderR += Color.red(p)
                borderG += Color.green(p)
                borderB += Color.blue(p)
                count++
            }
            for (x in 0 until workWidth step step) {
                sample(x)
                sample((workHeight - 1) * workWidth + x)
            }
            for (y in 0 until workHeight step step) {
                sample(y * workWidth)
                sample(y * workWidth + workWidth - 1)
            }
            val meanR = (borderR / count.coerceAtLeast(1)).toInt()
            val meanG = (borderG / count.coerceAtLeast(1)).toInt()
            val meanB = (borderB / count.coerceAtLeast(1)).toInt()
            val tolerance = threshold.coerceIn(8f, 100f)
            val visited = ByteArray(pixels.size)
            val queue = IntArray(pixels.size)
            var head = 0
            var tail = 0

            fun isBackgroundCandidate(index: Int): Boolean {
                val p = pixels[index]
                val dr = Color.red(p) - meanR
                val dg = Color.green(p) - meanG
                val db = Color.blue(p) - meanB
                val distance = sqrt((dr * dr + dg * dg + db * db).toFloat())
                return distance <= tolerance * 2.35f
            }
            fun enqueue(index: Int) {
                if (visited[index].toInt() == 0 && isBackgroundCandidate(index)) {
                    visited[index] = 1
                    queue[tail++] = index
                }
            }
            for (x in 0 until workWidth) {
                enqueue(x)
                enqueue((workHeight - 1) * workWidth + x)
            }
            for (y in 0 until workHeight) {
                enqueue(y * workWidth)
                enqueue(y * workWidth + workWidth - 1)
            }

            while (head < tail) {
                if (head % 32768 == 0) currentCoroutineContext().ensureActive()
                val index = queue[head++]
                val x = index % workWidth
                val y = index / workWidth
                if (x > 0) enqueue(index - 1)
                if (x + 1 < workWidth) enqueue(index + 1)
                if (y > 0) enqueue(index - workWidth)
                if (y + 1 < workHeight) enqueue(index + workWidth)
            }

            val maskPixels = IntArray(pixels.size)
            for (i in maskPixels.indices) {
                maskPixels[i] = if (visited[i].toInt() == 0) Color.WHITE else Color.TRANSPARENT
            }
            var mask = Bitmap.createBitmap(workWidth, workHeight, Bitmap.Config.ARGB_8888)
            mask.setPixels(maskPixels, 0, workWidth, 0, 0, workWidth, workHeight)
            if (feather > 0f) {
                val blurred = fastBlur(mask, (feather * scale.coerceAtLeast(0.25f)).roundToInt().coerceAtLeast(1))
                if (!mask.isRecycled) mask.recycle()
                mask = blurred
            }
            val full = if (mask.width == source.width && mask.height == source.height) mask else Bitmap.createScaledBitmap(mask, source.width, source.height, true)
            if (full !== mask && !mask.isRecycled) mask.recycle()
            if (work !== source && !work.isRecycled) work.recycle()
            full
        }

    suspend fun applyCutout(source: Bitmap, settings: CutoutSettings): Bitmap =
        withContext(Dispatchers.Default) {
            var mask = autoSubjectMask(source, settings.threshold, settings.feather)
            if (settings.refinement.isNotEmpty()) {
                val canvas = Canvas(mask)
                drawMaskStrokes(canvas, source.width, source.height, settings.refinement)
            }
            val result = renderCutout(source, mask, settings)
            if (!mask.isRecycled) mask.recycle()
            result
        }

    suspend fun applyHealing(source: Bitmap, settings: HealingSettings): Bitmap =
        withContext(Dispatchers.Default) {
            if (settings.strokes.isEmpty()) return@withContext source.copyArgb()
            val bounds = strokeBounds(source.width, source.height, settings.strokes, paddingMultiplier = 2.5f)
            if (bounds.width() <= 1 || bounds.height() <= 1) return@withContext source.copyArgb()
            val crop = Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width(), bounds.height())
            val mask = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            val translated = settings.strokes.map { stroke ->
                stroke.copy(points = stroke.points.map { point ->
                    NormalizedPoint(
                        x = (point.x * source.width - bounds.left) / bounds.width().toFloat(),
                        y = (point.y * source.height - bounds.top) / bounds.height().toFloat(),
                    )
                })
            }
            drawMaskStrokes(Canvas(mask), bounds.width(), bounds.height(), translated.map { it.copy(subtract = false) })
            val healed = inpaintRegion(crop, mask, settings.textureBlend)
            val output = source.copyArgb(mutable = true)
            Canvas(output).drawBitmap(healed, bounds.left.toFloat(), bounds.top.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            crop.recycle()
            mask.recycle()
            healed.recycle()
            output
        }

    suspend fun applyRecipe(source: Bitmap, settings: RecipeSettings): Bitmap =
        withContext(Dispatchers.Default) {
            val intensity = settings.intensity.coerceIn(0f, 100f)
            if (intensity <= 0f) return@withContext source.copyArgb()
            val (adjustments, filter, filterIntensity, effect) = recipeDefinition(settings.preset)
            var result = ImageProcessor.applyAdjustments(source, adjustments)
            if (filter != FilterPreset.ORIGINAL) {
                val filtered = ImageProcessor.applyFilter(result, filter, filterIntensity)
                result.recycle()
                result = filtered
            }
            if (effect != null && !effect.isIdentity()) {
                val effected = AdvancedImageProcessor.applyCreativeEffect(result, effect)
                result.recycle()
                result = effected
            }
            if (intensity >= 99.9f) result else AdvancedImageProcessor.blend(source, result, intensity).also { result.recycle() }
        }

    suspend fun createProductImage(source: Bitmap, settings: ProductStudioSettings): Bitmap =
        withContext(Dispatchers.Default) {
            val mask = autoSubjectMask(source, settings.cutoutThreshold, settings.feather)
            val bounds = alphaBounds(mask)
            if (bounds.width() <= 1 || bounds.height() <= 1) {
                mask.recycle()
                return@withContext source.copyArgb()
            }
            val subject = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val subjectCanvas = Canvas(subject)
            subjectCanvas.drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }.also {
                subjectCanvas.drawBitmap(mask, 0f, 0f, it)
                it.xfermode = null
            }
            val cropped = Bitmap.createBitmap(subject, bounds.left, bounds.top, bounds.width(), bounds.height())
            val baseSize = min(2400, max(1200, max(source.width, source.height)))
            val outWidth: Int
            val outHeight: Int
            if (settings.canvas.widthRatio >= settings.canvas.heightRatio) {
                outWidth = baseSize
                outHeight = (baseSize * settings.canvas.heightRatio.toFloat() / settings.canvas.widthRatio).roundToInt()
            } else {
                outHeight = baseSize
                outWidth = (baseSize * settings.canvas.widthRatio.toFloat() / settings.canvas.heightRatio).roundToInt()
            }
            val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            drawProductBackground(canvas, outWidth, outHeight, settings)
            val padding = settings.padding.coerceIn(0.02f, 0.35f)
            val availableW = outWidth * (1f - padding * 2f)
            val availableH = outHeight * (1f - padding * 2f)
            val scale = min(availableW / cropped.width, availableH / cropped.height)
            val drawW = cropped.width * scale
            val drawH = cropped.height * scale
            val left = (outWidth - drawW) / 2f
            val top = (outHeight - drawH) / 2f
            val dst = RectF(left, top, left + drawW, top + drawH)

            if (settings.contactShadow > 0f) {
                val shadowSource = Bitmap.createScaledBitmap(
                    cropped,
                    max(1, drawW.roundToInt()),
                    max(1, drawH.roundToInt()),
                    true,
                )
                val blurred = fastBlur(
                    shadowSource,
                    (outWidth * 0.012f * settings.contactShadow / 50f).roundToInt().coerceAtLeast(2),
                )
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (settings.contactShadow.coerceIn(0f, 100f) * 1.45f).roundToInt().coerceIn(0, 145)
                    colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(floatArrayOf(
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )))
                }
                canvas.drawBitmap(blurred, left, top + drawH * 0.025f, shadowPaint)
                shadowSource.recycle()
                blurred.recycle()
            }
            canvas.drawBitmap(cropped, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

            if (settings.reflection > 0f) {
                val reflection = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
                val reflectionCanvas = Canvas(reflection)
                reflectionCanvas.scale(1f, -1f, cropped.width / 2f, cropped.height / 2f)
                reflectionCanvas.drawBitmap(cropped, 0f, 0f, null)
                val refHeight = drawH * 0.28f
                val refDst = RectF(left, top + drawH, left + drawW, top + drawH + refHeight)
                val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = (settings.reflection.coerceIn(0f, 100f) * 1.5f).roundToInt().coerceIn(0, 150)
                }
                canvas.drawBitmap(reflection, null, refDst, reflectionPaint)
                reflection.recycle()
            }
            mask.recycle()
            subject.recycle()
            cropped.recycle()
            output
        }

    suspend fun prepareSmartExport(source: Bitmap, preset: SmartExportPreset): Bitmap =
        withContext(Dispatchers.Default) {
            if (preset.width == 0 || preset.height == 0) return@withContext source.copyArgb()
            when (preset) {
                SmartExportPreset.WHATSAPP, SmartExportPreset.WEB_FAST -> {
                    val scale = min(1f, min(preset.width.toFloat() / source.width, preset.height.toFloat() / source.height))
                    Bitmap.createScaledBitmap(source, max(1, (source.width * scale).roundToInt()), max(1, (source.height * scale).roundToInt()), true)
                }
                SmartExportPreset.MARKETPLACE -> fitOnCanvas(source, preset.width, preset.height, Color.WHITE)
                else -> if (preset.cropToFill) centerCrop(source, preset.width, preset.height) else fitOnCanvas(source, preset.width, preset.height, Color.TRANSPARENT)
            }
        }

    private suspend fun blendLayer(base: Bitmap, overlay: Bitmap, opacity: Float, mode: BlendModeOption): Bitmap {
        val width = base.width
        val height = base.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val baseRow = IntArray(width)
        val overRow = IntArray(width)
        val amount = opacity.coerceIn(0f, 1f)
        for (y in 0 until height) {
            if (y % 32 == 0) currentCoroutineContext().ensureActive()
            base.getPixels(baseRow, 0, width, 0, y, width, 1)
            overlay.getPixels(overRow, 0, width, 0, y, width, 1)
            for (x in 0 until width) baseRow[x] = blendPixel(baseRow[x], overRow[x], amount, mode)
            output.setPixels(baseRow, 0, width, 0, y, width, 1)
        }
        return output
    }

    private fun blendPixel(base: Int, over: Int, opacity: Float, mode: BlendModeOption): Int {
        val oa = Color.alpha(over) / 255f * opacity
        if (oa <= 0f) return base
        val ba = Color.alpha(base) / 255f
        val br = Color.red(base) / 255f
        val bg = Color.green(base) / 255f
        val bb = Color.blue(base) / 255f
        val or = Color.red(over) / 255f
        val og = Color.green(over) / 255f
        val ob = Color.blue(over) / 255f
        fun channel(b: Float, o: Float): Float = when (mode) {
            BlendModeOption.NORMAL -> o
            BlendModeOption.MULTIPLY -> b * o
            BlendModeOption.SCREEN -> 1f - (1f - b) * (1f - o)
            BlendModeOption.OVERLAY -> if (b < 0.5f) 2f * b * o else 1f - 2f * (1f - b) * (1f - o)
            BlendModeOption.SOFT_LIGHT -> (1f - 2f * o) * b * b + 2f * o * b
            BlendModeOption.HARD_LIGHT -> if (o < 0.5f) 2f * b * o else 1f - 2f * (1f - b) * (1f - o)
            BlendModeOption.DARKEN -> min(b, o)
            BlendModeOption.LIGHTEN -> max(b, o)
            BlendModeOption.DIFFERENCE -> abs(b - o)
        }
        val outA = oa + ba * (1f - oa)
        fun composite(b: Float, o: Float): Int {
            val mixed = channel(b, o)
            return (((mixed * oa + b * ba * (1f - oa)) / outA.coerceAtLeast(0.0001f)) * 255f).roundToInt().coerceIn(0, 255)
        }
        return Color.argb((outA * 255f).roundToInt().coerceIn(0, 255), composite(br, or), composite(bg, og), composite(bb, ob))
    }

    private fun drawMaskStrokes(canvas: Canvas, width: Int, height: Int, strokes: List<MaskStroke>) {
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (stroke.subtract) Color.TRANSPARENT else Color.WHITE
                alpha = (stroke.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = max(1f, min(width, height) * stroke.widthFraction.coerceIn(0.001f, 0.5f))
                xfermode = if (stroke.subtract) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            }
            val path = Path().apply {
                moveTo(stroke.points.first().x * width, stroke.points.first().y * height)
                stroke.points.drop(1).forEach { point -> lineTo(point.x * width, point.y * height) }
            }
            if (stroke.points.size == 1) canvas.drawCircle(stroke.points.first().x * width, stroke.points.first().y * height, paint.strokeWidth / 2f, paint)
            else canvas.drawPath(path, paint)
            paint.xfermode = null
        }
    }

    private suspend fun blendWithMask(original: Bitmap, processed: Bitmap, mask: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val originalRow = IntArray(width)
        val processedRow = IntArray(width)
        val maskRow = IntArray(width)
        for (y in 0 until height) {
            if (y % 32 == 0) currentCoroutineContext().ensureActive()
            original.getPixels(originalRow, 0, width, 0, y, width, 1)
            processed.getPixels(processedRow, 0, width, 0, y, width, 1)
            mask.getPixels(maskRow, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val t = Color.alpha(maskRow[x]) / 255f
                val a = lerp(Color.alpha(originalRow[x]), Color.alpha(processedRow[x]), t)
                val r = lerp(Color.red(originalRow[x]), Color.red(processedRow[x]), t)
                val g = lerp(Color.green(originalRow[x]), Color.green(processedRow[x]), t)
                val b = lerp(Color.blue(originalRow[x]), Color.blue(processedRow[x]), t)
                originalRow[x] = Color.argb(a, r, g, b)
            }
            output.setPixels(originalRow, 0, width, 0, y, width, 1)
        }
        return output
    }

    private fun renderCutout(source: Bitmap, mask: Bitmap, settings: CutoutSettings): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        when (settings.background) {
            CutoutBackground.TRANSPARENT -> canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            CutoutBackground.SOLID -> canvas.drawColor(settings.backgroundColor)
            CutoutBackground.GRADIENT -> Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), settings.backgroundColor, settings.gradientColor, Shader.TileMode.CLAMP)
            }.also { canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), it) }
            CutoutBackground.BLUR -> {
                val blurred = fastBlur(source, (settings.blurAmount.coerceIn(1f, 100f) / 4f).roundToInt().coerceAtLeast(1))
                canvas.drawBitmap(blurred, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                blurred.recycle()
            }
        }
        if (settings.shadowAmount > 0f && settings.background != CutoutBackground.TRANSPARENT) {
            val shadowMask = fastBlur(mask, (settings.shadowAmount / 3f).roundToInt().coerceAtLeast(1))
            val shadow = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val shadowCanvas = Canvas(shadow)
            shadowCanvas.drawColor(Color.BLACK)
            Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }.also {
                shadowCanvas.drawBitmap(shadowMask, 0f, 0f, it)
                it.xfermode = null
            }
            canvas.drawBitmap(shadow, 0f, height * 0.012f, Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = (settings.shadowAmount * 1.5f).roundToInt().coerceIn(0, 110) })
            shadowMask.recycle()
            shadow.recycle()
        }
        val subject = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val subjectCanvas = Canvas(subject)
        subjectCanvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }.also {
            subjectCanvas.drawBitmap(mask, 0f, 0f, it)
            it.xfermode = null
        }
        canvas.drawBitmap(subject, 0f, 0f, null)
        subject.recycle()
        return output
    }

    private fun invertAlphaMask(mask: Bitmap): Bitmap {
        val width = mask.width
        val height = mask.height
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) pixels[i] = Color.argb(255 - Color.alpha(pixels[i]), 255, 255, 255)
        mask.setPixels(pixels, 0, width, 0, 0, width, height)
        return mask
    }

    private fun strokeBounds(width: Int, height: Int, strokes: List<MaskStroke>, paddingMultiplier: Float): Rect {
        var minX = width.toFloat()
        var minY = height.toFloat()
        var maxX = 0f
        var maxY = 0f
        var widest = 1f
        strokes.forEach { stroke ->
            widest = max(widest, min(width, height) * stroke.widthFraction)
            stroke.points.forEach { point ->
                minX = min(minX, point.x * width)
                minY = min(minY, point.y * height)
                maxX = max(maxX, point.x * width)
                maxY = max(maxY, point.y * height)
            }
        }
        val pad = widest * paddingMultiplier + 8f
        return Rect(
            floor(minX - pad).toInt().coerceIn(0, width - 1),
            floor(minY - pad).toInt().coerceIn(0, height - 1),
            ceil(maxX + pad).toInt().coerceIn(1, width),
            ceil(maxY + pad).toInt().coerceIn(1, height),
        )
    }

    private suspend fun inpaintRegion(source: Bitmap, mask: Bitmap, textureBlend: Float): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)
        val unknown = BooleanArray(pixels.size) { Color.alpha(maskPixels[it]) > 18 }
        val queued = BooleanArray(pixels.size)
        val queue = IntArray(pixels.size)
        var head = 0
        var tail = 0
        fun isKnown(index: Int) = index in pixels.indices && !unknown[index]
        fun enqueueBoundary(index: Int) {
            if (!unknown[index] || queued[index]) return
            val x = index % width
            val y = index / width
            if ((x > 0 && isKnown(index - 1)) || (x + 1 < width && isKnown(index + 1)) || (y > 0 && isKnown(index - width)) || (y + 1 < height && isKnown(index + width))) {
                queued[index] = true
                queue[tail++] = index
            }
        }
        for (i in pixels.indices) enqueueBoundary(i)
        while (head < tail) {
            if (head % 16384 == 0) currentCoroutineContext().ensureActive()
            val index = queue[head++]
            val x = index % width
            val y = index / width
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            var count = 0
            fun accumulate(next: Int) {
                if (next in pixels.indices && !unknown[next]) {
                    val p = pixels[next]
                    a += Color.alpha(p); r += Color.red(p); g += Color.green(p); b += Color.blue(p); count++
                }
            }
            if (x > 0) accumulate(index - 1)
            if (x + 1 < width) accumulate(index + 1)
            if (y > 0) accumulate(index - width)
            if (y + 1 < height) accumulate(index + width)
            if (x > 0 && y > 0) accumulate(index - width - 1)
            if (x + 1 < width && y > 0) accumulate(index - width + 1)
            if (x > 0 && y + 1 < height) accumulate(index + width - 1)
            if (x + 1 < width && y + 1 < height) accumulate(index + width + 1)
            if (count > 0) {
                pixels[index] = Color.argb(a / count, r / count, g / count, b / count)
                unknown[index] = false
                if (x > 0) enqueueBoundary(index - 1)
                if (x + 1 < width) enqueueBoundary(index + 1)
                if (y > 0) enqueueBoundary(index - width)
                if (y + 1 < height) enqueueBoundary(index + width)
            }
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        val blend = textureBlend.coerceIn(0f, 100f) / 100f
        if (blend < 0.99f) {
            val original = IntArray(width * height)
            source.getPixels(original, 0, width, 0, 0, width, height)
            output.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) if (Color.alpha(maskPixels[i]) > 0) {
                val t = Color.alpha(maskPixels[i]) / 255f * blend
                pixels[i] = Color.argb(
                    lerp(Color.alpha(original[i]), Color.alpha(pixels[i]), t),
                    lerp(Color.red(original[i]), Color.red(pixels[i]), t),
                    lerp(Color.green(original[i]), Color.green(pixels[i]), t),
                    lerp(Color.blue(original[i]), Color.blue(pixels[i]), t),
                )
            }
            output.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        return output
    }

    private fun recipeDefinition(preset: RecipePreset): RecipeDefinition = when (preset) {
        RecipePreset.CLEAN_PRODUCT -> RecipeDefinition(mapOf(
            AdjustmentType.EXPOSURE to 0.16f, AdjustmentType.CONTRAST to 8f, AdjustmentType.WHITES to 13f,
            AdjustmentType.BLACKS to -5f, AdjustmentType.VIBRANCE to 8f, AdjustmentType.CLARITY to 12f,
            AdjustmentType.SHARPNESS to 16f,
        ), FilterPreset.BRIGHT, 18f, null)
        RecipePreset.WARM_PORTRAIT -> RecipeDefinition(mapOf(
            AdjustmentType.EXPOSURE to 0.12f, AdjustmentType.HIGHLIGHTS to -18f, AdjustmentType.SHADOWS to 12f,
            AdjustmentType.WARMTH to 11f, AdjustmentType.TINT to 3f, AdjustmentType.VIBRANCE to 9f,
            AdjustmentType.CLARITY to -8f, AdjustmentType.SHARPNESS to 8f,
        ), FilterPreset.WARM, 22f, EffectSettings(CreativeEffect.BLOOM, 10f, 52f))
        RecipePreset.CINEMATIC_STREET -> RecipeDefinition(mapOf(
            AdjustmentType.CONTRAST to 16f, AdjustmentType.HIGHLIGHTS to -20f, AdjustmentType.SHADOWS to 8f,
            AdjustmentType.SATURATION to -8f, AdjustmentType.DEHAZE to 10f, AdjustmentType.GRAIN to 8f,
            AdjustmentType.VIGNETTE to 12f,
        ), FilterPreset.CINEMATIC, 65f, null)
        RecipePreset.FOOD_POP -> RecipeDefinition(mapOf(
            AdjustmentType.EXPOSURE to 0.1f, AdjustmentType.CONTRAST to 12f, AdjustmentType.VIBRANCE to 24f,
            AdjustmentType.WARMTH to 9f, AdjustmentType.CLARITY to 18f, AdjustmentType.SHARPNESS to 12f,
        ), FilterPreset.VIVID, 30f, null)
        RecipePreset.NIGHT_RESCUE -> RecipeDefinition(mapOf(
            AdjustmentType.EXPOSURE to 0.34f, AdjustmentType.SHADOWS to 34f, AdjustmentType.HIGHLIGHTS to -25f,
            AdjustmentType.NOISE_REDUCTION to 32f, AdjustmentType.DEHAZE to 10f, AdjustmentType.VIBRANCE to 14f,
            AdjustmentType.BLACKS to -8f,
        ), FilterPreset.COOL, 12f, null)
        RecipePreset.SOFT_FILM -> RecipeDefinition(mapOf(
            AdjustmentType.CONTRAST to -10f, AdjustmentType.FADE to 30f, AdjustmentType.SATURATION to -14f,
            AdjustmentType.GRAIN to 14f, AdjustmentType.WARMTH to 5f, AdjustmentType.HIGHLIGHTS to -10f,
        ), FilterPreset.FADED_FILM, 40f, null)
        RecipePreset.DOCUMENT_SCAN -> RecipeDefinition(mapOf(
            AdjustmentType.SATURATION to -100f, AdjustmentType.CONTRAST to 44f, AdjustmentType.WHITES to 34f,
            AdjustmentType.BLACKS to -38f, AdjustmentType.CLARITY to 28f, AdjustmentType.SHARPNESS to 24f,
        ), FilterPreset.ORIGINAL, 0f, null)
        RecipePreset.SOCIAL_VIVID -> RecipeDefinition(mapOf(
            AdjustmentType.EXPOSURE to 0.14f, AdjustmentType.CONTRAST to 14f, AdjustmentType.VIBRANCE to 25f,
            AdjustmentType.CLARITY to 12f, AdjustmentType.SHARPNESS to 13f, AdjustmentType.VIGNETTE to 6f,
        ), FilterPreset.VIVID, 42f, null)
    }

    private fun drawProductBackground(canvas: Canvas, width: Int, height: Int, settings: ProductStudioSettings) {
        if (settings.useGradient) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), settings.backgroundColor, settings.gradientColor, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        } else canvas.drawColor(settings.backgroundColor)
    }

    private fun alphaBounds(mask: Bitmap): Rect {
        val width = mask.width
        val height = mask.height
        val row = IntArray(width)
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        for (y in 0 until height) {
            mask.getPixels(row, 0, width, 0, y, width, 1)
            for (x in row.indices) if (Color.alpha(row[x]) > 24) {
                left = min(left, x); top = min(top, y); right = max(right, x + 1); bottom = max(bottom, y + 1)
            }
        }
        return if (right <= left || bottom <= top) Rect(0, 0, width, height) else Rect(left, top, right, bottom)
    }

    private fun fitOnCanvas(source: Bitmap, width: Int, height: Int, color: Int): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(color)
        val scale = min(width.toFloat() / source.width, height.toFloat() / source.height)
        val drawW = source.width * scale
        val drawH = source.height * scale
        val dst = RectF((width - drawW) / 2f, (height - drawH) / 2f, (width + drawW) / 2f, (height + drawH) / 2f)
        canvas.drawBitmap(source, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun centerCrop(source: Bitmap, width: Int, height: Int): Bitmap {
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height)
        val scaledW = max(1, (source.width * scale).roundToInt())
        val scaledH = max(1, (source.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        val left = ((scaledW - width) / 2).coerceAtLeast(0)
        val top = ((scaledH - height) / 2).coerceAtLeast(0)
        val result = Bitmap.createBitmap(scaled, left, top, min(width, scaled.width - left), min(height, scaled.height - top))
        if (scaled !== source && scaled !== result && !scaled.isRecycled) scaled.recycle()
        return if (result.width == width && result.height == height) result else fitOnCanvas(result, width, height, Color.BLACK).also { result.recycle() }
    }

    private fun fastBlur(source: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return source.copyArgb()
        val factor = (1f / (1f + radius * 0.12f)).coerceIn(0.06f, 0.7f)
        val small = Bitmap.createScaledBitmap(source, max(1, (source.width * factor).roundToInt()), max(1, (source.height * factor).roundToInt()), true)
        val result = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        if (small !== source && small !== result && !small.isRecycled) small.recycle()
        return result
    }

    private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) return if (value < edge0) 0f else 1f
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return min(d, 360f - d)
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)

    private data class RecipeDefinition(
        val adjustments: Map<AdjustmentType, Float>,
        val filter: FilterPreset,
        val filterIntensity: Float,
        val effect: EffectSettings?,
    )

    private const val AUTO_MASK_MAX_PIXELS = 900_000L
}
