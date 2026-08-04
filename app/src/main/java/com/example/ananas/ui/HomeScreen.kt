package com.example.ananas.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ananas.editor.BatchState
import com.example.ananas.editor.EditorViewModel
import com.example.ananas.editor.ProjectSummary
import com.example.ananas.editor.RecipePreset

@Composable
fun HomeScreen(viewModel: EditorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showBatchSetup by rememberSaveable { mutableStateOf(false) }
    var showBatchResult by rememberSaveable { mutableStateOf(false) }
    var pendingBatchRecipe by remember { mutableStateOf(RecipePreset.CLEAN_PRODUCT) }

    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::openImage)
    }
    val batchPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) viewModel.runBatch(uris, pendingBatchRecipe)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) viewModel.openImage(uri)
        cameraUri = null
    }
    val legacyBatchPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching {
                batchPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.onFailure { viewModel.notifyUser("Multiple photo selection is unavailable") }
        } else {
            viewModel.notifyUser("Storage permission is required for batch export on Android 9 and lower")
        }
    }

    fun selectBatchPhotos(recipe: RecipePreset) {
        pendingBatchRecipe = recipe
        showBatchSetup = false
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            legacyBatchPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            runCatching {
                batchPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.onFailure { viewModel.notifyUser("Multiple photo selection is unavailable") }
        }
    }

    LaunchedEffect(state.message?.id) {
        state.message?.let { message ->
            snackbarHost.showSnackbar(message.text)
            viewModel.consumeMessage(message.id)
        }
    }
    LaunchedEffect(state.batchState.isRunning, state.batchState.completed, state.batchState.total) {
        if (state.batchState.hasResult && !state.batchState.isRunning) showBatchResult = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF07080C),
                    0.48f to Color(0xFF0C0E15),
                    1f to Color(0xFF080A10),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 10.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                HomeHeader()
            }
            item {
                CreateHero(
                    enabled = !state.isLoading && !state.batchState.isRunning,
                    onChoose = {
                        runCatching {
                            singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }.onFailure { viewModel.notifyUser("No compatible photo picker is available") }
                    },
                    onCamera = {
                        runCatching {
                            viewModel.createCameraUri().also { uri ->
                                cameraUri = uri
                                camera.launch(uri)
                            }
                        }.onFailure {
                            cameraUri = null
                            viewModel.notifyUser("No camera application is available")
                        }
                    },
                    onDemo = viewModel::openSample,
                )
            }
            item {
                BatchStudioCard(
                    batchState = state.batchState,
                    onStart = { showBatchSetup = true },
                    onCancel = viewModel::cancelBatch,
                    onDetails = { showBatchResult = true },
                )
            }
            if (state.projects.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Recent projects",
                        subtitle = "Pick up exactly where you stopped",
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 8.dp),
                    ) {
                        items(state.projects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                enabled = !state.batchState.isRunning && !state.isLoading,
                                onOpen = { viewModel.openProject(project.id) },
                                onDuplicate = { viewModel.duplicateProject(project.id) },
                                onDelete = { viewModel.deleteProject(project.id) },
                            )
                        }
                    }
                }
            }
            item {
                PrivacyStrip()
            }
        }

        if (state.isLoading) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xE61A1C21),
                shadowElevation = 18.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Column {
                        Text("Opening photo", fontWeight = FontWeight.Bold)
                        Text("Preparing the full-resolution editor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SnackbarHost(snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (showBatchSetup) {
        BatchSetupDialog(
            selected = pendingBatchRecipe,
            onDismiss = { showBatchSetup = false },
            onStart = ::selectBatchPhotos,
        )
    }
    if (showBatchResult && state.batchState.total > 0) {
        BatchResultDialog(
            batchState = state.batchState,
            onDismiss = { showBatchResult = false },
            onRetry = {
                showBatchResult = false
                viewModel.retryFailedBatch()
            },
            onClear = {
                showBatchResult = false
                viewModel.clearBatchResult()
            },
        )
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, Color(0xFF7467F0)),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "A",
                color = Color(0xFF120D2B),
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )
        }
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Text(
                "ANANAS",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.8.sp,
                fontSize = 19.sp,
            )
            Text(
                "Private photo workspace",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.055f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text("On-device", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CreateHero(
    enabled: Boolean,
    onChoose: () -> Unit,
    onCamera: () -> Unit,
    onDemo: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF211A42),
                        Color(0xFF151827),
                        Color(0xFF10131B),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(32.dp))
            .padding(24.dp),
    ) {
        Spacer(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(132.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), Color.Transparent),
                    ),
                ),
        )
        Column {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                    Text("FULL-RESOLUTION STUDIO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Shape every pixel.\nKeep every detail.",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                "Layers, selective masks, retouching and professional color controls—without uploading your photos.",
                modifier = Modifier.padding(top = 12.dp, bottom = 22.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp,
            )
            Button(
                onClick = onChoose,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(19.dp),
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Text("Open photo", modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onCamera,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text("Camera", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = onDemo,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text("Explore demo", Modifier.padding(start = 7.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BatchStudioCard(
    batchState: BatchState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDetails: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF12151D),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Collections, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Batch Studio", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        if (batchState.isRunning) batchState.currentName ?: "Processing selected photos"
                        else "Apply one polished recipe to up to 50 photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!batchState.isRunning) {
                    FilledTonalButton(onClick = onStart, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Start", Modifier.padding(start = 4.dp))
                    }
                }
            }

            if (batchState.isRunning || batchState.hasResult) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color.White.copy(alpha = 0.06f))
                LinearProgressIndicator(
                    progress = { batchState.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${batchState.completed}/${batchState.total} processed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${batchState.exported} saved", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    if (batchState.failed > 0) {
                        Text(" · ${batchState.failed} failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (batchState.isRunning) {
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Stop", Modifier.padding(start = 5.dp))
                        }
                    } else {
                        TextButton(onClick = onDetails) { Text("View result") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val thumbnail = remember(project.thumbnailPath, project.updatedAt) {
        BitmapFactory.decodeFile(project.thumbnailPath)?.asImageBitmap()
    }
    Surface(
        onClick = onOpen,
        enabled = enabled,
        modifier = Modifier.width(224.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF12151D),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.42f)
                    .background(Color(0xFF202329)),
            ) {
                thumbnail?.let {
                    Image(it, contentDescription = project.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(9.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.68f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                        Text("${project.layerCount}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 11.dp, end = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(project.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${project.width} × ${project.height}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDuplicate, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PrivacyStrip() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text("Your photos stay on this device", fontWeight = FontWeight.Bold)
                Text("Core editing, projects and batch recipes do not require an account or upload.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BatchSetupDialog(
    selected: RecipePreset,
    onDismiss: () -> Unit,
    onStart: (RecipePreset) -> Unit,
) {
    var recipe by remember(selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch Studio", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Choose a recipe, then select up to 50 photos. Images are processed one at a time to keep memory usage stable.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RecipePreset.entries.forEach { option ->
                    Surface(
                        onClick = { recipe = option },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (recipe == option) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (recipe == option) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.Transparent,
                        ),
                    ) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = CircleShape,
                                color = if (recipe == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (recipe == option) Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                    else Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                                }
                            }
                            Column(Modifier.padding(start = 11.dp)) {
                                Text(option.label, fontWeight = FontWeight.Bold)
                                Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(recipe) }) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Select photos", Modifier.padding(start = 7.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BatchResultDialog(
    batchState: BatchState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (batchState.failed == 0) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (batchState.failed == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                when {
                    batchState.wasCancelled -> "Batch stopped"
                    batchState.failed == 0 -> "Batch complete"
                    batchState.exported > 0 -> "Batch completed with skips"
                    else -> "Batch failed"
                },
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${batchState.exported} saved · ${batchState.failed} failed · ${batchState.completed}/${batchState.total} processed")
                if (batchState.exported > 0) {
                    Text("Saved to Pictures/Ananas", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                }
                if (batchState.failures.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Failure details", fontWeight = FontWeight.Bold)
                    batchState.failures.take(6).forEach { failure ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(failure.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(failure.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    if (batchState.failures.size > 6) {
                        Text("+ ${batchState.failures.size - 6} more", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            if (batchState.failed > 0) {
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Retry failed", Modifier.padding(start = 6.dp))
                }
            } else {
                Button(onClick = onClear) { Text("Done") }
            }
        },
        dismissButton = {
            TextButton(onClick = if (batchState.failed > 0) onClear else onDismiss) {
                Text(if (batchState.failed > 0) "Clear" else "Close")
            }
        },
    )
}
