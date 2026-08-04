package com.example.ananas.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

object SampleImage {
    fun create(): Bitmap {
        val width = 1400
        val height = 1000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(Color.rgb(255, 177, 66), Color.rgb(255, 105, 135), Color.rgb(74, 48, 132)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

        val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 255, 241, 170) }
        canvas.drawCircle(width * 0.76f, height * 0.28f, height * 0.13f, sun)

        val hill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(42, 56, 74) }
        val path = android.graphics.Path().apply {
            moveTo(0f, height.toFloat())
            lineTo(0f, height * 0.68f)
            cubicTo(width * 0.2f, height * 0.5f, width * 0.36f, height * 0.74f, width * 0.56f, height * 0.57f)
            cubicTo(width * 0.76f, height * 0.42f, width * 0.9f, height * 0.64f, width.toFloat(), height * 0.52f)
            lineTo(width.toFloat(), height.toFloat())
            close()
        }
        canvas.drawPath(path, hill)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 104f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
        canvas.drawText("ANANAS", 82f, 160f, textPaint)
        textPaint.textSize = 42f
        textPaint.alpha = 210
        canvas.drawText("Create something bright.", 88f, 220f, textPaint)
        return bitmap
    }
}
