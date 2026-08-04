package com.example.ananas.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ananas.editor.BrushMode
import com.example.ananas.editor.CropSelection
import com.example.ananas.editor.DrawStroke
import com.example.ananas.editor.NormalizedPoint
import com.example.ananas.editor.StickerElement
import com.example.ananas.editor.TextElement
import com.example.ananas.editor.TextAlignmentOption
import com.example.ananas.editor.TextStyleOption
import kotlin.math.min
import kotlin.math.roundToInt

private enum class CropHandle { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@Composable
fun CropEditorOverlay(
    imageBounds: Rect,
    selection: CropSelection,
    onSelectionChange: (CropSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    var current by remember { mutableStateOf(selection) }
    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
    LaunchedEffect(selection) { current = selection }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(imageBounds) {
                detectDragGestures(
                    onDragStart = { point ->
                        val rect = selectionToRect(current, imageBounds)
                        activeHandle = hitCropHandle(rect, point)
                    },
                    onDragEnd = { activeHandle = CropHandle.NONE },
                    onDragCancel = { activeHandle = CropHandle.NONE },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (imageBounds.width <= 0f || imageBounds.height <= 0f) return@detectDragGestures
                        val dx = dragAmount.x / imageBounds.width
                        val dy = dragAmount.y / imageBounds.height
                        current = updateCropSelection(current, activeHandle, dx, dy)
                        onSelectionChange(current)
                    }
                )
            }
    ) {
        if (imageBounds.width <= 0f || imageBounds.height <= 0f) return@Canvas
        val crop = selectionToRect(current, imageBounds)
        val scrim = Color.Black.copy(alpha = 0.58f)
        drawRect(scrim, topLeft = imageBounds.topLeft, size = androidx.compose.ui.geometry.Size(imageBounds.width, crop.top - imageBounds.top))
        drawRect(scrim, topLeft = Offset(imageBounds.left, crop.bottom), size = androidx.compose.ui.geometry.Size(imageBounds.width, imageBounds.bottom - crop.bottom))
        drawRect(scrim, topLeft = Offset(imageBounds.left, crop.top), size = androidx.compose.ui.geometry.Size(crop.left - imageBounds.left, crop.height))
        drawRect(scrim, topLeft = Offset(crop.right, crop.top), size = androidx.compose.ui.geometry.Size(imageBounds.right - crop.right, crop.height))

        drawRect(Color.White, crop.topLeft, crop.size, style = Stroke(width = 3.dp.toPx()))
        for (index in 1..2) {
            val x = crop.left + crop.width * index / 3f
            val y = crop.top + crop.height * index / 3f
            drawLine(Color.White.copy(alpha = 0.45f), Offset(x, crop.top), Offset(x, crop.bottom), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.45f), Offset(crop.left, y), Offset(crop.right, y), 1.dp.toPx())
        }
        val radius = 7.dp.toPx()
        listOf(crop.topLeft, Offset(crop.right, crop.top), Offset(crop.left, crop.bottom), crop.bottomRight).forEach {
            drawCircle(Color.White, radius, it)
            drawCircle(Color.Black, radius * 0.45f, it)
        }
    }
}

