package com.example.ananas.editimage

import android.app.Activity
import android.content.Context
import android.content.Intent

class ImageEditorIntentBuilder(
    private val context: Context,
    private val sourcePath: String,
    private val outputPath: String
) {
    private val intent = Intent(context, EditImageActivity::class.java)

    companion object {
        const val SOURCE_PATH = "extra_source_path"
        const val OUTPUT_PATH = "extra_output_path"
    }

    init {
        intent.putExtra(SOURCE_PATH, sourcePath)
        intent.putExtra(OUTPUT_PATH, outputPath)
    }

    fun withAddText() = this
    fun withPaintFeature() = this
    fun withFilterFeature() = this
    fun withRotateFeature() = this
    fun withCropFeature() = this
    fun withBrightnessFeature() = this
    fun withSaturationFeature() = this
    fun withBeautyFeature() = this
    fun withStickerFeature() = this
    fun forcePortrait(force: Boolean) = this
    fun setSupportActionBarVisibility(visible: Boolean) = this

    fun build(): Intent = intent
}
