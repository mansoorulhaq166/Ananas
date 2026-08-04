package com.example.ananas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ananas.editor.MaskStroke
import com.example.ananas.editor.NormalizedPoint

@Composable
fun MaskPainterOverlay(
    modifier: Modifier = Modifier,
    imageBounds: Rect,
    strokes: List<MaskStroke>,
    brushSize: Float,
    subtract: Boolean,
    onStrokesChange: (List<MaskStroke>) -> Unit,
    addColor: Color = Color(0x9948D7FF),
    subtractColor: Color = Color(0x99FF5A72),
) {
    var currentPoints by remember(brushSize, subtract) { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    Canvas(
        modifier.pointerInput(imageBounds, brushSize, subtract, strokes.size) {
            detectDragGestures(
                onDragStart = { point ->
                    currentPoints = point.toNormalized(imageBounds)?.let(::listOf) ?: emptyList()
                },
                onDrag = { change, _ ->
                    change.consume()
                    change.position.toNormalized(imageBounds)?.let { point -> currentPoints = currentPoints + point }
                },
                onDragEnd = {
                    if (currentPoints.isNotEmpty()) onStrokesChange(strokes + MaskStroke(currentPoints, brushSize, subtract))
                    currentPoints = emptyList()
                },
                onDragCancel = { currentPoints = emptyList() },
            )
        }
    ) {
        strokes.forEach { stroke ->
            val color = if (stroke.subtract) subtractColor else addColor
            val width = (minOf(imageBounds.width, imageBounds.height) * stroke.widthFraction).coerceAtLeast(2f)
            if (stroke.points.size == 1) {
                val point = stroke.points.first().toOffset(imageBounds)
                drawCircle(color, radius = width / 2f, center = point)
            } else {
                val path = Path().apply {
                    val first = stroke.points.first().toOffset(imageBounds)
                    moveTo(first.x, first.y)
                    stroke.points.drop(1).forEach { point ->
                        val offset = point.toOffset(imageBounds)
                        lineTo(offset.x, offset.y)
                    }
                }
                drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
            }
        }
        if (currentPoints.isNotEmpty()) {
            val color = if (subtract) subtractColor else addColor
            val width = (minOf(imageBounds.width, imageBounds.height) * brushSize).coerceAtLeast(2f)
            if (currentPoints.size == 1) {
                drawCircle(color, radius = width / 2f, center = currentPoints.first().toOffset(imageBounds))
            } else {
                val path = Path().apply {
                    val first = currentPoints.first().toOffset(imageBounds)
                    moveTo(first.x, first.y)
                    currentPoints.drop(1).forEach { point ->
                        val offset = point.toOffset(imageBounds)
                        lineTo(offset.x, offset.y)
                    }
                }
                drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun RadialMaskGuide(
    modifier: Modifier = Modifier,
    imageBounds: Rect,
    centerX: Float,
    centerY: Float,
    radiusFraction: Float,
) {
    Canvas(modifier) {
        val center = Offset(
            imageBounds.left + imageBounds.width * centerX,
            imageBounds.top + imageBounds.height * centerY,
        )
        val radius = minOf(imageBounds.width, imageBounds.height) * radiusFraction
        drawCircle(Color.White.copy(alpha = 0.8f), radius, center, style = Stroke(width = 2f))
        drawCircle(Color(0xFF48D7FF), 7f, center)
    }
}

private fun Offset.toNormalized(bounds: Rect): NormalizedPoint? {
    if (bounds.width <= 0f || bounds.height <= 0f || x !in bounds.left..bounds.right || y !in bounds.top..bounds.bottom) return null
    return NormalizedPoint(
        ((x - bounds.left) / bounds.width).coerceIn(0f, 1f),
        ((y - bounds.top) / bounds.height).coerceIn(0f, 1f),
    )
}

private fun NormalizedPoint.toOffset(bounds: Rect): Offset = Offset(
    bounds.left + x * bounds.width,
    bounds.top + y * bounds.height,
)
