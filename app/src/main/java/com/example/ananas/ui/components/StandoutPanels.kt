package com.example.ananas.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ananas.editor.AdjustmentType
import com.example.ananas.editor.BatchState
import com.example.ananas.editor.BlendModeOption
import com.example.ananas.editor.CutoutBackground
import com.example.ananas.editor.CutoutSettings
import com.example.ananas.editor.EditorLayer
import com.example.ananas.editor.HealingSettings
import com.example.ananas.editor.MaskMode
import com.example.ananas.editor.ProductCanvas
import com.example.ananas.editor.ProductStudioSettings
import com.example.ananas.editor.RecipePreset
import com.example.ananas.editor.RecipeSettings
import com.example.ananas.editor.SelectiveMaskSettings
import com.example.ananas.editor.SmartExportPreset
import kotlin.math.roundToInt

@Composable
fun LayersPanel(
    layers: List<EditorLayer>,
    selectedLayerId: Long?,
    onSelect: (Long) -> Unit,
    onVisibility: (Long) -> Unit,
    onLock: (Long) -> Unit,
    onOpacity: (Long, Float) -> Unit,
    onBlendMode: (Long, BlendModeOption) -> Unit,
    onMove: (Long, Int) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onFlatten: () -> Unit,
    onSaveProject: () -> Unit,
    onClose: () -> Unit,
) {
    val selected = layers.firstOrNull { it.id == selectedLayerId }
    ToolPanel(
        title = "Layers",
        subtitle = "Reorder, blend and preserve editable components",
        onCancel = onClose,
        onApply = onClose,
    ) {
        layers.asReversed().forEach { layer ->
            val isSelected = layer.id == selectedLayerId
            Surface(
                onClick = { onSelect(layer.id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onVisibility(layer.id) }) {
                        Icon(if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Visibility")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(layer.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("${layer.type.label} · ${(layer.opacity * 100).roundToInt()}% · ${layer.blendMode.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onLock(layer.id) }) {
                        Icon(if (layer.locked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock")
                    }
                }
            }
        }

        selected?.let { layer ->
            Spacer(Modifier.height(8.dp))
            LayerOpacitySlider(layer = layer, onCommit = { onOpacity(layer.id, it) })
            BlendModeChooser(layer.blendMode, enabled = !layer.locked) { onBlendMode(layer.id, it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniAction(Icons.Default.ArrowUpward, "Up", enabled = !layer.locked) { onMove(layer.id, 1) }
                MiniAction(Icons.Default.ArrowDownward, "Down", enabled = !layer.locked) { onMove(layer.id, -1) }
                MiniAction(Icons.Default.ContentCopy, "Copy", enabled = !layer.locked) { onDuplicate(layer.id) }
                MiniAction(Icons.Default.Delete, "Delete", enabled = !layer.locked) { onDelete(layer.id) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onFlatten, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.LayersClear, contentDescription = null)
                Text("Flatten", Modifier.padding(start = 6.dp))
            }
            Button(onClick = onSaveProject, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text("Save project", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
fun MaskPanel(
    settings: SelectiveMaskSettings,
    onSettingsChange: (SelectiveMaskSettings) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(
        title = "Selective mask",
        subtitle = "Apply an adjustment only where the mask is active",
        onCancel = onCancel,
        onApply = onApply,
    ) {
        ChipRow {
            MaskMode.entries.forEach { mode ->
                FilterChip(selected = settings.mode == mode, onClick = { onSettingsChange(settings.copy(mode = mode)) }, label = { Text(mode.label) })
            }
        }
        ChipRow {
            AdjustmentType.entries.forEach { type ->
                FilterChip(selected = settings.adjustment == type, onClick = { onSettingsChange(settings.copy(adjustment = type, amount = type.default)) }, label = { Text(type.label) })
            }
        }
        LabeledSlider(
            settings.adjustment.label,
            settings.amount,
            settings.adjustment.min..settings.adjustment.max,
            { onSettingsChange(settings.copy(amount = it)) },
            settings.amount.formatValue(),
        )
        when (settings.mode) {
            MaskMode.BRUSH -> {
                LabeledSlider("Brush size", settings.brushSize * 100f, 1f..30f, { onSettingsChange(settings.copy(brushSize = it / 100f)) }, "${(settings.brushSize * 100).roundToInt()}%")
                ToggleRow("Subtract brush", settings.subtractBrush) { onSettingsChange(settings.copy(subtractBrush = it)) }
                Text("Paint directly on the image. Use Subtract to refine edges.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MaskMode.RADIAL -> {
                LabeledSlider("Radius", settings.radius * 100f, 5f..90f, { onSettingsChange(settings.copy(radius = it / 100f)) }, "${(settings.radius * 100).roundToInt()}%")
                LabeledSlider("Feather", settings.feather * 100f, 1f..100f, { onSettingsChange(settings.copy(feather = it / 100f)) }, "${(settings.feather * 100).roundToInt()}%")
            }
            MaskMode.LINEAR -> {
                LabeledSlider("Angle", settings.angle, -180f..180f, { onSettingsChange(settings.copy(angle = it)) }, "${settings.angle.roundToInt()}°")
                LabeledSlider("Feather", settings.feather * 100f, 2f..48f, { onSettingsChange(settings.copy(feather = it / 100f)) }, "${(settings.feather * 100).roundToInt()}%")
            }
            MaskMode.LUMINANCE -> {
                LabeledSlider("Dark limit", settings.luminanceMin * 100f, 0f..100f, { onSettingsChange(settings.copy(luminanceMin = it / 100f)) }, "${(settings.luminanceMin * 100).roundToInt()}%")
                LabeledSlider("Bright limit", settings.luminanceMax * 100f, 0f..100f, { onSettingsChange(settings.copy(luminanceMax = it / 100f)) }, "${(settings.luminanceMax * 100).roundToInt()}%")
            }
            MaskMode.COLOR_RANGE -> {
                LabeledSlider("Target hue", settings.targetHue, 0f..360f, { onSettingsChange(settings.copy(targetHue = it)) }, "${settings.targetHue.roundToInt()}°")
                LabeledSlider("Tolerance", settings.colorTolerance, 2f..120f, { onSettingsChange(settings.copy(colorTolerance = it)) }, settings.colorTolerance.roundToInt().toString())
                HueStrip(settings.targetHue)
            }
        }
        ToggleRow("Invert mask", settings.invert) { onSettingsChange(settings.copy(invert = it)) }
    }
}

@Composable
fun CutoutPanel(
    settings: CutoutSettings,
    onSettingsChange: (CutoutSettings) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(
        title = "Cutout studio",
        subtitle = "Automatic local subject extraction with edge refinement",
        onCancel = onCancel,
        onApply = onApply,
    ) {
        ChipRow {
            CutoutBackground.entries.forEach { background ->
                FilterChip(selected = settings.background == background, onClick = { onSettingsChange(settings.copy(background = background)) }, label = { Text(background.label) })
            }
        }
        LabeledSlider("Detection tolerance", settings.threshold, 8f..100f, { onSettingsChange(settings.copy(threshold = it)) }, settings.threshold.roundToInt().toString())
        LabeledSlider("Edge feather", settings.feather, 0f..16f, { onSettingsChange(settings.copy(feather = it)) }, settings.feather.formatValue())
        if (settings.background != CutoutBackground.TRANSPARENT) {
            ColorSwatches(settings.backgroundColor) { onSettingsChange(settings.copy(backgroundColor = it)) }
            LabeledSlider("Contact shadow", settings.shadowAmount, 0f..70f, { onSettingsChange(settings.copy(shadowAmount = it)) }, settings.shadowAmount.roundToInt().toString())
        }
        if (settings.background == CutoutBackground.BLUR) {
            LabeledSlider("Background blur", settings.blurAmount, 1f..100f, { onSettingsChange(settings.copy(blurAmount = it)) }, settings.blurAmount.roundToInt().toString())
        }
        LabeledSlider("Refine brush", settings.refineBrushSize * 100f, 1f..24f, { onSettingsChange(settings.copy(refineBrushSize = it / 100f)) }, "${(settings.refineBrushSize * 100).roundToInt()}%")
        ToggleRow("Remove from subject", settings.refineSubtract) { onSettingsChange(settings.copy(refineSubtract = it)) }
        Text("Paint over missed subject areas to add them, or enable Remove to clean the edge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HealingPanel(
    settings: HealingSettings,
    onSettingsChange: (HealingSettings) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(
        title = "Object remover",
        subtitle = "Paint over an object; Ananas reconstructs it from surrounding pixels",
        onCancel = onCancel,
        onApply = onApply,
    ) {
        LabeledSlider("Brush size", settings.brushSize * 100f, 1f..28f, { onSettingsChange(settings.copy(brushSize = it / 100f)) }, "${(settings.brushSize * 100).roundToInt()}%")
        LabeledSlider("Texture blend", settings.textureBlend, 25f..100f, { onSettingsChange(settings.copy(textureBlend = it)) }, "${settings.textureBlend.roundToInt()}%")
        Text("Use several smaller strokes for wires, blemishes and complex objects. The operation is limited to the painted region to reduce memory use.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RecipePanel(
    settings: RecipeSettings,
    onSettingsChange: (RecipeSettings) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(
        title = "Ananas recipes",
        subtitle = settings.preset.description,
        onCancel = onCancel,
        onApply = onApply,
    ) {
        RecipePreset.entries.forEach { recipe ->
            Surface(
                onClick = { onSettingsChange(settings.copy(preset = recipe)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                shape = RoundedCornerShape(15.dp),
                color = if (settings.preset == recipe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(recipe.label, fontWeight = FontWeight.Bold)
                    Text(recipe.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        LabeledSlider("Recipe strength", settings.intensity, 0f..100f, { onSettingsChange(settings.copy(intensity = it)) }, "${settings.intensity.roundToInt()}%")
    }
}

@Composable
fun ProductStudioPanel(
    settings: ProductStudioSettings,
    onSettingsChange: (ProductStudioSettings) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(
        title = "Product studio",
        subtitle = "Cut out, center and light a marketplace-ready product image",
        onCancel = onCancel,
        onApply = onApply,
    ) {
        ChipRow {
            ProductCanvas.entries.forEach { canvas ->
                FilterChip(selected = settings.canvas == canvas, onClick = { onSettingsChange(settings.copy(canvas = canvas)) }, label = { Text(canvas.label) })
            }
        }
        ColorSwatches(settings.backgroundColor) { onSettingsChange(settings.copy(backgroundColor = it)) }
        ToggleRow("Gradient background", settings.useGradient) { onSettingsChange(settings.copy(useGradient = it)) }
        LabeledSlider("Product margin", settings.padding * 100f, 2f..35f, { onSettingsChange(settings.copy(padding = it / 100f)) }, "${(settings.padding * 100).roundToInt()}%")
        LabeledSlider("Contact shadow", settings.contactShadow, 0f..70f, { onSettingsChange(settings.copy(contactShadow = it)) }, settings.contactShadow.roundToInt().toString())
        LabeledSlider("Reflection", settings.reflection, 0f..60f, { onSettingsChange(settings.copy(reflection = it)) }, settings.reflection.roundToInt().toString())
        LabeledSlider("Cutout tolerance", settings.cutoutThreshold, 8f..100f, { onSettingsChange(settings.copy(cutoutThreshold = it)) }, settings.cutoutThreshold.roundToInt().toString())
    }
}

@Composable
fun SmartExportDialog(
    selectedPreset: SmartExportPreset,
    currentQuality: Int,
    onDismiss: () -> Unit,
    onConfirm: (SmartExportPreset, Int) -> Unit,
) {
    var preset by remember(selectedPreset) { mutableStateOf(selectedPreset) }
    var quality by remember(currentQuality) { mutableStateOf(currentQuality.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Smart export") },
        text = {
            Column {
                SmartExportPreset.entries.forEach { item ->
                    FilterChip(
                        selected = preset == item,
                        onClick = { preset = item; quality = item.quality.toFloat() },
                        label = { Text(item.label) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (preset.format != com.example.ananas.editor.ExportFormat.PNG) {
                    LabeledSlider("Quality", quality, 50f..100f, { quality = it }, "${quality.roundToInt()}%")
                }
                Text(exportDescription(preset), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(preset, quality.roundToInt()) }) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BatchRecipeDialog(
    batchState: BatchState,
    onDismiss: () -> Unit,
    onSelect: (RecipePreset) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch recipe") },
        text = {
            Column {
                Text("Choose a look, then select up to 50 photos. Each photo is processed and saved as a new JPEG.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                RecipePreset.entries.forEach { recipe ->
                    Surface(
                        onClick = { onSelect(recipe) },
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            Text(recipe.label, fontWeight = FontWeight.Bold)
                            Text(recipe.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (batchState.isRunning) Text("${batchState.completed}/${batchState.total} processed", modifier = Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun BlendModeChooser(selected: BlendModeOption, enabled: Boolean, onSelect: (BlendModeOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Blend mode", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(selected.label) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BlendModeOption.entries.forEach { mode ->
                    DropdownMenuItem(text = { Text(mode.label) }, onClick = { expanded = false; onSelect(mode) })
                }
            }
        }
    }
}


@Composable
private fun LayerOpacitySlider(layer: EditorLayer, onCommit: (Float) -> Unit) {
    var value by remember(layer.id) { mutableStateOf(layer.opacity.coerceIn(0f, 1f)) }
    Column(Modifier.padding(top = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opacity", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${(value * 100).roundToInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onCommit(value) },
            valueRange = 0f..1f,
            enabled = !layer.locked,
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueText: String,
    enabled: Boolean = true,
) {
    Column(Modifier.padding(top = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(valueText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValueChange, valueRange = range, enabled = enabled)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) { content() }
}

@Composable
private fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit) {
    val colors = listOf(AndroidColor.WHITE, AndroidColor.rgb(248, 250, 252), AndroidColor.rgb(226, 232, 240), AndroidColor.rgb(15, 23, 42), AndroidColor.BLACK, AndroidColor.rgb(255, 247, 237), AndroidColor.rgb(239, 246, 255), AndroidColor.rgb(250, 245, 255))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        colors.forEach { color ->
            Surface(
                onClick = { onSelect(color) },
                modifier = Modifier.size(if (selected == color) 38.dp else 32.dp),
                shape = CircleShape,
                color = Color(color),
                border = androidx.compose.foundation.BorderStroke(if (selected == color) 3.dp else 1.dp, if (selected == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            ) {}
        }
    }
}

@Composable
private fun HueStrip(hue: Float) {
    Box(
        Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(
            androidx.compose.ui.graphics.Brush.horizontalGradient((0..12).map { Color(AndroidColor.HSVToColor(floatArrayOf(it * 30f, 0.9f, 0.95f))) })
        )
    )
    Text("Selected hue ${hue.roundToInt()}°", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
}

@Composable
private fun MiniAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label) }
}

private fun Float.formatValue(): String = if (kotlin.math.abs(this) < 10f && this % 1f != 0f) String.format("%.1f", this) else roundToInt().toString()

private fun exportDescription(preset: SmartExportPreset): String = when (preset) {
    SmartExportPreset.ORIGINAL -> "Keeps the current pixel dimensions and your selected format."
    SmartExportPreset.TRANSPARENT_PNG -> "Lossless PNG at the current dimensions; transparent cutouts remain transparent."
    SmartExportPreset.WHATSAPP -> "Downscales only when needed to reduce upload time without forcing a crop."
    SmartExportPreset.WEB_FAST -> "Efficient WebP output for websites and messaging."
    SmartExportPreset.MARKETPLACE -> "Centers the image on a 2000 × 2000 white marketplace canvas."
    else -> "Exports at ${preset.width} × ${preset.height} with a destination-safe crop."
}
