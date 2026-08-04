package com.example.ananas.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** CPU-based advanced effects used for both interactive previews and full-resolution commits. */
object AdvancedImageProcessor {

    suspend fun blend(source: Bitmap, effect: Bitmap, intensity: Float): Bitmap =
        withContext(Dispatchers.Default) {
            val mix = (intensity / 100f).coerceIn(0f, 1f)
            if (mix <= 0f) return@withContext source.copyArgb()
            if (mix >= 1f) return@withContext effect.copyArgb()

            val width = min(source.width, effect.width)
            val height = min(source.height, effect.height)
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val sourceRow = IntArray(width)
            val effectRow = IntArray(width)
            for (y in 0 until height) {
                if (y % 32 == 0) currentCoroutineContext().ensureActive()
                source.getPixels(sourceRow, 0, width, 0, y, width, 1)
                effect.getPixels(effectRow, 0, width, 0, y, width, 1)
                for (x in sourceRow.indices) {
                    val a = sourceRow[x]
                    val b = effectRow[x]
                    sourceRow[x] = Color.argb(
                        lerp(Color.alpha(a), Color.alpha(b), mix),
                        lerp(Color.red(a), Color.red(b), mix),
                        lerp(Color.green(a), Color.green(b), mix),
                        lerp(Color.blue(a), Color.blue(b), mix),
                    )
                }
                output.setPixels(sourceRow, 0, width, 0, y, width, 1)
            }
            output
        }

    suspend fun applyToneCurve(source: Bitmap, settings: ToneCurveSettings): Bitmap =
        withContext(Dispatchers.Default) {
            if (settings.isIdentity()) return@withContext source.copyArgb()
            val master = buildCurveLut(settings.points(CurveChannel.MASTER))
            val red = buildCurveLut(settings.points(CurveChannel.RED))
            val green = buildCurveLut(settings.points(CurveChannel.GREEN))
            val blue = buildCurveLut(settings.points(CurveChannel.BLUE))
            val width = source.width
            val height = source.height
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val row = IntArray(width)
            for (y in 0 until height) {
                if (y % 32 == 0) currentCoroutineContext().ensureActive()
                source.getPixels(row, 0, width, 0, y, width, 1)
                for (x in row.indices) {
                    val pixel = row[x]
                    row[x] = Color.argb(
                        Color.alpha(pixel),
                        red[master[Color.red(pixel)]],
                        green[master[Color.green(pixel)]],
                        blue[master[Color.blue(pixel)]],
                    )
                }
                output.setPixels(row, 0, width, 0, y, width, 1)
            }
            output
        }

