package com.example.ananas.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ananas.editor.AdjustmentGroup
import com.example.ananas.editor.AdjustmentType
import com.example.ananas.editor.ColorGradeSettings
import com.example.ananas.editor.CreativeEffect
import com.example.ananas.editor.CurveBand
import com.example.ananas.editor.CurveChannel
import com.example.ananas.editor.EffectSettings
import com.example.ananas.editor.ExportFormat
import com.example.ananas.editor.FilterPreset
import com.example.ananas.editor.FocusMode
import com.example.ananas.editor.FocusSettings
import com.example.ananas.editor.GradeZone
import com.example.ananas.editor.HslAdjustment
import com.example.ananas.editor.HslColorRange
import com.example.ananas.editor.LensSettings
import com.example.ananas.editor.PerspectiveSettings
import com.example.ananas.editor.SelectiveHslSettings
import com.example.ananas.editor.ToneCurveSettings
import kotlin.math.roundToInt

@Composable
fun FilterPanel(
    selected: FilterPreset,
    intensity: Float,
    onSelect: (FilterPreset) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Filters", subtitle = "20 render-safe looks with adjustable strength", onCancel = onCancel, onApply = onApply) {
        HorizontalChips {
            FilterPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selected == preset,
                    onClick = { onSelect(preset) },
                    label = { Text(preset.label) },
                )
            }
        }
        ParameterSlider(
            label = "Intensity",
            value = intensity,
            range = 0f..100f,
            onValueChange = onIntensityChange,
            enabled = selected != FilterPreset.ORIGINAL,
        )
    }
}

@Composable
fun AdjustmentPanel(
    selected: AdjustmentType,
    value: Float,
    onSelect: (AdjustmentType) -> Unit,
    onValueChange: (Float) -> Unit,
    onResetAll: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Adjust", subtitle = "Light, color, detail and finishing controls", onCancel = onCancel, onApply = onApply) {
        AdjustmentGroup.entries.forEach { group ->
            Text(
                text = group.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected.group == group) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            HorizontalChips {
                AdjustmentType.entries.filter { it.group == group }.forEach { adjustment ->
                    FilterChip(
                        selected = selected == adjustment,
                        onClick = { onSelect(adjustment) },
                        label = { Text(adjustment.label) },
                    )
                }
            }
        }
        ParameterSlider(
            label = selected.label,
            value = value,
            range = selected.min..selected.max,
            onValueChange = onValueChange,
            valueText = formatAdjustmentValue(selected, value),
            onReset = { onValueChange(selected.default) },
        )
        TextButton(onClick = onResetAll, modifier = Modifier.align(Alignment.End)) {
            Text("Reset all adjustments")
        }
    }
}

@Composable
fun CurvesPanel(
    channel: CurveChannel,
    band: CurveBand,
    settings: ToneCurveSettings,
    onChannelSelected: (CurveChannel) -> Unit,
    onBandSelected: (CurveBand) -> Unit,
    onSettingsChange: (ToneCurveSettings) -> Unit,
    onResetChannel: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val points = settings.points(channel)
    ToolPanel(title = "Tone curves", subtitle = "Independent RGB and master tonal shaping", onCancel = onCancel, onApply = onApply) {
        HorizontalChips {
            CurveChannel.entries.forEach { option ->
                FilterChip(selected = channel == option, onClick = { onChannelSelected(option) }, label = { Text(option.label) })
            }
        }
        CurveGraph(
            points = listOf(points.shadows, points.darks, points.midtones, points.lights, points.highlights),
            channel = channel,
            selectedBand = band,
            onPointChange = { changedBand, changedValue ->
                onBandSelected(changedBand)
                onSettingsChange(settings.update(channel, changedBand, changedValue))
            },
        )
        HorizontalChips {
            CurveBand.entries.forEach { option ->
                FilterChip(selected = band == option, onClick = { onBandSelected(option) }, label = { Text(option.label) })
            }
        }
        ParameterSlider(
            label = band.label,
            value = points.value(band),
            range = -100f..100f,
            onValueChange = { onSettingsChange(settings.update(channel, band, it)) },
            onReset = { onSettingsChange(settings.update(channel, band, 0f)) },
        )
        TextButton(onClick = onResetChannel, modifier = Modifier.align(Alignment.End)) { Text("Reset ${channel.label} curve") }
    }
}

