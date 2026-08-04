package com.example.ananas

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.enableEdgeToEdge
import com.example.ananas.editor.EditorViewModel
import com.example.ananas.ui.AnanasApp
import com.example.ananas.ui.theme.AnanasTheme

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()
    private var externalImageUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        externalImageUri = extractImageUri(intent)

        setContent {
            AnanasTheme(darkTheme = true) {
                AnanasApp(
                    viewModel = viewModel,
                    externalImageUri = externalImageUri,
                    onExternalUriConsumed = { externalImageUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalImageUri = extractImageUri(intent)
    }

    private fun extractImageUri(intent: Intent?): Uri? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            else -> null
        }
    }
}
