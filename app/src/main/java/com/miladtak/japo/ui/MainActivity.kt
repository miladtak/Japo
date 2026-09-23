package com.miladtak.japo.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.ui.PlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miladtak.japo.R
import com.miladtak.japo.decoder.Media3VideoDecoder
import com.miladtak.japo.logging.ErrorLogStore
import com.miladtak.japo.projects.ProjectStore
import com.miladtak.japo.video.VideoProject
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var decoder: Media3VideoDecoder
    private lateinit var logs: ErrorLogStore
    private lateinit var projects: ProjectStore
    private lateinit var status: TextView
    private lateinit var playButton: Button
    private lateinit var seekBar: SeekBar
    private val handler = Handler(Looper.getMainLooper())

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importVideo(uri)
    }

    private val progressTask = object : Runnable {
        override fun run() {
            val duration = decoder.duration()
            if (duration > 0L) {
                seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                seekBar.progress = decoder.position().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
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
        logs = ErrorLogStore(this)
        projects = ProjectStore(this)
        decoder = Media3VideoDecoder(this)
        findViewById<PlayerView>(R.id.playerView).player = decoder.player()

        findViewById<Button>(R.id.importButton).setOnClickListener { picker.launch(arrayOf("video/*")) }
        playButton.setOnClickListener {
            if (decoder.isPlaying()) { decoder.pause(); playButton.setText(R.string.play) }
            else { decoder.play(); playButton.setText(R.string.pause) }
        }
        findViewById<Button>(R.id.restartButton).setOnClickListener { decoder.restart() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveCurrentProject() }
        findViewById<Button>(R.id.logButton).setOnClickListener { showErrorLog() }

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
            status.text = getString(R.string.status_imported)
            playButton.setText(R.string.play)
        } catch (e: Exception) {
            logs.add("import", "Unable to import video", e)
            status.text = e.message ?: "Import failed"
        }
    }

    private fun saveCurrentProject() {
        try {
            val uri = decoder.currentUri() ?: error("ابتدا یک ویدیو وارد کنید.")
            val id = UUID.randomUUID().toString()
            projects.save(VideoProject(id, "Project " + id.take(8), uri.toString(), durationMs = decoder.duration()))
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
        MaterialAlertDialogBuilder(this).setTitle(R.string.error_log).setMessage(message)
            .setPositiveButton(R.string.close, null).show()
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressTask)
        decoder.release()
        super.onDestroy()
    }
}