@Composable
private fun CurveGraph(
    points: List<Float>,
    channel: CurveChannel,
    selectedBand: CurveBand,
    onPointChange: (CurveBand, Float) -> Unit,
) {
    val lineColor = when (channel) {
        CurveChannel.MASTER -> MaterialTheme.colorScheme.primary
        CurveChannel.RED -> Color(0xFFFF5A67)
        CurveChannel.GREEN -> Color(0xFF4FD28B)
        CurveChannel.BLUE -> Color(0xFF5B9DFF)
    }
    var graphSize by remember { mutableStateOf(IntSize.Zero) }

    fun updatePoint(position: Offset) {
        if (graphSize.width <= 0 || graphSize.height <= 0) return
        val index = (position.x / graphSize.width * 4f).roundToInt().coerceIn(0, 4)
        val identityY = graphSize.height - graphSize.height * index / 4f
        val value = ((identityY - position.y) / (graphSize.height * 0.34f) * 100f).coerceIn(-100f, 100f)
        onPointChange(CurveBand.entries[index], value)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Canvas(
            Modifier
                .padding(14.dp)
                .onSizeChanged { graphSize = it }
                .pointerInput(points, channel, graphSize) {
                    detectDragGestures(
                        onDragStart = ::updatePoint,
                        onDrag = { change, _ ->
                            change.consume()
                            updatePoint(change.position)
                        },
                    )
                },
        ) {
            val gridColor = Color.White.copy(alpha = 0.1f)
            repeat(5) { index ->
                val x = size.width * index / 4f
                val y = size.height * index / 4f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            }
            val path = Path()
            points.forEachIndexed { index, offset ->
                val x = size.width * index / 4f
                val identityY = size.height - size.height * index / 4f
                val y = (identityY - offset / 100f * size.height * 0.34f).coerceIn(0f, size.height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                val selected = CurveBand.entries[index] == selectedBand
                if (selected) drawCircle(lineColor.copy(alpha = 0.22f), radius = 12f, center = Offset(x, y))
                drawCircle(lineColor, radius = if (selected) 7f else 5f, center = Offset(x, y))
            }
            drawPath(path, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun HslPanel(
    selectedRange: HslColorRange,
    settings: SelectiveHslSettings,
    onRangeSelected: (HslColorRange) -> Unit,
    onSettingsChange: (SelectiveHslSettings) -> Unit,
    onResetRange: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val adjustment = settings.adjustment(selectedRange)
    ToolPanel(title = "Selective HSL", subtitle = "Target eight color families without shifting the rest", onCancel = onCancel, onApply = onApply) {
        HorizontalChips {
            HslColorRange.entries.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { onRangeSelected(range) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(14.dp)
                                .background(hueColor(range.centerHue), CircleShape),
                        )
                    },
                    label = { Text(range.label) },
                )
            }
        }
        ParameterSlider("Hue", adjustment.hue, -100f..100f, { value ->
            onSettingsChange(settings.update(selectedRange, adjustment.copy(hue = value)))
        })
        ParameterSlider("Saturation", adjustment.saturation, -100f..100f, { value ->
            onSettingsChange(settings.update(selectedRange, adjustment.copy(saturation = value)))
        })
        ParameterSlider("Luminance", adjustment.luminance, -100f..100f, { value ->
            onSettingsChange(settings.update(selectedRange, adjustment.copy(luminance = value)))
        })
        TextButton(onClick = onResetRange, modifier = Modifier.align(Alignment.End)) { Text("Reset ${selectedRange.label}") }
    }
}

@Composable
fun ColorGradePanel(
    selectedZone: GradeZone,
    settings: ColorGradeSettings,
    onZoneSelected: (GradeZone) -> Unit,
    onSettingsChange: (ColorGradeSettings) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Color grading", subtitle = "Three-way shadow, midtone and highlight toning", onCancel = onCancel, onApply = onApply) {
        HorizontalChips {
            GradeZone.entries.forEach { zone ->
                FilterChip(
                    selected = selectedZone == zone,
                    onClick = { onZoneSelected(zone) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(14.dp)
                                .background(hueColor(settings.hue(zone)), CircleShape),
                        )
                    },
                    label = { Text(zone.label) },
                )
            }
        }
        ParameterSlider("Hue", settings.hue(selectedZone), 0f..360f, { hue ->
            onSettingsChange(settings.update(selectedZone, hue, settings.saturation(selectedZone)))
        }, valueText = "${settings.hue(selectedZone).roundToInt()}°")
        ParameterSlider("Saturation", settings.saturation(selectedZone), 0f..100f, { saturation ->
            onSettingsChange(settings.update(selectedZone, settings.hue(selectedZone), saturation))
        })
        ParameterSlider("Balance", settings.balance, -100f..100f, { onSettingsChange(settings.copy(balance = it)) })
        ParameterSlider("Blending", settings.blending, 0f..100f, { onSettingsChange(settings.copy(blending = it)) })
    }
}