@Composable
fun TextLayerEditor(
    imageBounds: Rect,
    onCancel: () -> Unit,
    onApply: (List<TextElement>) -> Unit
) {
    val elements = remember { mutableStateListOf<TextElement>() }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var showAddDialog by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        elements.forEach { element ->
            EditableTextItem(
                element = element,
                imageBounds = imageBounds,
                selected = selectedId == element.id,
                onSelect = { selectedId = element.id },
                onChange = { updated ->
                    val index = elements.indexOfFirst { it.id == updated.id }
                    if (index >= 0) elements[index] = updated
                }
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            val selected = elements.firstOrNull { it.id == selectedId }
            ToolPanel(title = "Text", onCancel = onCancel, onApply = { onApply(elements.toList()) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        editingId = null
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add text")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            selected?.let {
                                editingId = it.id
                                showAddDialog = true
                            }
                        },
                        enabled = selected != null
                    ) { Icon(Icons.Default.Edit, contentDescription = "Edit text") }
                    IconButton(
                        onClick = {
                            selected?.let {
                                val duplicate = it.copy(id = System.nanoTime(), centerX = (it.centerX + 0.05f).coerceAtMost(0.95f), centerY = (it.centerY + 0.05f).coerceAtMost(0.95f))
                                elements.add(duplicate)
                                selectedId = duplicate.id
                            }
                        },
                        enabled = selected != null
                    ) { Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate") }
                    IconButton(
                        onClick = {
                            elements.removeAll { it.id == selectedId }
                            selectedId = elements.lastOrNull()?.id
                        },
                        enabled = selected != null
                    ) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }

                selected?.let { current ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        TextStyleOption.entries.forEach { style ->
                            FilterChip(
                                selected = current.style == style,
                                onClick = { updateText(elements, current.copy(style = style)) },
                                label = { Text(style.name.lowercase().replaceFirstChar(Char::uppercase)) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        TextAlignmentOption.entries.forEach { alignment ->
                            FilterChip(
                                selected = current.alignment == alignment,
                                onClick = { updateText(elements, current.copy(alignment = alignment)) },
                                label = { Text(alignment.name.lowercase().replaceFirstChar(Char::uppercase)) }
                            )
                        }
                    }
                    ColorChooser(current.color) { updateText(elements, current.copy(color = it)) }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        FilterChip(
                            selected = current.strokeWidthFraction > 0f,
                            onClick = {
                                updateText(
                                    elements,
                                    if (current.strokeWidthFraction > 0f) current.copy(strokeWidthFraction = 0f)
                                    else current.copy(strokeColor = AndroidColor.BLACK, strokeWidthFraction = 0.006f)
                                )
                            },
                            label = { Text("Outline") }
                        )
                        FilterChip(
                            selected = current.shadowBlurFraction > 0f,
                            onClick = {
                                updateText(
                                    elements,
                                    if (current.shadowBlurFraction > 0f) current.copy(shadowBlurFraction = 0f)
                                    else current.copy(shadowColor = AndroidColor.BLACK, shadowBlurFraction = 0.08f)
                                )
                            },
                            label = { Text("Shadow") }
                        )
                        FilterChip(
                            selected = current.backgroundOpacity > 0f,
                            onClick = {
                                updateText(
                                    elements,
                                    if (current.backgroundOpacity > 0f) current.copy(backgroundOpacity = 0f)
                                    else current.copy(backgroundColor = AndroidColor.BLACK, backgroundOpacity = 0.55f)
                                )
                            },
                            label = { Text("Background") }
                        )
                    }
                    if (current.strokeWidthFraction > 0f) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Outline", modifier = Modifier.size(width = 62.dp, height = 22.dp))
                            Slider(
                                value = current.strokeWidthFraction,
                                onValueChange = { updateText(elements, current.copy(strokeWidthFraction = it)) },
                                valueRange = 0.001f..0.018f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (current.backgroundOpacity > 0f) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Backdrop", modifier = Modifier.size(width = 62.dp, height = 22.dp))
                            Slider(
                                value = current.backgroundOpacity,
                                onValueChange = { updateText(elements, current.copy(backgroundOpacity = it)) },
                                valueRange = 0.1f..1f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Size", modifier = Modifier.size(width = 62.dp, height = 22.dp))
                        Slider(
                            value = current.sizeFraction,
                            onValueChange = { updateText(elements, current.copy(sizeFraction = it)) },
                            valueRange = 0.035f..0.18f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Opacity", modifier = Modifier.size(width = 62.dp, height = 22.dp))
                        Slider(
                            value = current.opacity,
                            onValueChange = { updateText(elements, current.copy(opacity = it)) },
                            valueRange = 0.2f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rotate", modifier = Modifier.size(width = 62.dp, height = 22.dp))
                        Slider(
                            value = current.rotation.coerceIn(-180f, 180f),
                            onValueChange = { updateText(elements, current.copy(rotation = it)) },
                            valueRange = -180f..180f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val editingElement = elements.firstOrNull { it.id == editingId }
        TextElementDialog(
            initial = editingElement,
            onDismiss = {
                showAddDialog = false
                editingId = null
            },
            onConfirm = { text, color, style ->
                if (editingElement == null) {
                    val element = TextElement(text = text, color = color, style = style)
                    elements.add(element)
                    selectedId = element.id
                } else {
                    updateText(elements, editingElement.copy(text = text, color = color, style = style))
                    selectedId = editingElement.id
                }
                showAddDialog = false
                editingId = null
            }
        )
    }
}

@Composable
fun StickerLayerEditor(
    imageBounds: Rect,
    onCancel: () -> Unit,
    onApply: (List<StickerElement>) -> Unit
) {
    val elements = remember { mutableStateListOf<StickerElement>() }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val choices = listOf("✨", "❤️", "🔥", "🌈", "🌸", "⭐", "😎", "🍍", "☀️", "🎉", "💬", "✓")

    Box(Modifier.fillMaxSize()) {
        elements.forEach { element ->
            EditableStickerItem(
                element = element,
                imageBounds = imageBounds,
                selected = selectedId == element.id,
                onSelect = { selectedId = element.id },
                onChange = { updated ->
                    val index = elements.indexOfFirst { it.id == updated.id }
                    if (index >= 0) elements[index] = updated
                }
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            ToolPanel(title = "Stickers", onCancel = onCancel, onApply = { onApply(elements.toList()) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    choices.forEach { sticker ->
                        TextButton(
                            onClick = {
                                val element = StickerElement(value = sticker)
                                elements.add(element)
                                selectedId = element.id
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) { Text(sticker, fontSize = 28.sp) }
                    }
                }
                val selected = elements.firstOrNull { it.id == selectedId }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Size")
                    Slider(
                        value = selected?.sizeFraction ?: 0.16f,
                        onValueChange = { value -> selected?.let { updateSticker(elements, it.copy(sizeFraction = value)) } },
                        valueRange = 0.07f..0.30f,
                        enabled = selected != null,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            selected?.let {
                                val duplicate = it.copy(id = System.nanoTime(), centerX = (it.centerX + 0.06f).coerceAtMost(0.95f))
                                elements.add(duplicate)
                                selectedId = duplicate.id
                            }
                        },
                        enabled = selected != null
                    ) { Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate") }
                    IconButton(
                        onClick = {
                            elements.removeAll { it.id == selectedId }
                            selectedId = elements.lastOrNull()?.id
                        },
                        enabled = selected != null
                    ) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacity", modifier = Modifier.size(width = 62.dp, height = 22.dp))
                    Slider(
                        value = selected?.opacity ?: 1f,
                        onValueChange = { value -> selected?.let { updateSticker(elements, it.copy(opacity = value)) } },
                        valueRange = 0.2f..1f,
                        enabled = selected != null,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rotate", modifier = Modifier.size(width = 62.dp, height = 22.dp))
                    Slider(
                        value = selected?.rotation?.coerceIn(-180f, 180f) ?: 0f,
                        onValueChange = { value -> selected?.let { updateSticker(elements, it.copy(rotation = value)) } },
                        valueRange = -180f..180f,
                        enabled = selected != null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun DrawingEditor(
    imageBounds: Rect,
    onCancel: () -> Unit,
    onApply: (List<DrawStroke>) -> Unit
) {
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var currentPoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(AndroidColor.WHITE) }
    var widthFraction by remember { mutableFloatStateOf(0.012f) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var brushMode by remember { mutableStateOf(BrushMode.SOLID) }
    var erasing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(imageBounds, selectedColor, widthFraction, opacity, brushMode, erasing) {
                    detectDragGestures(
                        onDragStart = { point ->
                            point.toNormalized(imageBounds)?.let { currentPoints = listOf(it) }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            change.position.toNormalized(imageBounds)?.let { point -> currentPoints = currentPoints + point }
                        },
                        onDragEnd = {
                            if (currentPoints.size > 1) {
                                strokes.add(
                                    DrawStroke(
                                        points = currentPoints,
                                        color = selectedColor,
                                        widthFraction = widthFraction,
                                        erase = erasing,
                                        opacity = opacity,
                                        brushMode = brushMode,
                                    )
                                )
                            }
                            currentPoints = emptyList()
                        },
                        onDragCancel = { currentPoints = emptyList() }
                    )
                }
        ) {
            strokes.forEach { stroke -> drawStrokePreview(stroke, imageBounds) }
            if (currentPoints.size > 1) {
                drawStrokePreview(
                    DrawStroke(currentPoints, selectedColor, widthFraction, erasing, opacity, brushMode),
                    imageBounds,
                )
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            ToolPanel(
                title = "Draw",
                subtitle = "Solid, highlighter and neon brushes with real export rendering",
                onCancel = onCancel,
                onApply = { onApply(strokes.toList()) },
            ) {
                ColorChooser(selectedColor) { selectedColor = it }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    BrushMode.entries.forEach { mode ->
                        FilterChip(
                            selected = brushMode == mode && !erasing,
                            onClick = { brushMode = mode; erasing = false },
                            label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        )
                    }
                    FilterChip(selected = erasing, onClick = { erasing = !erasing }, label = { Text("Eraser") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Width", modifier = Modifier.size(width = 62.dp, height = 22.dp))
                    Slider(value = widthFraction, onValueChange = { widthFraction = it }, valueRange = 0.003f..0.045f, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacity", modifier = Modifier.size(width = 62.dp, height = 22.dp))
                    Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..1f, modifier = Modifier.weight(1f), enabled = !erasing)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${strokes.size} strokes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) { Text("Clear") }
                    IconButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, enabled = strokes.isNotEmpty()) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo stroke")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableTextItem(
    element: TextElement,
    imageBounds: Rect,
    selected: Boolean,
    onSelect: () -> Unit,
    onChange: (TextElement) -> Unit
) {
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val latestElement by rememberUpdatedState(element)
    val centerX = imageBounds.left + element.centerX * imageBounds.width
    val centerY = imageBounds.top + element.centerY * imageBounds.height
    val fontSizePx = (min(imageBounds.width, imageBounds.height) * element.sizeFraction).coerceAtLeast(14f)
    val fontSizeSp = with(LocalDensity.current) { fontSizePx.toSp() }
    val family = when (element.style) {
        TextStyleOption.SANS, TextStyleOption.BOLD -> FontFamily.SansSerif
        TextStyleOption.SERIF -> FontFamily.Serif
        TextStyleOption.MONOSPACE -> FontFamily.Monospace
    }
    val alignment = when (element.alignment) {
        TextAlignmentOption.LEFT -> TextAlign.Left
        TextAlignmentOption.CENTER -> TextAlign.Center
        TextAlignmentOption.RIGHT -> TextAlign.Right
    }
    val shadow = if (element.shadowBlurFraction > 0f && element.shadowColor != AndroidColor.TRANSPARENT) {
        Shadow(
            color = Color(element.shadowColor),
            offset = Offset(fontSizePx * 0.035f, fontSizePx * 0.045f),
            blurRadius = fontSizePx * element.shadowBlurFraction,
        )
    } else null
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        element.backgroundOpacity > 0f -> Color(element.backgroundColor).copy(alpha = element.backgroundOpacity)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerX - measuredSize.width / 2f).roundToInt(),
                    (centerY - measuredSize.height / 2f).roundToInt(),
                )
            }
            .onSizeChanged { measuredSize = it }
            .graphicsLayer(
                scaleX = element.scale,
                scaleY = element.scale,
                rotationZ = element.rotation,
                alpha = element.opacity,
                shadowElevation = if (selected) 10f else 0f,
            )
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .clickable { onSelect() }
            .pointerInput(element.id, imageBounds) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    if (imageBounds.width <= 0f || imageBounds.height <= 0f) return@detectTransformGestures
                    onSelect()
                    val current = latestElement
                    onChange(
                        current.copy(
                            centerX = (current.centerX + pan.x / imageBounds.width).coerceIn(0f, 1f),
                            centerY = (current.centerY + pan.y / imageBounds.height).coerceIn(0f, 1f),
                            scale = (current.scale * zoom).coerceIn(0.3f, 5f),
                            rotation = current.rotation + rotation,
                        ),
                    )
                }
            }
    ) {
        if (element.strokeWidthFraction > 0f && element.strokeColor != AndroidColor.TRANSPARENT) {
            val outline = (fontSizePx * element.strokeWidthFraction * 2f).coerceIn(1f, 5f).dp
            listOf(-outline to 0.dp, outline to 0.dp, 0.dp to -outline, 0.dp to outline).forEach { (x, y) ->
                Text(
                    text = element.text,
                    color = Color(element.strokeColor),
                    fontSize = fontSizeSp,
                    fontWeight = if (element.style == TextStyleOption.BOLD) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = family,
                    textAlign = alignment,
                    modifier = Modifier.offset(x = x, y = y),
                )
            }
        }
        Text(
            text = element.text,
            color = Color(element.color),
            fontSize = fontSizeSp,
            fontWeight = if (element.style == TextStyleOption.BOLD) FontWeight.Bold else FontWeight.Normal,
            fontFamily = family,
            textAlign = alignment,
            style = TextStyle(shadow = shadow),
        )
    }
}

@Composable
private fun EditableStickerItem(
    element: StickerElement,
    imageBounds: Rect,
    selected: Boolean,
    onSelect: () -> Unit,
    onChange: (StickerElement) -> Unit
) {
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val latestElement by rememberUpdatedState(element)
    val centerX = imageBounds.left + element.centerX * imageBounds.width
    val centerY = imageBounds.top + element.centerY * imageBounds.height
    val fontSizePx = min(imageBounds.width, imageBounds.height) * element.sizeFraction
    val fontSizeSp = with(LocalDensity.current) { fontSizePx.toSp() }
    Text(
        text = element.value,
        fontSize = fontSizeSp,
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerX - measuredSize.width / 2f).roundToInt(),
                    (centerY - measuredSize.height / 2f).roundToInt()
                )
            }
            .onSizeChanged { measuredSize = it }
            .graphicsLayer(
                scaleX = element.scale,
                scaleY = element.scale,
                rotationZ = element.rotation,
                alpha = element.opacity
            )
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent, CircleShape)
            .padding(5.dp)
            .clickable { onSelect() }
            .pointerInput(element.id, imageBounds) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    if (imageBounds.width <= 0f || imageBounds.height <= 0f) return@detectTransformGestures
                    onSelect()
                    val current = latestElement
                    onChange(
                        current.copy(
                            centerX = (current.centerX + pan.x / imageBounds.width).coerceIn(0f, 1f),
                            centerY = (current.centerY + pan.y / imageBounds.height).coerceIn(0f, 1f),
                            scale = (current.scale * zoom).coerceIn(0.3f, 5f),
                            rotation = current.rotation + rotation
                        )
                    )
                }
            }
    )
}

@Composable
private fun TextElementDialog(
    initial: TextElement?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, TextStyleOption) -> Unit
) {
    var text by remember(initial?.id, initial?.text) { mutableStateOf(initial?.text.orEmpty()) }
    var color by remember(initial?.id, initial?.color) { mutableStateOf(initial?.color ?: AndroidColor.WHITE) }
    var style by remember(initial?.id, initial?.style) { mutableStateOf(initial?.style ?: TextStyleOption.BOLD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add text" else "Edit text") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(100) },
                    label = { Text("Text") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextStyleOption.entries.forEach { option ->
                        FilterChip(
                            selected = style == option,
                            onClick = { style = option },
                            label = { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) }
                        )
                    }
                }
                ColorChooser(color) { color = it }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.trim(), color, style) }, enabled = text.isNotBlank()) {
                Text(if (initial == null) "Add" else "Update")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ColorChooser(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    val colors = listOf(
        AndroidColor.WHITE,
        AndroidColor.BLACK,
        AndroidColor.rgb(255, 200, 87),
        AndroidColor.rgb(255, 91, 127),
        AndroidColor.rgb(93, 190, 255),
        AndroidColor.rgb(105, 220, 144),
        AndroidColor.rgb(176, 122, 255)
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(if (selectedColor == color) 30.dp else 26.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

private fun updateText(elements: MutableList<TextElement>, updated: TextElement) {
    val index = elements.indexOfFirst { it.id == updated.id }
    if (index >= 0) elements[index] = updated
}

private fun updateSticker(elements: MutableList<StickerElement>, updated: StickerElement) {
    val index = elements.indexOfFirst { it.id == updated.id }
    if (index >= 0) elements[index] = updated
}

private fun selectionToRect(selection: CropSelection, bounds: Rect): Rect = Rect(
    left = bounds.left + selection.left * bounds.width,
    top = bounds.top + selection.top * bounds.height,
    right = bounds.left + selection.right * bounds.width,
    bottom = bounds.top + selection.bottom * bounds.height
)

private fun hitCropHandle(rect: Rect, point: Offset): CropHandle {
    val radius = 48f
    return when {
        (point - rect.topLeft).getDistance() <= radius -> CropHandle.TOP_LEFT
        (point - Offset(rect.right, rect.top)).getDistance() <= radius -> CropHandle.TOP_RIGHT
        (point - Offset(rect.left, rect.bottom)).getDistance() <= radius -> CropHandle.BOTTOM_LEFT
        (point - rect.bottomRight).getDistance() <= radius -> CropHandle.BOTTOM_RIGHT
        rect.contains(point) -> CropHandle.MOVE
        else -> CropHandle.NONE
    }
}

private fun updateCropSelection(
    selection: CropSelection,
    handle: CropHandle,
    dx: Float,
    dy: Float
): CropSelection {
    val minSize = 0.08f
    return when (handle) {
        CropHandle.MOVE -> {
            val movedLeft = (selection.left + dx).coerceIn(0f, 1f - selection.width)
            val movedTop = (selection.top + dy).coerceIn(0f, 1f - selection.height)
            selection.copy(
                left = movedLeft,
                right = movedLeft + selection.width,
                top = movedTop,
                bottom = movedTop + selection.height
            )
        }
        CropHandle.TOP_LEFT -> selection.copy(
            left = (selection.left + dx).coerceIn(0f, selection.right - minSize),
            top = (selection.top + dy).coerceIn(0f, selection.bottom - minSize)
        )
        CropHandle.TOP_RIGHT -> selection.copy(
            right = (selection.right + dx).coerceIn(selection.left + minSize, 1f),
            top = (selection.top + dy).coerceIn(0f, selection.bottom - minSize)
        )
        CropHandle.BOTTOM_LEFT -> selection.copy(
            left = (selection.left + dx).coerceIn(0f, selection.right - minSize),
            bottom = (selection.bottom + dy).coerceIn(selection.top + minSize, 1f)
        )
        CropHandle.BOTTOM_RIGHT -> selection.copy(
            right = (selection.right + dx).coerceIn(selection.left + minSize, 1f),
            bottom = (selection.bottom + dy).coerceIn(selection.top + minSize, 1f)
        )
        CropHandle.NONE -> selection
    }
}

private fun Offset.toNormalized(bounds: Rect): NormalizedPoint? {
    if (!bounds.contains(this) || bounds.width <= 0f || bounds.height <= 0f) return null
    return NormalizedPoint(
        x = ((x - bounds.left) / bounds.width).coerceIn(0f, 1f),
        y = ((y - bounds.top) / bounds.height).coerceIn(0f, 1f)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePreview(
    stroke: DrawStroke,
    bounds: Rect
) {
    if (stroke.points.size < 2) return
    val path = Path().apply {
        val first = stroke.points.first()
        moveTo(bounds.left + first.x * bounds.width, bounds.top + first.y * bounds.height)
        for (index in 1 until stroke.points.size) {
            val previous = stroke.points[index - 1]
            val current = stroke.points[index]
            val midX = bounds.left + (previous.x + current.x) * 0.5f * bounds.width
            val midY = bounds.top + (previous.y + current.y) * 0.5f * bounds.height
            quadraticTo(
                bounds.left + previous.x * bounds.width,
                bounds.top + previous.y * bounds.height,
                midX,
                midY,
            )
        }
        val last = stroke.points.last()
        lineTo(bounds.left + last.x * bounds.width, bounds.top + last.y * bounds.height)
    }
    val baseWidth = min(bounds.width, bounds.height) * stroke.widthFraction
    val alpha = when (stroke.brushMode) {
        BrushMode.SOLID, BrushMode.NEON -> stroke.opacity
        BrushMode.HIGHLIGHTER -> stroke.opacity * 0.42f
        else -> stroke.opacity
    }.coerceIn(0f, 1f)
    val blend = if (stroke.erase) BlendMode.Clear else BlendMode.SrcOver
    if (stroke.brushMode == BrushMode.NEON && !stroke.erase) {
        drawPath(
            path = path,
            color = Color(stroke.color).copy(alpha = alpha * 0.28f),
            style = Stroke(width = baseWidth * 2.7f, cap = StrokeCap.Round),
            blendMode = blend,
        )
        drawPath(
            path = path,
            color = Color.White.copy(alpha = alpha),
            style = Stroke(width = (baseWidth * 0.34f).coerceAtLeast(1f), cap = StrokeCap.Round),
            blendMode = blend,
        )
    } else {
        drawPath(
            path = path,
            color = if (stroke.erase) Color.Transparent else Color(stroke.color).copy(alpha = alpha),
            style = Stroke(
                width = if (stroke.brushMode == BrushMode.HIGHLIGHTER) baseWidth * 1.7f else baseWidth,
                cap = StrokeCap.Round,
            ),
            blendMode = blend,
        )
    }
}

