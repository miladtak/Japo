package com.miladtak.japo.projects

import android.content.Context
import com.miladtak.japo.video.VideoProject
import org.json.JSONObject

class ProjectStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("projects", Context.MODE_PRIVATE)

    fun save(project: VideoProject) {
        val json = JSONObject().apply {
            put("id", project.id)
            put("name", project.name)
            put("sourceUri", project.sourceUri ?: "")
            put("createdAt", project.createdAt)
            put("durationMs", project.durationMs)
        }
        prefs.edit().putString(project.id, json.toString()).apply()
    }

    fun load(id: String): VideoProject? {
        val raw = prefs.getString(id, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            VideoProject(
                o.getString("id"),
                o.getString("name"),
                o.optString("sourceUri").ifBlank { null },
                o.optLong("createdAt"),
                o.optLong("durationMs")
            )
        }.getOrNull()
    }
}