    suspend fun applySelectiveHsl(source: Bitmap, settings: SelectiveHslSettings): Bitmap =
        withContext(Dispatchers.Default) {
            if (settings.isIdentity()) return@withContext source.copyArgb()
            val active = settings.ranges.filterValues { !it.isIdentity() }
            val width = source.width
            val height = source.height
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val row = IntArray(width)
            for (y in 0 until height) {
                if (y % 20 == 0) currentCoroutineContext().ensureActive()
                source.getPixels(row, 0, width, 0, y, width, 1)
                for (x in row.indices) {
                    val pixel = row[x]
                    val hsv = rgbToHsv(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                    var hueShift = 0f
                    var saturationShift = 0f
                    var luminanceShift = 0f
                    var totalWeight = 0f
                    active.forEach { (range, adjustment) ->
                        val distance = circularHueDistance(hsv[0], range.centerHue)
                        val weight = (1f - distance / 52f).coerceIn(0f, 1f).let(::smoothStep)
                        if (weight > 0f) {
                            hueShift += adjustment.hue * 0.6f * weight
                            saturationShift += adjustment.saturation / 100f * weight
                            luminanceShift += adjustment.luminance / 100f * weight
                            totalWeight += weight
                        }
                    }
                    if (totalWeight > 0f) {
                        val normalized = max(1f, totalWeight)
                        hsv[0] = wrapHue(hsv[0] + hueShift / normalized)
                        hsv[1] = (hsv[1] + saturationShift / normalized * (1f - hsv[1] * 0.35f)).coerceIn(0f, 1f)
                        hsv[2] = adjustLuminance(hsv[2], luminanceShift / normalized)
                    }
                    row[x] = hsvToColor(Color.alpha(pixel), hsv[0], hsv[1], hsv[2])
                }
                output.setPixels(row, 0, width, 0, y, width, 1)
            }
            output
        }

    suspend fun applyColorGrade(source: Bitmap, settings: ColorGradeSettings): Bitmap =
        withContext(Dispatchers.Default) {
            if (settings.isIdentity()) return@withContext source.copyArgb()
            val shadowColor = hueVector(settings.shadowHue)
            val midColor = hueVector(settings.midtoneHue)
            val highlightColor = hueVector(settings.highlightHue)
            val balance = settings.balance.coerceIn(-100f, 100f) / 220f
            val overlap = 0.8f + settings.blending.coerceIn(0f, 100f) / 100f * 1.6f
            val width = source.width
            val height = source.height
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val row = IntArray(width)

            for (y in 0 until height) {
                if (y % 24 == 0) currentCoroutineContext().ensureActive()
                source.getPixels(row, 0, width, 0, y, width, 1)
                for (x in row.indices) {
                    val pixel = row[x]
                    var red = Color.red(pixel).toFloat()
                    var green = Color.green(pixel).toFloat()
                    var blue = Color.blue(pixel).toFloat()
                    val luma = ((0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f + balance).coerceIn(0f, 1f)
                    val shadowWeight = (1f - luma).pow(overlap)
                    val highlightWeight = luma.pow(overlap)
                    val midWeight = (1f - abs(luma - 0.5f) * 2f).coerceIn(0f, 1f).pow(1.3f)
                    val shadowStrength = settings.shadowSaturation.coerceIn(0f, 100f) / 100f * shadowWeight
                    val midStrength = settings.midtoneSaturation.coerceIn(0f, 100f) / 100f * midWeight
                    val highlightStrength = settings.highlightSaturation.coerceIn(0f, 100f) / 100f * highlightWeight
                    red += colorGradeDelta(shadowColor[0], shadowStrength) + colorGradeDelta(midColor[0], midStrength) + colorGradeDelta(highlightColor[0], highlightStrength)
                    green += colorGradeDelta(shadowColor[1], shadowStrength) + colorGradeDelta(midColor[1], midStrength) + colorGradeDelta(highlightColor[1], highlightStrength)
                    blue += colorGradeDelta(shadowColor[2], shadowStrength) + colorGradeDelta(midColor[2], midStrength) + colorGradeDelta(highlightColor[2], highlightStrength)
                    row[x] = Color.argb(
                        Color.alpha(pixel),
                        red.roundToInt().coerceIn(0, 255),
                        green.roundToInt().coerceIn(0, 255),
                        blue.roundToInt().coerceIn(0, 255),
                    )
                }
                output.setPixels(row, 0, width, 0, y, width, 1)
            }
            output
        }

    suspend fun applyCreativeEffect(source: Bitmap, settings: EffectSettings): Bitmap =
        withContext(Dispatchers.Default) {
            val amount = settings.amount.coerceIn(0f, 100f)
            if (amount == 0f) return@withContext source.copyArgb()
            when (settings.effect) {
                CreativeEffect.BLOOM -> bloom(source, amount, settings.secondary)
                CreativeEffect.PIXELATE -> pixelate(source, amount)
                CreativeEffect.POSTERIZE -> posterize(source, amount, settings.secondary)
                CreativeEffect.CHROMATIC -> chromatic(source, amount, settings.secondary)
                CreativeEffect.GLITCH -> glitch(source, amount, settings.secondary)
                CreativeEffect.DUOTONE -> duotone(source, amount, settings.secondary)
                CreativeEffect.FILM_GRAIN -> grain(source, amount, settings.secondary.roundToInt())
                CreativeEffect.SOLARIZE -> solarize(source, amount, settings.secondary)
            }
        }

    suspend fun applyLens(source: Bitmap, settings: LensSettings): Bitmap =
        withContext(Dispatchers.Default) {
            if (settings.isIdentity()) return@withContext source.copyArgb()
            val width = source.width
            val height = source.height
            val input = IntArray(width * height)
            source.getPixels(input, 0, width, 0, 0, width, height)
            val outputPixels = IntArray(input.size)
            val centerX = (width - 1) / 2f
            val centerY = (height - 1) / 2f
            val maxRadius = (min(width, height) / 2f).coerceAtLeast(1f)
            val distortion = settings.distortion.coerceIn(-100f, 100f) / 100f * 0.72f
            val chromatic = settings.chromaticAberration.coerceIn(0f, 100f) / 100f * 0.025f
            val vignette = settings.edgeVignette.coerceIn(-100f, 100f) / 100f

            for (y in 0 until height) {
                if (y % 12 == 0) currentCoroutineContext().ensureActive()
                for (x in 0 until width) {
                    val nx = (x - centerX) / maxRadius
                    val ny = (y - centerY) / maxRadius
                    val radiusSquared = nx * nx + ny * ny
                    val radialScale = 1f + distortion * radiusSquared
                    val sourceX = centerX + nx * radialScale * maxRadius
                    val sourceY = centerY + ny * radialScale * maxRadius
                    val base = sampleBilinear(input, width, height, sourceX, sourceY)
                    val redSample = if (chromatic > 0f) {
                        sampleBilinear(input, width, height, centerX + (sourceX - centerX) * (1f + chromatic), centerY + (sourceY - centerY) * (1f + chromatic))
                    } else base
                    val blueSample = if (chromatic > 0f) {
                        sampleBilinear(input, width, height, centerX + (sourceX - centerX) * (1f - chromatic), centerY + (sourceY - centerY) * (1f - chromatic))
                    } else base
                    val edge = ((sqrt(radiusSquared).toFloat() - 0.45f) / 0.75f).coerceIn(0f, 1f).let(::smoothStep)
                    val factor = if (vignette >= 0f) 1f - vignette * edge * 0.75f else 1f + -vignette * edge * 0.4f
                    outputPixels[y * width + x] = Color.argb(
                        Color.alpha(base),
                        (Color.red(redSample) * factor).roundToInt().coerceIn(0, 255),
                        (Color.green(base) * factor).roundToInt().coerceIn(0, 255),
                        (Color.blue(blueSample) * factor).roundToInt().coerceIn(0, 255),
                    )
                }
            }
            Bitmap.createBitmap(outputPixels, width, height, Bitmap.Config.ARGB_8888)
        }

    suspend fun applyFocus(source: Bitmap, settings: FocusSettings): Bitmap =
        withContext(Dispatchers.Default) {
            if (settings.isIdentity()) return@withContext source.copyArgb()
            val blurred = fastBlur(source, settings.blur.coerceIn(0f, 100f) / 100f)
            val width = source.width
            val height = source.height
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val sourceRow = IntArray(width)
            val blurredRow = IntArray(width)
            val centerX = settings.centerX.coerceIn(0f, 1f)
            val centerY = settings.centerY.coerceIn(0f, 1f)
            val radius = settings.radius.coerceIn(0.04f, 0.85f)
            val feather = settings.feather.coerceIn(0.02f, 0.6f)
            val angle = settings.angle / 180f * PI.toFloat()
            val cosAngle = cos(angle)
            val sinAngle = sin(angle)
            for (y in 0 until height) {
                if (y % 24 == 0) currentCoroutineContext().ensureActive()
                source.getPixels(sourceRow, 0, width, 0, y, width, 1)
                blurred.getPixels(blurredRow, 0, width, 0, y, width, 1)
                val normalizedY = y.toFloat() / height.coerceAtLeast(1) - centerY
                for (x in sourceRow.indices) {
                    val normalizedX = x.toFloat() / width.coerceAtLeast(1) - centerX
                    val distance = when (settings.mode) {
                        FocusMode.RADIAL -> sqrt(normalizedX * normalizedX + normalizedY * normalizedY).toFloat()
                        FocusMode.LINEAR -> abs(normalizedX * -sinAngle + normalizedY * cosAngle)
                    }
                    val mix = smoothRange(radius, radius + feather, distance)
                    val original = sourceRow[x]
                    val soft = blurredRow[x]
                    sourceRow[x] = Color.argb(
                        Color.alpha(original),
                        lerp(Color.red(original), Color.red(soft), mix),
                        lerp(Color.green(original), Color.green(soft), mix),
                        lerp(Color.blue(original), Color.blue(soft), mix),
                    )
                }
                output.setPixels(sourceRow, 0, width, 0, y, width, 1)
            }
            if (!blurred.isRecycled) blurred.recycle()
            output
        }

    suspend fun applyPerspective(source: Bitmap, settings: PerspectiveSettings): Bitmap =
        withContext(Dispatchers.Default) {
            if (settings.isIdentity()) return@withContext source.copyArgb()
            val width = source.width.toFloat()
            val height = source.height.toFloat()
            val horizontal = settings.horizontal.coerceIn(-100f, 100f) / 100f
            val vertical = settings.vertical.coerceIn(-100f, 100f) / 100f
            val topInset = max(0f, horizontal) * width * 0.24f
            val bottomInset = max(0f, -horizontal) * width * 0.24f
            val leftInset = max(0f, vertical) * height * 0.24f
            val rightInset = max(0f, -vertical) * height * 0.24f
            val sourcePoints = floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height)
            val destinationPoints = floatArrayOf(
                topInset, leftInset,
                width - topInset, rightInset,
                width - bottomInset, height - rightInset,
                bottomInset, height - leftInset,
            )
            val matrix = Matrix().apply {
                setPolyToPoly(sourcePoints, 0, destinationPoints, 0, 4)
                postScale(
                    settings.scale.coerceIn(70f, 130f) / 100f,
                    settings.scale.coerceIn(70f, 130f) / 100f,
                    width / 2f,
                    height / 2f,
                )
                postRotate(settings.rotate.coerceIn(-45f, 45f), width / 2f, height / 2f)
            }
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { output ->
                Canvas(output).drawBitmap(
                    source,
                    matrix,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
            }
        }

    suspend fun vibrance(source: Bitmap, value: Float): Bitmap = withContext(Dispatchers.Default) {
        val amount = value.coerceIn(-100f, 100f) / 100f
        if (amount == 0f) return@withContext source.copyArgb()
        mapPixels(source) { pixel ->
            val r = Color.red(pixel).toFloat()
            val g = Color.green(pixel).toFloat()
            val b = Color.blue(pixel).toFloat()
            val maxChannel = max(r, max(g, b))
            val minChannel = min(r, min(g, b))
            val saturation = if (maxChannel == 0f) 0f else (maxChannel - minChannel) / maxChannel
            val strength = amount * (1f - saturation) * 1.35f
            val gray = 0.2126f * r + 0.7152f * g + 0.0722f * b
            Color.argb(
                Color.alpha(pixel),
                (gray + (r - gray) * (1f + strength)).roundToInt().coerceIn(0, 255),
                (gray + (g - gray) * (1f + strength)).roundToInt().coerceIn(0, 255),
                (gray + (b - gray) * (1f + strength)).roundToInt().coerceIn(0, 255),
            )
        }
    }

    suspend fun gamma(source: Bitmap, value: Float): Bitmap = withContext(Dispatchers.Default) {
        val gamma = 2.0.pow(value.coerceIn(-100f, 100f).toDouble() / 100.0).toFloat()
        if (abs(gamma - 1f) < 0.001f) return@withContext source.copyArgb()
        val lut = IntArray(256) { index ->
            (255f * (index / 255f).pow(1f / gamma)).roundToInt().coerceIn(0, 255)
        }
        mapPixels(source) { pixel ->
            Color.argb(Color.alpha(pixel), lut[Color.red(pixel)], lut[Color.green(pixel)], lut[Color.blue(pixel)])
        }
    }

    suspend fun hueRotate(source: Bitmap, value: Float): Bitmap = withContext(Dispatchers.Default) {
        val shift = value.coerceIn(-180f, 180f)
        if (shift == 0f) return@withContext source.copyArgb()
        mapPixels(source) { pixel ->
            val hsv = rgbToHsv(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            hsvToColor(Color.alpha(pixel), wrapHue(hsv[0] + shift), hsv[1], hsv[2])
        }
    }

    suspend fun whitesBlacks(source: Bitmap, whites: Float, blacks: Float): Bitmap =
        withContext(Dispatchers.Default) {
            val whiteAmount = whites.coerceIn(-100f, 100f) / 100f
            val blackAmount = blacks.coerceIn(-100f, 100f) / 100f
            if (whiteAmount == 0f && blackAmount == 0f) return@withContext source.copyArgb()
            mapPixels(source) { pixel ->
                val r = Color.red(pixel).toFloat()
                val g = Color.green(pixel).toFloat()
                val b = Color.blue(pixel).toFloat()
                val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                val whiteWeight = luma.pow(3)
                val blackWeight = (1f - luma).pow(3)
                val delta = whiteAmount * whiteWeight * 82f + blackAmount * blackWeight * 82f
                Color.argb(
                    Color.alpha(pixel),
                    (r + delta).roundToInt().coerceIn(0, 255),
                    (g + delta).roundToInt().coerceIn(0, 255),
                    (b + delta).roundToInt().coerceIn(0, 255),
                )
            }
        }

    suspend fun clarity(source: Bitmap, value: Float): Bitmap = withContext(Dispatchers.Default) {
        val amount = value.coerceIn(-100f, 100f) / 100f
        if (amount == 0f) return@withContext source.copyArgb()
        val blurred = fastBlur(source, 0.12f)
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val sourceRow = IntArray(width)
        val blurRow = IntArray(width)
        for (y in 0 until height) {
            if (y % 24 == 0) currentCoroutineContext().ensureActive()
            source.getPixels(sourceRow, 0, width, 0, y, width, 1)
            blurred.getPixels(blurRow, 0, width, 0, y, width, 1)
            for (x in sourceRow.indices) {
                val original = sourceRow[x]
                val soft = blurRow[x]
                val luma = (0.2126f * Color.red(original) + 0.7152f * Color.green(original) + 0.0722f * Color.blue(original)) / 255f
                val midWeight = (1f - abs(luma - 0.5f) * 1.7f).coerceIn(0.15f, 1f)
                val strength = amount * 1.6f * midWeight
                sourceRow[x] = Color.argb(
                    Color.alpha(original),
                    (Color.red(original) + (Color.red(original) - Color.red(soft)) * strength).roundToInt().coerceIn(0, 255),
                    (Color.green(original) + (Color.green(original) - Color.green(soft)) * strength).roundToInt().coerceIn(0, 255),
                    (Color.blue(original) + (Color.blue(original) - Color.blue(soft)) * strength).roundToInt().coerceIn(0, 255),
                )
            }
            output.setPixels(sourceRow, 0, width, 0, y, width, 1)
        }
        if (!blurred.isRecycled) blurred.recycle()
        output
    }

    suspend fun dehaze(source: Bitmap, value: Float): Bitmap = withContext(Dispatchers.Default) {
        val amount = value.coerceIn(-100f, 100f) / 100f
        if (amount == 0f) return@withContext source.copyArgb()
        mapPixels(source) { pixel ->
            val r = Color.red(pixel).toFloat()
            val g = Color.green(pixel).toFloat()
            val b = Color.blue(pixel).toFloat()
            val gray = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val contrast = 1f + amount * 0.65f
            val saturation = 1f + amount * 0.35f
            val blackPoint = amount * -12f
            val cr = ((r - 127.5f) * contrast + 127.5f + blackPoint)
            val cg = ((g - 127.5f) * contrast + 127.5f + blackPoint)
            val cb = ((b - 127.5f) * contrast + 127.5f + blackPoint)
            Color.argb(
                Color.alpha(pixel),
                (gray + (cr - gray) * saturation).roundToInt().coerceIn(0, 255),
                (gray + (cg - gray) * saturation).roundToInt().coerceIn(0, 255),
                (gray + (cb - gray) * saturation).roundToInt().coerceIn(0, 255),
            )
        }
    }

    suspend fun noiseReduction(source: Bitmap, value: Float): Bitmap = withContext(Dispatchers.Default) {
        val amount = value.coerceIn(0f, 100f) / 100f
        if (amount == 0f) return@withContext source.copyArgb()
        val softened = fastBlur(source, 0.06f + amount * 0.15f)
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val sourceRow = IntArray(width)
        val softRow = IntArray(width)
        for (y in 0 until height) {
            if (y % 24 == 0) currentCoroutineContext().ensureActive()
            source.getPixels(sourceRow, 0, width, 0, y, width, 1)
            softened.getPixels(softRow, 0, width, 0, y, width, 1)
            for (x in sourceRow.indices) {
                val original = sourceRow[x]
                val soft = softRow[x]
                val difference = (
                    abs(Color.red(original) - Color.red(soft)) +
                        abs(Color.green(original) - Color.green(soft)) +
                        abs(Color.blue(original) - Color.blue(soft))
                    ) / 3f
                val edgeProtection = (1f - difference / 52f).coerceIn(0.08f, 1f)
                val mix = amount * edgeProtection * 0.88f
                sourceRow[x] = Color.argb(
                    Color.alpha(original),
                    lerp(Color.red(original), Color.red(soft), mix),
                    lerp(Color.green(original), Color.green(soft), mix),
                    lerp(Color.blue(original), Color.blue(soft), mix),
                )
            }
            output.setPixels(sourceRow, 0, width, 0, y, width, 1)
        }
        if (!softened.isRecycled) softened.recycle()
        output
    }

    suspend fun fade(source: Bitmap, value: Float): Bitmap = withContext(Dispatchers.Default) {
        val amount = value.coerceIn(0f, 100f) / 100f
        if (amount == 0f) return@withContext source.copyArgb()
        mapPixels(source) { pixel ->
            fun channel(value: Int): Int {
                val lifted = value * (1f - amount * 0.22f) + 255f * amount * 0.06f + 22f * amount
                return lifted.roundToInt().coerceIn(0, 255)
            }
            Color.argb(Color.alpha(pixel), channel(Color.red(pixel)), channel(Color.green(pixel)), channel(Color.blue(pixel)))
        }
    }

    suspend fun grain(source: Bitmap, value: Float, seed: Int = 37): Bitmap =
        withContext(Dispatchers.Default) {
            val amount = value.coerceIn(0f, 100f) / 100f
            if (amount == 0f) return@withContext source.copyArgb()
            val amplitude = 42f * amount
            val width = source.width
            val height = source.height
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val row = IntArray(width)
            for (y in 0 until height) {
                if (y % 32 == 0) currentCoroutineContext().ensureActive()
                source.getPixels(row, 0, width, 0, y, width, 1)
                for (x in row.indices) {
                    val pixel = row[x]
                    val noise = deterministicNoise(x, y, seed) * amplitude
                    row[x] = Color.argb(
                        Color.alpha(pixel),
                        (Color.red(pixel) + noise).roundToInt().coerceIn(0, 255),
                        (Color.green(pixel) + noise).roundToInt().coerceIn(0, 255),
                        (Color.blue(pixel) + noise).roundToInt().coerceIn(0, 255),
                    )
                }
                output.setPixels(row, 0, width, 0, y, width, 1)
            }
            output
        }

    suspend fun signedVignette(source: Bitmap, value: Float): Bitmap =
        withContext(Dispatchers.Default) {
            val amount = value.coerceIn(-100f, 100f) / 100f
            if (amount == 0f) return@withContext source.copyArgb()
            val width = source.width
            val height = source.height
            val centerX = (width - 1) / 2f
            val centerY = (height - 1) / 2f
            val maxDistance = sqrt(centerX * centerX + centerY * centerY).toFloat().coerceAtLeast(1f)
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val row = IntArray(width)
            for (y in 0 until height) {
                if (y % 32 == 0) currentCoroutineContext().ensureActive()
                source.getPixels(row, 0, width, 0, y, width, 1)
                val dy = y - centerY
                for (x in row.indices) {
                    val pixel = row[x]
                    val normalized = (sqrt((x - centerX) * (x - centerX) + dy * dy).toFloat() / maxDistance).coerceIn(0f, 1f)
                    val edge = smoothStep(((normalized - 0.32f) / 0.68f).coerceIn(0f, 1f))
                    val factor = if (amount > 0f) 1f - amount * edge * 0.88f else 1f + -amount * edge * 0.48f
                    row[x] = Color.argb(
                        Color.alpha(pixel),
                        (Color.red(pixel) * factor).roundToInt().coerceIn(0, 255),
                        (Color.green(pixel) * factor).roundToInt().coerceIn(0, 255),
                        (Color.blue(pixel) * factor).roundToInt().coerceIn(0, 255),
                    )
                }
                output.setPixels(row, 0, width, 0, y, width, 1)
            }
            output
        }

    private suspend fun bloom(source: Bitmap, amount: Float, thresholdValue: Float): Bitmap {
        val threshold = (thresholdValue.coerceIn(0f, 100f) / 100f * 0.55f + 0.25f) * 255f
        val highlights = mapPixels(source) { pixel ->
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val gain = ((luma - threshold) / (255f - threshold).coerceAtLeast(1f)).coerceIn(0f, 1f)
            Color.argb(Color.alpha(pixel), (r * gain).roundToInt(), (g * gain).roundToInt(), (b * gain).roundToInt())
        }
        val blurred = fastBlur(highlights, 0.2f + amount / 100f * 0.45f)
        if (!highlights.isRecycled) highlights.recycle()
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val sourceRow = IntArray(width)
        val glowRow = IntArray(width)
        val strength = amount / 100f * 0.85f
        for (y in 0 until height) {
            if (y % 24 == 0) currentCoroutineContext().ensureActive()
            source.getPixels(sourceRow, 0, width, 0, y, width, 1)
            blurred.getPixels(glowRow, 0, width, 0, y, width, 1)
            for (x in sourceRow.indices) {
                val original = sourceRow[x]
                val glow = glowRow[x]
                sourceRow[x] = Color.argb(
                    Color.alpha(original),
                    screen(Color.red(original), (Color.red(glow) * strength).roundToInt()),
                    screen(Color.green(original), (Color.green(glow) * strength).roundToInt()),
                    screen(Color.blue(original), (Color.blue(glow) * strength).roundToInt()),
                )
            }
            output.setPixels(sourceRow, 0, width, 0, y, width, 1)
        }
        if (!blurred.isRecycled) blurred.recycle()
        return output
    }

    private fun pixelate(source: Bitmap, amount: Float): Bitmap {
        val block = (2f + amount / 100f * 52f).roundToInt().coerceAtLeast(2)
        val smallWidth = max(1, source.width / block)
        val smallHeight = max(1, source.height / block)
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, false)
        return Bitmap.createScaledBitmap(small, source.width, source.height, false).also {
            if (small !== source && small !== it && !small.isRecycled) small.recycle()
        }
    }

    private suspend fun posterize(source: Bitmap, amount: Float, secondary: Float): Bitmap {
        val levels = (32f - amount / 100f * 28f).roundToInt().coerceIn(3, 32)
        val contrast = 0.85f + secondary.coerceIn(0f, 100f) / 100f * 0.45f
        return mapPixels(source) { pixel ->
            fun channel(value: Int): Int {
                val normalized = ((value - 127.5f) * contrast + 127.5f).coerceIn(0f, 255f)
                return ((normalized / 255f * (levels - 1)).roundToInt() * 255f / (levels - 1)).roundToInt().coerceIn(0, 255)
            }
            Color.argb(Color.alpha(pixel), channel(Color.red(pixel)), channel(Color.green(pixel)), channel(Color.blue(pixel)))
        }
    }

    private suspend fun chromatic(source: Bitmap, amount: Float, secondary: Float): Bitmap {
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val output = IntArray(input.size)
        val shift = (1f + amount / 100f * min(width, height) * 0.022f).roundToInt()
        val vertical = ((secondary - 50f) / 50f * shift * 0.5f).roundToInt()
        for (y in 0 until height) {
            if (y % 24 == 0) currentCoroutineContext().ensureActive()
            for (x in 0 until width) {
                val base = input[y * width + x]
                val red = input[(y + vertical).coerceIn(0, height - 1) * width + (x + shift).coerceIn(0, width - 1)]
                val blue = input[(y - vertical).coerceIn(0, height - 1) * width + (x - shift).coerceIn(0, width - 1)]
                output[y * width + x] = Color.argb(Color.alpha(base), Color.red(red), Color.green(base), Color.blue(blue))
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private suspend fun glitch(source: Bitmap, amount: Float, secondary: Float): Bitmap {
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val output = IntArray(input.size)
        val amplitude = (amount / 100f * width * 0.08f).roundToInt()
        val bandHeight = (3f + (100f - secondary.coerceIn(0f, 100f)) / 100f * 28f).roundToInt().coerceAtLeast(2)
        for (y in 0 until height) {
            if (y % 24 == 0) currentCoroutineContext().ensureActive()
            val band = y / bandHeight
            val shift = (sin(band * 12.9898) * amplitude).roundToInt()
            val split = max(1, amplitude / 4)
            for (x in 0 until width) {
                val baseX = (x + shift).coerceIn(0, width - 1)
                val base = input[y * width + baseX]
                val red = input[y * width + (baseX + split).coerceIn(0, width - 1)]
                val blue = input[y * width + (baseX - split).coerceIn(0, width - 1)]
                output[y * width + x] = Color.argb(Color.alpha(base), Color.red(red), Color.green(base), Color.blue(blue))
            }
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    private suspend fun duotone(source: Bitmap, amount: Float, secondary: Float): Bitmap {
        val shadowHue = 190f + secondary.coerceIn(0f, 100f) * 1.1f
        val highlightHue = wrapHue(shadowHue + 145f)
        val shadow = hueVector(shadowHue)
        val highlight = hueVector(highlightHue)
        val mix = amount / 100f
        return mapPixels(source) { pixel ->
            val luma = (0.2126f * Color.red(pixel) + 0.7152f * Color.green(pixel) + 0.0722f * Color.blue(pixel)) / 255f
            val targetR = ((shadow[0] * (1f - luma) + highlight[0] * luma) * 255f).roundToInt()
            val targetG = ((shadow[1] * (1f - luma) + highlight[1] * luma) * 255f).roundToInt()
            val targetB = ((shadow[2] * (1f - luma) + highlight[2] * luma) * 255f).roundToInt()
            Color.argb(
                Color.alpha(pixel),
                lerp(Color.red(pixel), targetR, mix),
                lerp(Color.green(pixel), targetG, mix),
                lerp(Color.blue(pixel), targetB, mix),
            )
        }
    }

    private suspend fun solarize(source: Bitmap, amount: Float, secondary: Float): Bitmap {
        val threshold = secondary.coerceIn(0f, 100f) / 100f * 255f
        val mix = amount / 100f
        return mapPixels(source) { pixel ->
            fun solar(channel: Int): Int = if (channel > threshold) 255 - channel else channel
            Color.argb(
                Color.alpha(pixel),
                lerp(Color.red(pixel), solar(Color.red(pixel)), mix),
                lerp(Color.green(pixel), solar(Color.green(pixel)), mix),
                lerp(Color.blue(pixel), solar(Color.blue(pixel)), mix),
            )
        }
    }

    private suspend fun mapPixels(source: Bitmap, transform: (Int) -> Int): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val row = IntArray(width)
        for (y in 0 until height) {
            if (y % 32 == 0) currentCoroutineContext().ensureActive()
            source.getPixels(row, 0, width, 0, y, width, 1)
            for (x in row.indices) row[x] = transform(row[x])
            output.setPixels(row, 0, width, 0, y, width, 1)
        }
        return output
    }

    private fun fastBlur(source: Bitmap, amount: Float): Bitmap {
        if (amount <= 0f) return source.copyArgb()
        val factor = (1f - amount.coerceIn(0f, 1f) * 0.95f).coerceAtLeast(0.035f)
        val smallWidth = max(1, (source.width * factor).roundToInt())
        val smallHeight = max(1, (source.height * factor).roundToInt())
        val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
        return Bitmap.createScaledBitmap(small, source.width, source.height, true).also {
            if (small !== source && small !== it && !small.isRecycled) small.recycle()
        }
    }

    private fun buildCurveLut(points: CurvePoints): IntArray {
        val anchorsX = intArrayOf(0, 64, 128, 192, 255)
        val offsets = floatArrayOf(points.shadows, points.darks, points.midtones, points.lights, points.highlights)
        val anchorsY = IntArray(5) { index ->
            (anchorsX[index] + offsets[index] * 1.18f).roundToInt().coerceIn(0, 255)
        }
        // Keep the curve monotonic to avoid channel reversals and posterization spikes.
        for (index in 1 until anchorsY.size) anchorsY[index] = max(anchorsY[index], anchorsY[index - 1])
        return IntArray(256) { input ->
            val segment = when {
                input < 64 -> 0
                input < 128 -> 1
                input < 192 -> 2
                else -> 3
            }
            val startX = anchorsX[segment]
            val endX = anchorsX[segment + 1]
            val t = (input - startX).toFloat() / (endX - startX).coerceAtLeast(1)
            lerp(anchorsY[segment], anchorsY[segment + 1], smoothStep(t))
        }
    }

    private fun sampleBilinear(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        if (x < 0f || y < 0f || x > width - 1f || y > height - 1f) return Color.TRANSPARENT
        val x0 = floor(x).toInt().coerceIn(0, width - 1)
        val y0 = floor(y).toInt().coerceIn(0, height - 1)
        val x1 = min(width - 1, x0 + 1)
        val y1 = min(height - 1, y0 + 1)
        val tx = x - x0
        val ty = y - y0
        val p00 = pixels[y0 * width + x0]
        val p10 = pixels[y0 * width + x1]
        val p01 = pixels[y1 * width + x0]
        val p11 = pixels[y1 * width + x1]
        fun channel(extract: (Int) -> Int): Int {
            val top = extract(p00) + (extract(p10) - extract(p00)) * tx
            val bottom = extract(p01) + (extract(p11) - extract(p01)) * tx
            return (top + (bottom - top) * ty).roundToInt().coerceIn(0, 255)
        }
        return Color.argb(
            channel { Color.alpha(it) },
            channel { Color.red(it) },
            channel { Color.green(it) },
            channel { Color.blue(it) },
        )
    }

    private fun rgbToHsv(red: Int, green: Int, blue: Int): FloatArray {
        val r = red / 255f
        val g = green / 255f
        val b = blue / 255f
        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val delta = maxChannel - minChannel
        val hue = when {
            delta == 0f -> 0f
            maxChannel == r -> 60f * (((g - b) / delta) % 6f)
            maxChannel == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        return floatArrayOf(wrapHue(hue), if (maxChannel == 0f) 0f else delta / maxChannel, maxChannel)
    }

    private fun hsvToColor(alpha: Int, hue: Float, saturation: Float, value: Float): Int {
        val h = wrapHue(hue) / 60f
        val c = value.coerceIn(0f, 1f) * saturation.coerceIn(0f, 1f)
        val x = c * (1f - abs(h % 2f - 1f))
        val m = value.coerceIn(0f, 1f) - c
        val (r, g, b) = when (floor(h).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color.argb(
            alpha,
            ((r + m) * 255f).roundToInt().coerceIn(0, 255),
            ((g + m) * 255f).roundToInt().coerceIn(0, 255),
            ((b + m) * 255f).roundToInt().coerceIn(0, 255),
        )
    }

    private fun hueVector(hue: Float): FloatArray {
        val color = hsvToColor(255, hue, 1f, 1f)
        return floatArrayOf(Color.red(color) / 255f, Color.green(color) / 255f, Color.blue(color) / 255f)
    }

    private fun colorGradeDelta(target: Float, strength: Float): Float = (target - 0.5f) * strength * 112f

    private fun adjustLuminance(value: Float, amount: Float): Float =
        if (amount >= 0f) value + (1f - value) * amount else value * (1f + amount)

    private fun circularHueDistance(a: Float, b: Float): Float {
        val direct = abs(a - b) % 360f
        return min(direct, 360f - direct)
    }

    private fun wrapHue(value: Float): Float = ((value % 360f) + 360f) % 360f

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun smoothRange(start: Float, end: Float, value: Float): Float =
        smoothStep(((value - start) / (end - start).coerceAtLeast(0.0001f)).coerceIn(0f, 1f))

    private fun lerp(start: Int, end: Int, amount: Float): Int =
        (start + (end - start) * amount.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)

    private fun screen(base: Int, overlay: Int): Int =
        (255f - (255f - base.coerceIn(0, 255)) * (255f - overlay.coerceIn(0, 255)) / 255f).roundToInt().coerceIn(0, 255)

    private fun deterministicNoise(x: Int, y: Int, seed: Int): Float {
        var value = x * 374761393 + y * 668265263 + seed * 982451653
        value = (value xor (value ushr 13)) * 1274126177
        value = value xor (value ushr 16)
        return ((value and 0xFFFF) / 32767.5f - 1f)
    }
}
