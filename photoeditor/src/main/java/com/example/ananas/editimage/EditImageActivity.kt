package com.example.ananas.editimage

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ananas.editor.EditorViewModel
import com.example.ananas.ui.EditorScreen
import com.example.ananas.ui.theme.AnanasTheme
import java.io.File

class EditImageActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    companion object {
        const val IS_IMAGE_EDITED = "is_image_edited"
        
        fun start(activity: Activity, intent: Intent, requestCode: Int) {
            activity.startActivityForResult(intent, requestCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sourcePath = intent.getStringExtra(ImageEditorIntentBuilder.SOURCE_PATH)
        val uri = sourcePath?.let { Uri.fromFile(File(it)) }

        if (uri != null) {
            viewModel.openImage(uri)
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            
            LaunchedEffect(state.lastExportUri) {
                if (state.lastExportUri != null) {
                    val resultIntent = Intent().apply {
                        putExtra(ImageEditorIntentBuilder.OUTPUT_PATH, intent.getStringExtra(ImageEditorIntentBuilder.OUTPUT_PATH) ?: state.lastExportUri.toString())
                        putExtra(IS_IMAGE_EDITED, state.hasUnsavedChanges || state.lastExportUri != null)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            }

            AnanasTheme(darkTheme = true) {
                if (state.currentBitmap != null) {
                    EditorScreen(viewModel = viewModel, state = state)
                }
            }
        }
    }
}
