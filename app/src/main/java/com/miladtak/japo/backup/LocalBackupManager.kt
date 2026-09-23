package com.miladtak.japo.backup

import android.content.Context
import com.miladtak.japo.projects.ProjectStore

class LocalBackupManager(
    private val context: Context,
    private val projectStore: ProjectStore
) : BackupManager {
    override fun createBackup(projectId: String): String {
        val project = projectStore.load(projectId) ?: error("Project not found: " + projectId)
        val file = java.io.File(context.getDir("backups", Context.MODE_PRIVATE), project.id + ".txt")
        file.writeText(
            "Japo project backup\n" +
            "id=" + project.id + "\n" +
            "name=" + project.name + "\n" +
            "sourceUri=" + (project.sourceUri ?: "") + "\n" +
            "createdAt=" + project.createdAt + "\n" +
            "durationMs=" + project.durationMs + "\n"
        )
        return file.absolutePath
    }
}
