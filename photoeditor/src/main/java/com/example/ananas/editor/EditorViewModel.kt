package com.example.ananas.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EditorViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository = ImageRepository(application)
    private val projectRepository = ProjectRepository(application)
    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var previewBase: Bitmap? = null
    private var previewRenderBase: Bitmap? = null
    private var previewJob: Job? = null
    private var operationJob: Job? = null
    private var batchJob: Job? = null
    private var persistenceJob: Job? = null
    private var projectPersistenceJob: Job? = null
    private var operationRestoreBitmap: Bitmap? = null
    private var previewGeneration = 0L
    private var pendingPreview: PreviewRequest? = null
    private val adjustmentValues = mutableMapOf<AdjustmentType, Float>()
    private val renderMutex = Mutex()
    private var failedBatchUris: List<Uri> = emptyList()

    init {
        restoreSessionIfAvailable()
        refreshProjects()
    }

    fun createCameraUri(): Uri = repository.createCameraUri()

    fun openImage(uri: Uri) {
        cancelOperationAndRestore()
        abandonPreview(restoreBase = true)
        operationJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    currentTool = EditorTool.NONE,
                    isLoading = true,
                    isProcessing = false,
                    message = null,
                )
            }
            runCatching { repository.loadBitmap(uri) }
                .onSuccess { replaceDocument(it, repository.displayName(uri)) }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isProcessing = false,
                                message = EditorMessage(
                                    text = if (error is OutOfMemoryError) {
                                        "This image is too large for the available memory"
                                    } else {
                                        error.message ?: "The image could not be opened"
                                    },
                                ),
                            )
                        }
                    }
                }
        }
    }

    fun openSample() {
        cancelOperationAndRestore()
        abandonPreview(restoreBase = true)
        operationJob = viewModelScope.launch {
            _state.update { it.copy(currentTool = EditorTool.NONE, isLoading = true, message = null) }
            runCatching { withContext(Dispatchers.Default) { SampleImage.create() } }
                .onSuccess { replaceDocument(it, "Sample image") }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                message = EditorMessage(text = processingErrorMessage(error)),
                            )
                        }
                    }
                }
        }
    }

    fun openTool(tool: EditorTool) {
        if (_state.value.isProcessing || _state.value.currentBitmap == null) return
        abandonPreview(restoreBase = true)
        adjustmentValues.clear()

        val request = initialPreviewRequest(tool)
        if (request != null) {
            previewBase = _state.value.currentBitmap
            pendingPreview = request
        }

        _state.update { state ->
            state.copy(
                currentTool = tool,
                selectedAdjustment = AdjustmentType.BRIGHTNESS,
                adjustmentValue = 0f,
                selectedFilter = FilterPreset.ORIGINAL,
                filterIntensity = 100f,
                curveChannel = CurveChannel.MASTER,
                curveBand = CurveBand.MIDTONES,
                curveSettings = ToneCurveSettings(),
                selectedHslRange = HslColorRange.RED,
                hslSettings = SelectiveHslSettings(),
                selectedGradeZone = GradeZone.SHADOWS,
                colorGradeSettings = ColorGradeSettings(),
                effectSettings = EffectSettings(),
                lensSettings = LensSettings(),
                focusSettings = FocusSettings(),
                perspectiveSettings = PerspectiveSettings(),
                beautySmooth = 0f,
                beautyWhiten = 0f,
                maskSettings = SelectiveMaskSettings(),
                cutoutSettings = CutoutSettings(),
                healingSettings = HealingSettings(),
                recipeSettings = RecipeSettings(),
                productStudioSettings = ProductStudioSettings(),
            )
        }
        when (tool) {
            EditorTool.CUTOUT -> previewCutout(CutoutSettings())
            EditorTool.RECIPES -> previewRecipe(RecipeSettings())
            EditorTool.PRODUCT -> previewProductStudio(ProductStudioSettings())
            else -> Unit
        }
    }

    fun closeTool() {
        cancelOperationAndRestore()
        abandonPreview(restoreBase = true)
        adjustmentValues.clear()
        _state.update { it.copy(currentTool = EditorTool.NONE, isProcessing = false) }
    }

    fun cancelPreview() = closeTool()

    fun selectAdjustment(type: AdjustmentType) {
        if (_state.value.currentTool != EditorTool.ADJUST || operationJob?.isActive == true) return
        _state.update {
            it.copy(
                selectedAdjustment = type,
                adjustmentValue = adjustmentValues[type] ?: type.default,
            )
        }
    }

    fun previewAdjustment(type: AdjustmentType, value: Float) {
        val clamped = value.coerceIn(type.min, type.max)
        adjustmentValues[type] = clamped
        val snapshot = adjustmentValues.toMap()
        schedulePreview(
            request = PreviewRequest.Adjustments(snapshot),
            delayMs = if (type in EXPENSIVE_ADJUSTMENTS) 70L else 38L,
        ) {
            it.copy(selectedAdjustment = type, adjustmentValue = clamped)
        }
    }

    fun resetAdjustments() {
        adjustmentValues.clear()
        schedulePreview(PreviewRequest.Adjustments(emptyMap()), 0L) {
            it.copy(adjustmentValue = it.selectedAdjustment.default)
        }
    }

    fun previewFilter(filter: FilterPreset) {
        val intensity = _state.value.filterIntensity
        schedulePreview(PreviewRequest.Filter(filter, intensity), 18L) {
            it.copy(selectedFilter = filter)
        }
    }

    fun previewFilterIntensity(intensity: Float) {
        val value = intensity.coerceIn(0f, 100f)
        val filter = _state.value.selectedFilter
        schedulePreview(PreviewRequest.Filter(filter, value), 35L) {
            it.copy(filterIntensity = value)
        }
    }

    fun selectCurveChannel(channel: CurveChannel) {
        _state.update { it.copy(curveChannel = channel) }
    }

    fun selectCurveBand(band: CurveBand) {
        _state.update { it.copy(curveBand = band) }
    }

    fun previewCurve(settings: ToneCurveSettings) {
        schedulePreview(PreviewRequest.Curves(settings), 45L) { it.copy(curveSettings = settings) }
    }

    fun resetCurveChannel() {
        val state = _state.value
        previewCurve(state.curveSettings.reset(state.curveChannel))
    }

    fun selectHslRange(range: HslColorRange) {
        _state.update { it.copy(selectedHslRange = range) }
    }

    fun previewHsl(settings: SelectiveHslSettings) {
        schedulePreview(PreviewRequest.Hsl(settings), 50L) { it.copy(hslSettings = settings) }
    }

    fun resetHslRange() {
        val state = _state.value
        previewHsl(state.hslSettings.reset(state.selectedHslRange))
    }

    fun selectGradeZone(zone: GradeZone) {
        _state.update { it.copy(selectedGradeZone = zone) }
    }

    fun previewColorGrade(settings: ColorGradeSettings) {
        schedulePreview(PreviewRequest.ColorGrade(settings), 50L) {
            it.copy(colorGradeSettings = settings)
        }
    }

    fun previewEffect(settings: EffectSettings) {
        schedulePreview(PreviewRequest.Effect(settings), 60L) { it.copy(effectSettings = settings) }
    }

    fun previewLens(settings: LensSettings) {
        schedulePreview(PreviewRequest.Lens(settings), 55L) { it.copy(lensSettings = settings) }
    }

    fun previewFocus(settings: FocusSettings) {
        schedulePreview(PreviewRequest.Focus(settings), 48L) { it.copy(focusSettings = settings) }
    }

    fun previewPerspective(settings: PerspectiveSettings) {
        schedulePreview(PreviewRequest.Perspective(settings), 42L) {
            it.copy(perspectiveSettings = settings)
        }
    }

    fun previewStraighten(degrees: Float) {
        val value = degrees.coerceIn(-15f, 15f)
        schedulePreview(PreviewRequest.Straighten(value), 32L) { it.copy(adjustmentValue = value) }
    }

    fun previewBeauty(smooth: Float, whiten: Float) {
        val smoothValue = smooth.coerceIn(0f, 100f)
        val whitenValue = whiten.coerceIn(0f, 100f)
        schedulePreview(PreviewRequest.Beauty(smoothValue, whitenValue), 58L) {
            it.copy(beautySmooth = smoothValue, beautyWhiten = whitenValue)
        }
    }

    fun previewMask(settings: SelectiveMaskSettings) {
        schedulePreview(PreviewRequest.Masked(settings), 72L) { it.copy(maskSettings = settings) }
    }

    fun previewCutout(settings: CutoutSettings) {
        schedulePreview(PreviewRequest.Cutout(settings), 115L) { it.copy(cutoutSettings = settings) }
    }

    fun previewRecipe(settings: RecipeSettings) {
        schedulePreview(PreviewRequest.Recipe(settings), 62L) { it.copy(recipeSettings = settings) }
    }

    fun previewProductStudio(settings: ProductStudioSettings) {
        schedulePreview(PreviewRequest.Product(settings), 130L) { it.copy(productStudioSettings = settings) }
    }

    fun updateHealingSettings(settings: HealingSettings) {
        _state.update { it.copy(healingSettings = settings) }
    }

    fun applyHealing(settings: HealingSettings) {
        if (settings.strokes.isEmpty()) {
            notifyUser("Paint over an object first")
            return
        }
        performEdit("Object removal", LayerType.HEAL) { StandoutImageProcessor.applyHealing(it, settings) }
    }

    fun commitPreview() {
        if (operationJob?.isActive == true) return
        previewJob?.cancel()
        previewGeneration++
        clearPreviewRenderBase()
        val base = previewBase ?: run {
            pendingPreview = null
            _state.update { it.copy(currentTool = EditorTool.NONE, isProcessing = false) }
            return
        }
        val request = pendingPreview
        if (request == null || request.isNoOp()) {
            previewBase = null
            pendingPreview = null
            adjustmentValues.clear()
            _state.update {
                it.copy(currentBitmap = base, currentTool = EditorTool.NONE, isProcessing = false)
            }
            return
        }

        operationRestoreBitmap = base
        _state.update { it.copy(isProcessing = true) }
        operationJob = viewModelScope.launch {
            runCatching { renderMutex.withLock { renderPreview(base, request) } }
                .onSuccess { result ->
                    pushUndo(base)
                    operationRestoreBitmap = null
                    previewBase = null
                    pendingPreview = null
                    adjustmentValues.clear()
                    _state.update { state ->
                        val layer = EditorLayer(
                            name = layerNameFor(request),
                            type = layerTypeFor(request),
                            bitmap = result,
                        )
                        val normalizedLayer = if (result.width != base.width || result.height != base.height) {
                            layer.copy(type = LayerType.BASE, locked = true)
                        } else {
                            layer
                        }
                        val nextLayers = if (normalizedLayer.type == LayerType.BASE) {
                            listOf(normalizedLayer)
                        } else {
                            appendLayerWithBudget(state.layers, normalizedLayer, base)
                        }
                        state.copy(
                            currentBitmap = result,
                            documentWidth = result.width,
                            documentHeight = result.height,
                            currentTool = EditorTool.NONE,
                            isProcessing = false,
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                            hasUnsavedChanges = true,
                            layers = nextLayers,
                            selectedLayerId = normalizedLayer.id,
                        )
                    }
                    persistCurrentSession()
                    persistCurrentProject(createIfMissing = true)
                }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        restoreFailedOperation()
                        showProcessingError(error)
                    }
                }
        }
    }

    fun rotateBy(degrees: Float) = performEdit("Rotate", LayerType.RASTER, flattenLayers = true) { ImageProcessor.rotate(it, degrees) }

    fun flipHorizontal() = performEdit("Flip horizontal", LayerType.RASTER, flattenLayers = true) { ImageProcessor.flip(it, horizontal = true) }

    fun flipVertical() = performEdit("Flip vertical", LayerType.RASTER, flattenLayers = true) { ImageProcessor.flip(it, horizontal = false) }

    fun crop(selection: CropSelection) = performEdit("Crop", LayerType.RASTER, flattenLayers = true) { ImageProcessor.crop(it, selection) }

    fun resize(width: Int, height: Int) {
        val pixels = width.toLong() * height.toLong()
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || pixels > MAX_RESIZE_PIXELS) {
            notifyUser("Use 1–8192 px per side and no more than 12 megapixels")
            return
        }
        performEdit("Resize", LayerType.RASTER, flattenLayers = true) { ImageProcessor.resize(it, width, height) }
    }

    fun applyTexts(elements: List<TextElement>) {
        if (elements.isEmpty()) closeTool() else addOverlayLayer("Text", LayerType.TEXT) { width, height ->
            StandoutImageProcessor.createTextLayer(width, height, elements)
        }
    }

    fun applyStickers(elements: List<StickerElement>) {
        if (elements.isEmpty()) closeTool() else addOverlayLayer("Stickers", LayerType.STICKER) { width, height ->
            StandoutImageProcessor.createStickerLayer(width, height, elements)
        }
    }

    fun applyDrawing(strokes: List<DrawStroke>) {
        if (strokes.isEmpty()) closeTool() else addOverlayLayer("Drawing", LayerType.DRAWING) { width, height ->
            StandoutImageProcessor.createDrawingLayer(width, height, strokes)
        }
    }

    fun applyFrame(style: Int) {
        if (style == 0) closeTool() else performEdit("Frame", LayerType.RASTER) { ImageProcessor.applyFrame(it, style) }
    }

    fun undo() {
        if (_state.value.isProcessing || _state.value.currentTool != EditorTool.NONE || undoStack.isEmpty()) return
        val current = _state.value.currentBitmap ?: return
        redoStack.addLast(current)
        trimHistory(redoStack, current)
        val previous = undoStack.removeLast()
        val restoredLayer = EditorLayer(name = "Undo state", type = LayerType.BASE, bitmap = previous, locked = true)
        _state.update {
            it.copy(
                currentBitmap = previous,
                documentWidth = previous.width,
                documentHeight = previous.height,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                hasUnsavedChanges = true,
                layers = listOf(restoredLayer),
                selectedLayerId = restoredLayer.id,
            )
        }
        persistCurrentSession()
        persistCurrentProject(createIfMissing = true)
    }

    fun redo() {
        if (_state.value.isProcessing || _state.value.currentTool != EditorTool.NONE || redoStack.isEmpty()) return
        val current = _state.value.currentBitmap ?: return
        undoStack.addLast(current)
        trimHistory(undoStack, current)
        val next = redoStack.removeLast()
        val restoredLayer = EditorLayer(name = "Redo state", type = LayerType.BASE, bitmap = next, locked = true)
        _state.update {
            it.copy(
                currentBitmap = next,
                documentWidth = next.width,
                documentHeight = next.height,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                hasUnsavedChanges = true,
                layers = listOf(restoredLayer),
                selectedLayerId = restoredLayer.id,
            )
        }
        persistCurrentSession()
        persistCurrentProject(createIfMissing = true)
    }

    fun resetAll() {
        if (_state.value.currentTool != EditorTool.NONE || _state.value.isProcessing) return
        val original = _state.value.originalBitmap ?: return
        performEdit("Reset", LayerType.BASE, flattenLayers = true) { ImageProcessor.copy(original) }
    }

    fun selectLayer(layerId: Long) {
        _state.update { it.copy(selectedLayerId = layerId) }
    }

    fun toggleLayerVisibility(layerId: Long) = mutateLayers { layers ->
        layers.map { if (it.id == layerId) it.copy(visible = !it.visible) else it }
    }

    fun toggleLayerLock(layerId: Long) {
        _state.update { state ->
            state.copy(layers = state.layers.map { if (it.id == layerId && it.type != LayerType.BASE) it.copy(locked = !it.locked) else it })
        }
        persistCurrentProject(createIfMissing = true)
    }

    fun setLayerOpacity(layerId: Long, opacity: Float) = mutateLayers { layers ->
        layers.map { if (it.id == layerId && !it.locked) it.copy(opacity = opacity.coerceIn(0f, 1f)) else it }
    }

    fun setLayerBlendMode(layerId: Long, mode: BlendModeOption) = mutateLayers { layers ->
        layers.map { if (it.id == layerId && !it.locked) it.copy(blendMode = mode) else it }
    }

    fun moveLayer(layerId: Long, direction: Int) = mutateLayers { layers ->
        val index = layers.indexOfFirst { it.id == layerId }
        if (index < 0) return@mutateLayers layers
        val target = (index + direction).coerceIn(0, layers.lastIndex)
        if (target == index || layers[index].type == LayerType.BASE) return@mutateLayers layers
        layers.toMutableList().apply { add(target, removeAt(index)) }
    }

    fun duplicateLayer(layerId: Long) {
        val state = _state.value
        val source = state.layers.firstOrNull { it.id == layerId } ?: return
        if (source.locked && source.type == LayerType.BASE) {
            notifyUser("The base layer is protected")
            return
        }
        viewModelScope.launch {
            val duplicate = source.copy(
                id = System.nanoTime(),
                name = "${source.name} copy",
                bitmap = source.bitmap.copyArgb(),
                locked = false,
            )
            mutateLayersNow(state.layers + duplicate, duplicate.id)
        }
    }

    fun deleteLayer(layerId: Long) {
        val state = _state.value
        val layer = state.layers.firstOrNull { it.id == layerId } ?: return
        if (layer.type == LayerType.BASE || layer.locked) {
            notifyUser("Unlock the layer before deleting it")
            return
        }
        mutateLayers { layers -> layers.filterNot { it.id == layerId } }
    }

    fun flattenLayers() {
        val current = _state.value.currentBitmap ?: return
        val layer = EditorLayer(name = "Flattened image", type = LayerType.BASE, bitmap = current, locked = true)
        _state.update { it.copy(layers = listOf(layer), selectedLayerId = layer.id, hasUnsavedChanges = true) }
        persistCurrentProject(createIfMissing = true)
        notifyUser("Layers flattened")
    }

    fun renameProject(name: String) {
        val clean = name.trim().take(60).ifBlank { "Untitled project" }
        _state.update { it.copy(activeProjectName = clean) }
        persistCurrentProject(createIfMissing = true, immediate = true)
    }

    fun saveProjectNow() {
        persistCurrentProject(createIfMissing = true, immediate = true)
        notifyUser("Project saved")
    }

    fun openProject(projectId: String) {
        if (_state.value.isProcessing) return
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, message = null) }
            runCatching { projectRepository.loadProject(projectId) }
                .onSuccess { document ->
                    val composite = StandoutImageProcessor.compositeLayers(
                        document.summary.width,
                        document.summary.height,
                        document.layers,
                    )
                    clearHistory()
                    _state.value = EditorState(
                        currentBitmap = composite,
                        originalBitmap = document.originalBitmap,
                        documentWidth = composite.width,
                        documentHeight = composite.height,
                        layers = document.layers,
                        selectedLayerId = document.layers.lastOrNull()?.id,
                        sourceName = document.summary.name,
                        activeProjectId = document.summary.id,
                        activeProjectName = document.summary.name,
                        projects = _state.value.projects,
                        isLoading = false,
                    )
                    persistCurrentSession(includeOriginal = true)
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, message = EditorMessage(text = error.message ?: "Unable to open project")) }
                }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            runCatching { projectRepository.deleteProject(projectId) }
            refreshProjects()
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            runCatching { projectRepository.duplicateProject(projectId) }
                .onSuccess { notifyUser("Project duplicated") }
                .onFailure { notifyUser(it.message ?: "Unable to duplicate project") }
            refreshProjects()
        }
    }

    fun setSmartExportPreset(preset: SmartExportPreset) {
        _state.update { it.copy(
            smartExportPreset = preset,
            exportFormat = if (preset == SmartExportPreset.ORIGINAL) it.exportFormat else preset.format,
            exportQuality = if (preset == SmartExportPreset.ORIGINAL) it.exportQuality else preset.quality,
        ) }
    }

    fun runBatch(uris: List<Uri>, preset: RecipePreset) {
        if (uris.isEmpty() || batchJob?.isActive == true) return
        batchJob = viewModelScope.launch {
            failedBatchUris = emptyList()
            _state.update {
                it.copy(
                    batchState = BatchState(
                        isRunning = true,
                        total = uris.size,
                        preset = preset,
                    ),
                    message = null,
                )
            }

            var exported = 0
            var completed = 0
            val failures = mutableListOf<BatchFailure>()
            val failedUris = mutableListOf<Uri>()

            try {
                uris.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    val name = repository.displayName(uri)?.takeIf(String::isNotBlank)
                        ?: "Photo ${index + 1}"
                    _state.update {
                        it.copy(batchState = it.batchState.copy(currentName = name))
                    }

                    var source: Bitmap? = null
                    var edited: Bitmap? = null
                    try {
                        source = repository.loadBatchBitmap(uri)
                        edited = StandoutImageProcessor.applyRecipe(
                            source,
                            RecipeSettings(preset = preset, intensity = 100f),
                        )
                        repository.saveToGallery(
                            bitmap = edited,
                            format = ExportFormat.JPEG,
                            quality = 92,
                            namePrefix = "Ananas_Batch_${preset.name}_${index + 1}",
                        )
                        exported++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        failures += BatchFailure(
                            uriString = uri.toString(),
                            name = name,
                            reason = batchErrorMessage(error),
                        )
                        failedUris += uri
                    } finally {
                        edited?.let { if (it !== source && !it.isRecycled) it.recycle() }
                        source?.let { if (!it.isRecycled) it.recycle() }
                    }

                    completed = index + 1
                    _state.update {
                        it.copy(
                            batchState = it.batchState.copy(
                                completed = completed,
                                exported = exported,
                                failed = failures.size,
                                failures = failures.toList(),
                                currentName = null,
                            ),
                        )
                    }
                    if (failures.lastOrNull()?.reason?.contains("memory", ignoreCase = true) == true) {
                        System.gc()
                    }
                }

                failedBatchUris = failedUris.toList()
                _state.update {
                    it.copy(
                        batchState = it.batchState.copy(
                            isRunning = false,
                            currentName = null,
                            failures = failures.toList(),
                        ),
                        message = EditorMessage(
                            text = when {
                                exported == uris.size -> "Batch complete — $exported photos saved to Pictures/Ananas"
                                exported > 0 -> "Batch complete — $exported saved, ${failures.size} skipped"
                                else -> "Batch could not save any photos. Open the result details for the exact cause."
                            },
                        ),
                    )
                }
            } catch (_: CancellationException) {
                failedBatchUris = failedUris.toList()
                _state.update {
                    it.copy(
                        batchState = it.batchState.copy(
                            isRunning = false,
                            completed = completed,
                            exported = exported,
                            failed = failures.size,
                            currentName = null,
                            failures = failures.toList(),
                            wasCancelled = true,
                        ),
                        message = EditorMessage(text = "Batch stopped after $completed of ${uris.size} photos"),
                    )
                }
            } finally {
                batchJob = null
            }
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
    }

    fun retryFailedBatch() {
        val retry = failedBatchUris
        if (retry.isNotEmpty()) runBatch(retry, _state.value.batchState.preset)
    }

    fun clearBatchResult() {
        if (batchJob?.isActive == true) return
        failedBatchUris = emptyList()
        _state.update { it.copy(batchState = BatchState()) }
    }

    private fun batchErrorMessage(error: Throwable): String = when (error) {
        is OutOfMemoryError -> "Not enough memory for this image"
        is SecurityException -> "Storage access was denied"
        is java.io.FileNotFoundException -> "The source is no longer available"
        is java.io.IOException -> error.message ?: "The image could not be read or saved"
        else -> error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName
    }

    fun setShowOriginal(show: Boolean) {
        _state.update { it.copy(showOriginal = show) }
    }

    fun setExportOptions(format: ExportFormat, quality: Int) {
        _state.update { it.copy(exportFormat = format, exportQuality = quality.coerceIn(1, 100)) }
    }

    fun exportToGallery() {
        val bitmap = _state.value.currentBitmap ?: return
        if (_state.value.isProcessing || _state.value.currentTool != EditorTool.NONE) return
        operationJob?.cancel()
        _state.update { it.copy(isProcessing = true) }
        operationJob = viewModelScope.launch {
            val snapshot = _state.value
            val preset = snapshot.smartExportPreset
            val format = if (preset == SmartExportPreset.ORIGINAL) snapshot.exportFormat else preset.format
            val quality = if (preset == SmartExportPreset.ORIGINAL) snapshot.exportQuality else preset.quality
            var prepared: Bitmap? = null
            runCatching {
                val output = if (preset == SmartExportPreset.ORIGINAL || preset == SmartExportPreset.TRANSPARENT_PNG) bitmap else {
                    StandoutImageProcessor.prepareSmartExport(bitmap, preset).also { prepared = it }
                }
                repository.saveToGallery(output, format, quality)
            }
                .onSuccess { uri ->
                    prepared?.let { if (it !== bitmap && !it.isRecycled) it.recycle() }
                    persistenceJob?.cancel()
                    savedStateHandle[HAS_UNSAVED_CHANGES] = false
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            lastExportUri = uri,
                            hasUnsavedChanges = false,
                            message = EditorMessage(text = "Saved to Pictures/Ananas"),
                        )
                    }
                }
                .onFailure { error ->
                    prepared?.let { if (it !== bitmap && !it.isRecycled) it.recycle() }
                    if (error !is CancellationException) {
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                message = EditorMessage(text = error.message ?: "Unable to save the image"),
                            )
                        }
                    }
                }
        }
    }

    fun prepareShare() {
        val bitmap = _state.value.currentBitmap ?: return
        if (_state.value.isProcessing || _state.value.currentTool != EditorTool.NONE) return
        operationJob?.cancel()
        _state.update { it.copy(isProcessing = true) }
        operationJob = viewModelScope.launch {
            val snapshot = _state.value
            val preset = snapshot.smartExportPreset
            val format = if (preset == SmartExportPreset.ORIGINAL) snapshot.exportFormat else preset.format
            val quality = if (preset == SmartExportPreset.ORIGINAL) snapshot.exportQuality else preset.quality
            var prepared: Bitmap? = null
            runCatching {
                val output = if (preset == SmartExportPreset.ORIGINAL || preset == SmartExportPreset.TRANSPARENT_PNG) bitmap else {
                    StandoutImageProcessor.prepareSmartExport(bitmap, preset).also { prepared = it }
                }
                repository.createShareUri(output, format, quality)
            }
                .onSuccess { uri ->
                    prepared?.let { if (it !== bitmap && !it.isRecycled) it.recycle() }
                    _state.update { it.copy(isProcessing = false, pendingShareUri = uri, exportFormat = format) }
                }
                .onFailure { error ->
                    prepared?.let { if (it !== bitmap && !it.isRecycled) it.recycle() }
                    if (error !is CancellationException) {
                        _state.update {
                            it.copy(
                                isProcessing = false,
                                message = EditorMessage(text = error.message ?: "Unable to share the image"),
                            )
                        }
                    }
                }
        }
    }

    fun consumeShareUri() {
        _state.update { it.copy(pendingShareUri = null) }
    }

    fun notifyUser(text: String) {
        _state.update { it.copy(message = EditorMessage(text = text)) }
    }

    fun consumeMessage(id: Long) {
        _state.update { state -> if (state.message?.id == id) state.copy(message = null) else state }
    }

    fun closeDocument() {
        previewJob?.cancel()
        operationJob?.cancel()
        persistenceJob?.cancel()
        projectPersistenceJob?.cancel()
        operationRestoreBitmap = null
        clearPreviewRenderBase()
        previewBase = null
        pendingPreview = null
        adjustmentValues.clear()
        clearHistory()
        repository.clearSession()
        savedStateHandle.remove<String>(SESSION_PATH)
        savedStateHandle.remove<String>(SOURCE_NAME)
        savedStateHandle.remove<Boolean>(HAS_UNSAVED_CHANGES)
        _state.value = EditorState()
        refreshProjects()
    }

    private fun initialPreviewRequest(tool: EditorTool): PreviewRequest? = when (tool) {
        EditorTool.FILTERS -> PreviewRequest.Filter(FilterPreset.ORIGINAL, 100f)
        EditorTool.ADJUST -> PreviewRequest.Adjustments(emptyMap())
        EditorTool.CURVES -> PreviewRequest.Curves(ToneCurveSettings())
        EditorTool.HSL -> PreviewRequest.Hsl(SelectiveHslSettings())
        EditorTool.COLOR_GRADE -> PreviewRequest.ColorGrade(ColorGradeSettings())
        EditorTool.EFFECTS -> PreviewRequest.Effect(EffectSettings())
        EditorTool.LENS -> PreviewRequest.Lens(LensSettings())
        EditorTool.FOCUS -> PreviewRequest.Focus(FocusSettings())
        EditorTool.PERSPECTIVE -> PreviewRequest.Perspective(PerspectiveSettings())
        EditorTool.ROTATE -> PreviewRequest.Straighten(0f)
        EditorTool.BEAUTY -> PreviewRequest.Beauty(0f, 0f)
        EditorTool.MASK -> PreviewRequest.Masked(SelectiveMaskSettings())
        EditorTool.CUTOUT -> PreviewRequest.Cutout(CutoutSettings())
        EditorTool.RECIPES -> PreviewRequest.Recipe(RecipeSettings())
        EditorTool.PRODUCT -> PreviewRequest.Product(ProductStudioSettings())
        else -> null
    }

    private fun schedulePreview(
        request: PreviewRequest,
        delayMs: Long,
        stateUpdate: (EditorState) -> EditorState,
    ) {
        if (operationJob?.isActive == true) return
        val base = previewBase ?: _state.value.currentBitmap ?: return
        previewBase = base
        pendingPreview = request
        _state.update(stateUpdate)
        val generation = ++previewGeneration
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            if (generation != previewGeneration) return@launch
            _state.update { it.copy(isProcessing = true) }
            runCatching {
                renderMutex.withLock {
                    val renderBase = obtainPreviewRenderBase(base)
                    renderPreview(renderBase, request)
                }
            }
                .onSuccess { result ->
                    if (generation == previewGeneration) {
                        _state.update { it.copy(currentBitmap = result, isProcessing = false) }
                    } else if (!result.isRecycled) {
                        result.recycle()
                    }
                }
                .onFailure { error ->
                    if (error !is CancellationException && generation == previewGeneration) {
                        _state.update {
                            it.copy(
                                currentBitmap = base,
                                isProcessing = false,
                                message = EditorMessage(text = processingErrorMessage(error)),
                            )
                        }
                    }
                }
        }
    }

    private fun performEdit(
        layerName: String = "Raster edit",
        layerType: LayerType = LayerType.ADJUSTMENT,
        flattenLayers: Boolean = false,
        markDirty: Boolean = true,
        block: suspend (Bitmap) -> Bitmap,
    ) {
        val displayed = _state.value.currentBitmap ?: return
        val historyBase = previewBase ?: displayed
        val previewRequest = pendingPreview
        if (_state.value.isProcessing) return
        previewJob?.cancel()
        previewGeneration++
        clearPreviewRenderBase()
        previewBase = null
        pendingPreview = null
        adjustmentValues.clear()
        operationJob?.cancel()
        operationRestoreBitmap = historyBase
        _state.update { it.copy(isProcessing = true) }
        operationJob = viewModelScope.launch {
            var prepared: Bitmap? = null
            runCatching {
                renderMutex.withLock {
                    val source = if (previewRequest != null && !previewRequest.isNoOp()) {
                        renderPreview(historyBase, previewRequest).also { prepared = it }
                    } else {
                        historyBase
                    }
                    block(source)
                }
            }
                .onSuccess { result ->
                    prepared?.let { intermediate ->
                        if (intermediate !== historyBase && intermediate !== result && !intermediate.isRecycled) {
                            intermediate.recycle()
                        }
                    }
                    pushUndo(historyBase)
                    operationRestoreBitmap = null
                    _state.update { state ->
                        val layer = EditorLayer(
                            name = layerName,
                            type = if (flattenLayers) LayerType.BASE else layerType,
                            bitmap = result,
                            locked = flattenLayers,
                        )
                        val nextLayers = if (flattenLayers || result.width != historyBase.width || result.height != historyBase.height) {
                            listOf(layer)
                        } else {
                            appendLayerWithBudget(state.layers, layer, historyBase)
                        }
                        state.copy(
                            currentBitmap = result,
                            documentWidth = result.width,
                            documentHeight = result.height,
                            currentTool = EditorTool.NONE,
                            isProcessing = false,
                            canUndo = undoStack.isNotEmpty(),
                            canRedo = redoStack.isNotEmpty(),
                            hasUnsavedChanges = markDirty,
                            layers = nextLayers,
                            selectedLayerId = layer.id,
                        )
                    }
                    persistCurrentSession()
                    persistCurrentProject(createIfMissing = true)
                }
                .onFailure { error ->
                    prepared?.let { intermediate ->
                        if (intermediate !== historyBase && !intermediate.isRecycled) intermediate.recycle()
                    }
                    if (error !is CancellationException) {
                        restoreFailedOperation()
                        showProcessingError(error)
                    }
                }
        }
    }

    private fun addOverlayLayer(
        name: String,
        type: LayerType,
        factory: suspend (Int, Int) -> Bitmap,
    ) {
        val state = _state.value
        val current = state.currentBitmap ?: return
        if (state.isProcessing) return
        operationJob?.cancel()
        operationRestoreBitmap = current
        _state.update { it.copy(isProcessing = true) }
        operationJob = viewModelScope.launch {
            runCatching {
                renderMutex.withLock {
                    val bitmap = factory(state.documentWidth, state.documentHeight)
                    val layer = EditorLayer(name = name, type = type, bitmap = bitmap)
                    val layers = appendLayerWithBudget(state.layers, layer, current)
                    val composite = StandoutImageProcessor.compositeLayers(state.documentWidth, state.documentHeight, layers)
                    Triple(layer, layers, composite)
                }
            }.onSuccess { (layer, layers, composite) ->
                pushUndo(current)
                operationRestoreBitmap = null
                _state.update { it.copy(
                    currentBitmap = composite,
                    currentTool = EditorTool.NONE,
                    isProcessing = false,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    hasUnsavedChanges = true,
                    layers = layers,
                    selectedLayerId = layer.id,
                ) }
                persistCurrentSession()
                persistCurrentProject(createIfMissing = true)
            }.onFailure { error ->
                if (error !is CancellationException) {
                    restoreFailedOperation()
                    showProcessingError(error)
                }
            }
        }
    }

    private fun mutateLayers(transform: (List<EditorLayer>) -> List<EditorLayer>) {
        val state = _state.value
        if (state.isProcessing || state.currentTool != EditorTool.LAYERS) return
        val next = transform(state.layers)
        if (next == state.layers || next.isEmpty()) return
        mutateLayersNow(next, state.selectedLayerId?.takeIf { id -> next.any { it.id == id } } ?: next.last().id)
    }

    private fun mutateLayersNow(layers: List<EditorLayer>, selectedId: Long?) {
        val state = _state.value
        val current = state.currentBitmap ?: return
        operationJob?.cancel()
        operationRestoreBitmap = current
        _state.update { it.copy(isProcessing = true) }
        operationJob = viewModelScope.launch {
            runCatching { StandoutImageProcessor.compositeLayers(state.documentWidth, state.documentHeight, layers) }
                .onSuccess { composite ->
                    pushUndo(current)
                    operationRestoreBitmap = null
                    _state.update { it.copy(
                        currentBitmap = composite,
                        isProcessing = false,
                        layers = layers,
                        selectedLayerId = selectedId,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        hasUnsavedChanges = true,
                    ) }
                    persistCurrentSession()
                    persistCurrentProject(createIfMissing = true)
                }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        restoreFailedOperation()
                        showProcessingError(error)
                    }
                }
        }
    }

    private fun appendLayerWithBudget(
        existing: List<EditorLayer>,
        layer: EditorLayer,
        flattenedBase: Bitmap,
    ): List<EditorLayer> {
        if (existing.size < MAX_LAYERS) return existing + layer
        val base = EditorLayer(
            name = "Flattened history",
            type = LayerType.BASE,
            bitmap = flattenedBase,
            locked = true,
        )
        return listOf(base, layer)
    }

    private fun layerNameFor(request: PreviewRequest): String = when (request) {
        is PreviewRequest.Adjustments -> "Adjustments"
        is PreviewRequest.Filter -> "Filter: ${request.preset.label}"
        is PreviewRequest.Curves -> "Tone curves"
        is PreviewRequest.Hsl -> "Selective HSL"
        is PreviewRequest.ColorGrade -> "Color grade"
        is PreviewRequest.Effect -> request.settings.effect.label
        is PreviewRequest.Lens -> "Lens correction"
        is PreviewRequest.Focus -> "Selective focus"
        is PreviewRequest.Perspective -> "Perspective"
        is PreviewRequest.Straighten -> "Straighten"
        is PreviewRequest.Beauty -> "Portrait retouch"
        is PreviewRequest.Masked -> "Masked ${request.settings.adjustment.label}"
        is PreviewRequest.Cutout -> "Subject cutout"
        is PreviewRequest.Recipe -> "Recipe: ${request.settings.preset.label}"
        is PreviewRequest.Product -> "Product studio"
    }

    private fun layerTypeFor(request: PreviewRequest): LayerType = when (request) {
        is PreviewRequest.Cutout -> LayerType.CUTOUT
        is PreviewRequest.Product -> LayerType.CUTOUT
        is PreviewRequest.Masked,
        is PreviewRequest.Adjustments,
        is PreviewRequest.Filter,
        is PreviewRequest.Curves,
        is PreviewRequest.Hsl,
        is PreviewRequest.ColorGrade,
        is PreviewRequest.Effect,
        is PreviewRequest.Lens,
        is PreviewRequest.Focus,
        is PreviewRequest.Perspective,
        is PreviewRequest.Straighten,
        is PreviewRequest.Beauty,
        is PreviewRequest.Recipe -> LayerType.ADJUSTMENT
    }

    private fun refreshProjects() {
        viewModelScope.launch {
            val projects = runCatching { projectRepository.listProjects() }.getOrDefault(emptyList())
            _state.update { it.copy(projects = projects) }
        }
    }

    private fun persistCurrentProject(createIfMissing: Boolean, immediate: Boolean = false) {
        val state = _state.value
        val original = state.originalBitmap ?: return
        val layers = state.layers.ifEmpty {
            state.currentBitmap?.let { listOf(EditorLayer(name = "Base image", type = LayerType.BASE, bitmap = it, locked = true)) } ?: return
        }
        if (!createIfMissing && state.activeProjectId == null) return
        projectPersistenceJob?.cancel()
        projectPersistenceJob = viewModelScope.launch {
            if (!immediate) delay(900)
            runCatching {
                projectRepository.saveProject(
                    projectId = state.activeProjectId,
                    name = state.activeProjectName,
                    original = original,
                    documentWidth = state.documentWidth,
                    documentHeight = state.documentHeight,
                    layers = layers,
                )
            }.onSuccess { summary ->
                _state.update { current ->
                    current.copy(
                        activeProjectId = summary.id,
                        activeProjectName = summary.name,
                    )
                }
                refreshProjects()
            }.onFailure { error ->
                if (immediate && error !is CancellationException) notifyUser(error.message ?: "Unable to save project")
            }
        }
    }

    private fun replaceDocument(bitmap: Bitmap, sourceName: String?) {
        previewJob?.cancel()
        operationRestoreBitmap = null
        persistenceJob?.cancel()
        clearPreviewRenderBase()
        previewBase = null
        pendingPreview = null
        adjustmentValues.clear()
        clearHistory()
        repository.clearSession()
        savedStateHandle.remove<String>(SESSION_PATH)
        savedStateHandle.remove<String>(SOURCE_NAME)
        savedStateHandle.remove<Boolean>(HAS_UNSAVED_CHANGES)
        val current = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copyArgb().also { if (it !== bitmap && !bitmap.isRecycled) bitmap.recycle() }
        }
        val baseLayer = EditorLayer(
            name = "Base image",
            type = LayerType.BASE,
            bitmap = current,
            locked = true,
        )
        _state.value = EditorState(
            currentBitmap = current,
            originalBitmap = current,
            documentWidth = current.width,
            documentHeight = current.height,
            sourceName = sourceName,
            layers = listOf(baseLayer),
            selectedLayerId = baseLayer.id,
            activeProjectName = sourceName?.substringBeforeLast('.')?.ifBlank { "Untitled project" } ?: "Untitled project",
            projects = _state.value.projects,
            isLoading = false,
        )
        persistCurrentSession(includeOriginal = true)
        persistCurrentProject(createIfMissing = true)
    }

    private fun abandonPreview(restoreBase: Boolean) {
        previewJob?.cancel()
        previewGeneration++
        clearPreviewRenderBase()
        val base = previewBase
        previewBase = null
        pendingPreview = null
        adjustmentValues.clear()
        if (restoreBase && base != null) {
            _state.update { it.copy(currentBitmap = base, isProcessing = false) }
        }
    }

    private fun cancelOperationAndRestore() {
        if (operationJob?.isActive == true) operationJob?.cancel()
        operationRestoreBitmap?.let { restore ->
            _state.update {
                it.copy(
                    currentBitmap = restore,
                    documentWidth = restore.width,
                    documentHeight = restore.height,
                    isProcessing = false,
                )
            }
        }
        operationRestoreBitmap = null
    }

    private fun restoreFailedOperation() {
        operationRestoreBitmap?.let { restore ->
            _state.update {
                it.copy(
                    currentBitmap = restore,
                    documentWidth = restore.width,
                    documentHeight = restore.height,
                    currentTool = EditorTool.NONE,
                    isProcessing = false,
                )
            }
        }
        operationRestoreBitmap = null
        previewBase = null
        pendingPreview = null
        adjustmentValues.clear()
        clearPreviewRenderBase()
    }

    private fun pushUndo(bitmap: Bitmap) {
        undoStack.addLast(bitmap)
        trimHistory(undoStack, bitmap)
        redoStack.clear()
    }

    private fun trimHistory(stack: ArrayDeque<Bitmap>, reference: Bitmap) {
        val pixels = reference.width.toLong() * reference.height.toLong()
        val limit = when {
            pixels > 8_000_000L -> 1
            pixels > 4_000_000L -> 2
            pixels > 2_000_000L -> 4
            else -> 10
        }
        while (stack.size > limit) stack.removeFirst()
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    private suspend fun obtainPreviewRenderBase(base: Bitmap): Bitmap {
        previewRenderBase?.let { return it }
        return ImageProcessor.createPreview(base).also { previewRenderBase = it }
    }

    private fun clearPreviewRenderBase() {
        // A canceled CPU render can still be reading this bitmap, so avoid eager recycle.
        previewRenderBase = null
    }

    private fun persistCurrentSession(includeOriginal: Boolean = false) {
        val bitmap = _state.value.currentBitmap ?: return
        val original = if (includeOriginal || repository.sessionOriginalFile() == null) {
            _state.value.originalBitmap
        } else {
            null
        }
        val sourceName = _state.value.sourceName
        val hasUnsavedChanges = _state.value.hasUnsavedChanges
        persistenceJob?.cancel()
        persistenceJob = viewModelScope.launch {
            delay(300)
            runCatching { repository.persistSession(bitmap, original) }
                .onSuccess { file ->
                    savedStateHandle[SESSION_PATH] = file.absolutePath
                    savedStateHandle[SOURCE_NAME] = sourceName
                    savedStateHandle[HAS_UNSAVED_CHANGES] = hasUnsavedChanges
                }
        }
    }

    private fun restoreSessionIfAvailable() {
        val path = savedStateHandle.get<String>(SESSION_PATH) ?: return
        val file = File(path)
        if (!file.exists()) return
        operationJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            runCatching {
                val restored = repository.loadBitmap(file)
                val original = repository.sessionOriginalFile()?.let { repository.loadBitmap(it) }
                    ?: restored.copyArgb()
                restored to original
            }
                .onSuccess { (restored, original) ->
                    _state.value = EditorState(
                        currentBitmap = restored,
                        originalBitmap = original,
                        documentWidth = restored.width,
                        documentHeight = restored.height,
                        sourceName = savedStateHandle.get<String>(SOURCE_NAME) ?: "Recovered session",
                        layers = listOf(EditorLayer(name = "Recovered image", type = LayerType.BASE, bitmap = restored, locked = true)),
                        activeProjectName = savedStateHandle.get<String>(SOURCE_NAME)?.substringBeforeLast('.') ?: "Recovered session",
                        projects = _state.value.projects,
                        isLoading = false,
                        hasUnsavedChanges = savedStateHandle.get<Boolean>(HAS_UNSAVED_CHANGES) ?: true,
                        message = EditorMessage(text = "Recovered your previous editing session"),
                    )
                }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        savedStateHandle.remove<String>(SESSION_PATH)
                        savedStateHandle.remove<String>(SOURCE_NAME)
                        savedStateHandle.remove<Boolean>(HAS_UNSAVED_CHANGES)
                        repository.clearSession()
                        _state.value = EditorState()
                    }
                }
        }
    }

    private suspend fun renderPreview(base: Bitmap, request: PreviewRequest): Bitmap = when (request) {
        is PreviewRequest.Adjustments -> ImageProcessor.applyAdjustments(base, request.values)
        is PreviewRequest.Filter -> ImageProcessor.applyFilter(base, request.preset, request.intensity)
        is PreviewRequest.Curves -> AdvancedImageProcessor.applyToneCurve(base, request.settings)
        is PreviewRequest.Hsl -> AdvancedImageProcessor.applySelectiveHsl(base, request.settings)
        is PreviewRequest.ColorGrade -> AdvancedImageProcessor.applyColorGrade(base, request.settings)
        is PreviewRequest.Effect -> AdvancedImageProcessor.applyCreativeEffect(base, request.settings)
        is PreviewRequest.Lens -> AdvancedImageProcessor.applyLens(base, request.settings)
        is PreviewRequest.Focus -> AdvancedImageProcessor.applyFocus(base, request.settings)
        is PreviewRequest.Perspective -> AdvancedImageProcessor.applyPerspective(base, request.settings)
        is PreviewRequest.Straighten -> ImageProcessor.rotate(base, request.degrees)
        is PreviewRequest.Beauty -> ImageProcessor.applyBeauty(base, request.smooth, request.whiten)
        is PreviewRequest.Masked -> StandoutImageProcessor.applySelectiveMask(base, request.settings)
        is PreviewRequest.Cutout -> StandoutImageProcessor.applyCutout(base, request.settings)
        is PreviewRequest.Recipe -> StandoutImageProcessor.applyRecipe(base, request.settings)
        is PreviewRequest.Product -> StandoutImageProcessor.createProductImage(base, request.settings)
    }

    private sealed interface PreviewRequest {
        data class Adjustments(val values: Map<AdjustmentType, Float>) : PreviewRequest
        data class Filter(val preset: FilterPreset, val intensity: Float) : PreviewRequest
        data class Curves(val settings: ToneCurveSettings) : PreviewRequest
        data class Hsl(val settings: SelectiveHslSettings) : PreviewRequest
        data class ColorGrade(val settings: ColorGradeSettings) : PreviewRequest
        data class Effect(val settings: EffectSettings) : PreviewRequest
        data class Lens(val settings: LensSettings) : PreviewRequest
        data class Focus(val settings: FocusSettings) : PreviewRequest
        data class Perspective(val settings: PerspectiveSettings) : PreviewRequest
        data class Straighten(val degrees: Float) : PreviewRequest
        data class Beauty(val smooth: Float, val whiten: Float) : PreviewRequest
        data class Masked(val settings: SelectiveMaskSettings) : PreviewRequest
        data class Cutout(val settings: CutoutSettings) : PreviewRequest
        data class Recipe(val settings: RecipeSettings) : PreviewRequest
        data class Product(val settings: ProductStudioSettings) : PreviewRequest

        fun isNoOp(): Boolean = when (this) {
            is Adjustments -> values.all { (type, value) -> value == type.default }
            is Filter -> preset == FilterPreset.ORIGINAL || intensity <= 0f
            is Curves -> settings.isIdentity()
            is Hsl -> settings.isIdentity()
            is ColorGrade -> settings.isIdentity()
            is Effect -> settings.isIdentity()
            is Lens -> settings.isIdentity()
            is Focus -> settings.isIdentity()
            is Perspective -> settings.isIdentity()
            is Straighten -> degrees == 0f
            is Beauty -> smooth == 0f && whiten == 0f
            is Masked -> settings.amount == settings.adjustment.default
            is Cutout -> false
            is Recipe -> settings.intensity <= 0f
            is Product -> false
        }
    }

    private fun showProcessingError(error: Throwable) {
        _state.update {
            it.copy(
                isProcessing = false,
                message = EditorMessage(text = processingErrorMessage(error)),
            )
        }
    }

    private fun processingErrorMessage(error: Throwable): String = if (error is OutOfMemoryError) {
        "This edit needs more memory than the device currently has"
    } else {
        error.message ?: "The edit could not be applied"
    }

    override fun onCleared() {
        previewJob?.cancel()
        operationJob?.cancel()
        batchJob?.cancel()
        persistenceJob?.cancel()
        projectPersistenceJob?.cancel()
        operationRestoreBitmap = null
        clearPreviewRenderBase()
        clearHistory()
        super.onCleared()
    }

    companion object {
        private const val SESSION_PATH = "active_session_path"
        private const val SOURCE_NAME = "active_source_name"
        private const val HAS_UNSAVED_CHANGES = "active_session_has_unsaved_changes"
        private const val MAX_RESIZE_PIXELS = 12_000_000L
        private const val MAX_LAYERS = 16
        private val EXPENSIVE_ADJUSTMENTS = setOf(
            AdjustmentType.CLARITY,
            AdjustmentType.NOISE_REDUCTION,
            AdjustmentType.BLUR,
            AdjustmentType.SHARPNESS,
        )
    }
}
