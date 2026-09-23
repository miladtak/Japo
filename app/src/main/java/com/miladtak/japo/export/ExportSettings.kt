package com.miladtak.japo.export

data class ExportSettings(
    val format: String = "MP4",
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30
)
