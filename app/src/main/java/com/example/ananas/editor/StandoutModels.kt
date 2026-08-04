package com.example.ananas.editor

import android.graphics.Bitmap
import androidx.annotation.ColorInt

enum class LayerType(val label: String) {
    BASE("Base image"),
    RASTER("Image layer"),
    TEXT("Text"),
    STICKER("Sticker"),
    DRAWING("Drawing"),
    ADJUSTMENT("Adjustment"),
    CUTOUT("Cutout"),
    HEAL("Healing"),
}

enum class BlendModeOption(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    SOFT_LIGHT("Soft light"),
    HARD_LIGHT("Hard light"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
    DIFFERENCE("Difference"),
}

data class EditorLayer(
    val id: Long = System.nanoTime(),
    val name: String,
    val type: LayerType,
    val bitmap: Bitmap,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val blendMode: BlendModeOption = BlendModeOption.NORMAL,
)

enum class MaskMode(val label: String) {
    BRUSH("Brush"),
    RADIAL("Radial"),
    LINEAR("Linear"),
    LUMINANCE("Luminance"),
    COLOR_RANGE("Color range"),
}

data class MaskStroke(
    val points: List<NormalizedPoint>,
    val widthFraction: Float,
    val subtract: Boolean = false,
    val opacity: Float = 1f,
)

data class SelectiveMaskSettings(
    val mode: MaskMode = MaskMode.BRUSH,
    val strokes: List<MaskStroke> = emptyList(),
    val brushSize: Float = 0.08f,
    val subtractBrush: Boolean = false,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radius: Float = 0.3f,
    val feather: Float = 0.25f,
    val angle: Float = 0f,
    val luminanceMin: Float = 0f,
    val luminanceMax: Float = 1f,
    val targetHue: Float = 0f,
    val colorTolerance: Float = 35f,
    val invert: Boolean = false,
    val adjustment: AdjustmentType = AdjustmentType.EXPOSURE,
    val amount: Float = 0f,
)

enum class CutoutBackground(val label: String) {
    TRANSPARENT("Transparent"),
    SOLID("Solid"),
    GRADIENT("Gradient"),
    BLUR("Blur original"),
}

data class CutoutSettings(
    val threshold: Float = 36f,
    val feather: Float = 3f,
    val background: CutoutBackground = CutoutBackground.TRANSPARENT,
    @ColorInt val backgroundColor: Int = android.graphics.Color.WHITE,
    @ColorInt val gradientColor: Int = android.graphics.Color.rgb(226, 232, 240),
    val blurAmount: Float = 55f,
    val shadowAmount: Float = 18f,
    val refinement: List<MaskStroke> = emptyList(),
    val refineBrushSize: Float = 0.06f,
    val refineSubtract: Boolean = false,
)

data class HealingSettings(
    val strokes: List<MaskStroke> = emptyList(),
    val brushSize: Float = 0.055f,
    val feather: Float = 4f,
    val textureBlend: Float = 72f,
)

enum class RecipePreset(val label: String, val description: String) {
    CLEAN_PRODUCT("Clean product", "Neutral whites, crisp detail and restrained color"),
    WARM_PORTRAIT("Warm portrait", "Natural skin warmth with protected highlights"),
    CINEMATIC_STREET("Cinematic street", "Teal shadows, warm highlights and controlled contrast"),
    FOOD_POP("Food pop", "Rich color, local contrast and warm appetizing tones"),
    NIGHT_RESCUE("Night rescue", "Lift shadows, reduce noise and recover color"),
    SOFT_FILM("Soft film", "Matte blacks, gentle grain and muted saturation"),
    DOCUMENT_SCAN("Document scan", "High-contrast neutral document cleanup"),
    SOCIAL_VIVID("Social vivid", "Bright, punchy color for small-screen feeds"),
}

data class RecipeSettings(
    val preset: RecipePreset = RecipePreset.CLEAN_PRODUCT,
    val intensity: Float = 100f,
)

enum class SmartExportPreset(
    val label: String,
    val width: Int,
    val height: Int,
    val format: ExportFormat,
    val quality: Int,
    val cropToFill: Boolean,
) {
    ORIGINAL("Original size", 0, 0, ExportFormat.JPEG, 95, false),
    INSTAGRAM_POST("Instagram post 4:5", 1080, 1350, ExportFormat.JPEG, 92, true),
    INSTAGRAM_STORY("Story / Status 9:16", 1080, 1920, ExportFormat.JPEG, 92, true),
    SQUARE_SOCIAL("Square social", 1080, 1080, ExportFormat.JPEG, 92, true),
    WHATSAPP("WhatsApp optimized", 1600, 1600, ExportFormat.JPEG, 86, false),
    WEB_FAST("Website fast", 1600, 1200, ExportFormat.WEBP, 82, false),
    MARKETPLACE("Marketplace square", 2000, 2000, ExportFormat.JPEG, 94, false),
    TRANSPARENT_PNG("Transparent PNG", 0, 0, ExportFormat.PNG, 100, false),
}

enum class ProductCanvas(val label: String, val widthRatio: Int, val heightRatio: Int) {
    SQUARE("Square 1:1", 1, 1),
    PORTRAIT("Portrait 4:5", 4, 5),
    LANDSCAPE("Landscape 4:3", 4, 3),
}

data class ProductStudioSettings(
    val canvas: ProductCanvas = ProductCanvas.SQUARE,
    val padding: Float = 0.12f,
    @ColorInt val backgroundColor: Int = android.graphics.Color.WHITE,
    val useGradient: Boolean = false,
    @ColorInt val gradientColor: Int = android.graphics.Color.rgb(241, 245, 249),
    val contactShadow: Float = 30f,
    val reflection: Float = 0f,
    val cutoutThreshold: Float = 36f,
    val feather: Float = 3f,
)

data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val width: Int,
    val height: Int,
    val layerCount: Int,
    val thumbnailPath: String,
)

data class BatchFailure(
    val uriString: String,
    val name: String,
    val reason: String,
)

data class BatchState(
    val isRunning: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val preset: RecipePreset = RecipePreset.CLEAN_PRODUCT,
    val exported: Int = 0,
    val failed: Int = 0,
    val currentName: String? = null,
    val failures: List<BatchFailure> = emptyList(),
    val wasCancelled: Boolean = false,
) {
    val progress: Float
        get() = if (total <= 0) 0f else completed.toFloat() / total.toFloat()

    val hasResult: Boolean
        get() = !isRunning && total > 0 && completed > 0
}

data class ProjectDocument(
    val summary: ProjectSummary,
    val originalBitmap: Bitmap,
    val layers: List<EditorLayer>,
)
