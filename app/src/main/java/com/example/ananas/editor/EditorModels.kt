package com.example.ananas.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.ColorInt

enum class EditorTool {
    NONE,
    FILTERS,
    ADJUST,
    CURVES,
    HSL,
    COLOR_GRADE,
    EFFECTS,
    LENS,
    FOCUS,
    PERSPECTIVE,
    CROP,
    ROTATE,
    RESIZE,
    TEXT,
    STICKERS,
    DRAW,
    BEAUTY,
    FRAME,
    LAYERS,
    MASK,
    CUTOUT,
    HEAL,
    RECIPES,
    PRODUCT,
}

enum class AdjustmentGroup(val label: String) {
    LIGHT("Light"),
    COLOR("Color"),
    DETAIL("Detail"),
    FINISH("Finish"),
}

enum class AdjustmentType(
    val label: String,
    val group: AdjustmentGroup,
    val min: Float,
    val max: Float,
    val default: Float,
) {
    BRIGHTNESS("Brightness", AdjustmentGroup.LIGHT, -100f, 100f, 0f),
    CONTRAST("Contrast", AdjustmentGroup.LIGHT, -100f, 100f, 0f),
    EXPOSURE("Exposure", AdjustmentGroup.LIGHT, -2f, 2f, 0f),
    GAMMA("Gamma", AdjustmentGroup.LIGHT, -100f, 100f, 0f),
    HIGHLIGHTS("Highlights", AdjustmentGroup.LIGHT, -100f, 100f, 0f),
    SHADOWS("Shadows", AdjustmentGroup.LIGHT, -100f, 100f, 0f),
    WHITES("Whites", AdjustmentGroup.LIGHT, -100f, 100f, 0f),
    BLACKS("Blacks", AdjustmentGroup.LIGHT, -100f, 100f, 0f),

    SATURATION("Saturation", AdjustmentGroup.COLOR, -100f, 100f, 0f),
    VIBRANCE("Vibrance", AdjustmentGroup.COLOR, -100f, 100f, 0f),
    WARMTH("Temperature", AdjustmentGroup.COLOR, -100f, 100f, 0f),
    TINT("Tint", AdjustmentGroup.COLOR, -100f, 100f, 0f),
    HUE("Hue", AdjustmentGroup.COLOR, -180f, 180f, 0f),

    CLARITY("Clarity", AdjustmentGroup.DETAIL, -100f, 100f, 0f),
    DEHAZE("Dehaze", AdjustmentGroup.DETAIL, -100f, 100f, 0f),
    SHARPNESS("Sharpness", AdjustmentGroup.DETAIL, 0f, 100f, 0f),
    NOISE_REDUCTION("Denoise", AdjustmentGroup.DETAIL, 0f, 100f, 0f),
    BLUR("Blur", AdjustmentGroup.DETAIL, 0f, 100f, 0f),

    FADE("Fade", AdjustmentGroup.FINISH, 0f, 100f, 0f),
    GRAIN("Grain", AdjustmentGroup.FINISH, 0f, 100f, 0f),
    VIGNETTE("Vignette", AdjustmentGroup.FINISH, -100f, 100f, 0f),
}

enum class FilterPreset(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vivid"),
    BRIGHT("Airy"),
    HIGH_CONTRAST("Punch"),
    WARM("Warm"),
    COOL("Cool"),
    VINTAGE("Vintage"),
    GRAYSCALE("Mono"),
    SEPIA("Sepia"),
    INVERT("Invert"),
    NOSTALGIA("Nostalgia"),
    PUNCH("Drama"),
    CINEMATIC("Cinema"),
    MATTE("Matte"),
    TEAL_ORANGE("Teal & Orange"),
    FADED_FILM("Faded Film"),
    NOIR("Noir"),
    DREAM("Dream"),
    CYANOTYPE("Cyanotype"),
    SUNSET("Sunset"),
}

enum class CurveChannel(val label: String) {
    MASTER("RGB"),
    RED("Red"),
    GREEN("Green"),
    BLUE("Blue"),
}

enum class CurveBand(val label: String) {
    SHADOWS("Shadows"),
    DARKS("Darks"),
    MIDTONES("Mids"),
    LIGHTS("Lights"),
    HIGHLIGHTS("Highlights"),
}

data class CurvePoints(
    val shadows: Float = 0f,
    val darks: Float = 0f,
    val midtones: Float = 0f,
    val lights: Float = 0f,
    val highlights: Float = 0f,
) {
    fun value(band: CurveBand): Float = when (band) {
        CurveBand.SHADOWS -> shadows
        CurveBand.DARKS -> darks
        CurveBand.MIDTONES -> midtones
        CurveBand.LIGHTS -> lights
        CurveBand.HIGHLIGHTS -> highlights
    }

    fun withValue(band: CurveBand, value: Float): CurvePoints = when (band) {
        CurveBand.SHADOWS -> copy(shadows = value)
        CurveBand.DARKS -> copy(darks = value)
        CurveBand.MIDTONES -> copy(midtones = value)
        CurveBand.LIGHTS -> copy(lights = value)
        CurveBand.HIGHLIGHTS -> copy(highlights = value)
    }

    fun isIdentity(): Boolean =
        shadows == 0f && darks == 0f && midtones == 0f && lights == 0f && highlights == 0f
}

