package com.example.ananas.editor

import android.graphics.Bitmap

internal fun Bitmap.copyArgb(mutable: Boolean = false): Bitmap =
    copy(Bitmap.Config.ARGB_8888, mutable)
        ?: throw OutOfMemoryError("Unable to allocate an ARGB bitmap copy")
