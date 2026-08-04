package com.example.ananas.ui

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ananas.editor.EditorViewModel

@Composable
fun AnanasApp(
    viewModel: EditorViewModel,
    externalImageUri: Uri?,
    onExternalUriConsumed: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val needsReplacementConfirmation = externalImageUri != null &&
        state.currentBitmap != null &&
        state.hasUnsavedChanges

    LaunchedEffect(
        externalImageUri,
        state.currentBitmap,
        state.hasUnsavedChanges,
        needsReplacementConfirmation
    ) {
        val uri = externalImageUri ?: return@LaunchedEffect
        if (!needsReplacementConfirmation) {
            viewModel.openImage(uri)
            onExternalUriConsumed()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (state.currentBitmap == null) {
            HomeScreen(viewModel = viewModel)
        } else {
            EditorScreen(viewModel = viewModel, state = state)
        }
    }

    if (needsReplacementConfirmation) {
        AlertDialog(
            onDismissRequest = onExternalUriConsumed,
            title = { Text("Open another image?") },
            text = { Text("Your current unsaved edits will be replaced.") },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = externalImageUri ?: return@Button
                        viewModel.openImage(uri)
                        onExternalUriConsumed()
                    }
                ) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = onExternalUriConsumed) { Text("Keep current") }
            }
        )
    }
}
