package com.miladtak.japo.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.ui.PlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miladtak.japo.R
import com.miladtak.japo.decoder.Media3VideoDecoder
import com.miladtak.japo.export.ExportFilter
import com.miladtak.japo.export.ExportRequest
import com.miladtak.japo.export.VideoExportManager
import com.miladtak.japo.export.ProcessedVideoExporter
import com.miladtak.japo.export.ProcessedVideoExportRequest
import com.miladtak.japo.processing.FrameProcessingConfig
import com.miladtak.japo.processing.StyleMode
import com.miladtak.japo.logging.ErrorLogStore
import com.miladtak.japo.layers.Layer
import com.miladtak.japo.layers.LayerController
import com.miladtak.japo.timeline.TimelineClip
import com.miladtak.japo.timeline.TimelineController
import com.miladtak.japo.projects.ProjectStore
import com.miladtak.japo.segmentation.MlKitPersonSegmenter
import com.miladtak.japo.video.VideoProject
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var decoder: Media3VideoDecoder
    private lateinit var logs: ErrorLogStore
    private lateinit var projects: ProjectStore
    private lateinit var segmenter: MlKitPersonSegmenter
    private lateinit var exporter: VideoExportManager
    private lateinit var processedExporter: ProcessedVideoExporter
    private lateinit var status: TextView
    private lateinit var playButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var startSeconds: EditText
    private lateinit var endSeconds: EditText
    private lateinit var filterSpinner: Spinner
    private lateinit var exportButton: Button
    private lateinit var chromaColor: EditText
    private lateinit var timelineText: TextView
    private lateinit var timelineController: TimelineController
    private lateinit var layerController: LayerController
    private val handler = Handler(Looper.getMainLooper())

    private var pendingCaptureUri: Uri? = null
    private var selectedClipId: String? = null
    private var selectedLayerId: String? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importVideo(uri)
    }

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = pendingCaptureUri
        if (result.resultCode == RESULT_OK && uri != null) {
            importVideo(uri)
        } else if (uri != null) {
            contentResolver.delete(uri, null, null)
            status.text = getString(R.string.capture_failed)
        }
        pendingCaptureUri = null
    }

    private val progressTask = object : Runnable {
        override fun run() {
            if (::decoder.isInitialized) {
                val duration = decoder.duration()
                if (duration > 0L) {
                    seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    seekBar.progress = decoder.position().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
            }
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.statusText)
        playButton = findViewById(R.id.playButton)
        seekBar = findViewById(R.id.seekBar)
        startSeconds = findViewById(R.id.startSeconds)
        endSeconds = findViewById(R.id.endSeconds)
        filterSpinner = findViewById(R.id.filterSpinner)
        exportButton = findViewById(R.id.exportButton)
        chromaColor = findViewById(R.id.chromaColor)
        timelineText = findViewById(R.id.timelineText)
        timelineController = TimelineController()
        layerController = LayerController()

        logs = ErrorLogStore(this)
        projects = ProjectStore(this)
        segmenter = MlKitPersonSegmenter()
        exporter = VideoExportManager(this)
        processedExporter = ProcessedVideoExporter(this)
        decoder = Media3VideoDecoder(this)
        findViewById<PlayerView>(R.id.playerView).player = decoder.player()

        val filterLabels = listOf("بدون فیلتر", "سیاه و سفید", "معکوس", "روشنایی", "کنتراست", "Anime سبک", "Pencil سبک", "Ink سبک", "Watercolor سبک", "Comic سبک", "Cartoon سبک", "Sketch سبک", "Oil سبک", "Illustration سبک")
        filterSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterLabels)

        findViewById<Button>(R.id.importButton).setOnClickListener {
            picker.launch(arrayOf("video/*"))
        }
        findViewById<Button>(R.id.captureButton).setOnClickListener {
            captureVideo()
        }
        playButton.setOnClickListener {
            if (decoder.isPlaying()) {
                decoder.pause()
                playButton.setText(R.string.play)
            } else {
                decoder.play()
                playButton.setText(R.string.pause)
            }
        }
        findViewById<Button>(R.id.restartButton).setOnClickListener { decoder.restart() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveCurrentProject() }
        findViewById<Button>(R.id.undoButton).setOnClickListener { if (timelineController.undo()) refreshTimeline() }
        findViewById<Button>(R.id.redoButton).setOnClickListener { if (timelineController.redo()) refreshTimeline() }
        findViewById<Button>(R.id.deleteClipButton).setOnClickListener { selectedClipId?.let { id -> if (timelineController.remove(id)) { selectedClipId = timelineController.snapshot().firstOrNull()?.id; refreshTimeline() } } }
        findViewById<Button>(R.id.splitClipButton).setOnClickListener { splitSelectedClip() }
        findViewById<Button>(R.id.moveClipLeftButton).setOnClickListener { moveSelectedClip(-1) }
        findViewById<Button>(R.id.moveClipRightButton).setOnClickListener { moveSelectedClip(1) }
        findViewById<Button>(R.id.addLayerButton).setOnClickListener { addLayer() }
        findViewById<Button>(R.id.removeLayerButton).setOnClickListener { selectedLayerId?.let { id -> if (layerController.remove(id)) { selectedLayerId = layerController.snapshot().firstOrNull()?.id; refreshLayers() } } }
        findViewById<Button>(R.id.toggleLayerButton).setOnClickListener { selectedLayerId?.let { id -> layerController.snapshot().firstOrNull { it.id == id }?.let { layerController.setVisible(id, !it.visible); refreshLayers() } } }
        findViewById<Button>(R.id.layerOpacityButton).setOnClickListener { setSelectedLayerOpacity() }
        findViewById<Button>(R.id.layerUpButton).setOnClickListener { moveSelectedLayer(-1) }
        findViewById<Button>(R.id.layerDownButton).setOnClickListener { moveSelectedLayer(1) }
        findViewById<Button>(R.id.logButton).setOnClickListener { showErrorLog() }
        findViewById<Button>(R.id.segmentButton).setOnClickListener { segmentCurrentFrame() }
        findViewById<Button>(R.id.chromaButton).setOnClickListener { chromaCurrentFrame() }
        exportButton.setOnClickListener { exportVideo() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) decoder.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        handler.post(progressTask)
    }

    private fun importVideo(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            decoder.attach(uri)
            val duration = decoder.duration()
            if (duration > 0L) {
                val clip = TimelineClip(UUID.randomUUID().toString(), uri.toString(), 0L, duration)
                timelineController.add(clip)
                selectedClipId = clip.id
                layerController.add(Layer("video-" + clip.id.take(8), "Video"))
                selectedLayerId = layerController.snapshot().lastOrNull()?.id
                refreshTimeline()
                refreshLayers()
            }
            status.text = getString(R.string.status_imported)
            playButton.setText(R.string.play)
        } catch (e: Exception) {
            logs.add("import", "Unable to import video", e)
            status.text = e.message ?: "Import failed"
        }
    }

    private fun captureVideo() {
        val values = android.content.ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Japo_capture_" + System.currentTimeMillis() + ".mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Japo")
            }
        }
        pendingCaptureUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        val uri = pendingCaptureUri
        if (uri == null) {
            status.text = getString(R.string.capture_failed)
            return
        }
        val intent = Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        captureLauncher.launch(intent)
    }

    private fun exportVideo() {
        val source = decoder.currentUri()
        if (source == null) {
            status.text = "ابتدا یک ویدیو وارد کنید."
            return
        }
        val duration = decoder.duration()
        val start = parseSeconds(startSeconds.text.toString()).coerceAtLeast(0L)
        val enteredEnd = parseSeconds(endSeconds.text.toString())
        val end = if (enteredEnd > 0L) enteredEnd.coerceAtMost(duration) else duration
        if (duration <= 0L || start >= end) {
            status.text = "بازه خروجی نامعتبر است."
            return
        }

        val filter = when (filterSpinner.selectedItemPosition) {
            1 -> ExportFilter.GRAYSCALE
            2 -> ExportFilter.INVERT
            3 -> ExportFilter.BRIGHT
            4 -> ExportFilter.CONTRAST
            5 -> ExportFilter.ANIME
            6 -> ExportFilter.PENCIL
            7 -> ExportFilter.INK
            8 -> ExportFilter.WATERCOLOR
            9 -> ExportFilter.COMIC
            10 -> ExportFilter.CARTOON
            11 -> ExportFilter.SKETCH
            12 -> ExportFilter.OIL
            13 -> ExportFilter.ILLUSTRATION
            else -> ExportFilter.NONE
        }

        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val output = File(outputDir, "Japo_" + System.currentTimeMillis() + ".mp4")
        exportButton.isEnabled = false
        status.text = getString(R.string.exporting, 0)

        val style = when (filter) {
            ExportFilter.ANIME -> StyleMode.ANIME
            ExportFilter.PENCIL -> StyleMode.PENCIL
            ExportFilter.INK -> StyleMode.INK
            ExportFilter.WATERCOLOR -> StyleMode.WATERCOLOR
            ExportFilter.COMIC -> StyleMode.COMIC
            ExportFilter.CARTOON -> StyleMode.CARTOON
            ExportFilter.SKETCH -> StyleMode.SKETCH
            ExportFilter.OIL -> StyleMode.OIL
            ExportFilter.ILLUSTRATION -> StyleMode.ILLUSTRATION
            else -> StyleMode.NONE
        }
        if (style != StyleMode.NONE) {
            try {
                processedExporter.export(
                    ProcessedVideoExportRequest(
                        source = source,
                        output = output,
                        startMs = start,
                        endMs = end,
                        config = FrameProcessingConfig(style = style, styleStrength = 0.65f)
                    ),
                    onProgress = { percent -> runOnUiThread { status.text = getString(R.string.exporting, percent) } },
                    onComplete = { file ->
                        val published = publishExport(file)
                        runOnUiThread {
                            exportButton.isEnabled = true
                            status.text = getString(R.string.export_done, published)
                        }
                    },
                    onError = { error ->
                        logs.add("processed-export", "Processed video export failed", error)
                        runOnUiThread {
                            exportButton.isEnabled = true
                            status.text = getString(R.string.export_failed, error.message ?: error.javaClass.simpleName)
                        }
                    }
                )
            } catch (e: Exception) {
                logs.add("processed-export", "Unable to start processed export", e)
                exportButton.isEnabled = true
                status.text = getString(R.string.export_failed, e.message ?: "unknown error")
            }
            return
        }

        try {
            exporter.export(
                ExportRequest(source, output, start, end, filter),
                onProgress = { percent ->
                    runOnUiThread { status.text = getString(R.string.exporting, percent) }
                },
                onComplete = { file ->
                    val published = publishExport(file)
                    runOnUiThread {
                        exportButton.isEnabled = true
                        status.text = getString(R.string.export_done, published)
                    }
                },
                onError = { error ->
                    logs.add("export", "Video export failed", error)
                    runOnUiThread {
                        exportButton.isEnabled = true
                        status.text = getString(R.string.export_failed, error.message ?: error.javaClass.simpleName)
                    }
                }
            )
        } catch (e: Exception) {
            logs.add("export", "Unable to start export", e)
            exportButton.isEnabled = true
            status.text = getString(R.string.export_failed, e.message ?: "unknown error")
        }
    }

    private fun publishExport(file: File): String {
        if (android.os.Build.VERSION.SDK_INT < 29) return file.absolutePath
        return runCatching {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Japo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return file.absolutePath
            contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Cannot open gallery output stream.")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            file.delete()
            uri.toString()
        }.getOrElse { file.absolutePath }
    }

    private fun parseSeconds(value: String): Long =
        value.trim().toDoubleOrNull()?.let { (it * 1000.0).toLong() } ?: 0L

    private fun segmentCurrentFrame() {
        val uri = decoder.currentUri()
        if (uri == null) {
            status.text = "ابتدا یک ویدیو وارد کنید."
            return
        }
        status.text = "در حال تشخیص انسان..."
        Thread {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                val frame = retriever.getFrameAtTime(
                    decoder.position() * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                retriever.release()
                if (frame == null) error("فریم فعلی قابل خواندن نیست.")
                runOnUiThread {
                    segmenter.segment(
                        frame,
                        onSuccess = { mask ->
                            status.text = "تشخیص انسان انجام شد."
                            showMask(mask)
                        },
                        onFailure = { error ->
                            logs.add("segmentation", "Segmentation failed", error)
                            status.text = error.message ?: "Segmentation failed"
                        }
                    )
                }
            } catch (e: Exception) {
                logs.add("segmentation", "Unable to extract current frame", e)
                runOnUiThread { status.text = e.message ?: "Frame extraction failed" }
            }
        }.start()
    }

    private fun chromaCurrentFrame() {
        val uri = decoder.currentUri()
        if (uri == null) {
            status.text = "ابتدا یک ویدیو وارد کنید."
            return
        }
        status.text = "در حال حذف پرده سبز..."
        Thread {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                val frame = retriever.getFrameAtTime(
                    decoder.position() * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                retriever.release()
                if (frame == null) error("فریم فعلی قابل خواندن نیست.")
                val processor = com.miladtak.japo.chroma.ChromaKeyProcessor()
                val parsed = runCatching { Color.parseColor(chromaColor.text.toString().trim()) }
                    .getOrDefault(Color.rgb(20, 204, 20))
                val result = processor.removeKey(
                    frame,
                    Color.red(parsed) / 255f,
                    Color.green(parsed) / 255f,
                    Color.blue(parsed) / 255f
                )
                runOnUiThread {
                    status.text = "حذف پرده سبز انجام شد."
                    showProcessed(result)
                }
            } catch (e: Exception) {
                logs.add("chroma", "Chroma processing failed", e)
                runOnUiThread { status.text = e.message ?: "Chroma processing failed" }
            }
        }.start()
    }

    private fun showMask(mask: Bitmap) {
        val image = ImageView(this)
        image.setBackgroundColor(Color.DKGRAY)
        image.setImageBitmap(mask)
        image.adjustViewBounds = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.segmentation_result)
            .setView(image)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun showProcessed(bitmap: Bitmap) {
        val image = ImageView(this)
        image.setBackgroundColor(Color.DKGRAY)
        image.setImageBitmap(bitmap)
        image.adjustViewBounds = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chroma_result)
            .setView(image)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun splitSelectedClip() {
        val id = selectedClipId ?: return
        val clip = timelineController.snapshot().firstOrNull { it.id == id } ?: return
        val at = decoder.position()
        if (at <= clip.startMs || at >= clip.endMs) {
            status.text = "مکان پخش باید داخل کلیپ انتخاب‌شده باشد."
            return
        }
        val parts = runCatching { timelineController.split(id, at) }.getOrElse {
            logs.add("timeline", "Unable to split clip", it)
            emptyList()
        }
        if (parts.isNotEmpty()) {
            selectedClipId = parts.first().id
            refreshTimeline()
        }
    }

    private fun moveSelectedClip(delta: Int) {
        val id = selectedClipId ?: return
        val clips = timelineController.snapshot()
        val current = clips.indexOfFirst { it.id == id }
        if (current >= 0 && timelineController.move(id, current + delta)) refreshTimeline()
    }

    private fun refreshLayers() {
        val layers = layerController.snapshot()
        findViewById<TextView>(R.id.layersText).text =
            if (layers.isEmpty()) getString(R.string.layers_empty) else layers.mapIndexed { index, layer ->
                val marker = if (layer.id == selectedLayerId) "▶ " else ""
                marker + "#" + (index + 1) + " " + layer.name + " " +
                    (if (layer.visible) "✓" else "×") + " " +
                    (layer.opacity * 100f).toInt() + "%"
            }.joinToString("\n")
    }

    private fun addLayer() {
        val id = "layer-" + UUID.randomUUID().toString().take(8)
        layerController.add(Layer(id, "Layer " + (layerController.snapshot().size + 1)))
        selectedLayerId = id
        refreshLayers()
    }

    private fun setSelectedLayerOpacity() {
        val id = selectedLayerId ?: return
        val current = layerController.snapshot().firstOrNull { it.id == id }?.opacity ?: return
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText((current * 100f).toInt().toString())
            hint = "0 تا 100"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.layer_opacity)
            .setView(input)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.apply) { _, _ ->
                val percent = input.text.toString().toFloatOrNull()?.coerceIn(0f, 100f) ?: return@setPositiveButton
                layerController.setOpacity(id, percent / 100f)
                refreshLayers()
            }
            .show()
    }

    private fun moveSelectedLayer(delta: Int) {
        val id = selectedLayerId ?: return
        val layers = layerController.snapshot()
        val current = layers.indexOfFirst { it.id == id }
        if (current >= 0 && layerController.move(id, current + delta)) refreshLayers()
    }

    private fun refreshTimeline() {
        val clips = timelineController.snapshot()
        timelineText.text = if (clips.isEmpty()) getString(R.string.timeline_empty) else clips.joinToString("\n") { clip ->
            "#" + (clip.order + 1) + "  " + clip.id.take(8) + "  " + clip.startMs + "ms → " + clip.endMs + "ms"
        }
    }

    private fun saveCurrentProject() {
        try {
            val uri = decoder.currentUri() ?: error("ابتدا یک ویدیو وارد کنید.")
            val id = UUID.randomUUID().toString()
            val duration = decoder.duration()
            projects.save(VideoProject(id, "Project " + id.take(8), uri.toString(), durationMs = duration, clips = timelineController.snapshot(), layers = layerController.snapshot()))
            status.text = "پروژه ذخیره شد."
        } catch (e: Exception) {
            logs.add("project", "Unable to save project", e)
            status.text = e.message ?: "Save failed"
        }
    }

    private fun showErrorLog() {
        val items = logs.read()
        val message = if (items.isEmpty()) getString(R.string.no_errors) else
            items.takeLast(30).joinToString("\n\n") { log ->
                log.component + ": " + log.message + "\n" + (log.stackTrace ?: "")
            }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_log)
            .setMessage(message)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressTask)
        decoder.release()
        super.onDestroy()
    }
}
