package com.miladtak.japo.projects

import android.content.Context
import com.miladtak.japo.layers.Layer
import com.miladtak.japo.timeline.TimelineClip
import com.miladtak.japo.video.VideoProject
import org.json.JSONArray
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
            put("clips", JSONArray().apply {
                project.clips.forEach { clip ->
                    put(JSONObject().apply {
                        put("id", clip.id)
                        put("sourceUri", clip.sourceUri)
                        put("startMs", clip.startMs)
                        put("endMs", clip.endMs)
                        put("order", clip.order)
                    })
                }
            })
            put("layers", JSONArray().apply {
                project.layers.forEach { layer ->
                    put(JSONObject().apply {
                        put("id", layer.id)
                        put("name", layer.name)
                        put("visible", layer.visible)
                        put("opacity", layer.opacity)
                    })
                }
            })
        }
        prefs.edit().putString(project.id, json.toString()).apply()
    }

    fun load(id: String): VideoProject? {
        val raw = prefs.getString(id, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val clipsJson = o.optJSONArray("clips") ?: JSONArray()
            val clips = buildList {
                for (i in 0 until clipsJson.length()) {
                    val c = clipsJson.getJSONObject(i)
                    add(TimelineClip(c.getString("id"), c.getString("sourceUri"), c.getLong("startMs"), c.getLong("endMs"), c.optInt("order", i)))
                }
            }
            val layersJson = o.optJSONArray("layers") ?: JSONArray()
            val layers = buildList {
                for (i in 0 until layersJson.length()) {
                    val layer = layersJson.getJSONObject(i)
                    add(
                        Layer(
                            layer.getString("id"),
                            layer.getString("name"),
                            layer.optBoolean("visible", true),
                            layer.optDouble("opacity", 1.0).toFloat()
                        )
                    )
                }
            }
            VideoProject(
                o.getString("id"),
                o.getString("name"),
                o.optString("sourceUri").ifBlank { null },
                o.optLong("createdAt"),
                o.optLong("durationMs"),
                clips,
                layers
            )
        }.getOrNull()
    }
}