@Composable
fun EffectsPanel(
    settings: EffectSettings,
    onSettingsChange: (EffectSettings) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Creative effects", subtitle = "Procedural effects rendered into the final export", onCancel = onCancel, onApply = onApply) {
        HorizontalChips {
            CreativeEffect.entries.forEach { effect ->
                FilterChip(
                    selected = settings.effect == effect,
                    onClick = { onSettingsChange(settings.copy(effect = effect)) },
                    label = { Text(effect.label) },
                )
            }
        }
        ParameterSlider("Amount", settings.amount, 0f..100f, { onSettingsChange(settings.copy(amount = it)) })
        ParameterSlider(effectSecondaryLabel(settings.effect), settings.secondary, 0f..100f, { onSettingsChange(settings.copy(secondary = it)) })
        Text(
            text = effectDescription(settings.effect),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun LensPanel(
    settings: LensSettings,
    onSettingsChange: (LensSettings) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Lens", subtitle = "Barrel/pincushion distortion and edge optics", onCancel = onCancel, onApply = onApply) {
        ParameterSlider("Distortion", settings.distortion, -100f..100f, { onSettingsChange(settings.copy(distortion = it)) })
        ParameterSlider("Chromatic aberration", settings.chromaticAberration, 0f..100f, { onSettingsChange(settings.copy(chromaticAberration = it)) })
        ParameterSlider("Edge vignette", settings.edgeVignette, -100f..100f, { onSettingsChange(settings.copy(edgeVignette = it)) })
        TextButton(onClick = { onSettingsChange(LensSettings()) }, modifier = Modifier.align(Alignment.End)) { Text("Reset lens") }
    }
}

@Composable
fun FocusPanel(
    settings: FocusSettings,
    onSettingsChange: (FocusSettings) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Focus", subtitle = "Radial or linear tilt-shift with movable focal center", onCancel = onCancel, onApply = onApply) {
        HorizontalChips {
            FocusMode.entries.forEach { mode ->
                FilterChip(selected = settings.mode == mode, onClick = { onSettingsChange(settings.copy(mode = mode)) }, label = { Text(mode.label) })
            }
        }
        ParameterSlider("Blur", settings.blur, 0f..100f, { onSettingsChange(settings.copy(blur = it)) })
        ParameterSlider("Focus size", settings.radius * 100f, 4f..85f, { onSettingsChange(settings.copy(radius = it / 100f)) })
        ParameterSlider("Feather", settings.feather * 100f, 2f..60f, { onSettingsChange(settings.copy(feather = it / 100f)) })
        ParameterSlider("Center X", settings.centerX * 100f, 0f..100f, { onSettingsChange(settings.copy(centerX = it / 100f)) })
        ParameterSlider("Center Y", settings.centerY * 100f, 0f..100f, { onSettingsChange(settings.copy(centerY = it / 100f)) })
        if (settings.mode == FocusMode.LINEAR) {
            ParameterSlider("Angle", settings.angle, -180f..180f, { onSettingsChange(settings.copy(angle = it)) }, valueText = "${settings.angle.roundToInt()}°")
        }
    }
}

