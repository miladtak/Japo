package com.miladtak.japo.models

import android.content.Context

class BundledModelManager(private val context: Context) : ModelManager {
    override fun installedModels(): List<String> {
        val bundled = runCatching { context.assets.list("models")?.toList().orEmpty() }.getOrDefault(emptyList())
        return bundled.sorted()
    }
}