data class ToneCurveSettings(
    val channels: Map<CurveChannel, CurvePoints> = CurveChannel.entries.associateWith { CurvePoints() },
) {
    fun points(channel: CurveChannel): CurvePoints = channels[channel] ?: CurvePoints()

    fun update(channel: CurveChannel, band: CurveBand, value: Float): ToneCurveSettings = copy(
        channels = channels + (channel to points(channel).withValue(band, value.coerceIn(-100f, 100f))),
    )

    fun reset(channel: CurveChannel): ToneCurveSettings = copy(channels = channels + (channel to CurvePoints()))

    fun isIdentity(): Boolean = channels.values.all(CurvePoints::isIdentity)
}

enum class HslColorRange(val label: String, val centerHue: Float) {
    RED("Red", 0f),
    ORANGE("Orange", 30f),
    YELLOW("Yellow", 60f),
    GREEN("Green", 120f),
    AQUA("Aqua", 180f),
    BLUE("Blue", 225f),
    PURPLE("Purple", 275f),
    MAGENTA("Magenta", 320f),
}

data class HslAdjustment(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
) {
    fun isIdentity(): Boolean = hue == 0f && saturation == 0f && luminance == 0f
}

data class SelectiveHslSettings(
    val ranges: Map<HslColorRange, HslAdjustment> = HslColorRange.entries.associateWith { HslAdjustment() },
) {
    fun adjustment(range: HslColorRange): HslAdjustment = ranges[range] ?: HslAdjustment()

    fun update(range: HslColorRange, value: HslAdjustment): SelectiveHslSettings = copy(
        ranges = ranges + (range to value.copy(
            hue = value.hue.coerceIn(-100f, 100f),
            saturation = value.saturation.coerceIn(-100f, 100f),
            luminance = value.luminance.coerceIn(-100f, 100f),
        )),
    )

    fun reset(range: HslColorRange): SelectiveHslSettings = copy(ranges = ranges + (range to HslAdjustment()))

    fun isIdentity(): Boolean = ranges.values.all(HslAdjustment::isIdentity)
}

enum class GradeZone(val label: String) {
    SHADOWS("Shadows"),
    MIDTONES("Midtones"),
    HIGHLIGHTS("Highlights"),
}

data class ColorGradeSettings(
    val shadowHue: Float = 220f,
    val shadowSaturation: Float = 0f,
    val midtoneHue: Float = 32f,
    val midtoneSaturation: Float = 0f,
    val highlightHue: Float = 48f,
    val highlightSaturation: Float = 0f,
    val balance: Float = 0f,
    val blending: Float = 50f,
) {
    fun hue(zone: GradeZone): Float = when (zone) {
        GradeZone.SHADOWS -> shadowHue
        GradeZone.MIDTONES -> midtoneHue
        GradeZone.HIGHLIGHTS -> highlightHue
    }

    fun saturation(zone: GradeZone): Float = when (zone) {
        GradeZone.SHADOWS -> shadowSaturation
        GradeZone.MIDTONES -> midtoneSaturation
        GradeZone.HIGHLIGHTS -> highlightSaturation
    }

    fun update(zone: GradeZone, hue: Float, saturation: Float): ColorGradeSettings = when (zone) {
        GradeZone.SHADOWS -> copy(shadowHue = hue, shadowSaturation = saturation)
        GradeZone.MIDTONES -> copy(midtoneHue = hue, midtoneSaturation = saturation)
        GradeZone.HIGHLIGHTS -> copy(highlightHue = hue, highlightSaturation = saturation)
    }

    fun isIdentity(): Boolean =
        shadowSaturation == 0f && midtoneSaturation == 0f && highlightSaturation == 0f
}

enum class CreativeEffect(val label: String) {
    BLOOM("Bloom"),
    PIXELATE("Pixelate"),
    POSTERIZE("Posterize"),
    CHROMATIC("Chromatic"),
    GLITCH("Glitch"),
    DUOTONE("Duotone"),
    FILM_GRAIN("Film Grain"),
    SOLARIZE("Solarize"),
}

data class EffectSettings(
    val effect: CreativeEffect = CreativeEffect.BLOOM,
    val amount: Float = 0f,
    val secondary: Float = 50f,
) {
    fun isIdentity(): Boolean = amount == 0f
}

data class LensSettings(
    val distortion: Float = 0f,
    val chromaticAberration: Float = 0f,
    val edgeVignette: Float = 0f,
) {
    fun isIdentity(): Boolean = distortion == 0f && chromaticAberration == 0f && edgeVignette == 0f
}

