package com.example.ananas.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ananas.editor.CropSelection
import com.example.ananas.editor.EditorState
import com.example.ananas.editor.EditorTool
import com.example.ananas.editor.EditorViewModel
import com.example.ananas.editor.FocusMode
import com.example.ananas.editor.MaskMode
import com.example.ananas.editor.SmartExportPreset
import com.example.ananas.ui.components.AdjustmentPanel
import com.example.ananas.ui.components.BeautyPanel
import com.example.ananas.ui.components.ColorGradePanel
import com.example.ananas.ui.components.CropEditorOverlay
import com.example.ananas.ui.components.CurvesPanel
import com.example.ananas.ui.components.DrawingEditor
import com.example.ananas.ui.components.EffectsPanel
import com.example.ananas.ui.components.SmartExportDialog
import com.example.ananas.ui.components.FilterPanel
import com.example.ananas.ui.components.FocusPanel
import com.example.ananas.ui.components.FramePanel
import com.example.ananas.ui.components.HslPanel
import com.example.ananas.ui.components.LensPanel
import com.example.ananas.ui.components.LayersPanel
import com.example.ananas.ui.components.MaskPainterOverlay
import com.example.ananas.ui.components.MaskPanel
import com.example.ananas.ui.components.CutoutPanel
import com.example.ananas.ui.components.HealingPanel
import com.example.ananas.ui.components.RecipePanel
import com.example.ananas.ui.components.ProductStudioPanel
import com.example.ananas.ui.components.RadialMaskGuide
import com.example.ananas.ui.components.PerspectivePanel
import com.example.ananas.ui.components.ResizePanel
import com.example.ananas.ui.components.RotatePanel
import com.example.ananas.ui.components.StickerLayerEditor
import com.example.ananas.ui.components.TextLayerEditor
import com.example.ananas.ui.components.ToolPanel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun EditorScreen(viewModel: EditorViewModel, state: EditorState) {
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var showExportDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var cropSelection by remember(state.currentTool) { mutableStateOf(CropSelection()) }
    var selectedFrame by remember(state.currentTool) { mutableIntStateOf(0) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var selectedCategory by rememberSaveable { mutableStateOf(EditorCategory.QUICK) }

    LaunchedEffect(state.currentTool) {
        zoom = 1f
        pan = Offset.Zero
    }

    val legacyStoragePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.exportToGallery()
        } else {
            val activity = context as? Activity
            val permanentlyDenied = activity != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            viewModel.notifyUser(
                if (permanentlyDenied) "Storage access is blocked. Enable it in App info to save on this Android version."
                else "Storage permission was denied, so the image was not saved.",
            )
        }
    }

    fun saveWithPermission() {
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.exportToGallery()
        }
    }

    LaunchedEffect(state.message?.id) {
        state.message?.let { message ->
            snackbarHost.showSnackbar(message.text)
            viewModel.consumeMessage(message.id)
        }
    }

    LaunchedEffect(state.pendingShareUri) {
        state.pendingShareUri?.let { uri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = state.exportFormat.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "Ananas image", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(shareIntent, "Share edited image")) }
                .onFailure { viewModel.notifyUser("No compatible sharing application is available") }
            viewModel.consumeShareUri()
        }
    }

    BackHandler {
        when {
            state.currentTool != EditorTool.NONE -> viewModel.closeTool()
            state.hasUnsavedChanges -> showExitDialog = true
            else -> viewModel.closeDocument()
        }
    }

    val bitmap = if (state.showOriginal) state.originalBitmap else state.currentBitmap
    val imageBounds = remember(canvasSize, bitmap?.width, bitmap?.height) {
        calculateImageBounds(canvasSize, bitmap?.width ?: 1, bitmap?.height ?: 1)
    }
    val layerTool = state.currentTool in setOf(EditorTool.TEXT, EditorTool.STICKERS, EditorTool.DRAW)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            EditorTopBar(
                title = state.activeProjectName,
                state = state,
                onBack = {
                    if (state.currentTool != EditorTool.NONE) viewModel.closeTool()
                    else if (state.hasUnsavedChanges) showExitDialog = true
                    else viewModel.closeDocument()
                },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onCompare = { viewModel.setShowOriginal(!state.showOriginal) },
                onReset = viewModel::resetAll,
                onSave = { showExportDialog = true },
                onShare = viewModel::prepareShare,
                onSaveProject = viewModel::saveProjectNow,
            )
        },
        bottomBar = {
            if (!layerTool) {
                when (state.currentTool) {
                    EditorTool.NONE -> ProfessionalToolDock(
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it },
                        onToolSelected = viewModel::openTool,
                    )
                    EditorTool.FILTERS -> FilterPanel(
                        selected = state.selectedFilter,
                        intensity = state.filterIntensity,
                        onSelect = viewModel::previewFilter,
                        onIntensityChange = viewModel::previewFilterIntensity,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.ADJUST -> AdjustmentPanel(
                        selected = state.selectedAdjustment,
                        value = state.adjustmentValue,
                        onSelect = viewModel::selectAdjustment,
                        onValueChange = { viewModel.previewAdjustment(state.selectedAdjustment, it) },
                        onResetAll = viewModel::resetAdjustments,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.CURVES -> CurvesPanel(
                        channel = state.curveChannel,
                        band = state.curveBand,
                        settings = state.curveSettings,
                        onChannelSelected = viewModel::selectCurveChannel,
                        onBandSelected = viewModel::selectCurveBand,
                        onSettingsChange = viewModel::previewCurve,
                        onResetChannel = viewModel::resetCurveChannel,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.HSL -> HslPanel(
                        selectedRange = state.selectedHslRange,
                        settings = state.hslSettings,
                        onRangeSelected = viewModel::selectHslRange,
                        onSettingsChange = viewModel::previewHsl,
                        onResetRange = viewModel::resetHslRange,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.COLOR_GRADE -> ColorGradePanel(
                        selectedZone = state.selectedGradeZone,
                        settings = state.colorGradeSettings,
                        onZoneSelected = viewModel::selectGradeZone,
                        onSettingsChange = viewModel::previewColorGrade,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.EFFECTS -> EffectsPanel(
                        settings = state.effectSettings,
                        onSettingsChange = viewModel::previewEffect,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.LENS -> LensPanel(
                        settings = state.lensSettings,
                        onSettingsChange = viewModel::previewLens,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.FOCUS -> FocusPanel(
                        settings = state.focusSettings,
                        onSettingsChange = viewModel::previewFocus,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.PERSPECTIVE -> PerspectivePanel(
                        settings = state.perspectiveSettings,
                        onSettingsChange = viewModel::previewPerspective,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.CROP -> CropPanel(
                        selection = cropSelection,
                        imageAspect = state.documentWidth.toFloat() / state.documentHeight.coerceAtLeast(1),
                        onSelectionChange = { cropSelection = it },
                        onCancel = viewModel::closeTool,
                        onApply = { viewModel.crop(cropSelection) },
                    )
                    EditorTool.ROTATE -> RotatePanel(
                        straightenValue = state.adjustmentValue,
                        onStraightenChange = viewModel::previewStraighten,
                        onRotateLeft = { viewModel.rotateBy(-90f) },
                        onRotateRight = { viewModel.rotateBy(90f) },
                        onFlipHorizontal = viewModel::flipHorizontal,
                        onFlipVertical = viewModel::flipVertical,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.RESIZE -> ResizePanel(
                        currentWidth = state.documentWidth,
                        currentHeight = state.documentHeight,
                        onCancel = viewModel::closeTool,
                        onResize = viewModel::resize,
                    )
                    EditorTool.BEAUTY -> BeautyPanel(
                        smooth = state.beautySmooth,
                        whiten = state.beautyWhiten,
                        onChange = viewModel::previewBeauty,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.FRAME -> FramePanel(
                        selectedStyle = selectedFrame,
                        onSelect = { selectedFrame = it },
                        onCancel = viewModel::closeTool,
                        onApply = { viewModel.applyFrame(selectedFrame) },
                    )
                    EditorTool.LAYERS -> LayersPanel(
                        layers = state.layers,
                        selectedLayerId = state.selectedLayerId,
                        onSelect = viewModel::selectLayer,
                        onVisibility = viewModel::toggleLayerVisibility,
                        onLock = viewModel::toggleLayerLock,
                        onOpacity = viewModel::setLayerOpacity,
                        onBlendMode = viewModel::setLayerBlendMode,
                        onMove = viewModel::moveLayer,
                        onDuplicate = viewModel::duplicateLayer,
                        onDelete = viewModel::deleteLayer,
                        onFlatten = viewModel::flattenLayers,
                        onSaveProject = viewModel::saveProjectNow,
                        onClose = viewModel::closeTool,
                    )
                    EditorTool.MASK -> MaskPanel(
                        settings = state.maskSettings,
                        onSettingsChange = viewModel::previewMask,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.CUTOUT -> CutoutPanel(
                        settings = state.cutoutSettings,
                        onSettingsChange = viewModel::previewCutout,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.HEAL -> HealingPanel(
                        settings = state.healingSettings,
                        onSettingsChange = viewModel::updateHealingSettings,
                        onCancel = viewModel::closeTool,
                        onApply = { viewModel.applyHealing(state.healingSettings) },
                    )
                    EditorTool.RECIPES -> RecipePanel(
                        settings = state.recipeSettings,
                        onSettingsChange = viewModel::previewRecipe,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.PRODUCT -> ProductStudioPanel(
                        settings = state.productStudioSettings,
                        onSettingsChange = viewModel::previewProductStudio,
                        onCancel = viewModel::cancelPreview,
                        onApply = viewModel::commitPreview,
                    )
                    EditorTool.TEXT, EditorTool.STICKERS, EditorTool.DRAW -> Unit
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF171A24), Color(0xFF07080D)),
                        radius = 1200f,
                    ),
                )
                .onSizeChanged { canvasSize = it },
            contentAlignment = Alignment.Center,
        ) {
            StudioStageBackdrop(imageBounds = imageBounds)

            bitmap?.let { image ->
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Edited image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        }
                        .then(
                            if (state.currentTool == EditorTool.NONE) {
                                Modifier.pointerInput(image.width, image.height) {
                                    detectTransformGestures { _, panChange, zoomChange, _ ->
                                        zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                        val maxPanX = size.width * (zoom - 1f) * 0.5f
                                        val maxPanY = size.height * (zoom - 1f) * 0.5f
                                        pan = Offset(
                                            (pan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                            (pan.y + panChange.y).coerceIn(-maxPanY, maxPanY),
                                        )
                                    }
                                }
                            } else Modifier
                        ),
                )
            }

            if (state.currentTool == EditorTool.CROP) {
                CropEditorOverlay(imageBounds, cropSelection, { cropSelection = it })
            }
            if (state.currentTool == EditorTool.FOCUS) {
                FocusGuide(
                    imageBounds = imageBounds,
                    state = state,
                    onCenterChange = { centerX, centerY ->
                        viewModel.previewFocus(state.focusSettings.copy(centerX = centerX, centerY = centerY))
                    },
                )
            }
            if (state.currentTool == EditorTool.PERSPECTIVE) {
                PerspectiveGuide(imageBounds)
            }
            if (state.currentTool == EditorTool.MASK) {
                if (state.maskSettings.mode == MaskMode.BRUSH) {
                    MaskPainterOverlay(
                        modifier = Modifier.fillMaxSize(),
                        imageBounds = imageBounds,
                        strokes = state.maskSettings.strokes,
                        brushSize = state.maskSettings.brushSize,
                        subtract = state.maskSettings.subtractBrush,
                        onStrokesChange = { viewModel.previewMask(state.maskSettings.copy(strokes = it)) },
                    )
                } else if (state.maskSettings.mode == MaskMode.RADIAL) {
                    RadialMaskGuide(Modifier.fillMaxSize(), imageBounds, state.maskSettings.centerX, state.maskSettings.centerY, state.maskSettings.radius)
                }
            }
            if (state.currentTool == EditorTool.CUTOUT) {
                MaskPainterOverlay(
                    modifier = Modifier.fillMaxSize(),
                    imageBounds = imageBounds,
                    strokes = state.cutoutSettings.refinement,
                    brushSize = state.cutoutSettings.refineBrushSize,
                    subtract = state.cutoutSettings.refineSubtract,
                    onStrokesChange = { viewModel.previewCutout(state.cutoutSettings.copy(refinement = it)) },
                    addColor = Color(0x9949E69A),
                    subtractColor = Color(0x99FF6076),
                )
            }
            if (state.currentTool == EditorTool.HEAL) {
                MaskPainterOverlay(
                    modifier = Modifier.fillMaxSize(),
                    imageBounds = imageBounds,
                    strokes = state.healingSettings.strokes,
                    brushSize = state.healingSettings.brushSize,
                    subtract = false,
                    onStrokesChange = { viewModel.updateHealingSettings(state.healingSettings.copy(strokes = it)) },
                    addColor = Color(0x99FFB84D),
                )
            }

            when (state.currentTool) {
                EditorTool.TEXT -> TextLayerEditor(imageBounds, viewModel::closeTool, viewModel::applyTexts)
                EditorTool.STICKERS -> StickerLayerEditor(imageBounds, viewModel::closeTool, viewModel::applyStickers)
                EditorTool.DRAW -> DrawingEditor(imageBounds, viewModel::closeTool, viewModel::applyDrawing)
                else -> Unit
            }

            if (state.showOriginal) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        "ORIGINAL PREVIEW",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            if (state.currentTool == EditorTool.NONE && !state.isProcessing && !state.isLoading) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StageControl(
                        icon = Icons.Default.Compare,
                        label = if (state.showOriginal) "Edited" else "Compare",
                        active = state.showOriginal,
                        onClick = { viewModel.setShowOriginal(!state.showOriginal) },
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StageIconControl(Icons.Default.ZoomIn, "Zoom in") {
                        zoom = (zoom + 0.35f).coerceAtMost(5f)
                    }
                    StageIconControl(Icons.Default.ZoomOut, "Zoom out") {
                        zoom = (zoom - 0.35f).coerceAtLeast(1f)
                        if (zoom == 1f) pan = Offset.Zero
                    }
                    StageIconControl(Icons.Default.FitScreen, "Fit image") {
                        zoom = 1f
                        pan = Offset.Zero
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.58f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                ) {
                    Text(
                        "${(zoom * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (state.isProcessing || state.isLoading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shadowElevation = 10.dp) {
                        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                            Text(if (state.isLoading) "Opening image…" else "Rendering edit…", modifier = Modifier.padding(start = 12.dp), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        SmartExportDialog(
            selectedPreset = state.smartExportPreset,
            currentQuality = state.exportQuality,
            onDismiss = { showExportDialog = false },
            onConfirm = { preset, quality ->
                viewModel.setSmartExportPreset(preset)
                viewModel.setExportOptions(
                    if (preset == SmartExportPreset.ORIGINAL) state.exportFormat else preset.format,
                    quality,
                )
                showExportDialog = false
                saveWithPermission()
            },
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Discard unsaved edits?") },
            text = { Text("Your current document has changes that have not been exported.") },
            confirmButton = {
                Button(onClick = { showExitDialog = false; viewModel.closeDocument() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    state: EditorState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCompare: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onSaveProject: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val actionsEnabled = state.currentTool == EditorTool.NONE && !state.isProcessing

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF0A0C12), Color(0xFF121321), Color(0xFF0A0C12)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(68.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(15.dp),
                color = Color.White.copy(alpha = 0.055f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    title,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (state.hasUnsavedChanges) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                Modifier.size(5.dp).background(
                                    if (state.hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    CircleShape,
                                ),
                            )
                            Text(
                                if (state.hasUnsavedChanges) "UNSAVED" else "SAVED",
                                color = if (state.hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                    if (state.documentWidth > 0) {
                        Text(
                            "${state.documentWidth} × ${state.documentHeight}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.045f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
            ) {
                Row {
                    IconButton(onClick = onUndo, enabled = state.canUndo && actionsEnabled, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onRedo, enabled = state.canRedo && actionsEnabled, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(19.dp))
                    }
                }
            }
            Button(
                onClick = onSave,
                enabled = actionsEnabled,
                modifier = Modifier.padding(start = 8.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Export", modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.Black)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (state.showOriginal) "Show edited" else "Compare original") },
                        onClick = { menuExpanded = false; onCompare() },
                        enabled = !state.isProcessing,
                        leadingIcon = { Icon(Icons.Default.Compare, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Save editable project") },
                        onClick = { menuExpanded = false; onSaveProject() },
                        enabled = actionsEnabled,
                        leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Share image") },
                        onClick = { menuExpanded = false; onShare() },
                        enabled = actionsEnabled,
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Reset all edits") },
                        onClick = { menuExpanded = false; onReset() },
                        enabled = actionsEnabled,
                        leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfessionalToolDock(
    selectedCategory: EditorCategory,
    onCategorySelected: (EditorCategory) -> Unit,
    onToolSelected: (EditorTool) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF151722), Color(0xFF0C0E14))),
                RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)),
    ) {
        Column(modifier = Modifier.padding(top = 9.dp, bottom = 10.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "EDITING WORKSPACE",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        selectedCategory.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                ) {
                    Text(
                        "${selectedCategory.tools.size} TOOLS",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                EditorCategory.entries.forEach { category ->
                    val selected = category == selectedCategory
                    Surface(
                        onClick = { onCategorySelected(category) },
                        shape = RoundedCornerShape(15.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.045f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                category.icon,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                category.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 10.dp), color = Color.White.copy(alpha = 0.055f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedCategory.tools.forEach { tool ->
                    Surface(
                        onClick = { onToolSelected(tool.tool) },
                        modifier = Modifier.width(82.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.045f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(45.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                                            ),
                                        ),
                                        RoundedCornerShape(15.dp),
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(15.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    tool.icon,
                                    contentDescription = tool.label,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            Text(
                                tool.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 7.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioStageBackdrop(imageBounds: Rect) {
    Canvas(Modifier.fillMaxSize()) {
        val grid = 28.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = 0.018f), Offset(x, 0f), Offset(x, size.height), 1f)
            x += grid
        }
        var y = 0f
        while (y < size.height) {
            drawLine(Color.White.copy(alpha = 0.018f), Offset(0f, y), Offset(size.width, y), 1f)
            y += grid
        }
        if (imageBounds.width > 0f && imageBounds.height > 0f) {
            drawRect(
                color = Color.Black.copy(alpha = 0.42f),
                topLeft = Offset(imageBounds.left - 8.dp.toPx(), imageBounds.top - 8.dp.toPx()),
                size = Size(imageBounds.width + 16.dp.toPx(), imageBounds.height + 16.dp.toPx()),
            )
            drawRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(imageBounds.left, imageBounds.top),
                size = Size(imageBounds.width, imageBounds.height),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

@Composable
private fun StageControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (active) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.58f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (active) MaterialTheme.colorScheme.onPrimary else Color.White,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.onPrimary else Color.White,
            )
        }
    }
}

@Composable
private fun StageIconControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.58f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shadowElevation = 5.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun CropPanel(
    selection: CropSelection,
    imageAspect: Float,
    onSelectionChange: (CropSelection) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    ToolPanel(title = "Crop", subtitle = "Drag corners or choose an aspect ratio", onCancel = onCancel, onApply = onApply) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "Free" to null,
                "1:1" to 1f,
                "4:3" to (4f / 3f),
                "3:4" to (3f / 4f),
                "16:9" to (16f / 9f),
                "9:16" to (9f / 16f),
            ).forEach { (label, ratio) ->
                FilterChip(
                    selected = ratio != null && kotlin.math.abs(selection.width / selection.height - ratio) < 0.03f,
                    onClick = { if (ratio == null) onSelectionChange(CropSelection()) else onSelectionChange(centeredCrop(ratio, imageAspect)) },
                    label = { Text(label) },
                )
            }
        }
        Text("${(selection.width * 100).toInt()}% × ${(selection.height * 100).toInt()}% of the document", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun FocusGuide(
    imageBounds: Rect,
    state: EditorState,
    onCenterChange: (Float, Float) -> Unit,
) {
    val settings = state.focusSettings
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(imageBounds, settings.blur, settings.mode) {
                fun updateCenter(position: Offset) {
                    if (imageBounds.width <= 0f || imageBounds.height <= 0f) return
                    val centerX = ((position.x - imageBounds.left) / imageBounds.width).coerceIn(0f, 1f)
                    val centerY = ((position.y - imageBounds.top) / imageBounds.height).coerceIn(0f, 1f)
                    onCenterChange(centerX, centerY)
                }
                detectDragGestures(
                    onDragStart = ::updateCenter,
                    onDrag = { change, _ ->
                        change.consume()
                        updateCenter(change.position)
                    },
                )
            },
    ) {
        if (imageBounds.width <= 0f || settings.blur <= 0f) return@Canvas
        val center = Offset(
            imageBounds.left + settings.centerX * imageBounds.width,
            imageBounds.top + settings.centerY * imageBounds.height,
        )
        val guideColor = Color.White.copy(alpha = 0.72f)
        if (settings.mode == FocusMode.RADIAL) {
            drawCircle(
                guideColor,
                radius = min(imageBounds.width, imageBounds.height) * settings.radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
            )
            drawCircle(
                guideColor.copy(alpha = 0.35f),
                radius = min(imageBounds.width, imageBounds.height) * (settings.radius + settings.feather),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
        } else {
            val angle = settings.angle / 180f * Math.PI.toFloat()
            val direction = Offset(cos(angle), sin(angle))
            val normal = Offset(-direction.y, direction.x)
            val half = min(imageBounds.width, imageBounds.height) * settings.radius
            val length = imageBounds.width + imageBounds.height
            listOf(-half, half).forEach { distance ->
                val point = center + normal * distance
                drawLine(
                    guideColor,
                    point - direction * length,
                    point + direction * length,
                    2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(accent.copy(alpha = 0.18f), 12.dp.toPx(), center)
        drawCircle(accent, 5.dp.toPx(), center)
    }
}

@Composable
private fun PerspectiveGuide(imageBounds: Rect) {
    Canvas(Modifier.fillMaxSize()) {
        if (imageBounds.width <= 0f) return@Canvas
        val color = Color.White.copy(alpha = 0.32f)
        repeat(5) { index ->
            val x = imageBounds.left + imageBounds.width * index / 4f
            val y = imageBounds.top + imageBounds.height * index / 4f
            drawLine(color, Offset(x, imageBounds.top), Offset(x, imageBounds.bottom), 1.dp.toPx())
            drawLine(color, Offset(imageBounds.left, y), Offset(imageBounds.right, y), 1.dp.toPx())
        }
    }
}

private enum class EditorCategory(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tools: List<ToolEntry>,
) {
    QUICK(
        "Quick",
        Icons.Default.AutoFixHigh,
        listOf(
            ToolEntry(EditorTool.ADJUST, "Adjust", Icons.Default.Tune),
            ToolEntry(EditorTool.FILTERS, "Filters", Icons.Default.AutoAwesome),
            ToolEntry(EditorTool.CROP, "Crop", Icons.Default.Crop),
            ToolEntry(EditorTool.HEAL, "Remove", Icons.Default.Healing),
            ToolEntry(EditorTool.CUTOUT, "Cutout", Icons.Default.ContentCut),
        ),
    ),
    COLOR(
        "Color",
        Icons.Default.Palette,
        listOf(
            ToolEntry(EditorTool.ADJUST, "Adjust", Icons.Default.Tune),
            ToolEntry(EditorTool.CURVES, "Curves", Icons.Default.ShowChart),
            ToolEntry(EditorTool.HSL, "HSL", Icons.Default.Palette),
            ToolEntry(EditorTool.COLOR_GRADE, "Grade", Icons.Default.ColorLens),
            ToolEntry(EditorTool.FILTERS, "Filters", Icons.Default.AutoAwesome),
        ),
    ),
    RETOUCH(
        "Retouch",
        Icons.Default.Healing,
        listOf(
            ToolEntry(EditorTool.HEAL, "Remove", Icons.Default.Healing),
            ToolEntry(EditorTool.BEAUTY, "Beauty", Icons.Default.Face),
            ToolEntry(EditorTool.MASK, "Mask", Icons.Default.FilterAlt),
            ToolEntry(EditorTool.FOCUS, "Focus", Icons.Default.CenterFocusStrong),
            ToolEntry(EditorTool.CUTOUT, "Cutout", Icons.Default.ContentCut),
        ),
    ),
    CREATE(
        "Create",
        Icons.Default.TextFields,
        listOf(
            ToolEntry(EditorTool.TEXT, "Text", Icons.Default.TextFields),
            ToolEntry(EditorTool.STICKERS, "Stickers", Icons.Default.EmojiEmotions),
            ToolEntry(EditorTool.DRAW, "Draw", Icons.Default.Brush),
            ToolEntry(EditorTool.FRAME, "Frames", Icons.Default.FilterFrames),
            ToolEntry(EditorTool.RECIPES, "Recipes", Icons.Default.Style),
            ToolEntry(EditorTool.PRODUCT, "Product", Icons.Default.Storefront),
        ),
    ),
    GEOMETRY(
        "Geometry",
        Icons.Default.Transform,
        listOf(
            ToolEntry(EditorTool.CROP, "Crop", Icons.Default.Crop),
            ToolEntry(EditorTool.ROTATE, "Rotate", Icons.Default.Rotate90DegreesCcw),
            ToolEntry(EditorTool.RESIZE, "Resize", Icons.Default.AspectRatio),
            ToolEntry(EditorTool.PERSPECTIVE, "Perspective", Icons.Default.Transform),
            ToolEntry(EditorTool.LENS, "Lens", Icons.Default.CameraAlt),
        ),
    ),
    PRO(
        "Pro",
        Icons.Default.Layers,
        listOf(
            ToolEntry(EditorTool.LAYERS, "Layers", Icons.Default.Layers),
            ToolEntry(EditorTool.MASK, "Mask", Icons.Default.FilterAlt),
            ToolEntry(EditorTool.EFFECTS, "Effects", Icons.Default.AutoAwesome),
            ToolEntry(EditorTool.FOCUS, "Focus", Icons.Default.CenterFocusStrong),
            ToolEntry(EditorTool.PRODUCT, "Product", Icons.Default.Storefront),
        ),
    ),
}

private data class ToolEntry(
    val tool: EditorTool,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private fun calculateImageBounds(canvasSize: IntSize, imageWidth: Int, imageHeight: Int): Rect {
    if (canvasSize.width <= 0 || canvasSize.height <= 0 || imageWidth <= 0 || imageHeight <= 0) return Rect.Zero
    val scale = min(canvasSize.width.toFloat() / imageWidth, canvasSize.height.toFloat() / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (canvasSize.width - width) / 2f
    val top = (canvasSize.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun centeredCrop(aspectRatio: Float, imageAspect: Float): CropSelection {
    val normalizedRatio = aspectRatio / imageAspect.coerceAtLeast(0.001f)
    val margin = 0.06f
    return if (normalizedRatio >= 1f) {
        val width = 1f - margin * 2f
        val height = (width / normalizedRatio).coerceAtMost(1f - margin * 2f)
        val top = (1f - height) / 2f
        CropSelection(margin, top, 1f - margin, top + height)
    } else {
        val height = 1f - margin * 2f
        val width = (height * normalizedRatio).coerceAtMost(1f - margin * 2f)
        val left = (1f - width) / 2f
        CropSelection(left, margin, left + width, 1f - margin)
    }
}
