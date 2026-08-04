package com.example.ananas.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class ProjectRepository(private val context: Context) {
    private val projectsDirectory = File(context.filesDir, "projects").apply { mkdirs() }

    suspend fun listProjects(): List<ProjectSummary> = withContext(Dispatchers.IO) {
        projectsDirectory.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.mapNotNull(::readSummary)
            ?.sortedByDescending(ProjectSummary::updatedAt)
            ?.toList()
            ?: emptyList()
    }

    suspend fun saveProject(
        projectId: String?,
        name: String,
        original: Bitmap,
        documentWidth: Int,
        documentHeight: Int,
        layers: List<EditorLayer>,
    ): ProjectSummary = withContext(Dispatchers.IO) {
        val id = projectId ?: UUID.randomUUID().toString()
        val directory = File(projectsDirectory, id)
        val temporary = File(projectsDirectory, "$id.tmp")
        temporary.deleteRecursively()
        temporary.mkdirs()

        writePng(original, File(temporary, ORIGINAL_FILE))
        val layersArray = JSONArray()
        layers.forEachIndexed { index, layer ->
            val fileName = "layer_${index}_${layer.id}.png"
            writePng(layer.bitmap, File(temporary, fileName))
            layersArray.put(JSONObject().apply {
                put("id", layer.id)
                put("name", layer.name)
                put("type", layer.type.name)
                put("file", fileName)
                put("visible", layer.visible)
                put("locked", layer.locked)
                put("opacity", layer.opacity.toDouble())
                put("blendMode", layer.blendMode.name)
            })
        }

        val composite = StandoutImageProcessor.compositeLayers(documentWidth, documentHeight, layers)
        val thumb = createThumbnail(composite)
        writeJpeg(thumb, File(temporary, THUMBNAIL_FILE), 84)
        if (thumb !== composite && !thumb.isRecycled) thumb.recycle()
        if (!composite.isRecycled) composite.recycle()

        val updatedAt = System.currentTimeMillis()
        val metadata = JSONObject().apply {
            put("version", PROJECT_VERSION)
            put("id", id)
            put("name", name.ifBlank { "Untitled project" })
            put("updatedAt", updatedAt)
            put("width", documentWidth)
            put("height", documentHeight)
            put("layers", layersArray)
        }
        File(temporary, METADATA_FILE).writeText(metadata.toString(2), Charsets.UTF_8)

        directory.deleteRecursively()
        if (!temporary.renameTo(directory)) {
            temporary.copyRecursively(directory, overwrite = true)
            temporary.deleteRecursively()
        }
        ProjectSummary(
            id = id,
            name = name.ifBlank { "Untitled project" },
            updatedAt = updatedAt,
            width = documentWidth,
            height = documentHeight,
            layerCount = layers.size,
            thumbnailPath = File(directory, THUMBNAIL_FILE).absolutePath,
        )
    }

    suspend fun loadProject(projectId: String): ProjectDocument = withContext(Dispatchers.IO) {
        val directory = File(projectsDirectory, projectId)
        val metadataFile = File(directory, METADATA_FILE)
        if (!metadataFile.exists()) throw IOException("Project metadata is missing")
        val metadata = JSONObject(metadataFile.readText(Charsets.UTF_8))
        val original = BitmapFactory.decodeFile(File(directory, ORIGINAL_FILE).absolutePath)
            ?: throw IOException("Project original image is missing")
        val layerJson = metadata.getJSONArray("layers")
        val layers = buildList {
            for (index in 0 until layerJson.length()) {
                val item = layerJson.getJSONObject(index)
                val bitmap = BitmapFactory.decodeFile(File(directory, item.getString("file")).absolutePath)
                    ?: throw IOException("A project layer is damaged")
                add(EditorLayer(
                    id = item.optLong("id", System.nanoTime()),
                    name = item.optString("name", "Layer ${index + 1}"),
                    type = enumValueOrDefault(item.optString("type"), LayerType.RASTER),
                    bitmap = bitmap,
                    visible = item.optBoolean("visible", true),
                    locked = item.optBoolean("locked", false),
                    opacity = item.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                    blendMode = enumValueOrDefault(item.optString("blendMode"), BlendModeOption.NORMAL),
                ))
            }
        }
        val summary = ProjectSummary(
            id = projectId,
            name = metadata.optString("name", "Untitled project"),
            updatedAt = metadata.optLong("updatedAt", directory.lastModified()),
            width = metadata.optInt("width", original.width),
            height = metadata.optInt("height", original.height),
            layerCount = layers.size,
            thumbnailPath = File(directory, THUMBNAIL_FILE).absolutePath,
        )
        ProjectDocument(summary, original, layers)
    }

    suspend fun duplicateProject(projectId: String): ProjectSummary = withContext(Dispatchers.IO) {
        val document = loadProject(projectId)
        try {
            saveProject(
                projectId = null,
                name = "${document.summary.name} copy",
                original = document.originalBitmap,
                documentWidth = document.summary.width,
                documentHeight = document.summary.height,
                layers = document.layers,
            )
        } finally {
            if (!document.originalBitmap.isRecycled) document.originalBitmap.recycle()
            document.layers.forEach { layer ->
                if (layer.bitmap !== document.originalBitmap && !layer.bitmap.isRecycled) layer.bitmap.recycle()
            }
        }
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        File(projectsDirectory, projectId).deleteRecursively()
        Unit
    }

    fun thumbnailFile(projectId: String): File = File(projectsDirectory, "$projectId/$THUMBNAIL_FILE")

    private fun readSummary(directory: File): ProjectSummary? = runCatching {
        val metadata = JSONObject(File(directory, METADATA_FILE).readText(Charsets.UTF_8))
        ProjectSummary(
            id = metadata.getString("id"),
            name = metadata.optString("name", "Untitled project"),
            updatedAt = metadata.optLong("updatedAt", directory.lastModified()),
            width = metadata.optInt("width", 0),
            height = metadata.optInt("height", 0),
            layerCount = metadata.optJSONArray("layers")?.length() ?: 0,
            thumbnailPath = File(directory, THUMBNAIL_FILE).absolutePath,
        )
    }.getOrNull()

    private fun createThumbnail(bitmap: Bitmap): Bitmap {
        val maxDimension = 720f
        val scale = minOf(1f, maxDimension / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1))
        return if (scale >= 0.999f) bitmap.copyArgb() else Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("Unable to save project layer")
        }
    }

    private fun writeJpeg(bitmap: Bitmap, file: File, quality: Int) {
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) throw IOException("Unable to save project thumbnail")
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    companion object {
        private const val PROJECT_VERSION = 1
        private const val METADATA_FILE = "project.json"
        private const val ORIGINAL_FILE = "original.png"
        private const val THUMBNAIL_FILE = "preview.jpg"
    }
}