enum class FocusMode(val label: String) {
    RADIAL("Radial"),
    LINEAR("Linear"),
}

data class FocusSettings(
    val mode: FocusMode = FocusMode.RADIAL,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radius: Float = 0.34f,
    val feather: Float = 0.24f,
    val blur: Float = 0f,
    val angle: Float = 0f,
) {
    fun isIdentity(): Boolean = blur == 0f
}

data class PerspectiveSettings(
    val horizontal: Float = 0f,
    val vertical: Float = 0f,
    val rotate: Float = 0f,
    val scale: Float = 100f,
) {
    fun isIdentity(): Boolean = horizontal == 0f && vertical == 0f && rotate == 0f && scale == 100f
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
}

enum class TextStyleOption {
    SANS,
    SERIF,
    MONOSPACE,
    BOLD,
}

enum class TextAlignmentOption {
    LEFT,
    CENTER,
    RIGHT,
}

enum class BrushMode {
    SOLID,
    HIGHLIGHTER,
    NEON,
}

data class NormalizedPoint(val x: Float, val y: Float)

data class TextElement(
    val id: Long = System.nanoTime(),
    val text: String,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val sizeFraction: Float = 0.09f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    @ColorInt val color: Int = android.graphics.Color.WHITE,
    val opacity: Float = 1f,
    val style: TextStyleOption = TextStyleOption.BOLD,
    val alignment: TextAlignmentOption = TextAlignmentOption.CENTER,
    @ColorInt val strokeColor: Int = android.graphics.Color.TRANSPARENT,
    val strokeWidthFraction: Float = 0f,
    @ColorInt val shadowColor: Int = android.graphics.Color.TRANSPARENT,
    val shadowBlurFraction: Float = 0f,
    @ColorInt val backgroundColor: Int = android.graphics.Color.TRANSPARENT,
    val backgroundOpacity: Float = 0f,
)

data class StickerElement(
    val id: Long = System.nanoTime(),
    val value: String,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val sizeFraction: Float = 0.16f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
)

data class DrawStroke(
    val points: List<NormalizedPoint>,
    @ColorInt val color: Int,
    val widthFraction: Float,
    val erase: Boolean = false,
    val opacity: Float = 1f,
    val brushMode: BrushMode = BrushMode.SOLID,
)

data class CropSelection(
    val left: Float = 0.08f,
    val top: Float = 0.08f,
    val right: Float = 0.92f,
    val bottom: Float = 0.92f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class EditorMessage(
    val id: Long = System.nanoTime(),
    val text: String,
)

data class EditorState(
    val currentBitmap: Bitmap? = null,
    val originalBitmap: Bitmap? = null,
    val documentWidth: Int = 0,
    val documentHeight: Int = 0,
    val currentTool: EditorTool = EditorTool.NONE,
    val selectedAdjustment: AdjustmentType = AdjustmentType.BRIGHTNESS,
    val adjustmentValue: Float = 0f,
    val selectedFilter: FilterPreset = FilterPreset.ORIGINAL,
    val filterIntensity: Float = 100f,
    val curveChannel: CurveChannel = CurveChannel.MASTER,
    val curveBand: CurveBand = CurveBand.MIDTONES,
    val curveSettings: ToneCurveSettings = ToneCurveSettings(),
    val selectedHslRange: HslColorRange = HslColorRange.RED,
    val hslSettings: SelectiveHslSettings = SelectiveHslSettings(),
    val selectedGradeZone: GradeZone = GradeZone.SHADOWS,
    val colorGradeSettings: ColorGradeSettings = ColorGradeSettings(),
    val effectSettings: EffectSettings = EffectSettings(),
    val lensSettings: LensSettings = LensSettings(),
    val focusSettings: FocusSettings = FocusSettings(),
    val perspectiveSettings: PerspectiveSettings = PerspectiveSettings(),
    val beautySmooth: Float = 0f,
    val beautyWhiten: Float = 0f,
    val layers: List<EditorLayer> = emptyList(),
    val selectedLayerId: Long? = null,
    val maskSettings: SelectiveMaskSettings = SelectiveMaskSettings(),
    val cutoutSettings: CutoutSettings = CutoutSettings(),
    val healingSettings: HealingSettings = HealingSettings(),
    val recipeSettings: RecipeSettings = RecipeSettings(),
    val productStudioSettings: ProductStudioSettings = ProductStudioSettings(),
    val smartExportPreset: SmartExportPreset = SmartExportPreset.ORIGINAL,
    val projects: List<ProjectSummary> = emptyList(),
    val activeProjectId: String? = null,
    val activeProjectName: String = "Untitled project",
    val batchState: BatchState = BatchState(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val showOriginal: Boolean = false,
    val exportFormat: ExportFormat = ExportFormat.JPEG,
    val exportQuality: Int = 95,
    val lastExportUri: Uri? = null,
    val pendingShareUri: Uri? = null,
    val message: EditorMessage? = null,
    val sourceName: String? = null,
)