@Composable
fun PerspectivePanel(
    settings: PerspectiveSettings,
    onSettingsChange: (PerspectiveSettings) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Perspective", subtitle = "Keystone correction, rotation and safe scaling", onCancel = onCancel, onApply = onApply) {
        ParameterSlider("Horizontal", settings.horizontal, -100f..100f, { onSettingsChange(settings.copy(horizontal = it)) })
        ParameterSlider("Vertical", settings.vertical, -100f..100f, { onSettingsChange(settings.copy(vertical = it)) })
        ParameterSlider("Rotation", settings.rotate, -45f..45f, { onSettingsChange(settings.copy(rotate = it)) }, valueText = "${settings.rotate.roundToInt()}°")
        ParameterSlider("Scale", settings.scale, 70f..130f, { onSettingsChange(settings.copy(scale = it)) }, valueText = "${settings.scale.roundToInt()}%")
        TextButton(onClick = { onSettingsChange(PerspectiveSettings()) }, modifier = Modifier.align(Alignment.End)) { Text("Reset transform") }
    }
}

@Composable
fun RotatePanel(
    straightenValue: Float,
    onStraightenChange: (Float) -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Rotate & straighten", subtitle = "Geometric transforms preserve transparency", onCancel = onCancel, onApply = onApply) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SmallToolButton(Icons.Default.RotateLeft, "Left", onRotateLeft)
            SmallToolButton(Icons.Default.RotateRight, "Right", onRotateRight)
            SmallToolButton(Icons.Default.Flip, "Flip H", onFlipHorizontal)
            SmallToolButton(Icons.Default.FlipToBack, "Flip V", onFlipVertical)
        }
        ParameterSlider("Straighten", straightenValue, -15f..15f, onStraightenChange, valueText = "${straightenValue.roundToInt()}°", onReset = { onStraightenChange(0f) })
    }
}

@Composable
fun BeautyPanel(
    smooth: Float,
    whiten: Float,
    onChange: (smooth: Float, whiten: Float) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Beauty", subtitle = "Skin-aware smoothing with edge protection", onCancel = onCancel, onApply = onApply) {
        ParameterSlider("Smooth", smooth, 0f..100f, { onChange(it, whiten) })
        ParameterSlider("Tone lift", whiten, 0f..100f, { onChange(smooth, it) })
        TextButton(onClick = { onChange(0f, 0f) }, modifier = Modifier.align(Alignment.End)) { Text("Reset beauty") }
    }
}

