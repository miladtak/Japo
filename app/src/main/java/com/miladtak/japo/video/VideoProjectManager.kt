package com.miladtak.japo.video

import java.util.UUID

class VideoProjectManager {
    fun create(name: String, sourceUri: String, durationMs: Long): VideoProject =
        VideoProject(UUID.randomUUID().toString(), name, sourceUri, durationMs = durationMs)
}
