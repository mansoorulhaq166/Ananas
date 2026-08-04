package com.example.ananas.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ImageProcessor {

    suspend fun copy(source: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        source.copyArgb()
    }

    suspend fun applyAdjustments(
        source: Bitmap,
        values: Map<AdjustmentType, Float>
    ): Bitmap = withContext(Dispatchers.Default) {
        var current = source
        var ownsCurrent = false

        AdjustmentType.entries.forEach { type ->
            val value = values[type] ?: type.default
            if (value != type.default) {
                val next = applyAdjustmentInternal(current, type, value)
                if (ownsCurrent && current !== source && !current.isRecycled) current.recycle()
                current = next
                ownsCurrent = true
            }
        }

        if (ownsCurrent) current else source.copyArgb()
    }

    suspend fun createPreview(source: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val dimensionScale = min(
            PREVIEW_MAX_DIMENSION.toFloat() / source.width.coerceAtLeast(1),
            PREVIEW_MAX_DIMENSION.toFloat() / source.height.coerceAtLeast(1)
        )
        val pixelScale = kotlin.math.sqrt(
            PREVIEW_MAX_PIXELS.toDouble() / (source.width.toLong() * source.height).coerceAtLeast(1L)
        ).toFloat()
        val scale = min(1f, min(dimensionScale, pixelScale))
        if (scale >= 0.999f) {
            source.copyArgb()
        } else {
            Bitmap.createScaledBitmap(
                source,
                max(1, (source.width * scale).roundToInt()),
                max(1, (source.height * scale).roundToInt()),
                true
            )
        }
    }

    suspend fun applyFilter(
        source: Bitmap,
        preset: FilterPreset,
        intensity: Float = 100f,
    ): Bitmap = withContext(Dispatchers.Default) {
        val filtered = when (preset) {
            FilterPreset.ORIGINAL -> source.copyArgb()
            FilterPreset.VIVID -> applyMatrices(
                source,
                saturationMatrix(1.35f),
                contrastMatrix(1.12f),
                channelOffsetMatrix(8f, 2f, -2f),
            )
            FilterPreset.BRIGHT -> applyMatrices(
                source,
                contrastMatrix(0.94f),
                channelOffsetMatrix(22f, 22f, 22f),
            )
            FilterPreset.HIGH_CONTRAST -> applyMatrices(
                source,
                saturationMatrix(1.08f),
                contrastMatrix(1.26f),
            )
            FilterPreset.WARM -> applyMatrices(
                source,
                saturationMatrix(1.08f),
                channelOffsetMatrix(18f, 6f, -16f),
            )
            FilterPreset.COOL -> applyMatrices(
                source,
                saturationMatrix(1.04f),
                channelOffsetMatrix(-10f, 1f, 18f),
            )
            FilterPreset.VINTAGE -> applyMatrices(
                source,
                saturationMatrix(0.72f),
                contrastMatrix(0.86f),
                channelOffsetMatrix(15f, 13f, 10f),
            )
            FilterPreset.GRAYSCALE -> applyMatrices(
                source,
                saturationMatrix(0f),
                contrastMatrix(1.18f),
            )
            FilterPreset.SEPIA -> applyColorMatrix(
                source,
                ColorMatrix(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 0f,
                        0.349f, 0.686f, 0.168f, 0f, 0f,
                        0.272f, 0.534f, 0.131f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            FilterPreset.INVERT -> applyColorMatrix(
                source,
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            FilterPreset.NOSTALGIA -> applyMatrices(
                source,
                saturationMatrix(0.92f),
                contrastMatrix(1.18f),
                ColorMatrix(
                    floatArrayOf(
                        1.04f, 0f, 0f, 0f, 6f,
                        0f, 1.00f, 0f, 0f, -2f,
                        0f, 0f, 0.92f, 0f, -8f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            FilterPreset.PUNCH -> {
                val punched = applyMatrices(source, saturationMatrix(1.45f), contrastMatrix(1.22f))
                vignette(punched, 0.22f).also { result ->
                    if (punched !== result && !punched.isRecycled) punched.recycle()
                }
            }
            FilterPreset.CINEMATIC -> {
                val base = applyMatrices(source, saturationMatrix(0.88f), contrastMatrix(1.16f))
                AdvancedImageProcessor.applyColorGrade(
                    base,
                    ColorGradeSettings(
                        shadowHue = 205f,
                        shadowSaturation = 35f,
                        midtoneHue = 28f,
                        midtoneSaturation = 8f,
                        highlightHue = 42f,
                        highlightSaturation = 28f,
                        balance = 4f,
                        blending = 62f,
                    ),
                ).also { if (!base.isRecycled) base.recycle() }
            }
            FilterPreset.MATTE -> {
                val base = applyMatrices(source, saturationMatrix(0.86f), contrastMatrix(0.88f))
                AdvancedImageProcessor.fade(base, 45f).also { if (!base.isRecycled) base.recycle() }
            }
            FilterPreset.TEAL_ORANGE -> AdvancedImageProcessor.applyColorGrade(
                source,
                ColorGradeSettings(
                    shadowHue = 192f,
                    shadowSaturation = 48f,
                    midtoneHue = 24f,
                    midtoneSaturation = 12f,
                    highlightHue = 35f,
                    highlightSaturation = 42f,
                    balance = 7f,
                    blending = 70f,
                ),
            )
            FilterPreset.FADED_FILM -> {
                val base = applyMatrices(source, saturationMatrix(0.78f), contrastMatrix(0.92f), channelOffsetMatrix(8f, 5f, 1f))
                val faded = AdvancedImageProcessor.fade(base, 58f)
                if (!base.isRecycled) base.recycle()
                AdvancedImageProcessor.grain(faded, 18f, 83).also { if (!faded.isRecycled) faded.recycle() }
            }
            FilterPreset.NOIR -> {
                val mono = applyMatrices(source, saturationMatrix(0f), contrastMatrix(1.42f))
                AdvancedImageProcessor.signedVignette(mono, 42f).also { if (!mono.isRecycled) mono.recycle() }
            }
            FilterPreset.DREAM -> {
                val soft = applyMatrices(source, saturationMatrix(0.86f), contrastMatrix(0.9f), channelOffsetMatrix(15f, 8f, 17f))
                AdvancedImageProcessor.applyCreativeEffect(
                    soft,
                    EffectSettings(CreativeEffect.BLOOM, amount = 38f, secondary = 56f),
                ).also { if (!soft.isRecycled) soft.recycle() }
            }
            FilterPreset.CYANOTYPE -> applyColorMatrix(
                source,
                ColorMatrix(
                    floatArrayOf(
                        0.12f, 0.18f, 0.18f, 0f, 8f,
                        0.08f, 0.34f, 0.25f, 0f, 18f,
                        0.12f, 0.32f, 0.58f, 0f, 38f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            FilterPreset.SUNSET -> {
                val warm = applyMatrices(source, saturationMatrix(1.18f), contrastMatrix(1.08f), channelOffsetMatrix(20f, 3f, -18f))
                AdvancedImageProcessor.applyColorGrade(
                    warm,
                    ColorGradeSettings(
                        shadowHue = 288f,
                        shadowSaturation = 18f,
                        midtoneHue = 18f,
                        midtoneSaturation = 24f,
                        highlightHue = 42f,
                        highlightSaturation = 44f,
                        balance = 12f,
                    ),
                ).also { if (!warm.isRecycled) warm.recycle() }
            }
        }

        val mix = intensity.coerceIn(0f, 100f)
        if (preset == FilterPreset.ORIGINAL || mix >= 99.9f) {
            filtered
        } else {
            AdvancedImageProcessor.blend(source, filtered, mix).also {
                if (!filtered.isRecycled) filtered.recycle()
            }
        }
    }

    suspend fun applyBeauty(source: Bitmap, smooth: Float, whiten: Float): Bitmap =
        withContext(Dispatchers.Default) {
            val smoothAmount = smooth.coerceIn(0f, 100f) / 100f
            val whitenAmount = whiten.coerceIn(0f, 100f) / 100f
            if (smoothAmount == 0f && whitenAmount == 0f) return@withContext source.copyArgb()

            val softened = if (smoothAmount > 0f) {
                blur(source, 0.10f + smoothAmount * 0.24f)
            } else {
                null
            }
            val width = source.width
            val height = source.height
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val originalRow = IntArray(width)
            val softRow = if (softened != null) IntArray(width) else null

            for (y in 0 until height) {
                if (y % 24 == 0) currentCoroutineContext().ensureActive()
                source.getPixels(originalRow, 0, width, 0, y, width, 1)
                softened?.getPixels(softRow!!, 0, width, 0, y, width, 1)
                for (x in originalRow.indices) {
                    val original = originalRow[x]
                    val alpha = Color.alpha(original)
                    var red = Color.red(original).toFloat()
                    var green = Color.green(original).toFloat()
                    var blue = Color.blue(original).toFloat()

                    if (alpha > 0 && isLikelySkin(red, green, blue)) {
                        if (softened != null) {
                            val soft = softRow!![x]
                            val softRed = Color.red(soft).toFloat()
                            val softGreen = Color.green(soft).toFloat()
                            val softBlue = Color.blue(soft).toFloat()
                            val edgeDifference = (
                                kotlin.math.abs(red - softRed) +
                                    kotlin.math.abs(green - softGreen) +
                                    kotlin.math.abs(blue - softBlue)
                                ) / 3f
                            val edgeProtection = (1f - edgeDifference / 92f).coerceIn(0.12f, 1f)
                            val blend = smoothAmount * 0.72f * edgeProtection
                            red += (softRed - red) * blend
                            green += (softGreen - green) * blend
                            blue += (softBlue - blue) * blend
                        }

                        if (whitenAmount > 0f) {
                            val lift = whitenAmount * 0.22f
                            red += (255f - red) * lift
                            green += (255f - green) * lift
                            blue += (255f - blue) * lift
                        }
                    }

                    originalRow[x] = Color.argb(
                        alpha,
                        red.roundToInt().coerceIn(0, 255),
                        green.roundToInt().coerceIn(0, 255),
                        blue.roundToInt().coerceIn(0, 255)
                    )
                }
                output.setPixels(originalRow, 0, width, 0, y, width, 1)
            }

            softened?.let { if (!it.isRecycled) it.recycle() }
            output
        }

    suspend fun rotate(source: Bitmap, degrees: Float): Bitmap = withContext(Dispatchers.Default) {
        if (degrees % 360f == 0f) return@withContext source.copyArgb()
        val matrix = Matrix().apply { postRotate(degrees) }
        ensureDistinct(Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true), source)
    }

    suspend fun flip(source: Bitmap, horizontal: Boolean): Bitmap = withContext(Dispatchers.Default) {
        val matrix = Matrix().apply {
            setScale(if (horizontal) -1f else 1f, if (horizontal) 1f else -1f)
        }
        ensureDistinct(Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true), source)
    }

    suspend fun crop(source: Bitmap, selection: CropSelection): Bitmap = withContext(Dispatchers.Default) {
        val normalizedLeft = selection.left.coerceIn(0f, 0.99f)
        val normalizedTop = selection.top.coerceIn(0f, 0.99f)
        val normalizedRight = selection.right.coerceIn(normalizedLeft + 0.01f, 1f)
        val normalizedBottom = selection.bottom.coerceIn(normalizedTop + 0.01f, 1f)
        val rawLeft = (normalizedLeft * source.width).roundToInt()
        val rawTop = (normalizedTop * source.height).roundToInt()
        val rawRight = (normalizedRight * source.width).roundToInt()
        val rawBottom = (normalizedBottom * source.height).roundToInt()
        val left = rawLeft.coerceIn(0, source.width - 1)
        val top = rawTop.coerceIn(0, source.height - 1)
        val width = (rawRight - left).coerceIn(1, source.width - left)
        val height = (rawBottom - top).coerceIn(1, source.height - top)
        ensureDistinct(Bitmap.createBitmap(source, left, top, width, height), source)
    }

    suspend fun resize(source: Bitmap, width: Int, height: Int): Bitmap =
        withContext(Dispatchers.Default) {
            ensureDistinct(
                Bitmap.createScaledBitmap(
                    source,
                    width.coerceIn(1, 8192),
                    height.coerceIn(1, 8192),
                    true
                ),
                source
            )
        }

    suspend fun applyTextElements(source: Bitmap, elements: List<TextElement>): Bitmap =
        withContext(Dispatchers.Default) {
            val output = source.copyArgb(mutable = true)
            val canvas = Canvas(output)
            val minDimension = min(output.width, output.height).toFloat().coerceAtLeast(1f)

            elements.forEach { element ->
                if (element.text.isBlank()) return@forEach
                val alpha = (element.opacity.coerceIn(0f, 1f) * 255).roundToInt()
                val textSize = minDimension * element.sizeFraction * element.scale.coerceIn(0.25f, 6f)
                val typeface = when (element.style) {
                    TextStyleOption.SANS -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    TextStyleOption.SERIF -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                    TextStyleOption.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    TextStyleOption.BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                    color = element.color
                    this.alpha = alpha
                    this.textSize = textSize
                    this.typeface = typeface
                    textAlign = when (element.alignment) {
                        TextAlignmentOption.LEFT -> Paint.Align.LEFT
                        TextAlignmentOption.CENTER -> Paint.Align.CENTER
                        TextAlignmentOption.RIGHT -> Paint.Align.RIGHT
                    }
                    if (element.shadowColor != Color.TRANSPARENT && element.shadowBlurFraction > 0f) {
                        setShadowLayer(
                            textSize * element.shadowBlurFraction.coerceIn(0f, 0.3f),
                            textSize * 0.035f,
                            textSize * 0.045f,
                            element.shadowColor,
                        )
                    }
                }
                val strokePaint = Paint(fillPaint).apply {
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = (minDimension * element.strokeWidthFraction).coerceAtLeast(0f)
                    color = element.strokeColor
                    clearShadowLayer()
                }
                val lines = element.text.lines().ifEmpty { listOf(element.text) }
                val maxLineWidth = lines.maxOfOrNull(fillPaint::measureText) ?: 0f
                val lineHeight = fillPaint.fontSpacing
                val blockHeight = max(lineHeight, lines.size * lineHeight)
                val x = element.centerX.coerceIn(0f, 1f) * output.width
                val y = element.centerY.coerceIn(0f, 1f) * output.height
                val anchorX = when (element.alignment) {
                    TextAlignmentOption.LEFT -> x - maxLineWidth / 2f
                    TextAlignmentOption.CENTER -> x
                    TextAlignmentOption.RIGHT -> x + maxLineWidth / 2f
                }
                val firstBaseline = y - (lines.size - 1) * lineHeight / 2f - (fillPaint.ascent() + fillPaint.descent()) / 2f
                val padding = textSize * 0.28f

                canvas.save()
                canvas.rotate(element.rotation, x, y)
                if (element.backgroundColor != Color.TRANSPARENT && element.backgroundOpacity > 0f) {
                    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = element.backgroundColor
                        this.alpha = (element.backgroundOpacity.coerceIn(0f, 1f) * alpha).roundToInt()
                    }
                    canvas.drawRoundRect(
                        RectF(
                            x - maxLineWidth / 2f - padding,
                            y - blockHeight / 2f - padding * 0.6f,
                            x + maxLineWidth / 2f + padding,
                            y + blockHeight / 2f + padding * 0.6f,
                        ),
                        padding * 0.55f,
                        padding * 0.55f,
                        backgroundPaint,
                    )
                }
                lines.forEachIndexed { index, line ->
                    val baseline = firstBaseline + index * lineHeight
                    if (strokePaint.strokeWidth > 0f && element.strokeColor != Color.TRANSPARENT) {
                        canvas.drawText(line, anchorX, baseline, strokePaint)
                    }
                    canvas.drawText(line, anchorX, baseline, fillPaint)
                }
                canvas.restore()
            }
            output
        }

    suspend fun applyStickerElements(source: Bitmap, elements: List<StickerElement>): Bitmap =
        withContext(Dispatchers.Default) {
            val output = source.copyArgb(mutable = true)
            val canvas = Canvas(output)
            val minDimension = min(output.width, output.height).toFloat()
            elements.forEach { element ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    alpha = (element.opacity.coerceIn(0f, 1f) * 255).roundToInt()
                    textAlign = Paint.Align.CENTER
                    textSize = minDimension * element.sizeFraction * element.scale.coerceIn(0.25f, 6f)
                    typeface = Typeface.DEFAULT
                }
                val x = element.centerX.coerceIn(0f, 1f) * output.width
                val y = element.centerY.coerceIn(0f, 1f) * output.height
                canvas.save()
                canvas.rotate(element.rotation, x, y)
                canvas.drawText(element.value, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
                canvas.restore()
            }
            output
        }

    suspend fun applyDrawStrokes(source: Bitmap, strokes: List<DrawStroke>): Bitmap =
        withContext(Dispatchers.Default) {
            val overlay = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val overlayCanvas = Canvas(overlay)
            val minDimension = min(source.width, source.height).toFloat().coerceAtLeast(1f)

            strokes.forEach { stroke ->
                if (stroke.points.size < 2) return@forEach
                val path = Path().apply {
                    val first = stroke.points.first()
                    moveTo(first.x * source.width, first.y * source.height)
                    for (index in 1 until stroke.points.size) {
                        val previous = stroke.points[index - 1]
                        val current = stroke.points[index]
                        val midX = (previous.x + current.x) * 0.5f * source.width
                        val midY = (previous.y + current.y) * 0.5f * source.height
                        quadTo(previous.x * source.width, previous.y * source.height, midX, midY)
                    }
                    val last = stroke.points.last()
                    lineTo(last.x * source.width, last.y * source.height)
                }
                val baseWidth = max(1f, minDimension * stroke.widthFraction)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color
                    alpha = (stroke.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
                    style = Paint.Style.STROKE
                    strokeWidth = when (stroke.brushMode) {
                        BrushMode.SOLID -> baseWidth
                        BrushMode.HIGHLIGHTER -> baseWidth * 1.7f
                        BrushMode.NEON -> baseWidth
                    }
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    when {
                        stroke.erase -> xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                        stroke.brushMode == BrushMode.HIGHLIGHTER -> alpha = (alpha * 0.42f).roundToInt()
                        stroke.brushMode == BrushMode.NEON -> setShadowLayer(baseWidth * 1.6f, 0f, 0f, stroke.color)
                    }
                }
                if (stroke.brushMode == BrushMode.NEON && !stroke.erase) {
                    val glow = Paint(paint).apply {
                        alpha = (paint.alpha * 0.48f).roundToInt()
                        strokeWidth = baseWidth * 2.3f
                    }
                    overlayCanvas.drawPath(path, glow)
                    paint.clearShadowLayer()
                    paint.color = Color.WHITE
                    paint.strokeWidth = max(1f, baseWidth * 0.32f)
                }
                overlayCanvas.drawPath(path, paint)
                paint.xfermode = null
            }

            val output = source.copyArgb(mutable = true)
            Canvas(output).drawBitmap(overlay, 0f, 0f, null)
            overlay.recycle()
            output
        }

    suspend fun applyFrame(source: Bitmap, style: Int): Bitmap = withContext(Dispatchers.Default) {
        if (style == 0) return@withContext source.copyArgb()
        val output = source.copyArgb(mutable = true)
        val canvas = Canvas(output)
        val minDimension = min(source.width, source.height).toFloat()
        val width = minDimension * when (style) {
            1 -> 0.025f
            2 -> 0.055f
            else -> 0.035f
        }
        when (style) {
            1 -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    this.style = Paint.Style.STROKE
                    strokeWidth = width
                }
                canvas.drawRect(width / 2f, width / 2f, source.width - width / 2f, source.height - width / 2f, paint)
            }
            2 -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
                canvas.drawRect(0f, 0f, source.width.toFloat(), width, paint)
                canvas.drawRect(0f, source.height - width, source.width.toFloat(), source.height.toFloat(), paint)
                canvas.drawRect(0f, 0f, width, source.height.toFloat(), paint)
                canvas.drawRect(source.width - width, 0f, source.width.toFloat(), source.height.toFloat(), paint)
                val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    this.style = Paint.Style.STROKE
                    strokeWidth = width * 0.12f
                }
                canvas.drawRect(width, width, source.width - width, source.height - width, inner)
            }
            else -> {
                val gradient = LinearGradient(
                    0f,
                    0f,
                    source.width.toFloat(),
                    source.height.toFloat(),
                    intArrayOf(Color.rgb(255, 183, 77), Color.rgb(255, 105, 180), Color.rgb(126, 87, 194)),
                    null,
                    Shader.TileMode.CLAMP
                )
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = gradient
                    this.style = Paint.Style.STROKE
                    strokeWidth = width
                }
                canvas.drawRect(width / 2f, width / 2f, source.width - width / 2f, source.height - width / 2f, paint)
            }
        }
        output
    }


    private suspend fun applyAdjustmentInternal(
        source: Bitmap,
        type: AdjustmentType,
        value: Float
    ): Bitmap = when (type) {
        AdjustmentType.BRIGHTNESS -> brightness(source, value)
        AdjustmentType.CONTRAST -> contrast(source, value)
        AdjustmentType.EXPOSURE -> exposure(source, value)
        AdjustmentType.GAMMA -> AdvancedImageProcessor.gamma(source, value)
        AdjustmentType.HIGHLIGHTS -> highlightsShadows(source, highlights = value, shadows = 0f)
        AdjustmentType.SHADOWS -> highlightsShadows(source, highlights = 0f, shadows = value)
        AdjustmentType.WHITES -> AdvancedImageProcessor.whitesBlacks(source, whites = value, blacks = 0f)
        AdjustmentType.BLACKS -> AdvancedImageProcessor.whitesBlacks(source, whites = 0f, blacks = value)
        AdjustmentType.SATURATION -> saturation(source, value)
        AdjustmentType.VIBRANCE -> AdvancedImageProcessor.vibrance(source, value)
        AdjustmentType.WARMTH -> warmth(source, value)
        AdjustmentType.TINT -> tint(source, value)
        AdjustmentType.HUE -> AdvancedImageProcessor.hueRotate(source, value)
        AdjustmentType.CLARITY -> AdvancedImageProcessor.clarity(source, value)
        AdjustmentType.DEHAZE -> AdvancedImageProcessor.dehaze(source, value)
        AdjustmentType.SHARPNESS -> sharpen(source, value / 100f)
        AdjustmentType.NOISE_REDUCTION -> AdvancedImageProcessor.noiseReduction(source, value)
        AdjustmentType.BLUR -> blur(source, value / 100f)
        AdjustmentType.FADE -> AdvancedImageProcessor.fade(source, value)
        AdjustmentType.GRAIN -> AdvancedImageProcessor.grain(source, value)
        AdjustmentType.VIGNETTE -> AdvancedImageProcessor.signedVignette(source, value)
    }

    private fun ensureDistinct(result: Bitmap, source: Bitmap): Bitmap =
        if (result === source) source.copyArgb() else result

    private fun brightness(source: Bitmap, value: Float): Bitmap =
        applyColorMatrix(source, channelOffsetMatrix(value, value, value))

    private fun contrast(source: Bitmap, value: Float): Bitmap {
        val factor = 1f + value.coerceIn(-100f, 100f) / 100f
        return applyColorMatrix(source, contrastMatrix(factor))
    }

    private fun saturation(source: Bitmap, value: Float): Bitmap {
        val factor = 1f + value.coerceIn(-100f, 100f) / 100f
        return applyColorMatrix(source, saturationMatrix(factor))
    }

    private fun exposure(source: Bitmap, value: Float): Bitmap {
        val factor = 2.0.pow(value.coerceIn(-2f, 2f).toDouble()).toFloat()
        val matrix = ColorMatrix(
            floatArrayOf(
                factor, 0f, 0f, 0f, 0f,
                0f, factor, 0f, 0f, 0f,
                0f, 0f, factor, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return applyColorMatrix(source, matrix)
    }

    private fun warmth(source: Bitmap, value: Float): Bitmap {
        val amount = value.coerceIn(-100f, 100f) * 0.28f
        return applyColorMatrix(source, channelOffsetMatrix(amount, amount * 0.18f, -amount))
    }

    private fun tint(source: Bitmap, value: Float): Bitmap {
        val amount = value.coerceIn(-100f, 100f) * 0.24f
        return applyColorMatrix(source, channelOffsetMatrix(amount * 0.45f, -amount, amount * 0.45f))
    }

    private suspend fun highlightsShadows(source: Bitmap, highlights: Float, shadows: Float): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        val highlightAmount = highlights.coerceIn(-100f, 100f) / 100f
        val shadowAmount = shadows.coerceIn(-100f, 100f) / 100f

        for (y in 0 until height) {
            if (y % 24 == 0) currentCoroutineContext().ensureActive()
            source.getPixels(row, 0, width, 0, y, width, 1)
            for (x in row.indices) {
                val pixel = row[x]
                val a = Color.alpha(pixel)
                var r = Color.red(pixel).toFloat()
                var g = Color.green(pixel).toFloat()
                var b = Color.blue(pixel).toFloat()
                val luminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                val shadowWeight = (1f - luminance).pow(2)
                val highlightWeight = luminance.pow(2)
                val delta = shadowAmount * shadowWeight * 90f + highlightAmount * highlightWeight * 90f
                r = (r + delta).coerceIn(0f, 255f)
                g = (g + delta).coerceIn(0f, 255f)
                b = (b + delta).coerceIn(0f, 255f)
                row[x] = Color.argb(a, r.roundToInt(), g.roundToInt(), b.roundToInt())
            }
            output.setPixels(row, 0, width, 0, y, width, 1)
        }

        return output
    }

    private suspend fun sharpen(source: Bitmap, amount: Float): Bitmap {
        if (amount <= 0f) return source.copyArgb()
        val blurred = blur(source, 0.16f)
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val originalRow = IntArray(width)
        val blurredRow = IntArray(width)
        val strength = amount.coerceIn(0f, 1f) * 1.8f

        for (y in 0 until height) {
            if (y % 24 == 0) currentCoroutineContext().ensureActive()
            source.getPixels(originalRow, 0, width, 0, y, width, 1)
            blurred.getPixels(blurredRow, 0, width, 0, y, width, 1)
            for (x in originalRow.indices) {
                val original = originalRow[x]
                val soft = blurredRow[x]
                val r = (Color.red(original) + strength * (Color.red(original) - Color.red(soft))).roundToInt().coerceIn(0, 255)
                val g = (Color.green(original) + strength * (Color.green(original) - Color.green(soft))).roundToInt().coerceIn(0, 255)
                val b = (Color.blue(original) + strength * (Color.blue(original) - Color.blue(soft))).roundToInt().coerceIn(0, 255)
                originalRow[x] = Color.argb(Color.alpha(original), r, g, b)
            }
            output.setPixels(originalRow, 0, width, 0, y, width, 1)
        }
        if (!blurred.isRecycled) blurred.recycle()
        return output
    }

    private fun blur(source: Bitmap, amount: Float): Bitmap {
        if (amount <= 0f) return source.copyArgb()
        val factor = (1f - amount.coerceIn(0f, 1f) * 0.94f).coerceAtLeast(0.06f)
        val smallWidth = max(1, (source.width * factor).roundToInt())
        val smallHeight = max(1, (source.height * factor).roundToInt())
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        val scaled = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        val output = ensureDistinct(scaled, source)
        if (small !== source && small !== output && !small.isRecycled) small.recycle()
        return output
    }

    private suspend fun vignette(source: Bitmap, amount: Float): Bitmap {
        if (amount <= 0f) return source.copyArgb()
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        val centerX = (width - 1) / 2f
        val centerY = (height - 1) / 2f
        val maxDistance = sqrt(centerX * centerX + centerY * centerY).coerceAtLeast(1f)
        val strength = amount.coerceIn(0f, 1f) * 0.88f

        for (y in 0 until height) {
            if (y % 24 == 0) currentCoroutineContext().ensureActive()
            source.getPixels(row, 0, width, 0, y, width, 1)
            val dy = y - centerY
            for (x in row.indices) {
                val pixel = row[x]
                val dx = x - centerX
                val normalized = (sqrt(dx * dx + dy * dy) / maxDistance).coerceIn(0f, 1f)
                val edge = ((normalized - 0.38f) / 0.62f).coerceIn(0f, 1f)
                val smooth = edge * edge * (3f - 2f * edge)
                val factor = 1f - strength * smooth
                row[x] = Color.argb(
                    Color.alpha(pixel),
                    (Color.red(pixel) * factor).roundToInt().coerceIn(0, 255),
                    (Color.green(pixel) * factor).roundToInt().coerceIn(0, 255),
                    (Color.blue(pixel) * factor).roundToInt().coerceIn(0, 255)
                )
            }
            output.setPixels(row, 0, width, 0, y, width, 1)
        }
        return output
    }

    private fun isLikelySkin(red: Float, green: Float, blue: Float): Boolean {
        val cb = 128f - 0.168736f * red - 0.331264f * green + 0.5f * blue
        val cr = 128f + 0.5f * red - 0.418688f * green - 0.081312f * blue
        val chromaRange = cb in 72f..138f && cr in 122f..184f
        val hasColorSeparation = max(red, max(green, blue)) - min(red, min(green, blue)) > 8f
        return red > 24f && green > 14f && blue > 8f && chromaRange && hasColorSeparation
    }

    private fun applyMatrices(source: Bitmap, vararg matrices: ColorMatrix): Bitmap {
        val combined = ColorMatrix()
        matrices.forEach { combined.postConcat(it) }
        return applyColorMatrix(source, combined)
    }

    private fun applyColorMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(output).drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun saturationMatrix(factor: Float): ColorMatrix = ColorMatrix().apply {
        setSaturation(factor.coerceAtLeast(0f))
    }

    private fun contrastMatrix(factor: Float): ColorMatrix {
        val translation = (-0.5f * factor + 0.5f) * 255f
        return ColorMatrix(
            floatArrayOf(
                factor, 0f, 0f, 0f, translation,
                0f, factor, 0f, 0f, translation,
                0f, 0f, factor, 0f, translation,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun channelOffsetMatrix(red: Float, green: Float, blue: Float): ColorMatrix =
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, red,
                0f, 1f, 0f, 0f, green,
                0f, 0f, 1f, 0f, blue,
                0f, 0f, 0f, 1f, 0f
            )
        )

    private const val PREVIEW_MAX_DIMENSION = 2048
    private const val PREVIEW_MAX_PIXELS = 2_000_000L

}