@Composable
fun ResizePanel(
    currentWidth: Int,
    currentHeight: Int,
    onCancel: () -> Unit,
    onResize: (Int, Int) -> Unit,
) {
    var widthText by remember(currentWidth) { mutableStateOf(currentWidth.toString()) }
    var heightText by remember(currentHeight) { mutableStateOf(currentHeight.toString()) }
    var lockRatio by remember { mutableStateOf(true) }
    val ratio = remember(currentWidth, currentHeight) { if (currentHeight == 0) 1f else currentWidth.toFloat() / currentHeight }

    ToolPanel(title = "Resize", subtitle = "High-quality resampling with optional aspect lock", onCancel = onCancel, onApply = {
        onResize(widthText.toIntOrNull() ?: 0, heightText.toIntOrNull() ?: 0)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = widthText,
                onValueChange = { text ->
                    widthText = text.filter(Char::isDigit).take(4)
                    if (lockRatio) widthText.toIntOrNull()?.let { heightText = (it / ratio).roundToInt().coerceAtLeast(1).toString() }
                },
                label = { Text("Width") },
                suffix = { Text("px") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = heightText,
                onValueChange = { text ->
                    heightText = text.filter(Char::isDigit).take(4)
                    if (lockRatio) heightText.toIntOrNull()?.let { widthText = (it * ratio).roundToInt().coerceAtLeast(1).toString() }
                },
                label = { Text("Height") },
                suffix = { Text("px") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = lockRatio, onClick = { lockRatio = !lockRatio }, label = { Text("Lock ratio") })
            Spacer(Modifier.weight(1f))
            Text("Max 8192 px · 12MP", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun FramePanel(
    selectedStyle: Int,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Frames", subtitle = "Clean, gallery and gradient borders", onCancel = onCancel, onApply = onApply) {
        HorizontalChips {
            listOf("None", "Clean", "Gallery", "Gradient").forEachIndexed { index, label ->
                FilterChip(selected = selectedStyle == index, onClick = { onSelect(index) }, label = { Text(label) })
            }
        }
    }
}

@Composable
fun ExportOptionsDialog(
    currentFormat: ExportFormat,
    currentQuality: Int,
    onDismiss: () -> Unit,
    onConfirm: (ExportFormat, Int) -> Unit,
) {
    var format by remember(currentFormat) { mutableStateOf(currentFormat) }
    var quality by remember(currentQuality) { mutableIntStateOf(currentQuality) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export image") },
        text = {
            Column {
                Text("Format", fontWeight = FontWeight.SemiBold)
                HorizontalChips {
                    ExportFormat.entries.forEach { option ->
                        FilterChip(selected = format == option, onClick = { format = option }, label = { Text(option.name) })
                    }
                }
                ParameterSlider(
                    label = "Quality",
                    value = quality.toFloat(),
                    range = 50f..100f,
                    onValueChange = { quality = it.roundToInt() },
                    valueText = "$quality%",
                    enabled = format != ExportFormat.PNG,
                )
                Text(
                    if (format == ExportFormat.PNG) "PNG preserves transparency; quality is lossless."
                    else "Higher quality creates a larger file.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(format, quality) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ToolPanel(
    title: String,
    subtitle: String? = null,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF171925), Color(0xFF0E1016))),
                RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onCancel,
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.065f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(19.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.11f),
                        ) {
                            Text(
                                "LIVE",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                    subtitle?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
                Button(
                    onClick = onApply,
                    shape = RoundedCornerShape(15.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Apply", modifier = Modifier.padding(start = 5.dp), fontWeight = FontWeight.Black)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.055f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 410.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 15.dp, vertical = 12.dp),
            ) {
                content(this)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun HorizontalChips(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueText: String = value.roundToInt().toString(),
    enabled: Boolean = true,
    onReset: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
        shape = RoundedCornerShape(19.dp),
        color = Color.White.copy(alpha = 0.045f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.055f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                ) {
                    Text(
                        valueText,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (onReset != null) {
                    TextButton(onClick = onReset, enabled = enabled) { Text("Reset") }
                }
            }
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun SmallToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(16.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun formatAdjustmentValue(type: AdjustmentType, value: Float): String = when (type) {
    AdjustmentType.EXPOSURE -> String.format("%.1f", value)
    AdjustmentType.HUE -> "${value.roundToInt()}°"
    else -> value.roundToInt().toString()
}

private fun hueColor(hue: Float): Color = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 0.78f, 0.95f)))

private fun effectSecondaryLabel(effect: CreativeEffect): String = when (effect) {
    CreativeEffect.BLOOM -> "Threshold"
    CreativeEffect.PIXELATE -> "Block character"
    CreativeEffect.POSTERIZE -> "Color levels"
    CreativeEffect.CHROMATIC -> "Direction"
    CreativeEffect.GLITCH -> "Band density"
    CreativeEffect.DUOTONE -> "Color pair"
    CreativeEffect.FILM_GRAIN -> "Grain seed"
    CreativeEffect.SOLARIZE -> "Threshold"
}

private fun effectDescription(effect: CreativeEffect): String = when (effect) {
    CreativeEffect.BLOOM -> "Extracts bright areas, diffuses them and screen-blends the glow."
    CreativeEffect.PIXELATE -> "Downsamples into deliberate blocks and restores hard edges."
    CreativeEffect.POSTERIZE -> "Reduces tonal levels for graphic, print-like color separation."
    CreativeEffect.CHROMATIC -> "Offsets red and blue channels for an optical split."
    CreativeEffect.GLITCH -> "Applies deterministic scanline displacement and RGB tearing."
    CreativeEffect.DUOTONE -> "Maps luminance between a generated shadow/highlight color pair."
    CreativeEffect.FILM_GRAIN -> "Adds stable monochromatic texture without changing alpha."
    CreativeEffect.SOLARIZE -> "Partially inverts channels above a controllable threshold."
}
